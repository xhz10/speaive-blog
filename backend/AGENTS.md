# 后端开发约束

本文件适用于 `backend/` 下的全部代码和文档。与用户沟通使用中文；代码标识符、包名和提交信息沿用仓库现有英文风格。

## 架构边界

后端是一个模块化单体，最终代码结构固定为 `domain`、`application`、`infrastructure`、`interfaces`、`start` 五个 Maven 模块。不要新增横跨这些边界的 `common`、`shared` 或按技术随意分层的模块。

- `domain` 只放聚合、实体、值对象、领域服务、领域事件和业务枚举，不依赖 Spring、HTTP、MyBatis、文件系统或数据库模型。
- `application` 只放用例、input ports、output ports 以及用例级 Command/Query/Result，依赖 `domain`，不依赖具体适配器。
- `interfaces` 是入站适配层，负责 HTTP、定时投递箱、Session、CSRF、参数校验、DTO 和错误码映射；只能通过 application input port 发起用例，不得直接调用 Mapper、Repository 实现或 output port。
- `infrastructure` 是出站适配层，负责 PostgreSQL、MyBatis-Plus、Flyway、Markdown、媒体文件及第三方 SDK；实现 application output port，不承载业务决策。
- `start` 是唯一启动模块和组合根。它可以依赖并装配所有模块，这是相邻层依赖规则的唯一例外；除配置、Bean 装配、进程启动外，不得放业务逻辑。

正常调用方向是 `interfaces -> application -> domain`；需要 I/O 时由 `application -> output port <- infrastructure` 完成。禁止 Controller 直连持久化、application 引用 PO/Mapper、domain 引用外层类型，也不要为了省一次映射跨层返回对象。

## 富聚合与业务模型

- 新增或重构业务行为必须进入聚合根或合适的领域对象；创建、内容修改、发布、撤回和归档等状态变化不能散落成 Controller 或持久化适配器中的事务脚本。
- 聚合负责校验业务不变量并提供有业务含义的方法，避免公开 setter 和只装数据的贫血模型。现有过渡代码不应作为新增业务的模板。
- application 负责权限之外的用例编排、端口调用和结果转换，不重复实现聚合规则。
- 业务枚举定义在 `domain`，禁止用魔法字符串表达文章状态、作者类型或修订事件。数据库值和 HTTP 值需要保持兼容时，应在边界显式映射；修改已有枚举值必须同时评估迁移和 API 兼容性。
- 聚合之间通过稳定标识协作，不把另一个聚合的可变内部对象直接纳入自身状态。

## PO、DO、DTO 与映射

- PO（Persistence Object）只存在于 `infrastructure`，按表结构和查询需要建模，可以带 MyBatis 持久化注解，但不包含领域规则。
- DO（Domain Object）只存在于 `domain`，包括聚合、实体和值对象，不带数据库或 JSON 注解。
- DTO 只存在于 `interfaces`，用于外部请求和响应、序列化及参数校验；不得直接持久化，也不得作为 output port 参数。
- application 的 Command、Query、Result 是用例边界模型，不命名为 DTO，也不依赖 PO。
- 新增或重构的结构映射统一使用 MapStruct，并把 Mapper 放在对应适配层边界。简单字段复制交给 MapStruct；状态决策、默认值和权限判断必须留在领域或应用代码中，不能藏进映射表达式。
- 当前仍存在的手工映射可以渐进迁移；触碰相关映射时再引入 MapStruct 配置和编译期 processor。禁止使用 BeanUtils、反射复制或 JSON 往返完成映射。

## Ports 与适配器

- 每个对外用例由 application input port 描述，入站适配器只依赖该接口；用例实现放在 application。
- 数据库、文件、时钟、外部 AI/向量服务等能力由 application output port 描述，具体实现放在 infrastructure。
- port 签名使用领域对象或 application 边界模型，不暴露 `MultipartFile`、`HttpServletRequest`、MyBatis Entity、`ResultSet` 等框架类型。
- 新增定时任务、消息消费者或命令行入口也视为入站适配器，应调用 input port，不得绕过用例直接操作 output port。

## 数据库迁移、一致性与并发

- 数据库结构只由 Flyway 管理。已经发布的迁移不可修改或重排；任何 schema、约束、索引、枚举值或回填变化都新增下一个版本迁移。
- 迁移必须同时支持空库安装和真实旧数据升级。涉及作者、活动文章或修订历史时，要覆盖只有历史修订、没有活动行的归档数据。
- PostgreSQL 是文章真源，不使用 H2 模拟锁、事务、约束或 SQL 方言。
- 当前 API `version` 是不透明并发令牌，PostgreSQL 实现保持 `postId:revision` 语义。创建、更新、发布、撤回和归档各推进一次 revision；陈旧令牌必须得到 `409 VERSION_CONFLICT`。
- 写入必须使用行锁或带 `id + slug + revision` 条件的 CAS，不能先读后无条件覆盖。归档后重用 slug 时，旧文章令牌不得命中新文章。
- 一个用例内的文章主记录、标签、修订快照和归档删除必须处于同一事务；任一步失败都要回滚。数据库与媒体文件这类双资源操作必须保留失败补偿和完整性校验。
- Markdown 投递箱按内容哈希去重时，必须在同一数据库事务内先按哈希串行化，再检查和记录台账；禁止用无锁的“先查再写”实现并发去重。
- 事务边界跟随 application 用例语义，具体 Spring 事务实现留在 infrastructure 或 `start` 装配，不向 domain 泄漏技术注解。

## 兼容性与测试

- 保持 `/api/v1` 请求字段、响应结构、HTTP 状态和 `{code,message}` 错误体兼容。公开 API 只能读取 `PUBLISHED`；作者由服务端分配，后续写操作必须保留原作者。
- 领域规则新增聚合单元测试；application 用假 port 验证用例编排；infrastructure 用真实 PostgreSQL Testcontainers 验证迁移、CAS 和事务；interfaces 用 MockMvc 验证 JSON、认证、CSRF 和错误码。
- 修改仓储或其他 output port 时，要同时验证端口契约、PostgreSQL 适配器和 application 用例；禁止再引入第二套自行实现文章生命周期的存储适配器。
- 后端测试需要 Java 25。仓库根目录执行：

```bash
cd backend
env -u JAVA_HOME sh -c '. ../scripts/java-25.sh && use_java_25 && ./mvnw test'
```

- 发布前执行完整校验；Testcontainers 测试要求 Docker 可用：

```bash
cd backend
env -u JAVA_HOME sh -c '. ../scripts/java-25.sh && use_java_25 && ./mvnw clean verify'
```

- 不得通过跳过测试、删除并发断言或改用 H2 让构建通过。文档-only 变更至少执行 `git diff --check`。
