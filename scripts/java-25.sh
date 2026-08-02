#!/bin/sh

use_java_25() {
  if [ -z "${JAVA_HOME:-}" ]; then
    if [ -x /opt/homebrew/opt/openjdk/bin/java ]; then
      JAVA_HOME=/opt/homebrew/opt/openjdk
    elif [ -x /usr/local/opt/openjdk/bin/java ]; then
      JAVA_HOME=/usr/local/opt/openjdk
    elif [ "$(uname -s)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
      JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
    fi
  fi

  if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "找不到 JDK 25，请先设置 JAVA_HOME" >&2
    return 1
  fi

  java_version=$($JAVA_HOME/bin/java -version 2>&1 | awk -F '"' 'NR == 1 { print $2 }')
  java_major=${java_version%%.*}
  if [ "$java_major" != "25" ]; then
    echo "需要 JDK 25，当前 JAVA_HOME 是 Java $java_version" >&2
    return 1
  fi

  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
}
