# 高并发秒杀系统（seckill）

Spring Boot + Redis(Lua) + RabbitMQ 的秒杀 Demo，重点解决三件事：

1. **不超卖**：Redis 中 Lua 脚本原子扣减库存，数据库再以 `stock > 0` 条件扣减兜底。
2. **不重复下单**：Redis 用户集合 + 数据库 `(voucher_id, user_id)` 唯一索引双重保证。
3. **削峰**：秒杀接口只做 Redis 扣减 + 投递 MQ，订单由消费者异步落库，接口响应稳定。

## 技术栈

| 组件 | 说明 |
| --- | --- |
| JDK 8 / Spring Boot 2.7.10 | Web 服务 |
| MySQL 8.0 | 活动、订单存储 |
| Redis 7 | Lua 原子扣减、用户去重、限流、结果缓存 |
| RabbitMQ 3 | 异步下单，手动 ACK |
| MyBatis-Plus 3.5.3.1 | 持久层 |

## 目录结构

```
seckill
├── docker-compose.yml          # 本项目专用的 MySQL/Redis/RabbitMQ
├── sql/init.sql                # 建库建表 + 种子活动
├── scripts/
│   ├── loadtest.mjs            # Node 压测脚本（推荐）
│   └── benchmark.sh            # curl 压测脚本
└── src/main/java/com/seckill
    ├── controller              # 秒杀 / 活动 / 管理接口
    ├── service                 # 秒杀主流程、缓存预热、订单落库、后台重置
    ├── mq                      # RabbitMQ 消费者
    ├── task                    # 启动预热 + 定时刷新本地缓存
    └── resources/lua           # seckill / rate_limit / restore_stock 脚本
```

## 秒杀链路

```
请求 → 本地缓存(时间/售罄标记) → Redis 滑动窗口限流
     → Redis Lua 原子操作:
         SISMEMBER 用户已买? → 是: 返回重复
         GET stock <= 0?      → 是: 售罄并打本地售罄标记
         DECR stock + SADD 用户
     → 投递 RabbitMQ → 立即返回“排队中”
消费者 → DB 条件扣减 stock>0 → 插入订单(唯一索引) → 写 Redis 结果
客户端 → 轮询 /api/seckill/result 获取最终结果
```

失败补偿：MQ 投递失败或 DB 落库失败时，Lua 脚本回补 Redis 库存并移除用户标记；消费者手动 ACK，消息重复投递时由唯一索引兜底。

## 快速开始

### 1. 启动中间件（本机已装 Docker）

```bash
cd E:\programme\xiang-mu\seckill
docker compose up -d
```

项目使用独立端口，不会影响本机已有环境：

| 服务 | 端口 |
| --- | --- |
| MySQL | 33306 |
| Redis | 6382 |
| RabbitMQ | 5680（管理台 15680） |

### 2. 打包并启动（JDK 8）

```bash
mvn -DskipTests package
java -jar target/seckill-1.0.0.jar
```

启动日志出现 `秒杀活动缓存预热完成` 即代表 Redis 库存已就绪。
若 MySQL 尚未就绪，预热每 5 秒自动重试，不会导致进程退出。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/voucher/list` | 活动列表 |
| POST | `/api/seckill/{voucherId}` | 秒杀，Header `X-User-Id` |
| GET | `/api/seckill/result/{voucherId}` | 查询结果，Header `X-User-Id` |
| POST | `/api/admin/reset/{voucherId}?stock=100` | 重置活动（清空该活动订单） |
| GET | `/api/admin/stats/{voucherId}` | 对账统计 |

示例：

```bash
curl -X POST http://127.0.0.1:8080/api/seckill/1 -H "X-User-Id: 1001"
# {"code":200,"msg":"success","data":"抢购成功，订单处理中"}

curl http://127.0.0.1:8080/api/seckill/result/1 -H "X-User-Id: 1001"
# {"code":200,"data":{"status":1,"statusDesc":"抢购成功","orderNo":"..."}}
```

返回码：`200` 抢购成功、`409` 重复抢购、`410` 售罄/结束、`429` 触发限流。

结果状态：`0` 排队中、`1` 成功、`2` 失败、`3` 售罄、`4` 未参与。

## 压测

```bash
# 2000 请求，200 并发，100 库存（Node 18+）
node scripts/loadtest.mjs 2000 200 100

# 或者用 curl（Windows 下进程创建开销大，吞吐仅供参考）
bash scripts/benchmark.sh 200 2000 100
```

实测结果（本机 MySQL/Redis/RabbitMQ 均为 Docker，JDK 8）：

```
total=2000 concurrency=200 elapsed=2094ms rps=955
latency avg=205.2ms p50=67.3ms p95=1134.4ms p99=1292.5ms max=1751.2ms
biz code: { '200': 100, '410': 1900 }
stats: { dbStock: 0, dbOrderCount: 100, dbUserCount: 100,
         redisStock: '0', userSetSize: 100, soldOutFlag: true }
```

对账结论：库存 100 → 成功 100 单、下单用户 100 个、DB/Redis 库存均为 0，无超卖。

同一用户 50 并发重复请求：仅 1 单成功，其余均为重复/限流，DB 订单数 1。

## 设计要点

- **本地售罄标记**：Redis 返回售罄后，后续请求在 JVM 内直接拒绝，避免无效 Redis 访问。
- **活动时间缓存**：活动元信息每 30 秒刷新，减少热路径 Redis 查询。
- **限流**：单用户 5 次/秒，固定窗口 Lua 实现，超限返回 429。
- **结果查询**：优先读 Redis 结果；Redis 丢失时回查 DB 唯一索引兜底。
- **管理接口**：`reset` 会删除该活动订单并重置库存，仅用于本地演示和压测。
- 鉴权简化为 `X-User-Id` 请求头，接入真实项目时应替换为登录态/JWT。
