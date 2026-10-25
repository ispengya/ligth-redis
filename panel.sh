#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

HTTP_PORT="${HTTP_PORT:-8080}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_TIMEOUT="${REDIS_TIMEOUT:-5000}"

OPEN_ARG=""
if [ "${OPEN:-false}" = "true" ]; then
  OPEN_ARG="--open"
fi

# 面板依赖 light-redis-client，先确保它已安装到本地仓库
mvn -q -pl light-redis-client -am install -DskipTests

mvn -q -f light-redis-panel/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.panel.LightRedisPanelApplication" \
  "-Dexec.args=--redis-host=${REDIS_HOST} --redis-port=${REDIS_PORT} --http-port=${HTTP_PORT} --timeout=${REDIS_TIMEOUT} ${OPEN_ARG}"
