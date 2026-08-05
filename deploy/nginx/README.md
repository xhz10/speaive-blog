# Nginx 参考配置

本目录收录本博客生产环境实际使用的 nginx 站点配置（公网 IP 已用占位符脱敏），用于参考和灾难恢复。说明见 [`docs/deployment.md`](../../docs/deployment.md) §3、§7。

## 文件

- `blog-ip.conf` — 80 端口 `default_server`，**用 IP 直接访问**时命中。域名备案未通过期间的主入口。走 HTTP/IP 时需把 `.env` 的 `SPEAIVE_SECURE_COOKIES` 设为 `false`，否则后台登录失效。
- `speaive.cn.conf` — 域名入口：80 跳 443，443 反代到前端容器。备案通过后使用。

两个站点都整体反代到前端容器 `http://127.0.0.1:9266`（端口由根目录 `docker-compose.override.yml` 决定；未用 override 时是 4321）。`/api/*`、`/media/*` 由前端容器内部转发到后端，nginx 无需拆分路由。

## 安装

```bash
sudo cp deploy/nginx/blog-ip.conf   /etc/nginx/sites-available/blog-ip
sudo cp deploy/nginx/speaive.cn.conf /etc/nginx/sites-available/speaive.cn
sudo ln -sf /etc/nginx/sites-available/blog-ip   /etc/nginx/sites-enabled/blog-ip
sudo ln -sf /etc/nginx/sites-available/speaive.cn /etc/nginx/sites-enabled/speaive.cn
sudo nginx -t && sudo systemctl reload nginx
```

## 安全

- 公网只开放 `80/443`。后端、PostgreSQL 不映射宿主机端口；前端只绑 `127.0.0.1:9266`。
- 证书文件（`.pem` / `.key`）不在本仓库，按各自路径放到 `/etc/nginx/ssl/`。
- 改配置前先 `cp` 一份 `.bak`；`nginx -t` 通过再 `reload`，失败不要 reload。
