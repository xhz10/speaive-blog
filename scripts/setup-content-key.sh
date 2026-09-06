#!/bin/sh
# 创建独立内容密钥文件，不打印密钥，不覆盖旧文件，失败时不留下半写入的密钥。
set -eu
umask 077
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
key_file=${SPEAIVE_CONTENT_KEY_FILE:-"$project_root/.secrets/content-keys.properties"}
mkdir -p "$(dirname -- "$key_file")"
key_temp=$(mktemp "$(dirname -- "$key_file")/.content-key.XXXXXX")
trap 'rm -f "$key_temp"' EXIT HUP INT TERM
key_value=$(openssl rand -base64 32)
key_id="key-$(date -u +%Y%m%d%H%M%S)"
{ printf 'active=%s\n' "$key_id"; printf 'keys.%s=%s\n' "$key_id" "$key_value"; } > "$key_temp"
unset key_value
chmod 600 "$key_temp"
# 同目录硬链接原子创建；目标已存在时直接失败，保护旧密钥及其关联文章。
ln "$key_temp" "$key_file" 2>/dev/null || { echo "无法创建密钥文件（可能已存在），未覆盖：$key_file" >&2; exit 1; }
printf '已创建内容密钥：%s\n请单独备份此文件，并让后端的 SPEAIVE_CONTENT_KEY_FILE 指向它。\n' "$key_file"
