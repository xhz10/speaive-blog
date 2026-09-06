# 后端阅读地图

这个后端是一个 Java 25 模块化单体：一个应用、一个 PostgreSQL 数据库、四个编译模块。已有 DDD 与端口适配器架构的基础，不需要先掌握全部英文术语才能改代码。

建议按下面顺序阅读：

1. [架构与目录地图](architecture.md)：一次请求经过哪些层，各层为什么存在。
2. [业务模型与状态机](domain-model.md)：文章、作者、角色、评论、创作空间、访客分别是什么。
3. [请求流程](request-flows.md)：发布文章、生成回复、虚拟线程批量生成、访客采集。
4. [中文枚举字典](enum-dictionary.md)：每个英文枚举项的业务意思，以及源码位置。
5. [架构评审与重构建议](architecture-review.md)：哪些地方已经做对，哪些需要改，为什么不全面重写。
6. [访客统计使用与部署](visitor-analytics.md)：管理员入口、IP 和设备识别、地点库安装、统计口径。

7. [会员写作与内容加密](member-writing-and-encryption.md)：账号资格、个人主页、全部历史加密、密钥部署与边界。

## 想改一个功能，从哪里开始

| 你想做什么 | 第一处要看 |
| --- | --- |
| 修改“什么情况下可以发布文章” | [Post](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/post/Post.java) 的 `publish` |
| 修改“谁可以回复谁” | [Comment](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/comment/Comment.java) 的 `ensureCanReceiveAiReply` |
| 修改“生成前要准备什么上下文” | [CommentApplicationService](../speaive-blog-application/src/main/java/com/speaive/blog/application/service/CommentApplicationService.java) 的 `prepare` |
| 修改一批角色如何并发、失败如何返回 | [CommentBatchApplicationService](../speaive-blog-application/src/main/java/com/speaive/blog/application/service/CommentBatchApplicationService.java) |
| 调整 SQL 或索引 | [持久化目录](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/) 与 [迁移目录](../speaive-blog-infrastructure/src/main/resources/db/migration/) |
| 增加 HTTP 字段、验证或错误响应 | [HTTP 入站适配器](../speaive-blog-start/src/main/java/com/speaive/blog/interfaces/http/) |
| 调整谁能访问管理员功能 | [WebSecurityConfiguration](../speaive-blog-start/src/main/java/com/speaive/blog/interfaces/security/WebSecurityConfiguration.java) |
| 查看这些接口到底连了哪个实现 | [BlogBackendConfiguration](../speaive-blog-start/src/main/java/com/speaive/blog/BlogBackendConfiguration.java) |

## 先记住五个中文对应

- **聚合 Aggregate**：负责守住一组业务规则的对象，例如文章的发布、撤回和版本推进。
- **用例 Use case**：完成一个用户动作的步骤编排，例如读取文章、让聚合发布、存储变更。
- **端口 Port**：应用需要或提供的能力契约，例如“保存文章”“调用模型”。
- **适配器 Adapter**：契约的具体实现，例如用 PostgreSQL 保存文章、用 Spring AI 调模型。
- **组合根 Composition root**：启动时把接口和实现连接起来的地方，本项目就是 `BlogBackendConfiguration`。

注释解释业务含义、前置条件、事务和并发约束；普通 getter、setter 不逐行翻译。领域枚举和数据库枚举均有中文说明。后续修改枚举时应同步字典。
