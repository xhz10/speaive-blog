#!/bin/sh

set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_DIR/scripts/java-25.sh"
use_java_25

cd "$PROJECT_DIR"
pnpm exec astro check
pnpm exec astro build

cd "$PROJECT_DIR/backend"
./mvnw clean verify
