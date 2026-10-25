#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

HOST="${1:-127.0.0.1}"
PORT="${2:-6379}"

# CLI 依赖 light-redis-client，先确保它已安装到本地仓库
mvn -q -pl light-redis-client -am install -DskipTests

mvn -q -f light-redis-cli/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.cli.LightRedisCli" \
  "-Dexec.args=--host=${HOST} --port=${PORT}"
