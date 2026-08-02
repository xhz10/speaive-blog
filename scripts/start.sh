#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_DIR/scripts/java-25.sh"
. "$PROJECT_DIR/scripts/database-env.sh"
. "$PROJECT_DIR/scripts/load-env.sh"

load_env_defaults "$PROJECT_DIR/.env"

use_java_25
configure_database_env

: "${SPEAIVE_DATA_DIR:?请在 .env 中设置仓库外的 SPEAIVE_DATA_DIR}"
: "${SPEAIVE_ADMIN_PASSWORD_HASH:?请在 .env 中设置 BCrypt 管理员密码哈希}"

case "$SPEAIVE_DATA_DIR" in
  /*) ;;
  *) echo "生产环境的 SPEAIVE_DATA_DIR 必须是绝对路径" >&2; exit 1 ;;
esac

BACKEND_JAR="$PROJECT_DIR/backend/speaive-blog-start/target/speaive-blog-backend.jar"
FRONTEND_ENTRY="$PROJECT_DIR/dist/server/entry.mjs"
if [ ! -f "$BACKEND_JAR" ] || [ ! -f "$FRONTEND_ENTRY" ]; then
  echo "未找到构建产物，请先运行 pnpm build" >&2
  exit 1
fi

SPEAIVE_BACKEND_PORT=${SPEAIVE_BACKEND_PORT:-8080}
SPEAIVE_BIND_ADDRESS=${SPEAIVE_BIND_ADDRESS:-127.0.0.1}
SPEAIVE_PORT=${SPEAIVE_PORT:-4321}
export SPEAIVE_BACKEND_PORT SPEAIVE_BIND_ADDRESS SPEAIVE_PORT

SPEAIVE_DATA_DIR="$SPEAIVE_DATA_DIR" "$PROJECT_DIR/scripts/init-data-dir.sh" >/dev/null

backend_pid=""
frontend_pid=""
cleanup() {
  if [ -n "$frontend_pid" ] && kill -0 "$frontend_pid" 2>/dev/null; then
    kill "$frontend_pid" 2>/dev/null || true
    wait "$frontend_pid" 2>/dev/null || true
  fi
  if [ -n "$backend_pid" ] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

"$JAVA_HOME/bin/java" -jar "$BACKEND_JAR" &
backend_pid=$!

attempt=0
until curl --fail --silent "http://127.0.0.1:$SPEAIVE_BACKEND_PORT/actuator/health" >/dev/null 2>&1; do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Spring Boot 启动失败" >&2
    wait "$backend_pid"
    exit 1
  fi
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "等待 Spring Boot 启动超时" >&2
    exit 1
  fi
  sleep 0.5
done

cd "$PROJECT_DIR"
HOST="$SPEAIVE_BIND_ADDRESS" PORT="$SPEAIVE_PORT" node "$FRONTEND_ENTRY" &
frontend_pid=$!
wait "$frontend_pid"
