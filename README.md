# light-redis

一个使用 Java 实现的轻量级 Redis 学习项目，目标是体验 Redis 的整体架构设计和核心模块，而不是做一个完全兼容的生产版本。

当前状态：
- 单机版、内存存储
- Maven 多模块：`light-redis-core` / `light-redis-client` / `light-redis-cli` / `light-redis-panel`
- 使用 Netty + 自研 remoting 框架作为通信层
- 简化文本协议（后续可以替换为 RESP）
- 支持 String / List / Hash / Set / Sorted Set 五种数据类型
- 支持基础数据结构命令，例如 `SET`、`GET`、`LPUSH`、`LRANGE`、`HSET`、`SADD`、`ZADD`
- 支持服务端观测命令：`INFO`、`KEYS`、`TYPE`、`DBSIZE`
- 提供本地 Web 控制台：命令控制台、键浏览、服务状态
- 线程模型参考 Redis 6：IO 多线程 + 命令单线程执行

## 模块结构

项目采用 Maven 多模块，根 `pom.xml` 只负责聚合与版本管理：

```text
light-redis/
├── pom.xml                  # 父 POM：modules、依赖版本、编译插件
├── light-redis-core/        # 服务端：协议、命令、存储、指标、启动入口
├── light-redis-client/      # 客户端核心：LightRedisClient / RemotingLightRedisClient
├── light-redis-cli/         # picocli 交互式 CLI
├── light-redis-panel/       # 本地 Web 控制台（HTTP API + 前端资源）
├── server.sh
├── cli.sh
└── panel.sh
```

模块依赖方向：

```text
light-redis-core     -> remoting-core
light-redis-client   -> remoting-core
light-redis-cli      -> light-redis-client + picocli
light-redis-panel    -> light-redis-client + picocli
```

`light-redis-core` 不依赖任何客户端或展示模块；面板和 CLI 都通过 `light-redis-client` 访问服务端，后续 SDK 也复用这一层。

各模块职责：

- `light-redis-core`：`bootstrap`、`network`、`protocol`、`command`、`store`、`metrics`
- `light-redis-client`：同步/异步命令调用、连接管理，CLI 与面板共用
- `light-redis-cli`：基于 picocli 的交互式命令行客户端
- `light-redis-panel`：JDK HttpServer API、静态资源、前端页面

## 环境要求

- JDK 8+
- Maven 3+
- 已安装并 `mvn install` 过自研项目 `netty-remoting-framework`
  - 路径示例：`d:\workspace\netty-remoting-framework`
  - 在该项目根目录执行一次：`mvn install -DskipTests`
- 推荐在 Windows 下使用 WSL 或 Git Bash 运行 `server.sh` / `cli.sh` / `panel.sh`，也可以在 IDE 中直接运行 main 方法。

## 构建

在 `light-redis` 根目录执行：

```bash
mvn clean install -DskipTests
```

只想编译不安装到本地仓库时用：

```bash
mvn compile
```

`server.sh` / `cli.sh` / `panel.sh` 使用 `mvn -f <module>/pom.xml compile exec:java` 启动对应模块；CLI 和面板会先安装 `light-redis-client`，因此不要求手动提前执行 `install`。

## 启动 server

### 方式一：脚本启动（bash/WSL/Git Bash）

```bash
cd /mnt/d/workspace/light-redis   # 根据实际挂载路径调整
./server.sh
```

`server.sh` 的行为等价于：

```bash
mvn -q -f light-redis-core/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication"
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
mvn -q -f light-redis-cli/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.cli.LightRedisCli" \
  "-Dexec.args=--host=${HOST} --port=${PORT}"
```

### 方式二：直接使用 Maven

```bash
cd d:\workspace\light-redis
mvn -f light-redis-cli/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.cli.LightRedisCli"
```

常用参数（由 picocli 解析）：

- `--host` / `-h`：服务器地址（默认 `127.0.0.1`）
- `--port` / `-p`：服务器端口（默认 `6379`）
- `--timeout` / `-t`：请求超时时间（毫秒，默认 `5000`）
- `--help`：查看帮助
- `--version`：查看版本

示例：

```bash
mvn -f light-redis-cli/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.cli.LightRedisCli" \
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

命令控制台顶部按通用、String、List、Hash、Set、ZSet 分组提供快捷命令，点击填入输入框后按 Enter 执行；写命令完成后会自动刷新 key 列表。

### 启动

先启动 server，再启动面板：

```bash
./panel.sh
# 或（先确保 client 已 install）
mvn -f light-redis-panel/pom.xml compile exec:java \
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

### 通用命令

- `PING` → 返回 `PONG`
- `DEL key [key ...]` → 返回删除成功的 key 数量
- `EXISTS key` → 存在返回 `1`，不存在返回 `0`
- `TYPE key` → 返回 `string`、`list`、`hash`、`set`、`zset` 或 `none`
- `KEYS pattern` → 按 glob 模式返回 key，换行分隔；支持 `*` 和 `?`
- `DBSIZE` → 返回 key 总数
- `INFO` → 返回运行时长、命令总数、活跃连接数、key 数量、JVM 内存和线程数（逐行 `key:value`）

### String

- `SET key value` → 返回 `OK`，可以覆盖任意类型的 key
- `GET key` → 返回 value 或 `(nil)`
- 对非 String 类型执行 `GET` 会返回 `WRONGTYPE ...`

### List

- `LPUSH key element [element ...]` → 从表头插入，返回列表长度
- `RPUSH key element [element ...]` → 从表尾插入，返回列表长度
- `LPOP key` / `RPOP key` → 弹出表头或表尾元素，空列表返回 `(nil)` 并删除 key
- `LLEN key` → 返回列表长度
- `LRANGE key start stop` → 返回闭区间内的元素，支持负数索引
- `LINDEX key index` → 返回指定索引元素
- `LSET key index element` → 修改指定索引元素
- `LREM key count element` → 删除元素，`count` 为正数从表头、负数从表尾、0 表示全部

### Hash

- `HSET key field value [field value ...]` → 设置字段，返回新增字段数量
- `HGET key field` → 返回字段值或 `(nil)`
- `HDEL key field [field ...]` → 删除字段，返回删除数量
- `HEXISTS key field` → 存在返回 `1`，不存在返回 `0`
- `HLEN key` → 返回字段数量
- `HGETALL key` → 按字段、值交替逐行输出
- `HKEYS key` / `HVALS key` → 逐行输出所有字段名或字段值

### Set

- `SADD key member [member ...]` → 添加成员，返回新增数量
- `SREM key member [member ...]` → 删除成员，返回删除数量
- `SMEMBERS key` → 逐行输出所有成员
- `SISMEMBER key member` → 存在返回 `1`，不存在返回 `0`
- `SCARD key` → 返回成员数量

### Sorted Set

- `ZADD key score member [score member ...]` → 添加或更新成员，返回新增成员数量
- `ZREM key member [member ...]` → 删除成员，返回删除数量
- `ZSCORE key member` → 返回成员分值或 `(nil)`
- `ZCARD key` → 返回成员数量
- `ZRANGE key start stop [WITHSCORES]` → 按分值升序返回成员；使用 `WITHSCORES` 时每行格式为 `member score`
- `ZRANK key member` → 返回从 0 开始的名次

注意：
- 列表、集合、哈希和有序集合的多个元素使用换行分隔，字符串内容本身不建议包含换行。
- 集合类 key 在删除最后一个元素后会自动删除，和 Redis 的行为一致。
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
  - 增加集合运算、`MGET` / `MSET`、`SCAN`、`ZREVRANGE` 等命令
- 过期与 TTL：
  - `EXPIRE` / `TTL`，惰性删除 + 定期扫描
- 持久化：
  - 简化版 RDB / AOF
- 发布订阅：
  - `PUBLISH` / `SUBSCRIBE` 等
