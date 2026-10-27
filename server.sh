#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

ARGS=("$@")
if [ -n "${DATA_DIR:-}" ]; then
  ARGS=("--data-dir=${DATA_DIR}" "${ARGS[@]}")
fi
if [ -n "${APPENDONLY:-}" ]; then
  ARGS=("--appendonly=${APPENDONLY}" "${ARGS[@]}")
fi

MVN_ARGS=()
if [ ${#ARGS[@]} -gt 0 ]; then
  MVN_ARGS+=("-Dexec.args=${ARGS[*]}")
fi

mvn -q -f light-redis-core/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication" \
  "${MVN_ARGS[@]}"
