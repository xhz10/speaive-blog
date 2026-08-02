#!/bin/sh

set -eu

DATA_DIR=${SPEAIVE_DATA_DIR:-.data}
DATA_OWNER=${SPEAIVE_DATA_OWNER:-}

case "$DATA_DIR" in
  *'
'*) echo "SPEAIVE_DATA_DIR 不能包含换行符" >&2; exit 1 ;;
  /*) ABSOLUTE_DATA_DIR=$DATA_DIR ;;
  *) ABSOLUTE_DATA_DIR="$(pwd -P)/$DATA_DIR" ;;
esac

LEXICAL_DATA_DIR=$(printf '%s\n' "$ABSOLUTE_DATA_DIR" | awk '
  {
    depth = 0
    count = split($0, parts, "/")
    for (position = 1; position <= count; position++) {
      part = parts[position]
      if (part == "" || part == ".") {
        continue
      }
      if (part == "..") {
        if (depth > 0) {
          depth--
        }
        continue
      }
      stack[++depth] = part
    }
    if (depth == 0) {
      print "/"
      next
    }
    result = ""
    for (position = 1; position <= depth; position++) {
      result = result "/" stack[position]
    }
    print result
  }
')

if [ "$LEXICAL_DATA_DIR" = "/" ]; then
  echo "拒绝使用根目录作为 SPEAIVE_DATA_DIR" >&2
  exit 1
fi
if [ -L "$LEXICAL_DATA_DIR" ]; then
  echo "SPEAIVE_DATA_DIR 不能是符号链接：$LEXICAL_DATA_DIR" >&2
  exit 1
fi

existing_parent=$LEXICAL_DATA_DIR
missing_suffix=
while [ ! -d "$existing_parent" ]; do
  if [ -L "$existing_parent" ]; then
    echo "SPEAIVE_DATA_DIR 不能经过无效符号链接：$existing_parent" >&2
    exit 1
  fi
  segment=${existing_parent##*/}
  if [ -z "$segment" ]; then
    echo "无法解析 SPEAIVE_DATA_DIR：$LEXICAL_DATA_DIR" >&2
    exit 1
  fi
  missing_suffix="/$segment$missing_suffix"
  existing_parent=${existing_parent%/*}
  if [ -z "$existing_parent" ]; then
    existing_parent=/
  fi
done

physical_parent=$(CDPATH= cd -- "$existing_parent" && pwd -P)
if [ "$physical_parent" = "/" ]; then
  DATA_DIR="/${missing_suffix#/}"
else
  DATA_DIR="$physical_parent$missing_suffix"
fi

if [ "$DATA_DIR" = "/" ]; then
  echo "拒绝使用根目录作为 SPEAIVE_DATA_DIR" >&2
  exit 1
fi
if [ -e "$DATA_DIR" ] && [ ! -d "$DATA_DIR" ]; then
  echo "SPEAIVE_DATA_DIR 不是目录：$DATA_DIR" >&2
  exit 1
fi

for managed_path in \
  "$DATA_DIR" \
  "$DATA_DIR/media" \
  "$DATA_DIR/inbox" \
  "$DATA_DIR/inbox/imported" \
  "$DATA_DIR/inbox/rejected"
do
  if [ -L "$managed_path" ]; then
    echo "数据目录不能使用符号链接：$managed_path" >&2
    exit 1
  fi
done

mkdir -p \
  "$DATA_DIR/media" \
  "$DATA_DIR/inbox/imported" \
  "$DATA_DIR/inbox/rejected"

for managed_path in \
  "$DATA_DIR" \
  "$DATA_DIR/media" \
  "$DATA_DIR/inbox" \
  "$DATA_DIR/inbox/imported" \
  "$DATA_DIR/inbox/rejected"
do
  if [ -L "$managed_path" ]; then
    echo "数据目录不能使用符号链接：$managed_path" >&2
    exit 1
  fi
done

created_data_dir=$(CDPATH= cd -- "$DATA_DIR" && pwd -P)
if [ "$created_data_dir" != "$DATA_DIR" ] || [ "$created_data_dir" = "/" ]; then
  echo "SPEAIVE_DATA_DIR 解析结果不安全：$created_data_dir" >&2
  exit 1
fi

chmod 0750 "$DATA_DIR" "$DATA_DIR/media"
chmod 0700 \
  "$DATA_DIR/inbox" \
  "$DATA_DIR/inbox/imported" \
  "$DATA_DIR/inbox/rejected"

if [ -n "$DATA_OWNER" ]; then
  chown -R "$DATA_OWNER" "$DATA_DIR"
fi

echo "数据目录已准备：$DATA_DIR"
