#!/bin/sh
# 安装固定版本的离线 IP 归属地库，运行时不会向外部服务提交访客 IP。
set -eu
GEO_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GEO_PROJECT_DIR=$(dirname "$GEO_SCRIPT_DIR")
cd "$GEO_PROJECT_DIR"
. "$GEO_SCRIPT_DIR/load-env.sh"
load_env_defaults "$GEO_PROJECT_DIR/.env"
GEO_TARGET=${SPEAIVE_ANALYTICS_GEO_DIRECTORY:-${SPEAIVE_DATA_DIR:-.data}/geoip}
GEO_REVISION=c1a1fc7d5941760db3f8431dc05c48cf7f0e30a1
mkdir -p "$GEO_TARGET"
GEO_TEMP=$(mktemp -d "$GEO_TARGET/.download-XXXXXX")
trap 'rm -rf "$GEO_TEMP"' EXIT HUP INT TERM
for GEO_VERSION in 4 6; do
  GEO_FILE="ip2region_v${GEO_VERSION}.xdb"
  curl --fail --location --retry 2 --connect-timeout 15 --max-time 180 \
    "https://raw.githubusercontent.com/lionsoul2014/ip2region/$GEO_REVISION/data/$GEO_FILE" \
    -o "$GEO_TEMP/$GEO_FILE"
done
cat > "$GEO_TEMP/SHA256SUMS" <<'HASHES'
8e31bbdccb5bf21028af10592d4312ec975da0bffa108c0c5d862a12190f9ad3  ip2region_v4.xdb
939f6b46bd2b8bec3cf7c5ceb8ba782266ae9b1f35b5ba7916700dec0b7506ed  ip2region_v6.xdb
HASHES
(
  cd "$GEO_TEMP"
  if command -v sha256sum >/dev/null 2>&1; then sha256sum -c SHA256SUMS
  else shasum -a 256 -c SHA256SUMS; fi
)
# 两份文件校验通过后才替换；应用启动时加载，更新后需重启后端。
for GEO_VERSION in 4 6; do
  chmod 644 "$GEO_TEMP/ip2region_v${GEO_VERSION}.xdb"
  mv "$GEO_TEMP/ip2region_v${GEO_VERSION}.xdb" "$GEO_TARGET/"
done
printf '离线地点库已安装到 %s，请重启后端加载。\n' "$GEO_TARGET"
