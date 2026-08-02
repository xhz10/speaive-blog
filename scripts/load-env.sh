#!/bin/sh

load_env_defaults() {
  _speaive_env_file=$1
  [ -f "$_speaive_env_file" ] || return 0

  while IFS= read -r _speaive_env_line || [ -n "$_speaive_env_line" ]; do
    case "$_speaive_env_line" in
      ""|\#*) continue ;;
    esac

    _speaive_env_name=${_speaive_env_line%%=*}
    case "$_speaive_env_name" in
      ""|[0-9]*|*[!A-Za-z0-9_]*)
        echo "无法解析环境变量配置：$_speaive_env_file" >&2
        return 1
        ;;
    esac

    if eval '[ "${'"$_speaive_env_name"'+x}" = x ]'; then
      continue
    fi

    eval "$_speaive_env_line"
    export "$_speaive_env_name"
  done < "$_speaive_env_file"

  unset _speaive_env_file _speaive_env_line _speaive_env_name
}
