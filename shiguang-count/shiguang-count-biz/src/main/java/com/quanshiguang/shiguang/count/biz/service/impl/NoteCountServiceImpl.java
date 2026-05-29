package com.quanshiguang.shiguang.count.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.count.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.count.biz.domain.dataobject.NoteCountDO;
import com.quanshiguang.shiguang.count.biz.domain.mapper.NoteCountDOMapper;
import com.quanshiguang.shiguang.count.biz.service.NoteCountService;
import com.quanshiguang.shiguang.count.dto.FindNoteCountsByIdRspDTO;
import com.quanshiguang.shiguang.count.dto.FindNoteCountsByIdsReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 笔记计数业务
 **/
@Service
@Slf4j
public class NoteCountServiceImpl implements NoteCountService {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @Override
    public Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO) {
        // 需要查询的笔记 ID 集合
        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();

        // 1. 先查询 Redis 缓存
        // 构建 Redis Hash Key 集合
        List<String> hashKeys = noteIds.stream()
                .map(RedisKeyConstants::buildCountNoteKey)
                .toList();

        // 使用 Pipeline 通道，从 Redis 中批量查询笔记 Hash 计数
        List<Object> countHashes = getCountHashesByPipelineFromRedis(hashKeys);

        // 返参 DTO
        List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = Lists.newArrayList();

        // 用于存储缓存中不存在，需要查数据库的笔记 ID
        List<Long> noteIdsNeedQuery = Lists.newArrayList();

        // 循环入参中需要查询的笔记 ID 集合，构建对应 DTO, 并设置缓存中存在的计数，以及过滤出需要查数据库的笔记 ID
        for (int i = 0; i < noteIds.size(); i++) {
            Long currNoteId = noteIds.get(i);
            List<Integer> currCountHash = (List<Integer>) countHashes.get(i);

            // 点赞数、收藏数、评论数
            Integer likeTotal = currCountHash.get(0);
            Integer collectTotal = currCountHash.get(1);
            Integer commentTotal = currCountHash.get(2);

            // Hash 中存在任意一个 Field 为 null, 都需要查询数据库
            if (Objects.isNull(likeTotal) || Objects.isNull(collectTotal) || Objects.isNull(commentTotal)) {
                noteIdsNeedQuery.add(currNoteId);
            }

            // 构建 DTO
            FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = FindNoteCountsByIdRspDTO.builder()
                    .noteId(currNoteId)
                    .likeTotal(Objects.nonNull(likeTotal) ? Long.valueOf(likeTotal) : null)
                    .collectTotal(Objects.nonNull(collectTotal) ? Long.valueOf(collectTotal) : null)
                    .commentTotal(Objects.nonNull(commentTotal) ? Long.valueOf(commentTotal) : null)
                    .build();

            findNoteCountsByIdRspDTOS.add(findNoteCountsByIdRspDTO);
        }

        // 所有 Hash 计数都存在于 Redis 中，直接返参
        if (CollUtil.isEmpty(noteIdsNeedQuery)) {
            return Response.success(findNoteCountsByIdRspDTOS);
        }

        // 2. 若缓存中无，则查询数据库
        // 从数据库中批量查询过滤出的 noteIdsNeedQuery 笔记 ID
        List<NoteCountDO> noteCountDOS = noteCountDOMapper.selectByNoteIds(noteIdsNeedQuery);

        // 若数据库查询的记录不为空
        if (CollUtil.isNotEmpty(noteCountDOS)) {
            // DO 集合转 Map, 方便后续查询对应笔记 ID 的计数
            Map<Long, NoteCountDO> noteIdAndDOMap = noteCountDOS.stream()
                    .collect(Collectors.toMap(NoteCountDO::getNoteId, noteCountDO -> noteCountDO));

            // 将笔记 Hash 计数同步到 Redis 中
            syncNoteHash2Redis(findNoteCountsByIdRspDTOS, noteIdAndDOMap);

            // 针对 DTO 中为 null 的计数字段，循环设置从数据库中查询到的计数
            for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);

                if (Objects.isNull(likeTotal))
                    findNoteCountsByIdRspDTO.setLikeTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0);
                if (Objects.isNull(collectTotal))
                    findNoteCountsByIdRspDTO.setCollectTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0);
                if (Objects.isNull(commentTotal))
                    findNoteCountsByIdRspDTO.setCommentTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0);
            }
        }

        return Response.success(findNoteCountsByIdRspDTOS);
    }

    /**
     * 将笔记 Hash 计数同步到 Redis 中
     *
     * @param findNoteCountsByIdRspDTOS
     * @param noteIdAndDOMap
     */
    private void syncNoteHash2Redis(List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS, Map<Long, NoteCountDO> noteIdAndDOMap) {
        // 将笔记计数同步到 Redis 中
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 循环已构建好的返参 DTO 集合
                for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                    Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                    Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                    Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                    // 若当前 DTO 的所有计数都不为空，则无需同步 Hash
                    if (Objects.nonNull(likeTotal) && Objects.nonNull(collectTotal) && Objects.nonNull(commentTotal)) {
                        continue;
                    }

                    // 否则，若有任意一个 Field 计数为空，则需要同步对应的 Field
                    Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                    // 构建 Hash Key
                    String noteCountHashKey = RedisKeyConstants.buildCountNoteKey(noteId);

                    // 设置 Field 计数
                    Map<String, Long> countMap = Maps.newHashMap();
                    NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);

                    if (Objects.isNull(likeTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0);
                    }
                    if (Objects.isNull(collectTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0);
                    }
                    if (Objects.isNull(commentTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COMMENT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0);
                    }

                    // 批量添加 Hash 的计数 Field
                    operations.opsForHash().putAll(noteCountHashKey, countMap);

                    // 设置随机过期时间 (1小时以内)
                    long expireTime = 60*30 + RandomUtil.randomInt(60 * 30);
                    operations.expire(noteCountHashKey, expireTime, TimeUnit.SECONDS);
                }

                return null;
            }
        });
    }

    /**
     * 从 Redis 中批量查询笔记 Hash 计数
     *
     * @param hashKeys
     * @return
     */
    private List<Object> getCountHashesByPipelineFromRedis(List<String> hashKeys) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String hashKey : hashKeys) {
                    // 批量获取多个字段
                    operations.opsForHash().multiGet(hashKey, List.of(
                            RedisKeyConstants.FIELD_LIKE_TOTAL,
                            RedisKeyConstants.FIELD_COLLECT_TOTAL,
                            RedisKeyConstants.FIELD_COMMENT_TOTAL
                    ));
                }
                return null;
            }
        });
    }

}
