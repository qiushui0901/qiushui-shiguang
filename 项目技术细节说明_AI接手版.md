# 小哈书项目技术细节说明（AI 接手版）

> 目标读者：另一个需要接手本仓库的 AI 或工程师。  
> 写作目标：不只解释“用了什么技术”，还要解释“为什么这样拆、请求怎么走、数据怎么落、缓存/MQ 怎么协作、改代码时最容易踩哪里”。  
> 依据范围：本文基于当前仓库代码结构、POM、配置、Controller、Service、Consumer、Lua、Mapper 和已有工程约定整理。

## 1. 项目一句话概览

这是一个仿“小红书”的 Spring Cloud Alibaba 微服务后端项目，核心能力包括：

- 用户注册、登录、资料查询、密码修改；
- 网关统一鉴权、路由、用户身份透传；
- 笔记发布、详情、修改、删除、置顶、可见性、点赞、收藏、已发布列表；
- 评论发布、分页查询、子评论、点赞、删除、热评维护；
- 关注、取关、关注列表、粉丝列表；
- 计数中心维护用户计数、笔记计数、评论点赞计数；
- KV 服务用 Cassandra 存储长文本内容；
- OSS 服务负责文件上传；
- 搜索服务基于 Elasticsearch 做笔记搜索、用户搜索、补全建议、趋势榜、索引重建；
- 推荐服务基于 ES + Redis 用户行为召回综合推荐；
- 数据对齐服务基于 RocketMQ 增量临时表 + XXL-JOB 分片任务修正最终计数；
- AI Agent 服务基于 Spring AI 编排内容理解、审核、创作助手、搜索总结；
- benchmark 模块保存若干性能基准验证代码。

整体风格是“同步接口尽量快返回，核心状态先写 Redis/Lua 保证幂等与体验，再用 RocketMQ 异步落库、计数、索引重建和数据对齐补偿”。

## 2. 技术栈与版本

根 POM：`pom.xml`

关键版本：

- Java 17。
- Spring Boot 3.0.2。
- Spring Cloud 2022.0.0。
- Spring Cloud Alibaba 2022.0.0.0。
- Nacos 做服务注册发现。
- Spring Cloud Gateway 做统一网关。
- Sa-Token 1.38.0 做认证鉴权。
- MyBatis + MySQL + Druid 做关系型数据访问。
- Redis 做登录态、缓存、ZSet、Hash、Lua 原子操作、Roaring Bitmap 兼容操作。
- RocketMQ 2.2.3 / client 4.9.4 做异步消息、事务消息、延迟消息。
- Cassandra 做 KV 长文本存储。
- Elasticsearch 7.3.0 做搜索、补全、趋势和推荐召回。
- XXL-JOB 2.4.1 做定时分片数据对齐。
- Caffeine 做本地缓存。
- MapStruct 做对象转换。
- Spring AI 1.0.0 做 Agent 能力。
- MinIO / Aliyun OSS SDK 做对象存储。
- Hutool、Guava、Apache Commons Lang 等工具库。

根 POM 通过 `dependencyManagement` 统一管理内部模块和三方依赖。所有业务模块继承根工程版本 `${revision}`，当前为 `0.0.1-SNAPSHOT`。

## 3. Maven 模块结构

根模块是聚合工程，`packaging=pom`。

主要模块：

- `shiguang-framework`：内部基础框架。
- `shiguang-gateway`：网关服务。
- `shiguang-auth`：认证服务。
- `shiguang-oss`：对象存储服务。
- `shiguang-user`：用户服务。
- `shiguang-kv`：KV 内容服务。
- `shiguang-distributed-id-generator`：分布式 ID 服务。
- `shiguang-note`：笔记服务。
- `shiguang-user-relation`：用户关系服务。
- `shiguang-count`：计数服务。
- `shiguang-data-align`：数据对齐服务。
- `shiguang-search`：搜索服务。
- `shiguang-comment`：评论服务。
- `shiguang-recommend`：推荐服务。
- `shiguang-agent`：AI Agent 服务。
- `shiguang-benchmark`：性能基准与评估工具。

多数业务服务采用 `api + biz` 双模块结构：

- `xxx-api`：Feign API、请求/响应 DTO、服务名常量，对外提供 RPC 契约。
- `xxx-biz`：Spring Boot 应用、Controller、Service、Mapper、Consumer、配置、业务实现。

例外：

- `shiguang-auth` 目前是单模块业务服务。
- `shiguang-gateway` 是单模块网关。
- `shiguang-data-align` 是单模块任务/消费服务。
- `shiguang-benchmark` 是单模块测试/基准代码。

## 4. 内部基础框架

### 4.1 shiguang-common

路径：`shiguang-framework/shiguang-common`

核心职责：

- 定义统一响应模型。
- 定义统一异常模型。
- 定义全局常量。
- 提供 JSON、日期、数字格式化等工具。

关键类：

- `com.quanshiguang.framework.common.response.Response<T>`
  - 字段：`success`、`message`、`errorCode`、`data`。
  - 成功：`Response.success()` / `Response.success(data)`。
  - 失败：`Response.fail(...)`。
- `PageResponse<T>`
  - 用于分页响应。
- `BizException`
  - 业务异常，通常由各服务的 `ResponseCodeEnum` 构造。
- `GlobalConstants`
  - 包含用户 ID 请求头常量，业务上下文依赖它透传用户身份。

### 4.2 shiguang-spring-boot-starter-biz-context

路径：`shiguang-framework/shiguang-spring-boot-starter-biz-context`

这是贯穿所有业务服务的身份上下文 starter。

核心类：

- `LoginUserContextHolder`
  - 用 ThreadLocal 保存当前登录用户 ID。
- `HeaderUserId2ContextFilter`
  - 从请求头读取 `GlobalConstants.USER_ID`。
  - 如果存在用户 ID，则写入 `LoginUserContextHolder`。
  - 请求结束后必须 `remove()`，避免线程池复用导致串号。
- `FeignRequestInterceptor`
  - 从 `LoginUserContextHolder` 读取用户 ID。
  - 如果存在，则写入 Feign 请求头，让下游服务继续知道当前用户。
- `ContextAutoConfiguration`
  - 注册 `HeaderUserId2ContextFilter`。
- `FeignContextAutoConfiguration`
  - 注册 `FeignRequestInterceptor`。

这套机制意味着：业务代码不要自己解析 token，直接调用 `LoginUserContextHolder.getUserId()` 获取当前用户。

### 4.3 shiguang-spring-boot-starter-biz-operationlog

用于接口操作日志切面，常见注解是 `@ApiOperationLog(description = "...")`。Controller 层普遍使用该注解记录接口调用含义。

### 4.4 shiguang-spring-boot-starter-jackson

统一 Jackson 配置，避免各服务重复配置序列化行为。

## 5. 请求入口与网关

模块：`shiguang-gateway`

启动类：

- `ShiguangGatewayApplication`

配置：

- `src/main/resources/bootstrap.yml`
  - 应用名：`shiguang-gateway`。
  - Nacos discovery：`127.0.0.1:8848`。
  - namespace：`shiguang`。
- `src/main/resources/application.yml`
  - 服务端口：`8000`。
  - Redis：`127.0.0.1:6379`，密码 `qwe123!@#`。
  - Sa-Token token 名：`Authorization`。
  - Sa-Token 前缀：`Bearer`。
  - token 风格：`random-128`。

### 5.1 路由规则

网关通过 Spring Cloud Gateway 路由到各服务：

- `/auth/**` -> `lb://shiguang-auth`，`StripPrefix=1`。
- `/user/**` -> `lb://shiguang-user`，`StripPrefix=1`。
- `/note/**` -> `lb://shiguang-note`，`StripPrefix=1`。
- `/relation/**` -> `lb://shiguang-user-relation`，`StripPrefix=1`。
- `/comment/**` -> `lb://shiguang-comment`，`StripPrefix=1`。
- `/search/**` -> `lb://shiguang-search`，`StripPrefix=1`。
- `/recommend/**` -> `lb://shiguang-recommend`，`StripPrefix=1`。
- `/agent/**` -> `lb://shiguang-agent`，`StripPrefix=1`。

因为使用 `StripPrefix=1`，外部访问 `/note/note/publish` 会转发到笔记服务内部 `/note/publish`。

注意：Controller 自身通常还有一层 `@RequestMapping`，例如笔记服务 `NoteController` 是 `/note`。所以外部路径一般是“网关前缀 + 服务内部 Controller 路径 + 方法路径”。

### 5.2 Sa-Token 鉴权

类：`shiguang-gateway/.../auth/SaTokenConfigure.java`

全局拦截 `/**`，排除部分匿名接口：

- `/auth/login`
- `/auth/verification/code/send`
- `/user/user/profile`
- `/search/note/trending`
- `/search/note/suggest`
- `/agent/search/summary`

其余接口执行 `StpUtil.checkLogin()`。

权限和角色检查代码目前是注释状态，项目当前主要是登录态校验。

### 5.3 用户 ID 透传

类：`AddUserId2HeaderFilter`

流程：

1. 从请求头 `Authorization` 获取 token。
2. 去掉 `Bearer ` 前缀。
3. 拼 Redis key：`satoken:login:token:{token}`。
4. 从 Redis 读取用户 ID。
5. 如果读取到用户 ID，把它写入请求头 `GlobalConstants.USER_ID`。
6. 下游业务服务的 `HeaderUserId2ContextFilter` 再把该请求头写入 ThreadLocal。

这个设计把“认证解析 token”收敛在网关，下游服务只信任网关透传的用户 ID。

## 6. 服务注册与配置

各业务服务的 `bootstrap.yml` 基本都配置了：

- `spring.application.name`：服务名，和 Feign `ApiConstants.SERVICE_NAME` 对应。
- `spring.profiles.active=dev`。
- Nacos discovery：`127.0.0.1:8848`。
- namespace：`shiguang`。

业务配置通常位于：

- `src/main/resources/config/application.yml`
- `src/main/resources/config/application-dev.yml`
- `src/main/resources/config/application-prod.yml`

网关例外，配置在 `src/main/resources/application.yml` 和 `bootstrap.yml`。

## 7. 认证服务

模块：`shiguang-auth`

启动类：

- `ShiguangAuthApplication`

主要 Controller：

- `AuthController`
- `VerificationCodeController`

主要 Service：

- `AuthServiceImpl`
- `VerificationCodeServiceImpl`

### 7.1 登录注册合一

核心方法：

- `AuthServiceImpl.loginAndRegister(UserLoginReqVO userLoginReqVO)`

大致流程：

1. 校验手机号与验证码。
2. 通过 `UserRpcService` 调用户服务 `findByPhone`。
3. 如果用户不存在，则调用户服务注册。
4. 调 Sa-Token 登录。
5. 返回 token。

认证服务不直接维护完整用户资料，而是把用户数据写入用户服务。

### 7.2 验证码发送

核心方法：

- `VerificationCodeServiceImpl.send(...)`

验证码相关 Redis key 在 `auth/constant/RedisKeyConstants`。

短信发送使用 Aliyun SDK，相关类：

- `AliyunSmsHelper`
- `AliyunSmsClientConfig`
- `AliyunAccessKeyProperties`

## 8. 用户服务

模块：`shiguang-user`

子模块：

- `shiguang-user-api`
- `shiguang-user-biz`

启动类：

- `ShiguangUserBizApplication`

主要 Controller：

- `UserController`

主要 Service：

- `UserServiceImpl`

主要 Mapper：

- `UserDOMapper`
- `RoleDOMapper`
- `PermissionDOMapper`
- `UserRoleDOMapper`
- `RolePermissionDOMapper`

主要接口能力：

- 更新用户资料。
- 注册用户。
- 按手机号查用户。
- 修改密码。
- 按 ID 查用户。
- 批量按 ID 查用户。
- 查用户主页信息。

### 8.1 用户缓存

用户服务使用 Redis 缓存用户信息，使用 Caffeine 做本地缓存。常见策略：

- 查用户先读本地缓存。
- 本地没有读 Redis。
- Redis 没有读 DB。
- DB 查到后异步回写 Redis 和本地缓存。
- DB 不存在时写短 TTL 空值，防缓存穿透。
- 更新用户资料后删除缓存，并发延迟消息做延迟双删。

相关消费者：

- `DelayDeleteUserRedisCacheConsumer`

相关 MQ 常量：

- `shiguang-user-biz/constant/MQConstants`

### 8.2 用户资料与计数聚合

用户主页接口通常不只返回用户基础信息，还会聚合：

- 关注数。
- 粉丝数。
- 获赞/收藏等计数。

这些计数来自 `shiguang-count` 服务或其缓存，不应该从用户表直接算。

## 9. OSS 服务

模块：`shiguang-oss`

子模块：

- `shiguang-oss-api`
- `shiguang-oss-biz`

启动类：

- `ShiguangOssBizApplication`

主要 Controller：

- `FileController`

主要 Service：

- `FileServiceImpl`

能力：

- 上传图片/文件。
- 对外通过 `FileFeignApi` 支持 multipart/form-data。

依赖：

- MinIO。
- Aliyun OSS。
- Feign Form。

注意：Feign 文件上传必须走 `FileFeignApi` 中的 Feign form 配置，否则 multipart 编码可能失败。

## 10. 分布式 ID 服务

模块：`shiguang-distributed-id-generator`

子模块：

- `shiguang-distributed-id-generator-api`
- `shiguang-distributed-id-generator-biz`

启动类：

- `ShiguangDistributedIdGeneratorBizApplication`

主要 Controller：

- `LeafController`

主要 Service：

- `SnowflakeService`
- `SegmentService`

对外 Feign：

- `DistributedIdGeneratorFeignApi`

用途：

- 笔记发布时生成笔记 ID。
- 评论、其他业务也可复用。

项目里可见两类 ID 思路：

- Snowflake：适合直接生成全局唯一 ID。
- Segment：Leaf 号段模式，通过 DB 分配号段提升吞吐。

## 11. KV 内容服务

模块：`shiguang-kv`

子模块：

- `shiguang-kv-api`
- `shiguang-kv-biz`

启动类：

- `ShiguangKVBizApplication`

主要 Controller：

- `NoteContentController`
- `CommentContentController`

主要 Service：

- `NoteContentServiceImpl`
- `CommentContentServiceImpl`

主要 Repository：

- `NoteContentRepository`
- `CommentContentRepository`

存储：

- Cassandra。

### 11.1 为什么拆 KV 服务

笔记和评论的正文属于较长文本，不适合全部放在笔记元数据表或评论元数据表中。项目将它们拆到 KV 服务：

- 笔记服务保存标题、图片、视频、作者、状态、可见性、正文 UUID 等元数据。
- KV 服务保存正文内容。
- 查询详情时笔记服务通过 Feign 调 KV 服务拿正文。

这样做有利于：

- 元数据表更轻。
- 可单独扩展长文本存储。
- 笔记发布可通过事务消息协调“笔记元数据”和“正文内容”。

### 11.2 笔记正文保存

发布带正文的笔记时，笔记服务不是直接 RPC 同步保存正文，而是发送 RocketMQ 事务消息：

- Topic：`PublishNoteTransactionTopic`
- 本地事务：写 `t_note` 元数据。
- 消费者：`SaveNoteContentConsumer` 保存正文到 Cassandra。

这样可以保证：只有笔记元数据本地事务提交后，正文保存消息才会真正投递。

## 12. 笔记服务：项目核心

模块：`shiguang-note`

子模块：

- `shiguang-note-api`
- `shiguang-note-biz`

启动类：

- `ShiguangNoteBizApplication`

主要 Controller：

- `NoteController`

主要 Service：

- `NoteServiceImpl`

主要 Mapper：

- `NoteDOMapper`
- `NoteLikeDOMapper`
- `NoteCollectionDOMapper`
- `TopicDOMapper`
- `ChannelDOMapper`
- `ChannelTopicRelDOMapper`

主要 RPC：

- `DistributedIdGeneratorRpcService`
- `KeyValueRpcService`
- `UserRpcService`
- `CountRpcService`

### 12.1 对外接口

`NoteController` 内部路径是 `/note`，主要方法：

- `POST /note/publish`：发布笔记。
- `POST /note/detail`：查询笔记详情。
- `POST /note/update`：修改笔记。
- `POST /note/delete`：删除笔记。
- `POST /note/visible/onlyme`：设为仅自己可见。
- `POST /note/top`：置顶/取消置顶。
- `POST /note/like`：点赞。
- `POST /note/unlike`：取消点赞。
- `POST /note/collect`：收藏。
- `POST /note/uncollect`：取消收藏。
- `POST /note/isLikedAndCollectedData`：批量查询当前用户对笔记是否点赞/收藏。
- `POST /note/published/list`：用户主页已发布笔记列表。

经过网关访问时，要加外层 `/note` 前缀，例如外部发布路径通常是 `/note/note/publish`。

### 12.2 笔记数据拆分

笔记元数据在 MySQL：

- ID。
- 作者 ID。
- 类型：图文/视频。
- 标题。
- 图片 URI 列表，逗号拼接。
- 视频 URI。
- 话题 ID / 话题名。
- 可见性。
- 状态。
- 是否置顶。
- 正文是否为空。
- 正文 UUID。
- 创建/更新时间。

正文内容在 Cassandra KV 服务，通过 `contentUuid` 关联。

点赞关系在 `t_note_like`。

收藏关系在 `t_note_collection`。

计数在 `shiguang-count` 服务维护的计数表与 Redis Hash。

### 12.3 发布笔记主流程

核心方法：

- `NoteServiceImpl.publishNote(PublishNoteReqVO publishNoteReqVO)`

流程：

1. 校验笔记类型：
   - 图文笔记必须有图片，最多 8 张。
   - 视频笔记必须有视频 URI。
2. 调 `DistributedIdGeneratorRpcService.getSnowflakeId()` 生成笔记 ID。
3. 如果有正文，生成 `contentUuid`。
4. 如果有话题 ID，查话题名。
5. 从 `LoginUserContextHolder` 取当前用户 ID 作为作者。
6. 构造 `NoteDO` 元数据。
7. 如果正文为空：
   - 直接删除用户已发布列表缓存。
   - 插入 `t_note`。
   - 发送延迟删除已发布列表缓存 MQ。
   - 发送笔记操作 MQ：`NoteOperateTopic:publishNote`。
8. 如果正文不为空：
   - 构造 `PublishNoteDTO`，包含元数据和正文。
   - 发送 RocketMQ 事务消息：`PublishNoteTransactionTopic`。

关键点：

- 正文为空时，不需要 KV 服务，直接本地写库。
- 正文不为空时，用事务消息协调笔记元数据写库和正文保存。
- 发布后发送 `NoteOperateTopic` 是为了通知计数、数据对齐、搜索/推荐相关链路。

### 12.4 发布笔记事务消息

类：

- `PublishNote2DBLocalTransactionListener`

执行本地事务：

1. 解析 `PublishNoteDTO`。
2. 删除 `note:published:list:{creatorId}` 缓存。
3. 插入 `t_note`。
4. 发送延迟双删消息：`DelayDeletePublishedNoteListRedisCacheTopic`。
5. 发送笔记操作消息：`NoteOperateTopic:publishNote`。
6. 本地事务成功返回 `COMMIT`，失败返回 `ROLLBACK`。

事务回查：

1. 解析消息拿到 `noteId`。
2. 查询 `noteDOMapper.selectCountByNoteId(noteId)`。
3. 存在返回 `COMMIT`，不存在返回 `ROLLBACK`。

消费者：

- `shiguang-kv-biz/consumer/SaveNoteContentConsumer`
  - 消费 `PublishNoteTransactionTopic`。
  - 保存正文内容到 Cassandra。

### 12.5 查询笔记详情

核心方法：

- `NoteServiceImpl.findNoteDetail(FindNoteDetailReqVO req)`

缓存层级：

1. Caffeine 本地缓存：`LOCAL_CACHE`，最大 10000，写后 1 小时过期。
2. Redis：`note:detail:{noteId}`。
3. MySQL：`t_note`。

查询流程：

1. 从请求中取 `noteId`。
2. 从上下文取当前用户 ID。
3. 查本地 Caffeine，命中后做可见性校验再返回。
4. 查 Redis，命中后异步写本地缓存，做可见性校验再返回。
5. 查 DB。
6. DB 不存在：
   - 异步写 Redis 空值 `"null"`，TTL 约 1 到 2 分钟，防缓存穿透。
   - 抛 `NOTE_NOT_FOUND`。
7. DB 存在：
   - 校验可见性。
   - 并发查询用户信息和正文内容：
     - 用户信息：`UserRpcService.findById(...)`。
     - 正文内容：如果 `isContentEmpty=false`，调 `KeyValueRpcService.findNoteContent(contentUuid)`。
   - 拼装 `FindNoteDetailRspVO`。
   - 异步写 Redis，TTL 约 1 到 2 天随机值，避免同一时刻大面积过期。
   - 异步写本地缓存。

注意：当前代码里查询用户信息时传入的是 `userId`，理论上笔记详情创作者信息应查 `noteDO.getCreatorId()`。如果后续 AI 维护该功能，应重点核查这个行为是否符合预期。

### 12.6 修改、删除、可见性、置顶

这些操作基本遵循同一套一致性策略：

1. 校验笔记存在。
2. 校验当前用户是作者。
3. 更新 MySQL 元数据。
4. 删除 Redis 详情缓存。
5. 删除 Caffeine 本地缓存。
6. 发送延迟删除 Redis 缓存消息，做延迟双删。
7. 必要时发送删除本地缓存 MQ，让多实例本地缓存失效。
8. 删除或更新用户已发布列表缓存。
9. 对删除操作，发送 `NoteOperateTopic:deleteNote`。

相关消费者：

- `DelayDeleteNoteRedisCacheConsumer`
- `DelayDeletePublishedNoteListRedisCacheConsumer`
- `DeleteNoteLocalCacheConsumer`

### 12.7 点赞笔记

核心方法：

- `NoteServiceImpl.likeNote(LikeNoteReqVO req)`

关键 Redis key：

- `rbitmap:note:likes:{userId}`：用户点赞笔记集合。
- `user:note:likes:{userId}`：用户点赞笔记 ZSet。

关键 Lua：

- `rbitmap_note_like_check.lua`
- `rbitmap_add_note_like_and_expire.lua`
- `rbitmap_batch_add_note_like_and_expire.lua`
- `note_like_check_and_update_zset.lua`
- 早期 Bloom 脚本也保留：`bloom_note_like_check.lua` 等。

设计目标：

- 点赞接口不能每次查 DB 判断是否已点赞。
- 使用 Redis + Lua 原子判断和写入，避免重复点赞。
- Redis 不存在该用户 bitmap 时，回源 DB，异步初始化该用户点赞集合。
- 写关系表和计数异步处理，接口快速返回。

大致流程：

1. 校验笔记存在，并拿到作者 ID。
2. 获取当前用户 ID。
3. 通过 Redis Lua 判断是否已经点赞。
4. 如果 Redis 结构不存在，则查 `t_note_like` 判断，并异步初始化 Redis Roaring Bitmap。
5. 如果已点赞，返回业务错误。
6. 如果可点赞：
   - Lua 写入 Roaring Bitmap / ZSet，并设置 TTL。
   - 发送 `LikeUnlikeTopic:Like` 消息。
   - 发送 `CountNoteLikeTopic` 计数消息。

真正关系落库由：

- `LikeUnlikeNoteConsumer`

计数由：

- `shiguang-count` 的 `CountNoteLikeConsumer`。

数据对齐由：

- `shiguang-data-align` 的 `TodayNoteLikeIncrementData2DBConsumer` 和 XXL-JOB。

推荐兴趣更新由：

- `shiguang-recommend` 的 `UserInterestByLikeConsumer`。

### 12.8 取消点赞

核心方法：

- `NoteServiceImpl.unlikeNote(UnlikeNoteReqVO req)`

逻辑是点赞的反向操作：

1. 校验笔记存在。
2. Redis/Lua 判断是否已经点赞。
3. 未点赞则返回业务错误。
4. 从 bitmap / ZSet 删除状态。
5. 发送 `LikeUnlikeTopic:Unlike`。
6. 发送计数消息，计数服务对点赞数做减法。

### 12.9 收藏与取消收藏

核心方法：

- `collectNote`
- `unCollectNote`

Redis key：

- `rbitmap:note:collects:{userId}`。
- `user:note:collects:{userId}`。

MQ：

- `CollectUnCollectTopic:Collect`
- `CollectUnCollectTopic:UnCollect`
- `CountNoteCollectTopic`

消费者：

- `CollectUnCollectNoteConsumer`
- `CountNoteCollectConsumer`
- `TodayNoteCollectIncrementData2DBConsumer`
- `UserInterestByCollectConsumer`

收藏逻辑与点赞高度相似。

### 12.10 查询当前用户是否点赞/收藏

核心方法：

- `isLikedAndCollectedData`

内部方法：

- `checkNoteIsLiked(noteId, currUserId)`
- `checkNoteIsCollected(noteId, currUserId)`

优先使用 Redis Roaring Bitmap Lua 判断，Redis 不存在时回源 DB，并异步初始化当前用户的点赞/收藏 bitmap。

### 12.11 已发布笔记列表

核心方法：

- `findPublishedNoteList`

缓存 key：

- `note:published:list:{userId}`

策略：

- 第一页列表会写 Redis。
- 发布、修改、删除、置顶、可见性变化会删除该缓存。
- 使用延迟双删减少并发读写造成的旧数据残留。

## 13. 评论服务

模块：`shiguang-comment`

子模块：

- `shiguang-comment-api`
- `shiguang-comment-biz`

启动类：

- `ShiguangCommentBizApplication`

主要 Controller：

- `CommentController`

主要 Service：

- `CommentServiceImpl`

主要 Mapper：

- `CommentDOMapper`
- `CommentLikeDOMapper`
- `NoteCountDOMapper`

主要接口能力：

- 发布评论。
- 查询一级评论分页。
- 查询子评论分页。
- 点赞评论。
- 取消点赞评论。
- 删除评论。

### 13.1 评论层级

评论有一级评论和子评论：

- 一级评论挂在笔记下。
- 子评论挂在一级评论下。
- 一级评论维护 `firstReplyCommentId`，用于展示首条回复。
- 评论计数会影响笔记评论数。

相关消费者：

- `OneLevelCommentFirstReplyCommentIdUpdateConsumer`
  - 监听评论计数/发布类事件，更新一级评论的首条回复 ID。

### 13.2 评论内容存储

评论正文也通过 KV 服务保存到 Cassandra。评论服务表里维护评论元数据与内容 UUID。

批量查询评论列表时，会批量调用 KV 服务获取评论正文。

### 13.3 评论热评

相关 Redis / Lua：

- `add_hot_comments.lua`
- `update_hot_comments.lua`

相关消费者：

- `CommentHeatUpdateConsumer`

评论点赞、回复等行为会更新热评排序。热评通常通过 Redis ZSet 或类似结构维护，避免每次从 DB 排序。

### 13.4 评论点赞

评论点赞与笔记点赞类似：

- 使用 Redis + Lua 做幂等检查。
- 发送 MQ 异步落关系表。
- 发送计数 MQ 更新评论点赞数。

相关 Lua：

- `bloom_comment_like_check.lua`
- `bloom_comment_unlike_check.lua`
- `bloom_add_comment_like_and_expire.lua`
- `bloom_batch_add_comment_like_and_expire.lua`

相关消费者：

- `CountCommentLikeConsumer`
- `CountCommentLike2DBConsumer`

### 13.5 评论删除

相关消费者：

- `DeleteCommentConsumer`
- `DeleteCommentLocalCacheConsumer`

删除评论要同步考虑：

- 评论元数据状态。
- 评论内容删除。
- 评论列表缓存。
- 本地缓存。
- 笔记评论计数。
- 子评论计数。

## 14. 用户关系服务

模块：`shiguang-user-relation`

子模块：

- `shiguang-user-relation-api`
- `shiguang-user-relation-biz`

启动类：

- `ShiguangUserRelationBizApplication`

主要 Controller：

- `RelationController`

主要 Service：

- `RelationServiceImpl`

主要 Mapper：

- `FollowingDOMapper`
- `FansDOMapper`

主要接口：

- 关注。
- 取关。
- 查询关注列表。
- 查询粉丝列表。

### 14.1 数据模型

关系服务通常维护两张关系表：

- following：我关注了谁。
- fans：谁关注了我。

这样查关注列表和粉丝列表都能走正向索引，避免一张表双向扫描。

### 14.2 关注流程

核心方法：

- `RelationServiceImpl.follow(FollowUserReqVO req)`

Redis key：

- 关注 ZSet。
- 粉丝 ZSet。

Lua：

- `follow_check_and_add.lua`
- `follow_check_and_update_fans_zset.lua`
- `follow_add_and_expire.lua`
- `follow_batch_add_and_expire.lua`

流程：

1. 校验不能关注自己。
2. 调用户服务校验目标用户存在。
3. 使用 Redis Lua 判断是否已关注并写缓存。
4. 发送 `FollowUnfollowTopic:Follow`。
5. 发送计数 MQ：
   - following 数 +1。
   - fans 数 +1。

关系落库由：

- `FollowUnfollowConsumer`

计数由：

- `CountFollowingConsumer`
- `CountFansConsumer`

数据对齐由：

- `TodayUserFollowIncrementData2DBConsumer`
- `FollowingCountShardingXxlJob`
- `FansCountShardingXxlJob`

### 14.3 取关流程

核心方法：

- `unfollow`

Lua：

- `unfollow_check_and_delete.lua`

流程：

1. 校验目标用户。
2. Redis/Lua 判断关注关系是否存在。
3. 不存在则返回业务错误。
4. 删除 Redis 关注/粉丝缓存状态。
5. 发送 `FollowUnfollowTopic:Unfollow`。
6. 发送计数 MQ 做 -1。

### 14.4 关注/粉丝列表

核心方法：

- `findFollowingList`
- `findFansList`

优先查 Redis ZSet，缓存不存在时回源 DB 并异步初始化 Redis。返回用户信息时会批量调用用户服务。

## 15. 计数服务

模块：`shiguang-count`

子模块：

- `shiguang-count-api`
- `shiguang-count-biz`

启动类：

- `ShiguangCountBizApplication`

主要 Controller：

- `NoteCountController`
- `UserCountController`

主要 Service：

- `NoteCountServiceImpl`
- `UserCountServiceImpl`

主要 Mapper：

- `NoteCountDOMapper`
- `UserCountDOMapper`
- `CommentDOMapper`

### 15.1 计数类型

笔记计数：

- 点赞数。
- 收藏数。
- 评论数。

用户计数：

- 关注数。
- 粉丝数。
- 获赞/收藏等聚合数。

评论计数：

- 评论点赞数。
- 一级评论下子评论数。

### 15.2 Redis Hash 计数缓存

计数服务大量使用 Redis Hash：

- 笔记计数 key：类似 `count:note:{noteId}`。
- 用户计数 key：类似 `count:user:{userId}`。

查询计数：

1. 批量读取 Redis Hash。
2. 未命中则回源 DB。
3. 回源后写 Redis。

### 15.3 MQ 消费计数增量

主要消费者：

- `CountNotePublishConsumer`
- `CountNoteLikeConsumer`
- `CountNoteLike2DBConsumer`
- `CountNoteCollectConsumer`
- `CountNoteCollect2DBConsumer`
- `CountNoteCommentConsumer`
- `CountNoteChildCommentConsumer`
- `CountCommentLikeConsumer`
- `CountCommentLike2DBConsumer`
- `CountFollowingConsumer`
- `CountFollowing2DBConsumer`
- `CountFansConsumer`
- `CountFans2DBConsumer`

设计模式：

- 用户行为服务发送领域事件。
- 计数服务消费事件，先更新 Redis 计数。
- 部分消费者再聚合写 DB 或转发到 `xxx_2_DB` Topic。
- 数据对齐服务再用临时表 + 定时任务做最终校正。

这样接口不用同步更新多个表，降低写路径延迟和耦合。

### 15.4 Sentinel 限流

`UserCountServiceImpl.findUserCountData` 上存在 Sentinel block handler，用于用户计数查询降级保护。后续 AI 修改该类时，注意 block handler 方法签名必须匹配 Sentinel 要求。

## 16. 数据对齐服务

模块：`shiguang-data-align`

启动类：

- `ShiguangDataAlignApplication`

核心思想：

> Redis 和 MQ 适合承接高频增量，但最终 MySQL 计数表必须周期性校准。数据对齐服务记录“今天哪些对象发生过变化”，再由 XXL-JOB 分片扫描这些临时表，回源真实关系表 count(*)，修正计数表、Redis 缓存和搜索索引。

### 16.1 增量消费者

主要消费者：

- `TodayNoteLikeIncrementData2DBConsumer`
  - 监听笔记点赞计数 Topic。
  - 将发生变化的 noteId 写入当天临时表。
- `TodayNoteCollectIncrementData2DBConsumer`
  - 监听笔记收藏计数 Topic。
- `TodayNotePublishIncrementData2DBConsumer`
  - 监听笔记发布操作 Topic。
- `TodayUserFollowIncrementData2DBConsumer`
  - 监听关注计数 Topic。

相关 Lua：

- `bloom_today_note_like_check.lua`
- `bloom_today_note_collect_check.lua`
- `bloom_today_user_follow_check.lua`
- `bloom_today_user_note_publish_check.lua`

这些 Lua 用于判断当天某个 ID 是否已经记录过，避免临时表重复插入过多。

### 16.2 临时表

相关 Mapper：

- `CreateTableMapper`
- `InsertMapper`
- `SelectMapper`
- `UpdateMapper`
- `DeleteMapper`
- `DeleteTableMapper`

相关常量：

- `TableConstants`

临时表通常带日期和分片后缀，例如：

- `t_data_align_note_like_count_temp_yyyyMMdd_{shardIndex}`
- `t_data_align_note_collect_count_temp_yyyyMMdd_{shardIndex}`
- `t_data_align_user_following_count_temp_yyyyMMdd_{shardIndex}`

### 16.3 XXL-JOB 任务

主要任务：

- `CreateTableXxlJob`
  - 创建当天/未来需要的临时表。
- `DeleteTableXxlJob`
  - 删除过期临时表。
- `NoteLikeCountShardingXxlJob`
- `NoteCollectCountShardingXxlJob`
- `NotePublishCountShardingXxlJob`
- `FollowingCountShardingXxlJob`
- `FansCountShardingXxlJob`
- `UserLikeCountShardingXxlJob`
- `UserCollectCountShardingXxlJob`

分片任务通用流程：

1. 通过 `XxlJobHelper.getShardIndex()` 和 `getShardTotal()` 获取分片参数。
2. 构造前一天临时表后缀。
3. 每批查询 1000 条发生变化的 ID。
4. 对每个 ID 回源真实业务表 `count(*)`。
5. 更新最终计数表。
6. 如果 Redis 计数 Hash 存在，同步更新 Redis。
7. 如果影响搜索结果，调用搜索服务 `rebuildNoteDocument` 或 `rebuildUserDocument`。
8. 批量删除已处理临时表记录。

以 `NoteLikeCountShardingXxlJob` 为例：

- 从临时表取发生点赞变化的 noteId。
- 去 `t_note_like` 统计真实点赞数。
- 更新 `t_note_count.like_total`。
- 更新 Redis `count:note:{noteId}` 的点赞字段。
- 调搜索服务重建笔记文档，让 ES 中点赞数同步。

## 17. 搜索服务

模块：`shiguang-search`

子模块：

- `shiguang-search-api`
- `shiguang-search-biz`

启动类：

- `ShiguangSearchBizApplication`

主要 Controller：

- `NoteController`
- `UserController`
- `ExtDictController`

主要 Service：

- `NoteServiceImpl`
- `UserServiceImpl`
- `ExtDictServiceImpl`

主要索引模型：

- `NoteIndex`
- `UserIndex`

主要接口能力：

- 搜索笔记。
- 搜索用户。
- 重建笔记文档。
- 重建用户文档。
- 搜索建议 suggest。
- 热度趋势 trending。
- IK 扩展词典热更新。

### 17.1 笔记搜索

核心方法：

- `shiguang-search-biz/.../service/impl/NoteServiceImpl.searchNote`

通常会构造 ES bool query：

- 关键词匹配标题、话题等字段。
- 类型筛选。
- 发布时间范围筛选。
- 排序规则：
  - 综合。
  - 最新。
  - 最热。

返回结果会携带：

- 笔记 ID。
- 封面。
- 标题。
- 作者昵称。
- 作者头像。
- 点赞/收藏/评论数。
- 更新时间相对时间。

### 17.2 索引重建

Feign API：

- `SearchFeignApi.rebuildNoteDocument`
- `SearchFeignApi.rebuildUserDocument`

使用场景：

- 用户资料更新后重建用户文档。
- 笔记发布/删除/计数变化后重建笔记文档。
- 数据对齐任务完成真实计数校准后重建笔记文档。

索引重建通常会聚合多个服务的数据：

- 笔记元数据。
- 用户信息。
- 计数数据。
- 正文/话题/封面等。

### 17.3 搜索建议

接口：

- `/search/note/suggest`

用于关键词补全。ES mapping 更新说明可见：

- `shiguang-search/es-mapping-update-suggest.md`

### 17.4 趋势榜

接口：

- `/search/note/trending`

网关允许匿名访问。

通常基于 ES 中笔记热度字段排序，如点赞、收藏、评论、发布时间等。

## 18. 推荐服务

模块：`shiguang-recommend`

子模块：

- `shiguang-recommend-biz`

启动类：

- `ShiguangRecommendBizApplication`

主要 Controller：

- `RecommendController`

主要 Service：

- `RecommendServiceImpl`

### 18.1 推荐入口

核心方法：

- `RecommendServiceImpl.recommend(RecommendNoteReqVO req)`

参数：

- `type`
  - `null` 或 `0`：综合推荐。
  - `1`：热度推荐。
  - `2`：关注推荐。
  - `3`：话题兴趣推荐。
- `pageNo`

页大小固定：

- `PAGE_SIZE = 10`

### 18.2 三路召回

综合推荐会合并三路：

1. 关注召回。
2. 话题召回。
3. 热度召回。

合并优先级：

1. following。
2. topic。
3. trending。

去重方式：

- `LinkedHashSet<Long> seen`，保持插入顺序。

### 18.3 热度召回

方法：

- `recallTrending`

数据源：

- ES `note` 索引。

算法：

- `function_score`
- 点赞数：`sqrt(like_total) * 0.5`
- 收藏数：`sqrt(collect_total) * 0.3`
- 评论数：`sqrt(comment_total) * 0.2`
- 发布时间高斯衰减：7 天尺度，越新越高。

排序：

- `_score desc`

### 18.4 关注召回

方法：

- `recallFromFollowing`

数据源：

- Redis ZSet：`following:{userId}` 或服务内 `RedisKeyConstants.buildUserFollowingKey(userId)`。
- ES `note` 索引。

流程：

1. 从 Redis 取用户最近关注的最多 50 个用户。
2. ES terms query：`creator_id in followingIds`。
3. 按 `create_time desc`。
4. 取 `PAGE_SIZE * 2`，供综合合并去重。

### 18.5 话题兴趣召回

方法：

- `recallByTopic`

数据源：

- Redis ZSet：用户兴趣话题 key。
- ES `note` 索引。

流程：

1. 取用户兴趣话题 Top3。
2. ES terms query：`topic in topics`。
3. 按 `like_total desc`。
4. 取 `PAGE_SIZE * 2`。

### 18.6 用户兴趣更新

消费者：

- `UserInterestByLikeConsumer`
- `UserInterestByCollectConsumer`

它们监听点赞/收藏行为，对用户兴趣话题 ZSet 加权。收藏通常权重应大于点赞，后续如果改推荐权重，需要同步看这两个消费者。

## 19. AI Agent 服务

模块：`shiguang-agent`

子模块：

- `shiguang-agent-biz`

启动类：

- `ShiguangAgentBizApplication`

主要 Controller：

- `AgentController`

主要 Service：

- `AgentServiceImpl`

核心组件：

- `Agent`
- `AgentContext`
- `AgentResult`
- `AgentOrchestrator`
- `ContentUnderstandingAgent`
- `ContentModerationAgent`
- `CreativeAssistantAgent`
- `SearchSummarizeAgent`
- `RedisChatMemory`

### 19.1 普通聊天

核心方法：

- `AgentServiceImpl.chat(AgentChatReqVO req)`

流程：

1. 如果请求没有 `sessionId`，生成 UUID。
2. 从 `LoginUserContextHolder` 取用户 ID。
3. 创建 `AgentContext`。
4. 从 RedisChatMemory 构建历史上下文摘要。
5. 根据 `workflow` 选择编排方式：
   - `sequential`：顺序执行内容理解、内容审核、创作助手。
   - `parallel`：内容理解和审核并行，再执行创作助手。
   - `conditional`：根据审核 Agent 输出决定是否继续。
   - 默认：guarded 顺序模式。
6. 输出所有 Agent 结果。
7. 最后一个成功结果作为 `finalResult`。
8. 将用户输入和助手输出写入 RedisChatMemory。

### 19.2 流式聊天

核心方法：

- `AgentServiceImpl.chatStream(AgentChatReqVO req, SseEmitter emitter)`

流程：

1. 先同步执行内容理解和内容审核。
2. 将 pre-agent 结果通过 SSE event `agent-result` 发给前端。
3. 如果审核不通过：
   - 发送 `moderation-gate` 结果。
   - 发送 `done`。
   - 写入 RedisChatMemory。
   - complete。
4. 如果审核通过：
   - 调 `creativeAssistantAgent.executeStream(...)`。
   - 每个 chunk 通过 SSE event `chunk` 发出。
   - 完成时发送 `done` 并保存对话。

### 19.3 搜索总结

核心方法：

- `AgentServiceImpl.searchSummary(SearchSummaryReqVO req)`

网关允许匿名访问 `/agent/search/summary`。

流程：

1. 用 ES 按关键词搜索热门笔记。
2. 用 ES terms aggregation 聚合相关话题。
3. 调 `SearchSummarizeAgent` 生成总结。
4. 返回总结、笔记列表、相关话题。

搜索排序也使用热度 function_score：

- 点赞 0.5。
- 收藏 0.3。
- 评论 0.2。
- 时间衰减。

## 20. RocketMQ Topic 总览

不同模块各自定义 `MQConstants`，名字可能重复引用。维护时不要只改一个模块常量，要搜全仓库。

笔记服务关键 Topic：

- `PublishNoteTransactionTopic`
  - 发布带正文笔记事务消息。
  - 本地事务写笔记元数据，KV 消费保存正文。
- `NoteOperateTopic`
  - 笔记发布/删除事件。
  - Tag：`publishNote`、`deleteNote`。
- `LikeUnlikeTopic`
  - 笔记点赞/取消点赞。
  - Tag：`Like`、`Unlike`。
- `CollectUnCollectTopic`
  - 笔记收藏/取消收藏。
  - Tag：`Collect`、`UnCollect`。
- `CountNoteLikeTopic`
  - 笔记点赞计数。
- `CountNoteCollectTopic`
  - 笔记收藏计数。
- `DeleteNoteLocalCacheTopic`
  - 删除多实例本地缓存。
- `DelayDeleteNoteRedisCacheTopic`
  - 延迟删除笔记详情 Redis 缓存。
- `DelayDeletePublishedNoteListRedisCacheTopic`
  - 延迟删除已发布列表缓存。

关系服务关键 Topic：

- `FollowUnfollowTopic`
  - 关注/取关关系落库。
- `CountFollowingTopic`
  - 用户关注数变更。
- `CountFansTopic`
  - 用户粉丝数变更。

评论服务关键 Topic：

- 评论发布计数 Topic。
- 评论点赞/取消点赞 Topic。
- 评论删除 Topic。
- 评论热度更新 Topic。
- 删除评论本地缓存 Topic。

计数服务关键 Topic：

- 各类 `Count...Topic`。
- 各类 `...2DBTopic`。

数据对齐服务监听多个计数 Topic，把变化对象写入临时表。

推荐服务监听点赞/收藏 Topic，更新用户兴趣。

## 21. Redis 使用总览

### 21.1 登录态

Sa-Token 在 Redis 中保存 token 到用户 ID 的映射。网关按：

- `satoken:login:token:{token}`

读取当前用户 ID。

### 21.2 详情缓存

笔记：

- `note:detail:{noteId}`

用户：

- 用户服务中定义的 user detail key。

策略：

- 本地 Caffeine + Redis + DB。
- 空值短 TTL 防穿透。
- 正常数据随机 TTL 防雪崩。
- 更新删除 + 延迟双删。
- 多实例本地缓存靠 MQ 通知删除。

### 21.3 列表缓存

笔记已发布列表：

- `note:published:list:{userId}`

关系列表：

- 用户关注 ZSet。
- 用户粉丝 ZSet。

点赞/收藏列表：

- `user:note:likes:{userId}`
- `user:note:collects:{userId}`

### 21.4 关系存在性

笔记点赞：

- `rbitmap:note:likes:{userId}`

笔记收藏：

- `rbitmap:note:collects:{userId}`

评论点赞：

- Bloom 相关 key。

关注关系：

- ZSet + Lua 判断。

### 21.5 计数 Hash

笔记计数：

- `count:note:{noteId}`，字段包括点赞、收藏、评论等。

用户计数：

- `count:user:{userId}`，字段包括关注、粉丝等。

### 21.6 用户兴趣

推荐服务维护用户兴趣话题 ZSet：

- key 由 `shiguang-recommend-biz/constant/RedisKeyConstants` 构造。
- score 表示兴趣权重。
- 点赞和收藏消费者增加权重。

## 22. Lua 脚本的角色

项目大量 Lua 位于各模块 `src/main/resources/lua`。

主要作用：

- 把“检查是否存在 + 写入/删除 + 设置过期时间”合并为一个 Redis 原子操作。
- 避免 Java 多步 Redis 操作的并发竞态。
- 减少网络往返。
- 保障点赞、收藏、关注、取关等幂等。

典型脚本类别：

- 笔记点赞/取消点赞：
  - `rbitmap_note_like_check.lua`
  - `rbitmap_note_unlike_check.lua`
  - `rbitmap_add_note_like_and_expire.lua`
- 笔记收藏/取消收藏：
  - `rbitmap_note_collect_check.lua`
  - `rbitmap_note_uncollect_check.lua`
- ZSet 维护：
  - `note_like_check_and_update_zset.lua`
  - `note_collect_check_and_update_zset.lua`
  - `batch_add_note_like_zset_and_expire.lua`
  - `batch_add_note_collect_zset_and_expire.lua`
- 关注关系：
  - `follow_check_and_add.lua`
  - `unfollow_check_and_delete.lua`
- 评论热评：
  - `add_hot_comments.lua`
  - `update_hot_comments.lua`
- 数据对齐当天去重：
  - `bloom_today_note_like_check.lua`
  - `bloom_today_note_collect_check.lua`

维护注意：

- Lua 返回值一般映射到 Java enum，例如 `NoteLikeLuaResultEnum`、`LuaResultEnum`。
- 如果改 Lua 返回码，必须同步改 enum。
- 如果改 Redis key 类型，例如 bitmap 改 ZSet，必须同步改所有读写路径和批量初始化路径。

## 23. 缓存一致性策略

项目中有几种常见策略：

### 23.1 Cache Aside

读：

1. 先读缓存。
2. 缓存未命中读 DB。
3. DB 查到后写缓存。

写：

1. 写 DB。
2. 删除缓存。

### 23.2 延迟双删

用于笔记详情、用户详情、已发布列表等缓存。

流程：

1. 写 DB 前后删除一次缓存。
2. 发送延迟 MQ。
3. 延迟消费者再删除一次 Redis 缓存。

目的：

- 减少并发读在写入期间把旧数据回填缓存的问题。

### 23.3 本地缓存失效广播

Caffeine 是进程内缓存，多实例下不能只删除当前实例。

因此项目通过 MQ 广播：

- `DeleteNoteLocalCacheTopic`
- `DeleteCommentLocalCacheTopic`
- 用户服务类似延迟删除消费者。

消费者收到后删除本实例 Caffeine 缓存。

### 23.4 空值缓存

当 DB 查不到时，写 Redis `"null"` 并设置短 TTL。

目的：

- 防止不存在 ID 被高频请求打穿 DB。

注意：

- 读缓存处必须能识别 `"null"`。
- 当前部分代码使用 `JsonUtils.parseObject("null", VO.class)`，一般会得到 `null`，但后续维护要小心。

### 23.5 TTL 随机化

正常缓存 TTL 会加随机秒数，例如 1 天 + 0 到 1 天随机值。

目的：

- 避免大量 key 同时过期引发缓存雪崩。

## 24. 异步一致性与最终一致性

项目多数写操作不是强一致更新全部衍生数据，而是最终一致：

点赞示例：

1. 用户调点赞接口。
2. 笔记服务 Redis/Lua 判断并写入用户点赞状态。
3. 接口返回成功。
4. MQ 异步落 `t_note_like`。
5. MQ 异步更新 Redis 计数。
6. MQ/定时任务修正 MySQL 计数表。
7. 数据对齐任务修正 ES 文档。
8. 推荐服务消费行为事件，更新兴趣画像。

这意味着：

- 用户刚点赞后，详情页是否立刻显示点赞数 +1，取决于读的是 Redis 计数还是 DB/ES。
- ES 搜索和推荐里的计数可能稍晚更新。
- 最终由数据对齐任务保证 MySQL 计数和 ES 文档回到正确状态。

如果后续要做强一致接口，要明确性能代价和跨服务事务问题。

## 25. Feign RPC 契约

每个 `api` 模块定义 Feign API：

- `UserFeignApi`
- `KeyValueFeignApi`
- `CountFeignApi`
- `SearchFeignApi`
- `DistributedIdGeneratorFeignApi`
- `FileFeignApi`

约定：

- Feign API 的 `name` 来自 `ApiConstants.SERVICE_NAME`。
- DTO 放在 api 模块，biz 模块不要重复造一套 RPC DTO。
- Feign fallback 目前在部分服务中存在，例如 Count。
- 业务服务间用户身份由 `FeignRequestInterceptor` 自动透传。

改接口时要同时检查：

- api 模块 Feign 方法。
- 请求 DTO。
- 响应 DTO。
- biz Controller。
- biz Service。
- 调用方 RPC wrapper。

## 26. 数据库访问模式

项目使用 MyBatis，不是 JPA。

常见结构：

- `domain/dataobject/*DO.java`：数据库对象。
- `domain/mapper/*Mapper.java`：Mapper 接口。
- `resources/mapper/*Mapper.xml`：SQL XML。
- `generatorConfig.xml`：MyBatis Generator 配置。

维护建议：

- 修改字段时先改 DO，再改 Mapper XML。
- 自定义 SQL 多在 XML 中。
- 批量查询、分页查询、计数查询要优先找现有 Mapper 方法复用。
- MyBatis Generator 可能覆盖生成文件，手写代码要注意位置。

## 27. 常见业务主链路

### 27.1 用户登录后发布带正文图文笔记

1. 客户端请求网关 `/note/note/publish`，带 `Authorization: Bearer xxx`。
2. 网关 Sa-Token 校验登录。
3. `AddUserId2HeaderFilter` 从 Redis token key 找到用户 ID，写请求头。
4. 网关路由到 `shiguang-note`。
5. 笔记服务 `HeaderUserId2ContextFilter` 把用户 ID 写 ThreadLocal。
6. `NoteController.publishNote` 调 `NoteServiceImpl.publishNote`。
7. 校验图文图片数量。
8. 调分布式 ID 服务生成 noteId。
9. 生成 contentUuid。
10. 查话题名。
11. 构造 NoteDO。
12. 发送 `PublishNoteTransactionTopic` 事务消息。
13. 本地事务监听器插入 `t_note`。
14. 本地事务提交后，KV 服务消费消息保存正文到 Cassandra。
15. 监听器发送 `NoteOperateTopic:publishNote`。
16. 计数服务初始化/更新笔记发布计数。
17. 数据对齐服务记录当天发布变化。
18. 后续定时任务对齐计数并重建 ES。

### 27.2 查询笔记详情

1. 客户端请求网关 `/note/note/detail`。
2. 网关鉴权并透传用户 ID。
3. 笔记服务取用户 ID。
4. 读 Caffeine。
5. 未命中读 Redis `note:detail:{noteId}`。
6. 未命中读 MySQL `t_note`。
7. 校验可见性：
   - 公开可见直接返回。
   - 仅自己可见时，当前用户必须是作者。
8. 并发 RPC：
   - 用户服务取作者信息。
   - KV 服务取正文。
9. 拼 VO。
10. 异步写 Redis 和本地缓存。
11. 返回。

### 27.3 点赞笔记

1. 客户端请求 `/note/note/like`。
2. 笔记服务校验笔记存在。
3. 从 ThreadLocal 取当前用户。
4. Redis/Lua 判断当前用户是否已点赞。
5. 如果 Redis 状态不存在，回源 `t_note_like`，异步初始化 bitmap。
6. 原子写 Redis 点赞状态。
7. 发送关系落库 MQ。
8. 发送计数 MQ。
9. 发送数据对齐/推荐相关消费链路。
10. 返回成功。

### 27.4 关注用户

1. 客户端请求 `/relation/relation/follow`。
2. 关系服务校验不能关注自己。
3. RPC 用户服务校验目标用户存在。
4. Redis/Lua 判断是否已关注并写关注状态。
5. 发送关注关系落库 MQ。
6. 发送 following/fans 计数 MQ。
7. 数据对齐服务记录当天关注变化。
8. 返回成功。

### 27.5 搜索笔记

1. 客户端请求 `/search/note/search` 或类似接口。
2. 搜索服务构造 ES 查询。
3. 根据关键词、类型、发布时间、排序类型筛选。
4. 返回 ES 文档中的笔记卡片信息。
5. ES 文档由发布、更新、计数对齐等链路触发重建。

### 27.6 综合推荐

1. 客户端请求 `/recommend/recommend`。
2. 推荐服务从 ThreadLocal 取用户 ID。
3. 如果 type=0：
   - 查关注召回。
   - 查话题兴趣召回。
   - 查热度召回。
   - 按关注、话题、热度合并去重。
4. 返回固定 10 条。

### 27.7 AI 搜索总结

1. 客户端请求 `/agent/search/summary`。
2. 网关允许匿名访问。
3. Agent 服务用 ES 搜索热门笔记。
4. 聚合相关话题。
5. 调 SearchSummarizeAgent 生成总结。
6. 返回 summary + notes + relatedTopics。

## 28. 启动顺序建议

本地完整启动至少需要：

1. MySQL。
2. Redis。
3. Nacos。
4. RocketMQ NameServer / Broker。
5. Cassandra。
6. Elasticsearch 7.3。
7. XXL-JOB admin，如果要跑数据对齐任务。
8. OSS 依赖：MinIO 或 Aliyun OSS。
9. Spring AI 所需模型服务配置，如果要跑 Agent。

服务启动建议：

1. `shiguang-distributed-id-generator`
2. `shiguang-user`
3. `shiguang-kv`
4. `shiguang-count`
5. `shiguang-search`
6. `shiguang-note`
7. `shiguang-comment`
8. `shiguang-user-relation`
9. `shiguang-recommend`
10. `shiguang-agent`
11. `shiguang-auth`
12. `shiguang-gateway`

实际顺序可调整，但网关和依赖调用方最好后启动。

## 29. AI 接手开发时的代码定位法

如果要改一个接口：

1. 找网关路径。
2. 找对应服务 Controller。
3. 找 Service interface。
4. 找 ServiceImpl。
5. 找 Mapper XML。
6. 找相关 RedisKeyConstants。
7. 找相关 MQConstants。
8. 找生产该 Topic 的代码。
9. 找消费该 Topic 的 Consumer。
10. 找数据对齐任务是否依赖该事件。
11. 找搜索索引是否需要重建。
12. 找推荐画像是否需要同步。

推荐搜索命令：

```powershell
rg "接口路径或方法名"
rg "TopicName"
rg "RedisKey前缀"
rg "DTO类名"
rg "Mapper方法名"
```

## 30. 高风险改动清单

### 30.1 改 Redis key

必须同步：

- 生产方。
- 消费方。
- Lua 脚本。
- 批量初始化逻辑。
- 数据对齐任务。
- 线上历史 key 兼容或迁移。

### 30.2 改 MQ Topic 或 Tag

必须同步：

- 所有生产者。
- 所有消费者。
- count 服务。
- data-align 服务。
- recommend 服务。
- RocketMQ 订阅表达式。

### 30.3 改 Lua 返回值

必须同步：

- Java enum。
- switch 分支。
- 单元/集成测试。

### 30.4 改笔记发布

必须核查：

- 正文为空路径。
- 正文非空事务消息路径。
- KV 保存正文消费者。
- `NoteOperateTopic` 是否仍发送。
- 已发布列表缓存是否删除。
- 搜索索引是否重建。
- 计数初始化是否正常。

### 30.5 改点赞/收藏/关注

必须核查：

- Redis 幂等状态。
- DB 关系表落库消费者。
- 计数服务。
- 数据对齐临时表。
- 推荐兴趣消费者。
- 用户可见状态查询。

### 30.6 改用户身份传递

必须核查：

- 网关 `AddUserId2HeaderFilter`。
- `GlobalConstants.USER_ID`。
- `HeaderUserId2ContextFilter`。
- `FeignRequestInterceptor`。
- ThreadLocal remove 是否保留。

### 30.7 改搜索索引字段

必须核查：

- `NoteIndex` / `UserIndex`。
- ES mapping。
- 搜索查询字段。
- 推荐服务 `NoteIndexFields`。
- Agent 搜索总结里的硬编码字段。
- 索引重建逻辑。

## 31. 当前代码中值得注意的问题

这些不是一定要立刻改，但接手时要知道：

1. 多数源码注释出现乱码，说明文件编码或终端显示存在历史问题。代码逻辑可读，但注释不完全可靠。
2. `NoteServiceImpl.findNoteDetail` 中用户信息查询看起来使用了当前登录用户 ID，而不是笔记作者 ID，可能导致详情作者信息错误。需要结合测试或产品期望确认。
3. 部分日志和异常信息也有乱码，排障体验会受影响。
4. 部分服务的配置含本地明文密码，生产环境需要配置中心/密钥管理。
5. 业务强依赖外部中间件，本地跑单测和集成测试前要准备 Redis、MQ、ES、Cassandra 等。
6. 许多写链路是最终一致，接口返回成功不代表所有 DB、计数、ES、推荐画像已经同步完成。
7. Feign fallback 并不是所有服务都有，跨服务异常传播策略不统一。
8. 计数类逻辑有实时增量和定时对齐两套路径，改动时不能只看其中一套。

## 32. 建议补充的测试

当前仓库测试覆盖不完整。后续 AI 如果继续增强，优先补这些：

- 笔记发布：
  - 图文无图片失败。
  - 图文超过 8 张失败。
  - 视频无视频 URI 失败。
  - 正文为空直接入库。
  - 正文非空事务消息路径。
- 笔记详情：
  - 本地缓存命中。
  - Redis 命中。
  - DB 回源。
  - 私密笔记非作者不可见。
- 点赞/收藏：
  - 重复点赞失败。
  - 取消未点赞失败。
  - Redis 不存在时 DB 回源初始化。
- 关注：
  - 不能关注自己。
  - 重复关注失败。
  - 取关未关注失败。
- 计数：
  - MQ 增量更新 Redis。
  - 数据对齐任务修正 DB 和 Redis。
- 搜索：
  - 索引重建字段完整性。
  - suggest 返回。
  - trending 排序。
- Agent：
  - 审核不通过时创作助手不执行。
  - SSE done 和 error 事件完整。

## 33. 一份最短的接手心智模型

如果只能记住一件事，记住这条：

> 网关负责登录态和用户 ID 透传；业务服务通过 ThreadLocal 拿用户；核心写操作先用 Redis/Lua 保证幂等和快速响应，再用 RocketMQ 异步落关系表、更新计数、清缓存、更新推荐画像；计数和 ES 通过数据对齐任务最终修正。

这条主线解释了项目里大多数看似分散的代码：

- 为什么有那么多 Redis key。
- 为什么有那么多 Lua。
- 为什么点赞/收藏/关注不直接同步写所有表。
- 为什么 count 和 data-align 都在处理同一类事件。
- 为什么搜索索引要被异步重建。
- 为什么 Caffeine 本地缓存还要配 MQ 删除。

## 34. 关键路径索引

为了方便 AI 快速跳转，下面列出最值得优先读的文件：

- 根 POM：`pom.xml`
- 网关路由：`shiguang-gateway/src/main/resources/application.yml`
- 网关鉴权：`shiguang-gateway/src/main/java/com/quanshiguang/shiguang/gateway/auth/SaTokenConfigure.java`
- 用户 ID 透传：`shiguang-gateway/src/main/java/com/quanshiguang/shiguang/gateway/filter/AddUserId2HeaderFilter.java`
- ThreadLocal 上下文：`shiguang-framework/shiguang-spring-boot-starter-biz-context/src/main/java/com/quanshiguang/framework/biz/context`
- 统一响应：`shiguang-framework/shiguang-common/src/main/java/com/quanshiguang/framework/common/response/Response.java`
- 认证核心：`shiguang-auth/src/main/java/com/quanshiguang/shiguang/auth/service/impl/AuthServiceImpl.java`
- 用户核心：`shiguang-user/shiguang-user-biz/src/main/java/com/quanshiguang/shiguang/user/biz/service/impl/UserServiceImpl.java`
- 笔记 Controller：`shiguang-note/shiguang-note-biz/src/main/java/com/quanshiguang/shiguang/note/biz/controller/NoteController.java`
- 笔记核心：`shiguang-note/shiguang-note-biz/src/main/java/com/quanshiguang/shiguang/note/biz/service/impl/NoteServiceImpl.java`
- 笔记事务消息：`shiguang-note/shiguang-note-biz/src/main/java/com/quanshiguang/shiguang/note/biz/listener/PublishNote2DBLocalTransactionListener.java`
- 笔记 MQ 常量：`shiguang-note/shiguang-note-biz/src/main/java/com/quanshiguang/shiguang/note/biz/constant/MQConstants.java`
- 笔记 Redis 常量：`shiguang-note/shiguang-note-biz/src/main/java/com/quanshiguang/shiguang/note/biz/constant/RedisKeyConstants.java`
- KV 核心：`shiguang-kv/shiguang-kv-biz/src/main/java/com/quanshiguang/shiguang/kv/biz/service/impl`
- 评论核心：`shiguang-comment/shiguang-comment-biz/src/main/java/com/quanshiguang/shiguang/comment/biz/service/impl/CommentServiceImpl.java`
- 关系核心：`shiguang-user-relation/shiguang-user-relation-biz/src/main/java/com/quanshiguang/shiguang/user/relation/biz/service/impl/RelationServiceImpl.java`
- 计数核心：`shiguang-count/shiguang-count-biz/src/main/java/com/quanshiguang/shiguang/count/biz`
- 数据对齐任务：`shiguang-data-align/src/main/java/com/quanshiguang/shiguang/data/align/job`
- 搜索核心：`shiguang-search/shiguang-search-biz/src/main/java/com/quanshiguang/shiguang/search/biz/service/impl`
- 推荐核心：`shiguang-recommend/shiguang-recommend-biz/src/main/java/com/quanshiguang/shiguang/recommend/biz/service/impl/RecommendServiceImpl.java`
- Agent 核心：`shiguang-agent/shiguang-agent-biz/src/main/java/com/quanshiguang/shiguang/agent/biz/service/impl/AgentServiceImpl.java`

