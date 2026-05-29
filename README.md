# 拾光 shiguang

拾光是一个仿小红书的内容社区后端项目，采用 Spring Cloud Alibaba 微服务架构实现。项目围绕用户、认证、笔记、评论、点赞计数、关注关系、搜索、推荐、对象存储和 AI Agent 等业务场景拆分服务，适合作为内容社区、分布式系统和微服务治理能力的综合实践项目。

## 项目特性

- 微服务拆分：按业务边界拆分认证、用户、笔记、评论、计数、搜索、推荐、对象存储等服务。
- 统一网关：通过 Gateway 承接外部请求，负责路由转发和统一入口治理。
- 权限认证：基于 Sa-Token 实现登录认证和权限上下文传递。
- 内容互动：支持笔记发布、评论、点赞、收藏、关注等社区核心链路。
- 分布式 ID：提供独立 ID 生成服务，支撑高并发写入场景。
- 缓存与异步化：结合 Redis、RocketMQ、Caffeine 等组件提升吞吐和稳定性。
- 搜索能力：基于 Elasticsearch 构建笔记和用户搜索能力。
- 对象存储：支持 MinIO 和阿里云 OSS，用于图片等文件上传。
- 推荐与 AI：包含推荐服务、AI Agent 服务和基准测试模块，用于扩展智能化能力。

## 技术栈

- Java 17
- Spring Boot 3.0.2
- Spring Cloud 2022.0.0
- Spring Cloud Alibaba 2022.0.0.0
- Maven 多模块工程
- MyBatis、MySQL、Druid
- Redis、Caffeine
- RocketMQ
- Nacos
- Elasticsearch 7.3.0
- Canal
- XXL-JOB
- Sa-Token
- MinIO、Aliyun OSS
- Spring AI

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `shiguang-framework` | 公共基础框架、通用组件和 starter |
| `shiguang-gateway` | API 网关服务 |
| `shiguang-auth` | 认证、登录、验证码和短信相关能力 |
| `shiguang-user` | 用户资料、账号信息和用户基础能力 |
| `shiguang-note` | 笔记发布、查询、可见性和笔记业务 |
| `shiguang-comment` | 评论发布、删除、分页查询和评论热度 |
| `shiguang-count` | 用户、笔记等计数服务 |
| `shiguang-user-relation` | 关注、取关、粉丝和关注列表 |
| `shiguang-kv` | KV 存储服务 |
| `shiguang-distributed-id-generator` | 分布式 ID 生成服务 |
| `shiguang-oss` | 文件上传、MinIO 和阿里云 OSS 对接 |
| `shiguang-search` | Elasticsearch 搜索、索引构建和搜索建议 |
| `shiguang-recommend` | 个性化推荐服务 |
| `shiguang-data-align` | 数据同步和数据对齐服务 |
| `shiguang-agent` | AI Agent 服务 |
| `shiguang-benchmark` | 性能测试和基准测试 |

## 本地环境

建议准备以下基础组件：

- JDK 17
- Maven 3.8+
- MySQL
- Redis
- Nacos
- RocketMQ
- Elasticsearch
- Canal
- MinIO 或阿里云 OSS

不同服务依赖的中间件不完全相同，可以按需要单独启动对应模块。

## 配置说明

项目配置主要位于各服务的：

```text
src/main/resources/config/
```

常见配置文件：

- `application.yml`
- `application-dev.yml`
- `application-prod.yml`
- `bootstrap.yml`

涉及云服务密钥时，不要把真实密钥写入 Git。推荐通过环境变量或部署平台的 Secret 管理能力注入，例如：

```bash
ALIYUN_ACCESS_KEY_ID=your-access-key-id
ALIYUN_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_OSS_ACCESS_KEY=your-oss-access-key
ALIYUN_OSS_SECRET_KEY=your-oss-secret-key
```

## 构建项目

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

只构建指定模块及其依赖：

```bash
mvn clean package -pl shiguang-auth -am -DskipTests
```

## 启动服务

先启动依赖的中间件和注册配置中心，再按业务依赖启动各服务。常见启动顺序可参考：

1. Nacos、MySQL、Redis、RocketMQ、Elasticsearch 等基础组件
2. `shiguang-distributed-id-generator`
3. `shiguang-kv`
4. `shiguang-user`
5. `shiguang-auth`
6. `shiguang-oss`
7. `shiguang-note`
8. `shiguang-count`
9. `shiguang-comment`
10. `shiguang-user-relation`
11. `shiguang-search`
12. `shiguang-recommend`
13. `shiguang-agent`
14. `shiguang-gateway`

单个 Spring Boot 服务可以在对应模块目录下通过 Maven 启动，例如：

```bash
mvn spring-boot:run
```

## 目录结构

```text
shiguang
├── shiguang-framework
├── shiguang-gateway
├── shiguang-auth
├── shiguang-user
├── shiguang-note
├── shiguang-comment
├── shiguang-count
├── shiguang-user-relation
├── shiguang-kv
├── shiguang-distributed-id-generator
├── shiguang-oss
├── shiguang-search
├── shiguang-recommend
├── shiguang-data-align
├── shiguang-agent
└── shiguang-benchmark
```

## 注意事项

- 不要提交真实数据库密码、Redis 密码、阿里云 AccessKey、OSS Secret 等敏感信息。
- `target/`、日志文件、IDE 配置和构建产物不应进入 Git。
- 本地开发建议使用 `dev` 配置，生产部署使用独立的配置中心或 Secret 管理。
- 如果 GitHub Push Protection 拦截推送，需要从提交历史中彻底移除密钥，而不是只修改最新文件。
