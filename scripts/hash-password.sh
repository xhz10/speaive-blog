#!/bin/sh

set -eu

if ! command -v htpasswd >/dev/null 2>&1; then
  echo "找不到 htpasswd。macOS 可直接使用系统自带版本，Linux 请安装 apache2-utils。" >&2
  exit 1
fi

if [ ! -t 0 ]; then
  if ! IFS= read -r password; then
    echo "未读取到管理员密码" >&2
    exit 1
  fi
else
  tty_echo_disabled=0

  restore_tty() {
    if [ "$tty_echo_disabled" -eq 1 ]; then
      stty echo 2>/dev/null || true
      tty_echo_disabled=0
    fi
  }

  trap 'restore_tty' EXIT
  trap 'restore_tty; exit 129' HUP
  trap 'restore_tty; exit 130' INT
  trap 'restore_tty; exit 143' TERM

  printf "管理员密码：" >&2
  stty -echo
  tty_echo_disabled=1
  if ! IFS= read -r password; then
    printf "\n" >&2
    echo "未读取到管理员密码" >&2
    exit 1
  fi
  restore_tty
  printf "\n再次输入：" >&2
  stty -echo
  tty_echo_disabled=1
  if ! IFS= read -r confirmation; then
    printf "\n" >&2
    echo "未读取到确认密码" >&2
    exit 1
  fi
  restore_tty
  printf "\n" >&2
  if [ "$password" != "$confirmation" ]; then
    echo "两次输入的密码不一致" >&2
    exit 1
  fi
fi

if [ "${#password}" -lt 12 ]; then
  echo "管理员密码至少需要 12 个字符" >&2
  exit 1
fi

htpasswd -bnBC 12 "" "$password" | tr -d ':\n'
printf "\n"
