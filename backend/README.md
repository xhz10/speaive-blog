# Speaive Blog Backend

Speaive Blog 的 Java 25 + Spring Boot 4.0.7 模块化单体后端，负责写作台认证、文章生命周期、Markdown 渲染、图片和公开读取 API。

## 技术基线

- Java 25、Maven 3.9+
- Spring Boot 4、Spring MVC、Spring Security
- PostgreSQL 17、MyBatis-Plus 3.5.17、Flyway
- `pgvector` 扩展已启用，向量业务表留到 Spring AI 阶段
- Testcontainers PostgreSQL 集成测试

## 模块

```text
speaive-blog-domain
        ^
speaive-blog-application
        ^
        +-------------------------------+
        |                               |
speaive-blog-infrastructure   speaive-blog-interfaces
        ^                               ^
        +---------------+---------------+
                        |
              speaive-blog-start
```

- `domain`：聚合、实体、值对象和业务枚举，不依赖 Spring；
- `application`：用例、input ports 和 output ports，依赖 `domain`；
- `infrastructure`：output port 的 PostgreSQL/文件适配器，以及 Flyway、Markdown 和媒体能力；
- `interfaces`：HTTP 与定时投递箱入站适配器、DTO、Session 登录、CSRF 和限流；
- `start`：唯一启动与组合根，负责依赖装配并产出可运行 JAR。

这是一个服务、一个进程、一个部署单元；Maven 模块只用于约束代码边界。

## 分层与模型约束

正常调用方向是 `interfaces -> application -> domain`；application 需要数据库、文件或外部服务时只调用 output port，由 infrastructure 提供实现。`start` 因承担组合根可以依赖所有模块，但不放业务逻辑。Controller 不直连 Mapper，application 不引用持久化对象，domain 不引用任何外层类型。

新增和重构的业务采用富聚合：文章状态变化和业务不变量收敛到聚合根，application 只编排用例。业务枚举放在 domain，不用魔法字符串绕过类型约束。

边界模型严格区分：PO 只属于 infrastructure，DO 只属于 domain，DTO 只属于 interfaces，application 使用自己的 Command/Query/Result。新增或重构的结构映射统一使用 MapStruct；当前手工映射按触碰范围渐进迁移，不使用 BeanUtils、反射复制或 JSON 往返。完整规则见 [`backend/AGENTS.md`](AGENTS.md)。

## 数据模型

`blog_user` 保存内容身份，当前内置固定 `admin`；它与环境变量提供的登录凭证相互独立，不保存密码。`blog_post` 保存活动文章，`blog_post_revision` 保存创建、更新、发布、撤回和归档快照，两者都通过 `author_id` 引用作者。每次写操作都在事务中递增 revision，并通过 `id + slug + revision` 做 CAS；旧客户端写入会得到 `409 VERSION_CONFLICT`。归档在同一事务中写快照并删除活动行，因此历史保留且 slug 可以重新使用。

文章正文保存 Markdown，不持久化 HTML。列表查询不读取正文，详情和预览由后端实时渲染并过滤危险 HTML。

图片原文件位于 `SPEAIVE_DATA_DIR/media`，`blog_media` 保存路径、MIME、大小和 SHA-256。Markdown 导入箱位于 `SPEAIVE_IMPORT_DIR`，默认是 `$SPEAIVE_DATA_DIR/inbox`。

## 构建与测试

测试会通过 Testcontainers 启动真实的 `pgvector/pgvector:pg17`，因此需要 Docker 正在运行：

```bash
env -u JAVA_HOME sh -c '. ../scripts/java-25.sh && use_java_25 && ./mvnw test'
```

发布前使用同一 Java 25 环境执行 `./mvnw clean verify`。Flyway 会在测试容器的空数据库执行完整迁移，并通过独立测试验证带活动文章和仅归档修订的 V1 数据升级到 V2。不要修改已发布迁移，也不要使用 H2 代替 PostgreSQL 验证锁、事务或 SQL 方言。

## 独立启动

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk ./mvnw clean package

SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/speaive_blog \
SPRING_DATASOURCE_USERNAME=speaive \
SPRING_DATASOURCE_PASSWORD=替换为数据库密码 \
SPEAIVE_DATA_DIR=/srv/speaive-blog/data \
SPEAIVE_ADMIN_USERNAME=admin \
SPEAIVE_ADMIN_PASSWORD_HASH='$2y$12$替换为完整 BCrypt 哈希' \
  /opt/homebrew/opt/openjdk/bin/java \
  -jar speaive-blog-start/target/speaive-blog-backend.jar
```

默认监听 `127.0.0.1:8080`，健康检查是 `GET /actuator/health`。未配置管理员哈希时服务可以启动，但写作台登录会被禁用。

## HTTP 接口

- `GET /api/v1/studio/csrf`：获取 CSRF token；
- `POST /api/v1/studio/login|logout`、`GET /api/v1/studio/session`：单管理员 Session；
- `/api/v1/studio/posts`：草稿创建、列表、详情和带 revision 更新；
- `/api/v1/studio/posts/{slug}/publish|unpublish|archive`：状态操作；
- `POST /api/v1/studio/import|media|preview`：Markdown 导入、图片上传和预览；
- `GET /api/v1/public/posts`、`GET /api/v1/public/posts/{slug}`、`GET /media/**`：公开读取。

默认 Markdown 上限 1 MiB，图片上限 8 MiB，请求上限 9 MiB。
