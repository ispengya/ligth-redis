#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

HOST="${1:-127.0.0.1}"
PORT="${2:-6379}"

mvn compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.client.LightRedisCli" \
  "-Dexec.args=--host=${HOST} --port=${PORT}"
