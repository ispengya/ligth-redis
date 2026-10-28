# light-redis

一个使用 Java 实现的轻量级 Redis 学习项目，目标是体验 Redis 的整体架构设计和核心模块，而不是做一个完全兼容的生产版本。

当前状态：
- 单机版、内存存储 + AOF 持久化
- Maven 多模块：`light-redis-core` / `light-redis-client` / `light-redis-cli` / `light-redis-panel`
- 使用 Netty + 自研 remoting 框架作为通信层
- 简化文本协议（后续可以替换为 RESP）
- 支持 String / List / Hash / Set / Sorted Set 五种数据类型
- 支持基础数据结构命令，例如 `SET`、`GET`、`LPUSH`、`LRANGE`、`HSET`、`SADD`、`ZADD`
- 支持 AOF 持久化与后台重写：写命令先写入 mini-file-storage，启动时通过自定义恢复索引回放
- 支持 key 过期时间：惰性删除 + 定期抽样删除，TTL 通过 AOF 持久化
- 支持发布订阅：频道/模式订阅、`PUBLISH`、`PUBSUB` 查询，面板支持 SSE 实时消息
- 支持服务端观测命令：`INFO`、`KEYS`、`TYPE`、`DBSIZE`
- 提供本地 Web 控制台：命令控制台、键浏览、服务状态
- 线程模型参考 Redis 6：IO 多线程 + 命令单线程执行

## 模块结构

项目采用 Maven 多模块，根 `pom.xml` 只负责聚合与版本管理：

```text
light-redis/
├── pom.xml                  # 父 POM：modules、依赖版本、编译插件
├── light-redis-core/        # 服务端：协议、命令、存储、持久化、指标、启动入口
├── light-redis-client/      # 客户端核心：LightRedisClient / RemotingLightRedisClient
├── light-redis-cli/         # picocli 交互式 CLI
├── light-redis-panel/       # 本地 Web 控制台（HTTP API + 前端资源）
├── server.sh
├── cli.sh
└── panel.sh
```

模块依赖方向：

```text
light-redis-core     -> remoting-core + file-storage
light-redis-client   -> remoting-core
light-redis-cli      -> light-redis-client + picocli
light-redis-panel    -> light-redis-client + picocli
```

`light-redis-core` 不依赖任何客户端或展示模块；面板和 CLI 都通过 `light-redis-client` 访问服务端，后续 SDK 也复用这一层。

各模块职责：

- `light-redis-core`：`bootstrap`、`network`、`protocol`、`command`、`store`、`persistence`、`pubsub`、`metrics`
- `light-redis-client`：同步/异步命令调用、连接管理、发布订阅消息监听，CLI 与面板共用
- `light-redis-cli`：基于 picocli 的交互式命令行客户端
- `light-redis-panel`：JDK HttpServer API、SSE 订阅、静态资源、前端页面

## 环境要求

- JDK 8+
- Maven 3+
- 通信层从 Maven Central 获取：`io.github.ispengya:remoting-core:0.2.0`
- 文件存储 SDK 从 Maven Central 获取：`io.github.ispengya:file-storage:0.1.0`
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

`server.sh` 默认开启 AOF，数据目录为 `./data/light-redis`。支持两个环境变量：

- `DATA_DIR`：AOF 数据目录
- `APPENDONLY`：`yes` 或 `no`，默认 `yes`

例如使用临时目录关闭持久化：

```bash
DATA_DIR=./data/tmp APPENDONLY=no ./server.sh
```

也可以直接传递服务端参数：

```bash
./server.sh --appendonly=no
./server.sh --data-dir=./data/demo --appendonly=yes
```

脚本内部等价于：

```bash
mvn -q -f light-redis-core/pom.xml compile exec:java \
  "-Dexec.mainClass=com.ispengya.lightredis.bootstrap.LightRedisApplication" \
  "-Dexec.args=--data-dir=./data/light-redis --appendonly=yes"
```

服务端参数：

- `--data-dir <path>`：AOF 数据目录，默认 `./data/light-redis`
- `--appendonly <yes|no>`：是否开启 AOF，默认 `yes`
- `-h` / `--help`：查看帮助

默认监听：`127.0.0.1:6379`。

### 持久化说明

- 写命令先追加到 mini-file-storage 的 CommitLog，追加成功后才修改内存
- 启动时通过自定义 `RedisRecoveryIndex` 自动回放历史命令，不使用手动 `scan`
- AOF 关闭时写命令直接执行，不创建数据目录
- 每条命令默认 `syncFlush=true`，返回客户端前会强制刷盘
- 因为先写日志后执行，语法或类型错误的写命令也会进入日志，恢复时记录警告并跳过
- 内置 ConsumeQueue 和 KeyIndex 未启用，只使用自定义恢复索引
- `store.properties` 会固定 CommitLog 分段大小和单条记录上限，修改这些配置需要迁移数据目录

### AOF 重写

支持手动触发后台重写：

```text
BGREWRITEAOF
```

重写流程：

1. 在命令线程边界记录旧 AOF 逻辑末尾位点
2. 后台从旧 AOF 前缀回放到临时内存 store
3. 按当前状态生成紧凑命令：`SET`、`RPUSH`、`HSET`、`SADD`、`ZADD`
4. 重写期间的写命令继续写旧 AOF，同时记录到 delta 队列
5. 快照写完后把 delta 追加到新 AOF
6. 短暂停止写入，原子切换 `CURRENT`，然后恢复写入

自动触发默认策略：

```text
逻辑日志 >= 16MB
且达到上次重写后日志大小的 2 倍
每 1 秒检查一次
```

逻辑大小来自 CommitLog 合法末尾位点，不使用预分配的文件大小。

目录结构：

```text
data/light-redis/
├── CURRENT                       # 当前 generation 指针
├── data/                         # 首次启动时的初始 generation（兼容旧布局）
├── store.properties
├── checkpoint.properties
├── .store.lock
└── generations/
    └── gen-<timestamp>-<seq>/    # 重写后的 generation
        ├── data/
        ├── store.properties
        ├── checkpoint.properties
        └── .store.lock
```

旧 generation 当前不会自动清理；切换成功后新 generation 通过 `CURRENT` 生效。

### 过期时间持久化

- `EXPIRE` / `PEXPIRE` / `EXPIREAT` 在写入 AOF 前统一转换为绝对时间的 `PEXPIREAT`
- 重写快照在数据命令之后补写 `PEXPIREAT`，已经过期的 key 不写入新 generation
- 过期删除本身不写 `DEL`：回放时过去的 `PEXPIREAT` 会立即删除 key，启动后再全量清理一次
- 与 Redis 的差异：AOF 中可能暂时保留已过期但尚未重写的数据；实现主从复制时需要补充过期删除传播

### 过期与删除策略

- 惰性删除：访问 `GET` / `EXISTS` / `TYPE` / `DEL` 时发现 key 已过期立即删除；`KEYS` 和 `DBSIZE` 会过滤过期 key
- 定期删除：在命令单线程上每 100ms 抽样最多 20 个带 TTL 的 key，过期比例超过 25% 时在 1ms 预算内继续采样
- 启动清理：AOF 回放完成后、服务启动前删除所有已过期 key
- 写入语义：`SET` 会清除 TTL；`LPUSH` / `HSET` / `SADD` 等修改已有 key 的操作保留 TTL

### 方式二：IDE 直接运行

在 IDE 中找到类：

- `com.ispengya.lightredis.bootstrap.LightRedisApplication`

直接运行其 `main` 方法即可。默认开启 AOF，也可以添加 `--appendonly=no` 参数关闭。

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

订阅消息会在当前连接上异步打印，不需要额外轮询：

```text
127.0.0.1:6379> SUBSCRIBE news
subscribed news
[news] hello

127.0.0.1:6379> PSUBSCRIBE news.*
psubscribed news.*
[pattern:news.*] news.tech hello
```

另一个客户端执行：

```text
PUBLISH news hello
1
```

CLI 不进入严格订阅模式，订阅之后仍然可以继续发送普通命令。

## 本地 Web 控制台（Panel）

面板是独立进程，通过客户端核心连接 light-redis，提供命令控制台、键浏览、订阅和服务状态。

命令控制台顶部按通用、Pub/Sub、String、List、Hash、Set、ZSet 分组提供快捷命令，点击填入输入框后按 Enter 执行；写命令完成后会自动刷新 key 列表。

键浏览按 key 中的 `:` 自动分层折叠，目录节点显示后代 key 数量；大分组默认折叠，小分组默认展开，可点击箭头逐层展开。

中间区域提供「命令控制台 / 键详情 / 订阅」三个标签页。点击左侧 key 会自动切到键详情，切回控制台后命令输出和输入状态仍会保留。键详情会显示 TTL 状态，通用快捷命令包含 `EXPIRE`、`TTL` 和 `PERSIST`。

订阅标签页通过 SSE 建立独立连接，输入频道后点击订阅即可实时接收 `PUBLISH` 消息；订阅连接和普通 API 请求互不影响。

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
| GET | `/api/status` | 执行 `INFO` 并返回结构化状态（含累计过期删除数） |
| POST | `/api/command` | body 为原始命令文本，执行一条命令 |
| GET | `/api/keys?pattern=*` | 执行 `KEYS` 并返回 key 列表 |
| GET | `/api/key?name=...` | 返回单个 key 的类型、TTL 和值 |
| DELETE | `/api/key?name=...` | 删除单个 key |
| GET | `/api/subscribe?channel=...` | SSE 订阅频道；也支持 `?pattern=...` 模式订阅 |

`POST` 和 `DELETE` 需要携带请求头 `X-Light-Redis-Panel: 1`，用于防止浏览器跨站调用。`/api/subscribe` 是长连接 SSE 流，校验 `Host` 和可选的 `Origin`。curl 示例：

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

curl -N 'http://127.0.0.1:8080/api/subscribe?channel=news'
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
- `DBSIZE` → 返回 key 总数，不包含已过期 key
- `EXPIRE key seconds` / `PEXPIRE key millis` → 设置相对过期时间；key 不存在返回 `0`，非正数立即删除 key
- `EXPIREAT key unix-seconds` / `PEXPIREAT key unix-millis` → 设置绝对过期时间
- `TTL key` / `PTTL key` → 返回剩余秒数或毫秒数；`-2` 表示 key 不存在，`-1` 表示没有过期时间
- `PERSIST key` → 移除过期时间，成功返回 `1`
- `INFO` → 返回运行时长、命令总数、活跃连接数、key 数量、过期删除数、JVM 内存和线程数（逐行 `key:value`）
- `BGREWRITEAOF` → 启动后台 AOF 重写；已有重写任务时返回错误

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

### 发布订阅

- `SUBSCRIBE channel [channel ...]` → 订阅一个或多个频道
- `UNSUBSCRIBE [channel ...]` → 取消订阅；不带参数时取消全部频道
- `PSUBSCRIBE pattern [pattern ...]` → 按 glob 模式订阅，支持 `*` 和 `?`
- `PUNSUBSCRIBE [pattern ...]` → 取消模式订阅；不带参数时取消全部模式
- `PUBLISH channel message` → 返回收到消息的订阅数量
- `PUBSUB CHANNELS [pattern]` → 返回当前有订阅者的频道
- `PUBSUB NUMSUB [channel ...]` → 返回每个频道的订阅者数量
- `PUBSUB NUMPAT` → 返回模式订阅数量

说明：

- 订阅关系属于连接级别，不写入 AOF，连接断开后自动清理
- `PUBLISH` 不修改 key，也不写入 AOF
- 同一个连接同时命中频道订阅和模式订阅时会收到两条消息：`message` 和 `pmessage`
- 重复订阅同一频道只会保留一份订阅关系
- 面板控制台不允许直接 `SUBSCRIBE`，请使用「订阅」标签页；`PUBLISH` 和 `PUBSUB` 可以在控制台执行

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
- 发布订阅：
  - `PUBLISH`、`SUBSCRIBE` 等命令同样在命令线程执行
  - 推送消息通过 remoting-core 的 `PUSH` 帧写回订阅连接，Netty 异步发送

这与 Redis 6 的整体思路类似：

- IO 多线程（提高网络吞吐）
- 命令执行单线程（简化并发控制，保证语义顺序）

## 后续规划

可以在当前基础上逐步扩展：

- 协议层：
  - 从简单文本协议演进到 RESP 协议
- 存储层：
  - 增加集合运算、`MGET` / `MSET`、`SCAN`、`ZREVRANGE` 等命令
- 过期增强：
  - `SET EX/PX/KEEPTTL`、`GETEX`、过期删除传播（为复制做准备）
- 持久化：
  - 旧 generation 自动清理
  - 简化版 RDB 快照
- 发布订阅增强：
  - 键空间通知
  - 慢消费者输出缓冲限制
