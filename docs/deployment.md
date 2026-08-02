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

## 4. 更新

```bash
cd /srv/speaive-blog/app
SPEAIVE_BACKUP_DIR=/var/backups/speaive-blog ./scripts/backup-data.sh
git pull --ff-only
./scripts/docker-up.sh
```

Flyway 会在后端启动时迁移数据库。更新程序和重建容器不会删除文章、修订或图片。

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
