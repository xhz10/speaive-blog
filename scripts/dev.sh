#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_DIR/scripts/java-25.sh"
. "$PROJECT_DIR/scripts/database-env.sh"
. "$PROJECT_DIR/scripts/load-env.sh"

load_env_defaults "$PROJECT_DIR/.env"

use_java_25
configure_database_env

SPEAIVE_DATA_DIR=${SPEAIVE_DATA_DIR:-$PROJECT_DIR/.data}
SPEAIVE_BACKEND_PORT=${SPEAIVE_BACKEND_PORT:-8080}
case "$SPEAIVE_DATA_DIR" in
  /*) ;;
  *) SPEAIVE_DATA_DIR="$PROJECT_DIR/$SPEAIVE_DATA_DIR" ;;
esac

SPEAIVE_DATA_DIR="$SPEAIVE_DATA_DIR" "$PROJECT_DIR/scripts/init-data-dir.sh" >/dev/null
SPEAIVE_DATA_DIR=$(CDPATH= cd -- "$SPEAIVE_DATA_DIR" && pwd -P)
export SPEAIVE_DATA_DIR SPEAIVE_BACKEND_PORT

cd "$PROJECT_DIR/backend"
./mvnw -q -DskipTests package

backend_pid=""
astro_pid=""
astro_started=false
cleanup() {
  if [ "$astro_started" = true ]; then
    (cd "$PROJECT_DIR" && pnpm exec astro dev stop >/dev/null 2>&1) || true
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

if curl --fail --silent "http://127.0.0.1:$SPEAIVE_BACKEND_PORT/actuator/health" >/dev/null 2>&1; then
  echo "Spring Boot 已在 127.0.0.1:$SPEAIVE_BACKEND_PORT 运行，直接复用"
else
  "$JAVA_HOME/bin/java" -jar "$PROJECT_DIR/backend/speaive-blog-start/target/speaive-blog-backend.jar" &
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
fi

echo "博客：http://127.0.0.1:4321"
echo "写作台：http://127.0.0.1:4321/studio"

cd "$PROJECT_DIR"
astro_status=$(pnpm exec astro dev status 2>&1)
astro_pid=$(printf '%s\n' "$astro_status" | sed -n 's/.*pid \([0-9][0-9]*\).*/\1/p' | head -n 1)
if [ -n "$astro_pid" ]; then
  echo "Astro 已在 127.0.0.1:4321 运行，直接复用"
else
  pnpm exec astro dev
  astro_started=true
  astro_status=$(pnpm exec astro dev status 2>&1)
  astro_pid=$(printf '%s\n' "$astro_status" | sed -n 's/.*pid \([0-9][0-9]*\).*/\1/p' | head -n 1)
fi

if [ -z "$astro_pid" ]; then
  echo "无法确认 Astro 开发服务状态" >&2
  exit 1
fi

backend_is_running() {
  if [ -n "$backend_pid" ]; then
    kill -0 "$backend_pid" 2>/dev/null
  else
    curl --fail --silent "http://127.0.0.1:$SPEAIVE_BACKEND_PORT/actuator/health" >/dev/null 2>&1
  fi
}

while backend_is_running && kill -0 "$astro_pid" 2>/dev/null; do
  sleep 1
done

if ! backend_is_running; then
  echo "Spring Boot 已退出" >&2
else
  echo "Astro 开发服务已退出" >&2
fi
exit 1
