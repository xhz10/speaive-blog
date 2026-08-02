#!/bin/sh

set -eu
umask 077

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_DIR/scripts/load-env.sh"
ENV_FILE=${SPEAIVE_ENV_FILE:-$PROJECT_DIR/.env}
load_env_defaults "$ENV_FILE"

DATA_DIR=${SPEAIVE_DATA_DIR:-$PROJECT_DIR/.data}
BACKUP_DIR=${SPEAIVE_BACKUP_DIR:-/var/backups/speaive-blog}
RETENTION_DAYS=${SPEAIVE_BACKUP_RETENTION_DAYS:-30}

case "$DATA_DIR" in
  /*) ;;
  *) DATA_DIR="$PROJECT_DIR/$DATA_DIR" ;;
esac

case "$DATA_DIR" in
  /) echo "拒绝使用根目录作为 SPEAIVE_DATA_DIR" >&2; exit 1 ;;
esac
case "$BACKUP_DIR" in
  /|"") echo "拒绝使用根目录作为 SPEAIVE_BACKUP_DIR" >&2; exit 1 ;;
  /*) ;;
  *) echo "SPEAIVE_BACKUP_DIR 必须是绝对路径" >&2; exit 1 ;;
esac
case "$RETENTION_DAYS" in
  *[!0-9]*|"") echo "SPEAIVE_BACKUP_RETENTION_DAYS 必须是非负整数" >&2; exit 1 ;;
esac

if [ ! -d "$DATA_DIR" ]; then
  echo "数据目录不存在：$DATA_DIR" >&2
  exit 1
fi

DATA_DIR=$(CDPATH= cd -- "$DATA_DIR" && pwd -P)
while [ "${DATA_DIR#//}" != "$DATA_DIR" ]; do
  DATA_DIR="/${DATA_DIR#//}"
done
case "$DATA_DIR" in
  /) echo "拒绝使用根目录作为 SPEAIVE_DATA_DIR" >&2; exit 1 ;;
esac

mkdir -p "$BACKUP_DIR"
BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd -P)
while [ "${BACKUP_DIR#//}" != "$BACKUP_DIR" ]; do
  BACKUP_DIR="/${BACKUP_DIR#//}"
done
case "$BACKUP_DIR" in
  /) echo "拒绝使用根目录作为 SPEAIVE_BACKUP_DIR" >&2; exit 1 ;;
esac
chmod 0700 "$BACKUP_DIR"
export SPEAIVE_DATA_DIR="$DATA_DIR"

case "$BACKUP_DIR/" in
  "$DATA_DIR/"*)
    echo "备份目录不能位于数据目录内部" >&2
    exit 1
    ;;
esac

LOCK_DIR="$BACKUP_DIR/.backup.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo "已有备份任务在运行：$LOCK_DIR" >&2
  exit 1
fi

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
archive="$BACKUP_DIR/speaive-backup-$timestamp.tar.gz"
temporary="$archive.tmp.$$"
staging="$BACKUP_DIR/.speaive-backup-$timestamp.$$"

cleanup() {
  rm -f "$temporary"
  if [ -d "$staging" ]; then
    rm -rf "$staging"
  fi
  rmdir "$LOCK_DIR" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

if [ -e "$archive" ]; then
  echo "同名备份已存在，请稍后重试：$archive" >&2
  exit 1
fi

mkdir "$staging"

echo "正在导出 PostgreSQL..."
(
  cd "$PROJECT_DIR"
  docker compose exec -T postgres sh -ec \
    'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom'
) > "$staging/postgres.dump"

echo "正在归档图片与 Markdown 投递目录..."
tar -C "$DATA_DIR" -czf "$staging/data.tar.gz" .
printf 'created_at=%s\ndata_directory=%s\n' "$timestamp" "$DATA_DIR" > "$staging/manifest.txt"

tar -C "$staging" -czf "$temporary" .
tar -tzf "$temporary" >/dev/null
mv "$temporary" "$archive"

archive_name=$(basename "$archive")
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && sha256sum "$archive_name" > "$archive_name.sha256")
elif command -v shasum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && shasum -a 256 "$archive_name" > "$archive_name.sha256")
else
  echo "警告：找不到 SHA-256 工具，未生成校验文件" >&2
fi

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'speaive-backup-*.tar.gz' -o -name 'speaive-backup-*.tar.gz.sha256' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "完整备份已生成：$archive"
