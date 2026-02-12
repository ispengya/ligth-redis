#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
mvn compile exec:java "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication"
