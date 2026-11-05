# Redis命令唤醒

本轮已把Redis接入实际命令投递，不再只有协议实验。普通消息受理事务提交后向Redis Streams发布命令引用；后台消费者复用CommandDeliveryService，经数据库授权、顺序校验和租约领取后调用SDK。通知本身不授予执行资格，也不表示模型已经完成。

## 当前运行

本地容器`squad-redis`已启动，仅绑定`127.0.0.1:16379`，使用官方Redis固定digest、认证、`squad-redis-data`持久卷、AOF everysec、128MB上限和noeviction。既有`application-local.yml`保存连接配置并启用`agentsquad.redis-wakeup.enabled`；业务Server已重新构建启动，health为UP，COMMAND/CONTROL消费者组已建立。未额外发送真实模型请求。

默认配置保持关闭，不要求其他环境必须具备Redis。Redis是唤醒加速器，故障不阻断业务健康检查；不可用时记录一次降级，恢复时记录一次恢复，不逐轮刷ERROR。

## 提交与消费

1. MessageAcceptanceWriter在原受理事务内保存命令并发布CommandReadySignal，事务提交后才登记Redis引用。
2. 引用只携带协议版本、通道、用户、Binding和commandId；不携带Prompt、工具参数或凭据。消费时重新读取数据库核对归属、通道及协议版本。
3. 同一命令用dirtyVersion合并重复通知。不同命令即使属于同一Binding也保持独立，不能合并掉用户补充消息。
4. COMMAND与CONTROL拥有独立Stream及消费者组；停止/答复仍使用既有独立执行容量。每次先读取CONTROL，pending超过配置空闲时间后接管。
5. 一次持久领取/投递尝试返回后才ACK并删除旧引用。执行器容量不足或异常不ACK；接管、重复投递仍经过数据库/SDK幂等屏障。ACK不等待模型生成结束，不与SSE存活绑定。
6. 新通知在处理期间到达时原子续排；迟到旧ACK不会清除后继引用。归属非法引用清理通知，不改变业务命令。
7. 发布失败直接交给原本地投递入口；未被观察的丢失、未领取命令及到期重试由持久命令扫描发现。Redis不是命令权威存储。

当前保留1秒补偿周期：目标、Worker及部分控制入口尚未全部发布提交后通知，不能提前调大扫描周期使这些入口变慢。扫描发现的候选也可经Redis唤醒，Redis故障时仍本地派发。后续入口全部接通并通过性能/故障验证后，再降低数据库扫描频率。

## 配置

`agentsquad.redis-wakeup`提供enabled、namespace、capacity、poll-delay-ms、compensation-delay-ms、reclaim-idle-ms；连接参数使用Spring的`spring.data.redis`。namespace须按环境隔离，不能让生产/测试共用。默认pending接管90秒，消息容量10000，不使用MAXLEN静默裁剪未确认引用。接管间隔不是业务租约，缩短不能绕过原执行资格。

## 验证与未完成项

`python3 experiments/sdk-contract/run_mysql_probe.py --with-redis --server-tests RedisCommandWakeupIntegrationTest,DynamicReplyModelIntegrationTest,ConversationSsePushIntegrationTest`

使用临时MySQL、临时认证Redis及本地模型，5个针对性用例通过。Redis业务测试有正常消费和遗留pending接管两个分支；均关闭十分钟内的数据库定时扫描，验证Redis实际接手、重复提示仅一次模型调用及一条终稿、通知连接不可用后消息仍成功。另回归双模型路由及真实HTTP/SSE断线续读。临时容器均自动清理，不使用本地业务库作故障实验。

尚未覆盖业务提交后未ACK进程崩溃、Redis全部数据丢失后的命令/journal补齐、多实例同绑定竞争、完整取消优先级、OOM与持久卷重建。完整traceparent/tracestate传播、PROJECTION通道及跨实例SSE通知尚未接入，不能宣称整个P07或调度改造完成。原有收件、消费确认及补偿保留；本轮不改数据库结构。
