package com.quanshiguang.shiguang.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.quanshiguang.framework.biz.context.holder.LoginUserContextHolder;
import com.quanshiguang.framework.common.exception.BizException;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.framework.common.util.DateUtils;
import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.framework.common.util.NumberUtils;
import com.quanshiguang.shiguang.count.dto.FindNoteCountsByIdRspDTO;
import com.quanshiguang.shiguang.note.biz.constant.MQConstants;
import com.quanshiguang.shiguang.note.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.note.biz.convert.NoteConvert;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.NoteCollectionDO;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.NoteDO;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.NoteLikeDO;
import com.quanshiguang.shiguang.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.quanshiguang.shiguang.note.biz.domain.mapper.NoteDOMapper;
import com.quanshiguang.shiguang.note.biz.domain.mapper.NoteLikeDOMapper;
import com.quanshiguang.shiguang.note.biz.domain.mapper.TopicDOMapper;
import com.quanshiguang.shiguang.note.biz.enums.*;
import com.quanshiguang.shiguang.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.quanshiguang.shiguang.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.quanshiguang.shiguang.note.biz.model.dto.NoteOperateMqDTO;
import com.quanshiguang.shiguang.note.biz.model.dto.PublishNoteDTO;
import com.quanshiguang.shiguang.note.biz.model.vo.*;
import com.quanshiguang.shiguang.note.biz.rpc.CountRpcService;
import com.quanshiguang.shiguang.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanshiguang.shiguang.note.biz.rpc.KeyValueRpcService;
import com.quanshiguang.shiguang.note.biz.rpc.UserRpcService;
import com.quanshiguang.shiguang.note.biz.service.NoteService;
import com.quanshiguang.shiguang.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 笔记业务
 **/
@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private CountRpcService countRpcService;

    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    /**
     * 笔记发布
     *
     * @param publishNoteReqVO
     * @return
     */
    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        // 笔记类型
        Integer type = publishNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");

                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        String noteIdStr = distributedIdGeneratorRpcService.getNoteId();
        // 笔记内容 UUID
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReqVO.getContent();

        // 若用户填写了笔记内容
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
            // // RPC: 调用 KV 键值服务，存储短文本
            // boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            //
            // // 若存储失败，抛出业务异常，提示用户发布笔记失败
            // if (!isSavedSuccess) {
            //     throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            // }
        }

        // 话题
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 获取话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(noteIdStr))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(publishNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();

        // 若笔记正文未填写，不用发事务消息
        if (StringUtils.isBlank(content)) {
            processPublishContentEmptyNote(creatorId, noteDO, noteIdStr);
            return Response.success();
        }

        // 发送事务消息
        // DO 转 DTO
        PublishNoteDTO publishNoteDTO = NoteConvert.INSTANCE.convertDO2DTO(noteDO);
        publishNoteDTO.setContent(content);

        // 构建消息内容
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(publishNoteDTO)).build();
        // 发送事务消息
        TransactionSendResult transactionSendResult = rocketMQTemplate.sendMessageInTransaction(MQConstants.TOPIC_PUBLISH_NOTE_TRANSACTION, message, null);

        log.info("## 事务消息发送结果: {}", transactionSendResult.getLocalTransactionState());

        return Response.success();
    }

    /**
     * 处理笔记正文为空的情况
     * @param creatorId
     * @param noteDO
     * @param noteIdStr
     */
    private void processPublishContentEmptyNote(Long creatorId, NoteDO noteDO, String noteIdStr) {
        // 删除个人主页 - 已发布笔记列表缓存
        // TODO: 应采取灵活的策略，如果是大V, 应该直接更新缓存，而不是直接删除；普通用户则可直接删除
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(creatorId);
        redisTemplate.delete(publishedNoteListRedisKey);

        // 笔记入库存储
        noteDOMapper.insert(noteDO);

        // 延迟双删：发送延迟消息
        sendDelayDeleteRedisPublishedNoteListCacheMQ(creatorId);

        // 发送 MQ
        // 构建消息体 DTO
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(Long.valueOf(noteIdStr))
                .type(NoteOperateEnum.PUBLISH.getCode()) // 发布笔记
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记发布】MQ 发送异常: ", throwable);
            }
        });
    }

    private void sendDelayDeleteRedisPublishedNoteListCacheMQ(Long userId) {
        Message<String> message = MessageBuilder.withPayload(String.valueOf(userId))
                .build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_PUBLISHED_NOTE_LIST_REDIS_CACHE, message,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 已发布笔记列表缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("## 延时删除 Redis 已发布笔记列表缓存消息发送失败...", e);
                    }
                },
                3000, // 超时时间
                1 // 延迟级别，1 表示延时 1s
        );
    }

    /**
     * 笔记详情
     *
     * @param findNoteDetailReqVO
     * @return
     */
    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        // 查询的笔记 ID
        Long noteId = findNoteDetailReqVO.getId();

        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();

        // 先从本地缓存中查询
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspVOStrLocalCache);
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailJson)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);

            return Response.success(findNoteDetailRspVO);
        }

        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        Integer visible = noteDO.getVisible();
        checkNoteVisible(visible, userId, noteDO.getCreatorId());

        // 并发查询优化
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findById(userId), threadPoolTaskExecutor);

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    // 获取 Future 返回的结果
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    // 笔记类型
                    Integer noteType = noteDO.getType();
                    // 图文笔记图片链接(字符串)
                    String imgUrisStr = noteDO.getImgUris();
                    // 图文笔记图片链接(集合)
                    List<String> imgUris = null;
                    // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
                    if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode())
                            && StringUtils.isNotBlank(imgUrisStr)) {
                        imgUris = List.of(imgUrisStr.split(","));
                    }

                    // 构建返参 VO 实体类
                    return FindNoteDetailRspVO.builder()
                            .id(noteDO.getId())
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRspDTO.getNickName())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(noteDO.getVisible())
                            .build();

                });

        // 获取拼装后的 FindNoteDetailRspVO
        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            String noteDetailJson1 = JsonUtils.toJsonString(findNoteDetailRspVO);
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue().set(noteDetailRedisKey, noteDetailJson1, expireSeconds, TimeUnit.SECONDS);
        });

        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 笔记更新
     *
     * @param updateNoteReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        // 笔记 ID
        Long noteId = updateNoteReqVO.getId();
        // 笔记类型
        Integer type = updateNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");

                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }


        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 笔记不存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许更新笔记
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 话题
        Long topicId = updateNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);

            // 判断一下提交的话题, 是否是真实存在的
            if (StringUtils.isBlank(topicName)) throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(currUserId);
        redisTemplate.delete(Arrays.asList(noteDetailRedisKey, publishedNoteListRedisKey));

        // 更新笔记元数据表 t_note
        String content = updateNoteReqVO.getContent();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(updateNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .build();

        noteDOMapper.updateByPrimaryKey(noteDO);

        // 一致性保证：延迟双删策略
        // 异步发送延时消息
        sendDelayDeleteRedisNoteCacheMQ(Arrays.asList(noteId, currUserId));

        // // 删除本地缓存
        // LOCAL_CACHE.invalidate(noteId);
        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        // 笔记内容更新
        // 查询此篇笔记内容对应的 UUID
        NoteDO noteDO1 = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = noteDO1.getContentUuid();

        // 笔记内容是否更新成功
        boolean isUpdateContentSuccess = false;
        if (StringUtils.isBlank(content)) {
            // 若笔记内容为空，则删除 K-V 存储
            isUpdateContentSuccess = keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 若将无内容的笔记，更新为了有内容的笔记，需要重新生成 UUID
            contentUuid = StringUtils.isBlank(contentUuid) ? UUID.randomUUID().toString() : contentUuid;
            // 调用 K-V 更新短文本
            isUpdateContentSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
        }

        // 如果更新失败，抛出业务异常，回滚事务
        if (!isUpdateContentSuccess) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        return Response.success();
    }

    private void sendDelayDeleteRedisNoteCacheMQ(List<Long> noteIdAndUserId) {
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteIdAndUserId))
                .build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE, message,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("## 延时删除 Redis 笔记缓存消息发送失败...", e);
                    }
                },
                3000, // 超时时间
                1 // 延迟级别，1 表示延时 1s
        );
    }

    /**
     * 删除本地笔记缓存
     * @param noteId
     */
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    /**
     * 删除笔记
     *
     * @param deleteNoteReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        // 笔记 ID
        Long noteId = deleteNoteReqVO.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许删除笔记
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 删除缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(currUserId);
        redisTemplate.delete(Arrays.asList(noteDetailRedisKey, publishedNoteListRedisKey));

        // 逻辑删除
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        noteDOMapper.updateByPrimaryKeySelective(noteDO);

        // 延迟双删
        sendDelayDeleteRedisPublishedNoteListCacheMQ(currUserId);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        // 发送 MQ
        // 构建消息体 DTO
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .type(NoteOperateEnum.DELETE.getCode()) // 删除笔记
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_DELETE;

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记删除】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记删除】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 笔记仅对自己可见
     *
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        // 笔记 ID
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许修改笔记权限
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode()) // 可见性设置为仅对自己可见
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);

        // 若影响的行数为 0，则表示该笔记无法修改为仅自己可见
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    /**
     * 笔记置顶 / 取消置顶
     *
     * @param topNoteReqVO
     * @return
     */
    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        // 笔记 ID
        Long noteId = topNoteReqVO.getId();
        // 是否置顶
        Boolean isTop = topNoteReqVO.getIsTop();

        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();

        // 构建置顶/取消置顶 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .creatorId(currUserId) // 只有笔记所有者，才能置顶/取消置顶笔记
                .build();

        int count = noteDOMapper.updateIsTop(noteDO);

        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    /**
     * 点赞笔记
     *
     * @param likeNoteReqVO
     * @return
     */
    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        // 笔记ID
        Long noteId = likeNoteReqVO.getId();

        // 1. 校验被点赞的笔记是否存在，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 判断目标笔记，是否已经点赞过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // Roaring Bitmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants.buildRBitmapUserNoteLikeListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_like_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId);

        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);

        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        switch (noteLikeLuaResultEnum) {
            // Redis 中 Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被点赞，并异步初始化 Roaring Bitmap，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被点赞
                if (count > 0) {
                    // 异步初始化 Roaring Bitmap
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteLike2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }

                // 若目标笔记未被点赞，查询当前用户是否有点赞其他笔记，有则同步初始化 Roaring Bitmap
                batchAddNoteLike2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey);

                // 添加当前点赞笔记 ID 到 Roaring Bitmap 中
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId, expireSeconds);
            }
            // 目标笔记已经被点赞
            case NOTE_LIKED -> {
                // // 校验 ZSet 列表中是否包含被点赞的笔记ID
                // Double score = redisTemplate.opsForZSet().score(userNoteLikeZSetKey, noteId);
                //
                // if (Objects.nonNull(score)) {
                //     throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                // }
                //
                // // 若 Score 为空，则表示 ZSet 点赞列表中不存在，查询数据库校验
                // int count = noteLikeDOMapper.selectNoteIsLiked(userId, noteId);
                //
                // if (count > 0) {
                //     // 数据库里面有点赞记录，而 Redis 中 ZSet 不存在，需要重新异步初始化 ZSet
                //     asynInitUserNoteLikesZSet(userId, userNoteLikeZSetKey);
                //
                //     throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                // }

                throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
            }
        }

        // 3. 更新用户 ZSET 点赞列表
        LocalDateTime now = LocalDateTime.now();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询当前用户最新点赞的 100 篇笔记
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);

            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            // Lua 脚本路径
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
            // 返回值类型
            script2.setResultType(Long.class);

            // 若数据库中存在点赞记录，需要批量同步
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                // 构建 Lua 参数
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);

                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);

                // 再次调用 note_like_check_and_update_zset.lua 脚本，将点赞的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 若数据库中，无点赞的笔记记录，则直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score
                luaArgs.add(noteId); // 当前点赞的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs.toArray());
            }
        }

        // 4. 发送 MQ, 将点赞数据落库
        // 构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode()) // 点赞笔记
                .createTime(now)
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        String hashKey = String.valueOf(userId);

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 取消点赞笔记
     *
     * @param unlikeNoteReqVO
     * @return
     */
    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 笔记ID
        Long noteId = unlikeNoteReqVO.getId();

        // 1. 校验笔记是否真实存在，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 校验笔记是否被点赞过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // Roaring Bitmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants.buildRBitmapUserNoteLikeListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_unlike_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId);

        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(result);

        switch (noteUnlikeLuaResultEnum) {
            // Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 异步初始化 Roaring Bitmap
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    batchAddNoteLike2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey);
                });

                // 从数据库中校验笔记是否被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 未点赞，无法取消点赞操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            // Roaring Bitmap 校验目标笔记未被点赞
            case NOTE_NOT_LIKED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }

        // 3. 能走到这里，说明 Roaring Bitmap 判断已点赞，直接删除 ZSET 中已点赞的笔记 ID
        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);

        // 4. 发送 MQ, 数据更新落库
        // 构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode()) // 取消点赞笔记
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        String hashKey = String.valueOf(userId);

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 收藏笔记
     *
     * @param collectNoteReqVO
     * @return
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        // 笔记ID
        Long noteId = collectNoteReqVO.getId();

        // 1. 校验被点赞的笔记是否存在，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 判断目标笔记，是否已经收藏过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants.buildRBitmapUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_collect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId);

        // 用户收藏列表 ZSet Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);

        switch (noteCollectLuaResultEnum) {
            // Redis 中 Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被收藏，并异步初始化布隆过滤器，设置过期时间
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被收藏
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }

                // 若目标笔记未被收藏，查询当前用户是否有收藏其他笔记，有则同步初始化 Roaring Bitmap
                batchAddNoteCollect2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey);

                // 添加当前收藏笔记 ID 到 Roaring Bitmap 中
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId, expireSeconds);
            }
            // 目标笔记已经被收藏
            case NOTE_COLLECTED -> {
                // // 校验 ZSet 列表中是否包含被收藏的笔记ID
                // Double score = redisTemplate.opsForZSet().score(userNoteCollectZSetKey, noteId);
                //
                // if (Objects.nonNull(score)) {
                //     throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                // }
                //
                // // 若 Score 为空，则表示 ZSet 收藏列表中不存在，查询数据库校验
                // int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);
                //
                // if (count > 0) {
                //     // 数据库里面有收藏记录，而 Redis 中 ZSet 未初始化，需要重新异步初始化 ZSet
                //     asynInitUserNoteCollectsZSet(userId, userNoteCollectZSetKey);
                //
                //     throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                // }

                throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
            }
        }

        // 3. 更新用户 ZSET 收藏列表
        LocalDateTime now = LocalDateTime.now();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_collect_check_and_update_zset.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, NoteCollectLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询当前用户最新收藏的 300 篇笔记
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);

            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            // Lua 脚本路径
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
            // 返回值类型
            script2.setResultType(Long.class);

            // 若数据库中存在已收藏笔记记录，需要批量同步
            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                // 构建 Lua 参数
                Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);

                // 再次调用 note_collect_check_and_update_zset.lua 脚本，将当前收藏的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 若数据库中，未收藏任何笔记，则直接将当前收藏的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score 收藏时间
                luaArgs.add(noteId); // 当前收藏的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs.toArray());
            }
        }

        // 4. 发送 MQ, 将收藏数据落库
        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode()) // 收藏笔记
                .createTime(now)
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;

        String hashKey = String.valueOf(userId);

        // 异步发送顺序 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记收藏】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 取消收藏笔记
     *
     * @param unCollectNoteReqVO
     * @return
     */
    @Override
    public Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO) {
        // 笔记ID
        Long noteId = unCollectNoteReqVO.getId();

        // 1. 校验被点赞的笔记是否存在，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

        // 2. 校验笔记是否被收藏过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants.buildRBitmapUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_uncollect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId);

        NoteUnCollectLuaResultEnum noteUnCollectLuaResultEnum = NoteUnCollectLuaResultEnum.valueOf(result);

        switch (noteUnCollectLuaResultEnum) {
            // Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 异步初始化 Roaring Bitmap
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    batchAddNoteCollect2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey);
                });

                // 从数据库中校验笔记是否被收藏
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 未收藏，无法取消收藏操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
            // 校验目标笔记未被收藏
            case NOTE_NOT_COLLECTED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
        }

        // 3. 删除 ZSET 中已收藏的笔记 ID
        // 能走到这里，说明 Roaring Bitmap 判断已收藏，直接删除 ZSET 中已收藏的笔记 ID
        // 用户收藏列表 ZSet Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        redisTemplate.opsForZSet().remove(userNoteCollectZSetKey, noteId);

        // 4. 发送 MQ, 数据更新落库
        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode()) // 取消收藏笔记
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;

        String hashKey = String.valueOf(userId);

        // 异步发送顺序 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消收藏】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 获取是否点赞、收藏数据
     *
     * @param findNoteIsLikedAndCollectedReqVO
     * @return
     */
    @Override
    public Response<FindNoteIsLikedAndCollectedRspVO> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReqVO findNoteIsLikedAndCollectedReqVO) {
        Long noteId = findNoteIsLikedAndCollectedReqVO.getNoteId();

        // 已登录的用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();

        // 默认未点赞、未收藏
        boolean isLiked = false;
        boolean isCollected = false;

        // 若当前用户已登录
        if (Objects.nonNull((currUserId))) {
            // 1. 校验是否点赞
            isLiked = checkNoteIsLiked(noteId, currUserId);

            // 2. 校验是否收藏
            isCollected = checkNoteIsCollected(noteId, currUserId);
        }

        return Response.success(FindNoteIsLikedAndCollectedRspVO.builder()
                        .noteId(noteId)
                        .isLiked(isLiked)
                        .isCollected(isCollected)
                        .build());
    }

    /**
     * 用户主页 - 查询已发布的笔记列表
     *
     * @param findPublishedNoteListReqVO
     * @return
     */
    @Override
    public Response<FindPublishedNoteListRspVO> findPublishedNoteList(FindPublishedNoteListReqVO findPublishedNoteListReqVO) {
        // 目标用户ID
        Long userId = findPublishedNoteListReqVO.getUserId();
        // 游标
        Long cursor = findPublishedNoteListReqVO.getCursor();

        // 返参 VO
        FindPublishedNoteListRspVO findPublishedNoteListRspVO = null;

        // 优先查询缓存
        // 构建 Redis Key
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        // 若游标为空，表示查询的是第一页
        if (Objects.isNull(cursor)) {
            String publishedNoteListJson = redisTemplate.opsForValue().get(publishedNoteListRedisKey);

            if (StringUtils.isNotBlank(publishedNoteListJson)) {
                try {
                    log.info("## 已发布笔记列表命中了 Redis 缓存...");
                    // Json 字符串转 VO 集合
                    List<NoteItemRspVO> noteItemRspVOS = JsonUtils.parseList(publishedNoteListJson, NoteItemRspVO.class);
                    // 按笔记 ID 降序，最新发布的笔记排最前面
                    List<NoteItemRspVO> sortedList = noteItemRspVOS.stream().sorted(Comparator.comparing(NoteItemRspVO::getNoteId).reversed()).toList();

                    // 过滤出最早发布的笔记 ID，充当下一页的游标
                    Optional<Long> earliestNoteId = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).min(Long::compareTo);

                    // 如果是博主本人，需要调用计数服务，获取最新的点赞数据
                    getAndSetLatestLikeTotalIfAuthor(userId, sortedList);

                    // 批量获取笔记的点赞状态
                    batchGetAndSetNoteIsLiked(sortedList);

                    findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                            .notes(sortedList)
                            .nextCursor(earliestNoteId.orElse(null))
                            .build();
                    return Response.success(findPublishedNoteListRspVO);
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }

        // 缓存无，则查询数据库
        List<NoteDO> noteDOS = noteDOMapper.selectPublishedNoteListByUserIdAndCursor(userId, cursor);

        if (CollUtil.isNotEmpty(noteDOS)) {
            // DO 转 VO
            List<NoteItemRspVO> noteVOS = noteDOS.stream()
                    .map(noteDO -> {
                        // 获取封面图片
                        String cover = StringUtils.isNotBlank(noteDO.getImgUris()) ?
                                StringUtils.split(noteDO.getImgUris(), ",")[0] : null;

                        NoteItemRspVO noteItemRspVO = NoteItemRspVO.builder()
                                .noteId(noteDO.getId())
                                .type(noteDO.getType())
                                .creatorId(noteDO.getCreatorId())
                                .cover(cover)
                                .videoUri(noteDO.getVideoUri())
                                .title(noteDO.getTitle())
                                .isLiked(false) // 默认为未点赞状态
                                .build();
                        return noteItemRspVO;
                    }).toList();

            // Feign 调用用户服务，获取博主的用户头像、昵称
            CompletableFuture<FindUserByIdRspDTO> userFuture = CompletableFuture
                    .supplyAsync(() -> {
                        Optional<Long> creatorIdOptional = noteDOS.stream().map(NoteDO::getCreatorId).findAny();
                        return userRpcService.findById(creatorIdOptional.get());
                    }, threadPoolTaskExecutor);

            // Feign 调用计数服务，批量获取笔记点赞数
            CompletableFuture<List<FindNoteCountsByIdRspDTO>> noteCountFuture = CompletableFuture
                    .supplyAsync(() -> {
                        List<Long> noteIds = noteDOS.stream().map(NoteDO::getId).toList();
                        return countRpcService.findByNoteIds(noteIds);
                    }, threadPoolTaskExecutor);

            // 等待所有任务完成，并合并结果
            CompletableFuture.allOf(userFuture, noteCountFuture).join();

            try {
                // 获取 Future 返回结果
                FindUserByIdRspDTO findUserByIdRspDTO = userFuture.get();
                List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = noteCountFuture.get();

                if (Objects.nonNull(findUserByIdRspDTO)) {
                    // 循环 VO 集合，分别设置头像、昵称
                    noteVOS.forEach(noteItemRspVO -> {
                        noteItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                        noteItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
                    });
                }

                // 设置笔记的点赞量
                setVOListLikeTotal(noteVOS, findNoteCountsByIdRspDTOS);

                // 批量获取笔记的点赞状态
                batchGetAndSetNoteIsLiked(noteVOS);
            } catch (Exception e) {
                log.error("## 并发调用错误: ", e);
            }

            // 过滤出最早发布的笔记 ID，充当下一页的游标
            Optional<Long> earliestNoteId = noteDOS.stream().map(NoteDO::getId).min(Long::compareTo);

            findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                    .notes(noteVOS)
                    .nextCursor(earliestNoteId.orElse(null))
                    .build();

            // 同步第一页已发布笔记到 Redis
            if (Objects.isNull(cursor)) {
                syncFirstPagePublishedNoteList2Redis(noteVOS, publishedNoteListRedisKey);
            }
        }

        return Response.success(findPublishedNoteListRspVO);
    }

    /**
     * 批量获取笔记的点赞状态
     * @param noteItemRspVOS
     */
    private void batchGetAndSetNoteIsLiked(List<NoteItemRspVO> noteItemRspVOS) {
        // 当前登录用户的 ID
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 若用户已登录
        if (Objects.nonNull(loginUserId)) {
            // 提取所有需要获取点赞状态的笔记 ID
            List<Long> noteIds = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).toList();
            // 构建 Roaring Bitmap Key
            String rbitmapUserNoteLikeListKey = RedisKeyConstants.buildRBitmapUserNoteLikeListKey(loginUserId);

            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            // Lua 脚本路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_get_note_liked.lua")));
            // 返回值类型
            script.setResultType(List.class);

            // 执行 Lua 脚本，拿到返回结果
            List<Long> results = redisTemplate.execute(
                    script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteIds.toArray());

            // 若 Redis 中缓存不存在，下标 0 存放的标识为 -1
            Long hasKey = results.get(0);
            // 若 Roaring Bitmap 不存在
            if (Objects.equals(hasKey, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
                // 从数据库查询
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserIdAndNoteIds(loginUserId, noteIds);

                if (CollUtil.isEmpty(noteLikeDOS)) return;

                // DO 转 Map, 方便查询对应笔记是否点赞
                Map<Long, NoteLikeDO> noteIdIsLikedMap = noteLikeDOS.stream()
                        .collect(Collectors.toMap(NoteLikeDO::getNoteId, notelikeDO -> notelikeDO));

                // 循环 VO 集合，设置是否点赞
                noteItemRspVOS.forEach(noteItemRspVO -> {
                    Long currNoteId = noteItemRspVO.getNoteId();
                    NoteLikeDO noteLikeDO = noteIdIsLikedMap.get(currNoteId);
                    if (Objects.nonNull(noteLikeDO)) noteItemRspVO.setIsLiked(true);
                });

                // 再异步初始化 Roaring Bitmap
                threadPoolTaskExecutor.submit(() -> {
                    // 随机过期时间（1小时内）
                    long expireSeconds = 60*30 + RandomUtil.randomInt(60*30);
                    batchAddNoteLike2RBitmapAndExpire(loginUserId, expireSeconds, rbitmapUserNoteLikeListKey);
                });
                return;
            }

            // 否则，则 Roaring Bitmap 存在
            // 初始化一个字典，解析 Lua 结果，并设置每篇笔记是否被点赞
            Map<Long, Boolean> likedMap = Maps.newHashMapWithExpectedSize(noteIds.size());
            for (int i = 0; i < noteIds.size(); i++) {
                Long currNoteId = noteIds.get(i);
                Boolean isLiked = Objects.equals(results.get(i), 1L);
                likedMap.put(currNoteId, isLiked);
            }

            // 循环 VO 集合，设置是否点赞
            noteItemRspVOS.forEach(noteItemRspVO -> {
                Long currNoteId = noteItemRspVO.getNoteId();
                noteItemRspVO.setIsLiked(likedMap.get(currNoteId));
            });
        }
    }

    /**
     * 如果是博主本人，需要调用计数服务，获取最新的点赞数据
     * @param userId
     * @param sortedList
     */
    private void getAndSetLatestLikeTotalIfAuthor(Long userId, List<NoteItemRspVO> sortedList) {
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 用户已登录，并且查询的是自己
        if (Objects.nonNull(loginUserId) && Objects.equals(loginUserId, userId)) {
            List<Long> noteIds = sortedList.stream().map(NoteItemRspVO::getNoteId).toList();
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countRpcService.findByNoteIds(noteIds);

            // 设置笔记的点赞量
            setVOListLikeTotal(sortedList, findNoteCountsByIdRspDTOS);
        }
    }

    /**
     * 设置 VO 集合中每篇笔记的点赞量
     * @param noteItemRspVOS
     * @param findNoteCountsByIdRspDTOS
     */
    private static void setVOListLikeTotal(List<NoteItemRspVO> noteItemRspVOS, List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS) {
        if (CollUtil.isNotEmpty(findNoteCountsByIdRspDTOS)) {
            // DTO 集合转 Map
            Map<Long, FindNoteCountsByIdRspDTO> noteIdAndDTOMap = findNoteCountsByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, dto -> dto));

            // 循环设置 VO 集合，设置每篇笔记的点赞量
            noteItemRspVOS.forEach(noteItemRspVO -> {
                Long currNoteId = noteItemRspVO.getNoteId();
                FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = noteIdAndDTOMap.get(currNoteId);
                noteItemRspVO.setLikeTotal((Objects.nonNull(findNoteCountsByIdRspDTO) && Objects.nonNull(findNoteCountsByIdRspDTO.getLikeTotal())) ?
                        NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()) : "0");
            });
        }
    }

    /**
     * 同步第一页已发布笔记到 Redis
     * @param noteVOS
     * @param publishedNoteListRedisKey
     */
    private void syncFirstPagePublishedNoteList2Redis(List<NoteItemRspVO> noteVOS, String publishedNoteListRedisKey) {
        if (CollUtil.isEmpty(noteVOS)) return;
        // 异步同步缓存
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间，一小时以内（保底30分钟+随机秒数）
            long expireSeconds = 60*30 + RandomUtil.randomInt(60*30);
            redisTemplate.opsForValue()
                    .set(publishedNoteListRedisKey, JsonUtils.toJsonString(noteVOS), expireSeconds, TimeUnit.SECONDS);
        });
    }

    /**
     * 校验当前用户是否点赞笔记
     * @param noteId
     * @param currUserId
     * @return
     */
    private boolean checkNoteIsLiked(Long noteId, Long currUserId) {
        // 是否点赞
        boolean isLiked = false;

        // Roaring Bitmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants.buildRBitmapUserNoteLikeListKey(currUserId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_like_only_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId);

        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);

        switch (noteLikeLuaResultEnum) {
            // Redis 中 Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被点赞，并异步初始化 Roaring Bitmap，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(currUserId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被点赞
                if (count > 0) {
                    // 异步初始化 Roaring Bitmap
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteLike2RBitmapAndExpire(currUserId, expireSeconds, rbitmapUserNoteLikeListKey));
                    isLiked = true;
                }
            }
            case NOTE_LIKED -> isLiked = true; // Roaring Bitmap 判断已点赞
        }

        return isLiked;
    }

    /**
     * 校验当前用户是否收藏笔记
     * @param noteId
     * @param currUserId
     * @return
     */
    private boolean checkNoteIsCollected(Long noteId, Long currUserId) {
        // 是否收藏
        boolean isCollected = false;

        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants.buildRBitmapUserNoteCollectListKey(currUserId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_note_collect_only_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId);

        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);

        switch (noteCollectLuaResultEnum) {
            // Redis 中 Roaring Bitmap 不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被收藏，并异步初始化布隆过滤器，设置过期时间
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(currUserId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 目标笔记已经被收藏
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2RBitmapAndExpire(currUserId, expireSeconds, rbitmapUserNoteCollectListKey));
                    isCollected = true;
                }
            }
            // 目标笔记已经被收藏
            case NOTE_COLLECTED -> isCollected = true;
        }

        return isCollected;
    }

    /**
     * 异步初始化用户收藏笔记 ZSet
     * @param userId
     * @param userNoteCollectZSetKey
     */
    private void asynInitUserNoteCollectsZSet(Long userId, String userNoteCollectZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记收藏 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteCollectZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新收藏的 300 篇笔记
                List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);
                if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);

                    redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);
                }
            }
        });
    }

    /**
     * 异步初始化用户点赞笔记 ZSet
     * @param userId
     * @param userNoteLikeZSetKey
     */
    private void asynInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记点赞 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteLikeZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新点赞的 100 篇笔记
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);

                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);

                    redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                }
            }
        });
    }

    /**
     * 构建笔记收藏 ZSET Lua 脚本参数
     *
     * @param noteCollectionDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteCollectZSetLuaArgs(List<NoteCollectionDO> noteCollectionDOS, long expireSeconds) {
        int argsLength = noteCollectionDOS.size() * 2 + 1; // 每个笔记收藏关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteCollectionDO noteCollectionDO : noteCollectionDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteCollectionDO.getCreateTime()); // 收藏时间作为 score
            luaArgs[i + 1] = noteCollectionDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 构建笔记点赞 ZSET Lua 脚本参数
     *
     * @param noteLikeDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteLikeZSetLuaArgs(List<NoteLikeDO> noteLikeDOS, long expireSeconds) {
        int argsLength = noteLikeDOS.size() * 2 + 1; // 每个笔记点赞关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime()); // 点赞时间作为 score
            luaArgs[i + 1] = noteLikeDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 初始化笔记收藏布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteCollectListKey
     */
    private void batchAddNoteCollect2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId())); // 将每个收藏的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】布隆过滤器异常: ", e);
        }
    }

    /**
     * 初始化笔记收藏布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param rbitmapUserNoteCollectListKey
     */
    private void batchAddNoteCollect2RBitmapAndExpire(Long userId, long expireSeconds, String rbitmapUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId())); // 将每个收藏的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】Roaring Bitmap 异常: ", e);
        }
    }

    /**
     * 初始化笔记点赞布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteLikeListKey
     */
    private void batchAddNoteLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记点赞】布隆过滤器异常: ", e);
        }
    }

    /**
     * 初始化笔记点赞 Roaring Bitmap
     * @param userId
     * @param expireSeconds
     * @param rbitmapUserNoteLikeListKey
     */
    private void batchAddNoteLike2RBitmapAndExpire(Long userId, long expireSeconds, String rbitmapUserNoteLikeListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记点赞】Roaring Bitmap 异常: ", e);
        }
    }

    /**
     * 校验笔记是否存在，若存在，则获取笔记的发布者 ID
     * @param noteId
     */
    private Long checkNoteIsExistAndGetCreatorId(Long noteId) {
        // 先从本地缓存校验
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        // 解析 Json 字符串为 VO 对象
        FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);

        // 若本地缓存没有
        if (Objects.isNull(findNoteDetailRspVO)) {
            // 再从 Redis 中校验
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);

            String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

            // 解析 Json 字符串为 VO 对象
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);

            // 都不存在，再查询数据库校验是否存在
            if (Objects.isNull(findNoteDetailRspVO)) {
                // 笔记发布者用户 ID
                Long creatorId = noteDOMapper.selectCreatorIdByNoteId(noteId);

                // 若数据库中也不存在，提示用户
                if (Objects.isNull(creatorId)) {
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }

                // 若数据库中存在，异步同步一下缓存
                threadPoolTaskExecutor.submit(() -> {
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO.builder().id(noteId).build();
                    findNoteDetail(findNoteDetailReqVO);
                });
                return creatorId;
            }
        }

        return findNoteDetailRspVO.getCreatorId();
    }

    /**
     * 校验笔记的可见性
     * @param visible 是否可见
     * @param currUserId 当前用户 ID
     * @param creatorId 笔记创建者
     */
    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) { // 仅自己可见, 并且访问用户为笔记创建者
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    /**
     * 校验笔记的可见性（针对 VO 实体类）
     * @param userId
     * @param findNoteDetailRspVO
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.nonNull(findNoteDetailRspVO)) {
            Integer visible = findNoteDetailRspVO.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRspVO.getCreatorId());
        }
    }


}
