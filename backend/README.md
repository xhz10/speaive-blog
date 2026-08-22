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

- `domain`：文章、状态和媒体领域模型，不依赖 Spring；
- `application`：用例编排和存储端口，依赖 `domain`；
- `infrastructure`：MyBatis-Plus、Flyway、Markdown、媒体文件和导入箱；
- `interfaces`：HTTP 请求模型、Session 登录、CSRF 和限流；
- `start`：唯一启动与装配模块，产出可运行 JAR。

这是一个服务、一个进程、一个部署单元；Maven 模块只用于约束代码边界。

## 数据模型

`blog_post` 保存活动文章，`blog_post_revision` 保存创建、更新、发布、撤回和归档快照。每次写操作都在事务中递增 revision，并通过 `WHERE slug + revision` 做 CAS；旧客户端写入会得到 `409 VERSION_CONFLICT`。归档在同一事务中写快照并删除活动行，因此历史保留且 slug 可以重新使用。

文章正文保存 Markdown，不持久化 HTML。列表查询不读取正文，详情和预览由后端实时渲染并过滤危险 HTML。

图片原文件位于 `SPEAIVE_DATA_DIR/media`，`blog_media` 保存路径、MIME、大小和 SHA-256，`blog_post_media` 记录文章引用。只有被公开且已发布文章引用的媒体可以匿名读取，其余媒体需要管理员 Session。Markdown 导入箱位于 `SPEAIVE_IMPORT_DIR`，默认是 `$SPEAIVE_DATA_DIR/inbox`。

## 构建与测试

测试会通过 Testcontainers 启动真实的 `pgvector/pgvector:pg17`，因此需要 Docker 正在运行：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk ./mvnw clean verify
```

Flyway 会在测试容器的空数据库执行完整迁移。不要使用 H2 代替 PostgreSQL 验证锁、事务或 SQL 方言。

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
- `GET /api/v1/public/posts`、`GET /api/v1/public/posts/{slug}`、`GET /media/**`：只读取公开且已发布的文章及其媒体；
- `GET /api/v1/studio/media/**`：管理员读取尚未公开或仅管理员文章的媒体。

默认 Markdown 上限 1 MiB，图片上限 8 MiB，请求上限 9 MiB。
