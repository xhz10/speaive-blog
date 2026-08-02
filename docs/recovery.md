# 备份恢复演练

恢复会替换当前数据库和媒体目录。先在临时服务器演练；生产恢复前必须再备份一次当前状态，并确认维护窗口内没有写作、发布或上传操作。

以下命令假设当前目录是项目根目录，`.env` 已配置，备份文件和同名 `.sha256` 位于 `/var/backups/speaive-blog/`。

## 1. 校验并解包

```bash
cd /var/backups/speaive-blog
shasum -a 256 -c speaive-backup-YYYYMMDDTHHMMSSZ.tar.gz.sha256

restore_dir="$(mktemp -d /tmp/speaive-restore.XXXXXX)"
tar -xzf speaive-backup-YYYYMMDDTHHMMSSZ.tar.gz -C "$restore_dir"
tar -tzf "$restore_dir/data.tar.gz" >/dev/null
```

Linux 可以使用 `sha256sum -c` 代替 `shasum -a 256 -c`。

## 2. 停止写入并保留当前副本

```bash
cd /srv/speaive-blog/app
docker compose stop frontend backend

current_copy="/srv/speaive-blog/data.before-restore.$(date -u +%Y%m%dT%H%M%SZ)"
mv /srv/speaive-blog/data "$current_copy"
install -d -m 0750 /srv/speaive-blog/data
tar -xzf "$restore_dir/data.tar.gz" -C /srv/speaive-blog/data
```

`current_copy` 是本次恢复前的可回退副本。不要在验证完成前删除它。

## 3. 重建数据库

下面的命令使用 PostgreSQL 容器内已有的 `POSTGRES_USER` 和 `POSTGRES_DB`，不会在终端展开数据库密码：

```bash
docker compose exec -T postgres sh -ec '
  dropdb --if-exists --force --username="$POSTGRES_USER" "$POSTGRES_DB"
  createdb --username="$POSTGRES_USER" "$POSTGRES_DB"
'

docker compose exec -T postgres sh -ec '
  exec pg_restore --exit-on-error --no-owner \
    --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"
' < "$restore_dir/postgres.dump"
```

如果 `pg_restore` 失败，不要启动应用。检查错误后重新创建空库再恢复，避免在半恢复数据库上继续运行。

## 4. 启动与验证

```bash
./scripts/docker-up.sh
docker compose ps
curl --fail --show-error http://127.0.0.1:4321/api/health
```

随后用浏览器确认：

- 公开首页和一篇已发布文章可读；
- 写作台可以登录，草稿和已发布文章的详情、状态均能正常读取；
- 至少一张历史图片能够加载；
- 新建并保存一篇临时草稿后 revision 正常递增。

确认无误后再清理 `$restore_dir` 和 `data.before-restore.*`。恢复演练应记录所用备份、耗时和验证结果。
