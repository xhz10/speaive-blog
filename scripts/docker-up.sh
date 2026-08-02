#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_DIR/scripts/load-env.sh"

if [ ! -f "$PROJECT_DIR/.env" ]; then
  echo "缺少 .env，请先复制 .env.example 并填写数据库密码和管理员密码哈希" >&2
  exit 1
fi

load_env_defaults "$PROJECT_DIR/.env"

case "${SPEAIVE_DB_PASSWORD:-}" in
  ""|replace-with-*)
    echo "请先在 .env 中设置真实的 SPEAIVE_DB_PASSWORD" >&2
    exit 1
    ;;
esac

case "${SPEAIVE_ADMIN_PASSWORD_HASH:-}" in
  ""|*replace-with-*)
    echo "请先在 .env 中设置 pnpm password:hash 生成的 BCrypt 哈希" >&2
    exit 1
    ;;
esac

SPEAIVE_DATA_DIR=${SPEAIVE_DATA_DIR:-$PROJECT_DIR/.data}
case "$SPEAIVE_DATA_DIR" in
  /*) ;;
  *) SPEAIVE_DATA_DIR="$PROJECT_DIR/$SPEAIVE_DATA_DIR" ;;
esac

SPEAIVE_DATA_DIR="$SPEAIVE_DATA_DIR" "$PROJECT_DIR/scripts/init-data-dir.sh" >/dev/null
SPEAIVE_DATA_DIR=$(CDPATH= cd -- "$SPEAIVE_DATA_DIR" && pwd -P)
SPEAIVE_RUNTIME_UID=${SPEAIVE_RUNTIME_UID:-$(id -u)}
SPEAIVE_RUNTIME_GID=${SPEAIVE_RUNTIME_GID:-$(id -g)}
export SPEAIVE_DATA_DIR SPEAIVE_RUNTIME_UID SPEAIVE_RUNTIME_GID

cd "$PROJECT_DIR"
docker compose up --build --detach --wait "$@"

echo "博客：${SPEAIVE_SITE_URL:-http://127.0.0.1:4321}"
echo "写作台：${SPEAIVE_SITE_URL:-http://127.0.0.1:4321}/studio"
