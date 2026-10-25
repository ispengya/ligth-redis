#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mvn -q -f light-redis-core/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication"
