# light-redis

一个使用 Java 实现的轻量级 Redis 学习项目，目标是体验 Redis 的整体架构设计和核心模块，而不是做一个完全兼容的生产版本。

当前状态：
- 单机版、内存存储
- 使用 Netty + 自研 remoting 框架作为通信层
- 简化文本协议（后续可以替换为 RESP）
- 支持 String 类型的基础命令：`PING`、`GET`、`SET`、`DEL`、`EXISTS`
- 线程模型参考 Redis 6：IO 多线程 + 命令单线程执行

## 目录结构

- `pom.xml`：Maven 配置，依赖自研 `remoting-core` 和 `picocli`
- `docs/project.md`：整体设计文档与模块说明
- `server.sh`：启动 server 的脚本（需要 bash/WSL/Git Bash）
- `cli.sh`：启动命令行客户端的脚本
- `src/main/java/com/ispengya/lightredis/bootstrap/LightRedisApplication.java`：服务启动入口
- `src/main/java/com/ispengya/lightredis/client/LightRedisCli.java`：基于 picocli 的交互式 CLI
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

## 当前支持的命令

在当前简化文本协议下，客户端直接输入：

- `PING` → 返回 `PONG`
- `SET key value` → 返回 `OK`
- `GET key` → 返回 value 或 `(nil)`
- `DEL key [key ...]` → 返回删除成功的 key 数量
- `EXISTS key` → 存在返回 `1`，不存在返回 `0`

注意：
- 目前协议是按空白分隔的简单文本协议，尚未实现 RESP。
- value 中包含空格的场景暂不支持（后续可以改为 RESP 或自定义转义规则）。

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
