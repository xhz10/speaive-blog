# 自有服务器部署

推荐使用 Docker Compose。服务器只需要 Git、Docker Engine、Docker Compose 和用于生成 BCrypt 的 `htpasswd`；不需要单独安装 Node.js、JDK 或 PostgreSQL。

## 1. 初始化

```bash
git clone <你的仓库地址> /srv/speaive-blog/app
cd /srv/speaive-blog/app
cp .env.example .env
chmod 600 .env
./scripts/hash-password.sh
```

编辑 `.env`，至少替换这些值：

```dotenv
SPEAIVE_SITE_URL=https://blog.example.com
SPEAIVE_DATA_DIR=/srv/speaive-blog/data
SPEAIVE_DB_PASSWORD=替换为随机长密码
SPEAIVE_ADMIN_USERNAME=admin
SPEAIVE_ADMIN_PASSWORD_HASH='$2y$12$替换为脚本生成的完整哈希'
SPEAIVE_SECURE_COOKIES=true
```

BCrypt 哈希必须保留单引号，避免其中的 `$` 被 shell 展开。数据库和管理员密码不要提交到 Git。

需要向搜索平台验证站点时，可再填写 `SPEAIVE_GOOGLE_SITE_VERIFICATION`、`SPEAIVE_BING_SITE_VERIFICATION` 和 `SPEAIVE_BAIDU_SITE_VERIFICATION`。只填写平台给出的 meta `content` 值，完整提交与检查步骤见 [search-discovery.md](search-discovery.md)。

## 2. 一键启动

```bash
./scripts/docker-up.sh
```

脚本会准备宿主机数据目录、使用当前 UID/GID 启动后端，并执行 `docker compose up --build -d --wait`。检查状态：

```bash
docker compose ps
docker compose logs -f frontend backend postgres
curl --fail --show-error http://127.0.0.1:4321/api/health
```

停止服务使用 `docker compose down`。不要随意使用 `docker compose down -v`，后者会删除 PostgreSQL 数据卷。

## 3. HTTPS 反向代理

Compose 只监听宿主机回环地址 `127.0.0.1:4321`。Nginx 示例：

```nginx
server {
    listen 443 ssl;
    server_name blog.example.com;

    client_max_body_size 9m;

    location / {
        proxy_pass http://127.0.0.1:4321;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

公网只开放 `80/443`。修改域名后要重新执行 `./scripts/docker-up.sh`，因为站点地址也参与 Astro 构建期配置。

### 端口：默认 4321，本机已用 override 改为 9266

仓库里的 `docker-compose.override.yml` 把前端发布到宿主机的 `127.0.0.1:9266`、后端容器内端口改为 `9265`（仅 docker 内网可达）。所以本机实际反代地址是 `127.0.0.1:9266`，不是 4321：

```nginx
location / {
    proxy_pass http://127.0.0.1:9266;   # 未用 override 时这里是 4321
    ...
}
```

### 域名备案未通过：临时走 IP 访问

如果域名备案还没下来、必须用 IP 访问，再加一个 80 端口的 `default_server` 站点（Host 不匹配任何域名时命中），同样反代到前端：

```nginx
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    client_max_body_size 9m;
    location / {
        proxy_pass http://127.0.0.1:9266;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

走 HTTP/IP 时必须临时把 `SPEAIVE_SECURE_COOKIES=false`，否则后台登录用的 cookie 带 `Secure` 标志、浏览器不会在 HTTP 下保存，登录会失效。备案通过、切回 HTTPS 域名后再改回 `true`。完整步骤见下文 [§7 运维注意事项](#7-运维注意事项踩坑记录)。

> 生产环境的实际 nginx 配置（IP 入口 + 域名 HTTPS 两份）已脱敏收录在 [`deploy/nginx/`](../deploy/nginx/) 下，可作参考或灾难恢复用。

## 4. 更新

服务器部署分支统一回 `main`。首次切换前先确认工作区没有服务器本地改动并完成备份：

```bash
cd /srv/speaive-blog/app
SPEAIVE_BACKUP_DIR=/var/backups/speaive-blog ./scripts/backup-data.sh
git fetch origin
git switch main
git pull --ff-only origin main
./scripts/docker-up.sh
```

以后更新继续在该分支执行 `git pull --ff-only` 和 `./scripts/docker-up.sh`。脚本本身不需要为权限、AI 摘要、评论线程、会员 Agent、小说片段或创作闭环功能修改；它会重建镜像，Flyway 会在后端启动时按 `V1 -> V2 -> V3 -> V4 -> V5 -> V6 -> V7 -> V8 -> V9` 迁移数据库。更新程序和重建容器不会删除文章、小说片段、灵感、作品集、分享授权、会员、Agent、摘要、评论、修订或图片。

AI 评论默认关闭。需要启用时，在 `.env` 增加 `SPEAIVE_AI_ENABLED=true`、`SPEAIVE_AI_MODEL_CHAT=openai`、模型密钥、服务地址和默认模型，然后再次执行同一个 `./scripts/docker-up.sh`。会员自动评论默认开启调度，但只有 AI 总开关开启后才会执行；扫描周期、批量、重试和数量上限可用 `SPEAIVE_AI_COMMUNITY_*` 参数调整。完整模型配置见 [ai-comments.md](ai-comments.md)，邀请码、审核、自动任务和停机开关见 [community-agents.md](community-agents.md)。

原服务器分支 `origin/docs/deployment-ops-notes` 会在本次合并时与 `main` 对齐，其代码和部署记录已经包含在统一历史中。以后只在 `main` 开发和部署，不再单独推进旧分支，避免重新产生分叉。

## 5. 备份到 Mac mini

管理员首次创建目录并交给部署用户，日常备份不要使用 sudo：

```bash
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /var/backups/speaive-blog
SPEAIVE_BACKUP_DIR=/var/backups/speaive-blog ./scripts/backup-data.sh
```

每个 `speaive-backup-*.tar.gz` 同时包含：

- `postgres.dump`：文章、修订和媒体元数据；
- `data.tar.gz`：图片与 Markdown 投递目录；
- `manifest.txt`：创建时间和源目录。

Mac mini 定时单向拉取：

```bash
rsync -av --partial --ignore-existing \
  blog-server:/var/backups/speaive-blog/ \
  /Volumes/Backup/speaive-blog/
```

建议每天备份，并定期在临时 PostgreSQL 和临时目录中做一次恢复演练。只有数据库和媒体能够一起恢复，备份才完整。

## 6. 直接投递 Markdown

将完成传输的 `.md` 原子移动到 `$SPEAIVE_DATA_DIR/inbox/`。后端会把它导入为草稿，不会直接公开。格式与安全传输方式见 [content-files.md](content-files.md)。

## 7. 运维注意事项（踩坑记录）

### 重建 backend 必须带上运行时 UID/GID

`docker-up.sh` 会用 `SPEAIVE_RUNTIME_UID=$(id -u)` / `SPEAIVE_RUNTIME_GID=$(id -g)` 启动后端，数据目录的属主也与之匹配。如果像平时那样直接敲 `docker compose up -d backend`，shell 里没有这两个变量、`.env` 也没定义，compose 会回退到默认的 `1000:1000` —— 这时后端进程进不去属主是 root（或别的 UID）的数据目录，启动即崩：

```
java.nio.file.AccessDeniedException: /data/media
```

正确做法二选一：

```bash
# 方式 A：用项目脚本（自动 export UID/GID，但会 --build）
./scripts/docker-up.sh backend

# 方式 B：手动补上变量再 up（不 rebuild，最快）
export SPEAIVE_RUNTIME_UID="$(id -u)"
export SPEAIVE_RUNTIME_GID="$(id -g)"
docker compose up -d backend
```

> 小结：凡是要重建 backend 容器，都先确认 `SPEAIVE_RUNTIME_UID/GID` 已在环境里；普通 `restart` 不会触发这个问题（它复用旧容器）。

### SECURE_COOKIES：HTTP/IP 与 HTTPS 之间的切换

`SPEAIVE_SECURE_COOKIES` 控制后台 session cookie 是否带 `Secure` 标志，是**后端运行时**读取的（改完 `.env` 重建 backend 即可，不用 rebuild 镜像）。

- 走 HTTPS 域名（备案通过后）：保持 `true`。
- 临时走 HTTP/IP（备案未通过、要登录后台）：设成 `false`，重建 backend。代价是登录 cookie 明文传输，个人博客临时期可接受。

```bash
# 切换后记得带上 UID/GID 重建 backend
export SPEAIVE_RUNTIME_UID="$(id -u)" SPEAIVE_RUNTIME_GID="$(id -g)"
docker compose up -d backend
docker exec speaive-blog-backend-1 sh -c 'echo $SPEAIVE_SECURE_COOKIES'   # 核对生效
```

### Nginx 配置位置与回滚

生产 nginx 站点配置在 `/etc/nginx/sites-available/`，通过 `/etc/nginx/sites-enabled/` 的软链启用。改完先 `nginx -t` 再 `systemctl reload nginx`；`nginx -t` 失败就不要 reload。改前 `cp` 一份 `.bak`，出问题恢复后 reload 即可。参考配置见 [`deploy/nginx/`](../deploy/nginx/)。

### 公网端口

只对公网开放 `80/443`。后端（8080/9265）和 PostgreSQL（5432）都只在 Compose 内网，不映射宿主机端口；前端只绑定 `127.0.0.1:9266`，由 nginx 反代出去。
