# Speaive Blog

一个面向个人、低频写作的博客。公开站点负责阅读，`/studio` 提供登录、在线写作、Markdown 导入、图片上传和文章可见性设置；文章与程序代码分开保存，不进入 GitHub。

## 当前架构

- Astro SSR：公开博客、写作台界面和同源 API 转发；
- Java 25 + Spring Boot 4.0.7：登录、内容管理、上传和 Markdown 预览；
- PostgreSQL 17 + pgvector：文章、修订记录和媒体元数据；当前只启用 `vector` 扩展，向量表和嵌入模型等到引入 Spring AI 时再设计；
- 独立宿主机数据目录：正文图片、封面原文件和 Markdown 投递箱；
- 单管理员 Session 登录、BCrypt 密码、CSRF 防护和登录限流；
- 公开或仅管理员两档文章权限，私密正文和图片都不通过匿名接口暴露；
- Spring Boot 仍是一个部署单元，Maven 模块只用于约束代码边界。

浏览器只访问 Astro。Astro 在容器网络内访问 Spring Boot，Spring Boot 再访问 PostgreSQL；因此不需要给浏览器配置跨域，也不需要把数据库或后端端口暴露到公网。

## Docker 一键启动

需要 Docker Engine、Docker Compose 和 `htpasswd`（Linux 通常由 `apache2-utils` 提供）。首次运行：

```bash
cp .env.example .env
chmod 600 .env
./scripts/hash-password.sh
```

把生成的 BCrypt 哈希填入 `.env` 的 `SPEAIVE_ADMIN_PASSWORD_HASH`，并替换 `SPEAIVE_DB_PASSWORD`，然后启动：

```bash
./scripts/docker-up.sh
```

该脚本实际执行 `docker compose up --build -d --wait`，并自动初始化数据目录、把当前用户的 UID/GID 传给后端容器，避免宿主机数据目录出现 root 所有者。数据目录已经初始化时，等价命令是：

```bash
SPEAIVE_RUNTIME_UID="$(id -u)" \
SPEAIVE_RUNTIME_GID="$(id -g)" \
docker compose up --build -d --wait
```

启动完成后访问：

- 博客：`http://127.0.0.1:4321`
- 写作台：`http://127.0.0.1:4321/studio`

后台运行、查看状态和停止：

```bash
docker compose ps
docker compose logs -f frontend backend postgres
docker compose down
```

`docker compose down` 只停止并删除容器，不删除文章或图片。不要随意使用 `docker compose down -v`，它会删除 PostgreSQL 数据卷；宿主机数据目录虽然仍在，但只剩图片和投递文件，不是一份完整博客。

项目脚本读取配置时遵循“命令行环境变量 > `.env` > 内置默认值”。例如可用 `SPEAIVE_BACKUP_DIR=/tmp/speaive-backups ./scripts/backup-data.sh` 临时覆盖备份目录，而不必修改 `.env`。

## 数据持久化

内容分成两部分保存：

- `postgres_data` 命名卷：文章、修订记录和媒体元数据；
- `SPEAIVE_DATA_DIR` 宿主机目录：上传图片和 Markdown 投递箱。本地默认是已被 Git 忽略的 `.data`，服务器建议设置为 `/srv/speaive-blog/data`。

`git pull`、重新构建镜像或替换容器不会把这些内容带入 Git，也不会覆盖已有内容。数据目录结构如下：

```text
SPEAIVE_DATA_DIR/
├── media/              # 上传图片
└── inbox/              # 直接投递 Markdown
    ├── imported/       # 已成功导入
    └── rejected/       # 导入失败及原因文件
```

直接投递时先上传为隐藏的 `.uploading` 临时文件，完成后原子改名成 `.md`。后端会自动导入 PostgreSQL，并将源文件移动到 `imported/` 或 `rejected/`；完整格式和命令见 [docs/content-files.md](docs/content-files.md)。

文章状态与阅读权限彼此独立：已发布文章也可以设置为“仅管理员”，此时不会进入首页、归档、RSS 或 Sitemap，只能登录后从写作台阅读。新文章默认仅管理员，历史文章升级后保持公开。设计、安全边界与使用说明见 [docs/content-visibility.md](docs/content-visibility.md)。

备份时两部分必须一起保存，且备份期间不要发布或上传文章。项目脚本会同时生成 PostgreSQL 自包含 dump 和数据目录归档：

管理员首次准备备份目录，之后始终由部署用户执行：

```bash
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /var/backups/speaive-blog
SPEAIVE_BACKUP_DIR=/var/backups/speaive-blog ./scripts/backup-data.sh
```

生成的 `speaive-backup-*.tar.gz` 同时包含 `postgres.dump` 和 `data.tar.gz`，可再由 Mac mini 通过 `rsync` 单向拉取。备份只有在做过实际恢复验证后才算可用，步骤见 [docs/recovery.md](docs/recovery.md)。

## 部署到自己的服务器

将 `.env` 中的 `SPEAIVE_SITE_URL` 改成真实 HTTPS 地址、把 `SPEAIVE_DATA_DIR` 改为仓库外绝对路径，并把 `SPEAIVE_SECURE_COOKIES` 改为 `true`，然后重新执行 `./scripts/docker-up.sh`。域名会参与 Astro 的构建期安全配置，所以修改域名后需要重建前端镜像。

反向代理只需指向 Astro：

```nginx
location / {
    client_max_body_size 9m;
    proxy_pass http://127.0.0.1:4321;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

公网只开放 `80/443`。Spring Boot 和 PostgreSQL 不映射宿主机端口，只能在 Compose 内部网络访问。

## 本地开发

不使用容器运行应用时，需要 Node.js 22.12+、pnpm 11.9+、JDK 25，以及一个已安装 `vector` 扩展的 PostgreSQL 17：

```bash
pnpm install
cp .env.example .env
pnpm password:hash
pnpm dev
```

`pnpm dev` 会构建并启动 Spring Boot，再启动 Astro 开发服务。数据库连接默认由 `.env` 中的 `SPEAIVE_DB_HOST`、`SPEAIVE_DB_PORT`、`SPEAIVE_DB_NAME`、`SPEAIVE_DB_USERNAME` 和 `SPEAIVE_DB_PASSWORD` 组成；也可以直接设置 Spring 标准的 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD` 覆盖它们。

本地检查和构建：

```bash
pnpm check
pnpm test
pnpm build
```

后端模块说明见 [backend/README.md](backend/README.md)。

## Credits

默认公共封面图 `public/images/editorial-writing.jpg` 来源于 [Unsplash](https://unsplash.com/)（资源标识：`photo-1455390582262-044cdead277a`）。
# speaive-blog
