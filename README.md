# light-redis

一个使用 Java 实现的轻量级 Redis 学习项目，目标是体验 Redis 的整体架构设计和核心模块，而不是做一个完全兼容的生产版本。

当前状态：
- 单机版、内存存储
- 使用 Netty + 自研 remoting 框架作为通信层
- 简化文本协议（后续可以替换为 RESP）
- 支持 String 类型的基础命令：`PING`、`GET`、`SET`、`DEL`、`EXISTS`
- 支持服务端观测命令：`INFO`、`KEYS`、`TYPE`、`DBSIZE`
- 提供本地 Web 控制台：命令控制台、键浏览、服务状态
- 线程模型参考 Redis 6：IO 多线程 + 命令单线程执行

## 目录结构

- `pom.xml`：Maven 配置，依赖自研 `remoting-core` 和 `picocli`
- `docs/project.md`：整体设计文档与模块说明
- `server.sh`：启动 server 的脚本（需要 bash/WSL/Git Bash）
- `cli.sh`：启动命令行客户端的脚本
- `panel.sh`：启动本地 Web 控制台的脚本
- `src/main/java/com/ispengya/lightredis/bootstrap/LightRedisApplication.java`：服务启动入口
- `src/main/java/com/ispengya/lightredis/client/LightRedisCli.java`：基于 picocli 的交互式 CLI
- `src/main/java/com/ispengya/lightredis/client/LightRedisClient.java`：客户端核心接口，面板和后续 SDK 复用
- `src/main/java/com/ispengya/lightredis/metrics/*`：服务端运行指标（供 `INFO` 读取）
- `src/main/java/com/ispengya/lightredis/panel/*`：本地 Web 控制台后端（HTTP API + 静态页面）
- `src/main/resources/web/*`：面板前端页面
- `src/main/java/com/ispengya/lightredis/network/LightRedisRequestProcessor.java`：请求处理与命令分发入口
- `src/main/java/com/ispengya/lightredis/protocol/*`：简化协议解析（文本命令 → Command）
- `src/main/java/com/ispengya/lightredis/command/*`：命令分发与各命令执行器
- `src/main/java/com/ispengya/lightredis/store/*`：内存存储引擎（String 类型）

更多架构细节请参考 [project.md](file:///d:/workspace/light-redis/docs/project.md)。

## 环境要求

- JDK 8+
- Maven 3+
- 已安装并 `mvn install` 过自研项目 `netty-remoting-framework`
  - 路径示例：`d:\workspace\netty-remoting-framework`
  - 在该项目根目录执行一次：`mvn install -DskipTests`
- 推荐在 Windows 下使用 WSL 或 Git Bash 运行 `server.sh` / `cli.sh`，也可以在 IDE 中直接运行 main 方法。

## 构建

在 `light-redis` 根目录执行：

```bash
mvn compile
```

如果仅使用 IDE 运行，也可以直接由 IDE 触发构建。

## 启动 server

### 方式一：脚本启动（bash/WSL/Git Bash）

```bash
cd /mnt/d/workspace/light-redis   # 根据实际挂载路径调整
./server.sh
```

`server.sh` 的行为等价于：

```bash
mvn compile exec:java "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication"
```

默认监听：`127.0.0.1:6379`。

### 方式二：IDE 直接运行

在 IDE 中找到类：

- `com.ispengya.lightredis.bootstrap.LightRedisApplication`

直接运行其 `main` 方法即可。

## 启动 CLI（命令行客户端）

### 方式一：脚本启动

```bash
cd /mnt/d/workspace/light-redis
./cli.sh              # 连接 127.0.0.1:6379
./cli.sh 127.0.0.1 6380   # 连接 127.0.0.1:6380
```

`cli.sh` 会把参数映射为 Java CLI 选项：

- `HOST` → `--host`
- `PORT` → `--port`

内部等价命令：

```bash
mvn compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.client.LightRedisCli" \
  "-Dexec.args=--host=${HOST} --port=${PORT}"
```

### 方式二：直接使用 Maven

```bash
cd d:\workspace\light-redis
mvn compile exec:java "-Dexec.mainClass=com.ispengya.lightredis.client.LightRedisCli"
```

常用参数（由 picocli 解析）：

- `--host` / `-h`：服务器地址（默认 `127.0.0.1`）
- `--port` / `-p`：服务器端口（默认 `6379`）
- `--timeout` / `-t`：请求超时时间（毫秒，默认 `5000`）
- `--help`：查看帮助
- `--version`：查看版本

示例：

```bash
mvn compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.client.LightRedisCli" \
  "-Dexec.args=--host=127.0.0.1 --port=6379 --timeout=8000"
```

CLI 启动后会进入交互模式：

```text
Connected to 127.0.0.1:6379, type "quit" or "exit" to close.
127.0.0.1:6379> PING
PONG
127.0.0.1:6379> SET mykey hello
OK
127.0.0.1:6379> GET mykey
hello
127.0.0.1:6379> EXISTS mykey
1
127.0.0.1:6379> DEL mykey
1
127.0.0.1:6379> quit
```

## 本地 Web 控制台（Panel）

面板是独立进程，通过客户端核心连接 light-redis，提供命令控制台、键浏览和服务状态。

### 启动

先启动 server，再启动面板：

```bash
./panel.sh
# 或
mvn compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.panel.LightRedisPanelApplication" \
  "-Dexec.args=--redis-host=127.0.0.1 --redis-port=6379 --http-port=8080"
```

浏览器打开：<http://127.0.0.1:8080>

`panel.sh` 支持的环境变量：

- `HTTP_PORT`：面板端口，默认 `8080`
- `REDIS_HOST` / `REDIS_PORT`：目标服务端，默认 `127.0.0.1:6379`
- `REDIS_TIMEOUT`：请求超时毫秒，默认 `5000`
- `OPEN=true`：启动后自动打开浏览器

### API

面板只监听 `127.0.0.1`，API 供本地页面使用：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ping` | 执行 `PING`，检查服务端连接 |
| GET | `/api/status` | 执行 `INFO` 并返回结构化状态 |
| POST | `/api/command` | body 为原始命令文本，执行一条命令 |
| GET | `/api/keys?pattern=*` | 执行 `KEYS` 并返回 key 列表 |
| GET | `/api/key?name=...` | 返回单个 key 的类型和值 |
| DELETE | `/api/key?name=...` | 删除单个 key |

`POST` 和 `DELETE` 需要携带请求头 `X-Light-Redis-Panel: 1`，用于防止浏览器跨站调用。curl 示例：

```bash
curl -s http://127.0.0.1:8080/api/status

curl -s -X POST \
  -H 'Content-Type: text/plain' \
  -H 'X-Light-Redis-Panel: 1' \
  --data 'SET panel:key hello' \
  http://127.0.0.1:8080/api/command

curl -s 'http://127.0.0.1:8080/api/keys?pattern=*'
curl -s 'http://127.0.0.1:8080/api/key?name=panel:key'

curl -s -X DELETE \
  -H 'X-Light-Redis-Panel: 1' \
  'http://127.0.0.1:8080/api/key?name=panel:key'
```

### 安全说明

- 面板只绑定 `127.0.0.1`，并校验 `Host`，避免 DNS rebinding
- `POST` / `DELETE` 校验自定义请求头，避免浏览器跨站调用
- 面板没有认证，任何本机进程都可以通过 API 执行命令；不要暴露到公网
- 前端所有动态内容使用 `textContent` 渲染，避免 key/value 注入 HTML

命令参数支持引号，面板的键浏览会自动为带空格的 key 加引号。

## 当前支持的命令

在当前简化文本协议下，客户端直接输入：

- `PING` → 返回 `PONG`
- `SET key value` → 返回 `OK`
- `GET key` → 返回 value 或 `(nil)`
- `DEL key [key ...]` → 返回删除成功的 key 数量
- `EXISTS key` → 存在返回 `1`，不存在返回 `0`
- `INFO` → 返回运行时长、命令总数、活跃连接数、key 数量、JVM 内存和线程数（逐行 `key:value`）
- `KEYS pattern` → 按 glob 模式返回 key，换行分隔；支持 `*` 和 `?`
- `TYPE key` → 返回 `string` 或 `none`
- `DBSIZE` → 返回 key 总数

注意：
- 目前协议是按空白分隔的简单文本协议，尚未实现 RESP。
- 参数支持引号和反斜杠转义，见下方「命令参数语法」。

### 命令参数语法

- 参数之间使用空白分隔
- 支持单引号和双引号，例如 `SET mykey "hello world"`、`SET "key with space" value`
- 支持反斜杠转义，例如 `SET escaped a\"b`、`SET path a\\b`
- 空字符串使用 `""` 或 `''`
- 未闭合的引号会返回 `ERR Unterminated ...`

## 线程模型说明

结合自研 remoting + Netty，目前线程模型为：

- Netty 层：
  - 1 个 boss 线程负责接受连接
  - N 个 worker 线程负责网络读写
- 命令执行层：
  - 在 `LightRedisApplication` 中使用单线程执行器 `light-redis-command-executor`
  - 所有命令在这个线程中串行执行

这与 Redis 6 的整体思路类似：

- IO 多线程（提高网络吞吐）
- 命令执行单线程（简化并发控制，保证语义顺序）

## 后续规划

可以在当前基础上逐步扩展：

- 协议层：
  - 从简单文本协议演进到 RESP 协议
- 存储层：
  - 增加 List / Hash / Set / Sorted Set 等数据结构
- 过期与 TTL：
  - `EXPIRE` / `TTL`，惰性删除 + 定期扫描
- 持久化：
  - 简化版 RDB / AOF
- 发布订阅：
  - `PUBLISH` / `SUBSCRIBE` 等

设计与实现顺序可参考 [docs/project.md](file:///d:/workspace/light-redis/docs/project.md) 中的建议。
