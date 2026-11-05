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

尚未覆盖业务提交后未ACK进程崩溃、Redis全部数据丢失后的命令/journal补齐、多实例同绑定竞争、完整取消优先级、OOM与持久卷重建。完整traceparent/tracestate传播及跨进程部署尚未验收，不能宣称整个P07或调度改造完成。原有收件、消费确认及补偿保留；本轮不改数据库结构。

## 原生事件投影接入

持久收件提交后的RuntimeJournalReadySignal现在优先发布PROJECTION引用；同一Binding增加dirtyVersion并合并排队。引用另带sessionId和mappingVersion，消费者重新核对数据库归属，不能用Redis中的head或序号覆盖业务游标。命令、控制与投影使用同一通道枚举，但各自的消费者组和后台容量独立。

RuntimeProjectionDispatcher仍负责连续补读官方journal与调用既有事务消费。每次最多4页、每页100条；有尾部时通过Lua续排，处理期间有新提交也不会清掉通知。业务消费失败不确认引用，pending接管与持久故障记录继续生效。Redis故障后短暂退避，防止每条SDK事件都等待连接超时，并直接使用原本地合并调度。

独立的15秒终态尾部核对继续保留，覆盖SDK最后一次导出与最终flush的间隙；不能因为收到过Redis通知就认定journal全部处理完。高频旧投影扫描当前仍保留，尚未声称查询量或端到端性能改善。

本轮回归仍为上面的5项，但Redis业务用例将高频投影扫描延长到十分钟，并检查projection-v1组的lastDeliveredId实际推进，再验证唯一终稿和Redis故障后的本地收尾。该证据证明PROJECTION实际接入，不代替完整数据丢失或跨进程崩溃验证。

## 跨实例会话通知

RedisConversationNotifications已接入会话事件提交后广播。通知只包含协议版本、实例ID、用户及会话ID；不携带正文、凭据或内部上下文。发布在独立有界队列执行，失败不回滚业务；接收方忽略自身通知，直接唤醒本机共享hub，不重新广播。每批仍通过原查询服务校验权限并按持久sequence补读。

通知可丢失。现有15秒会话核对和客户端重连继续保留；订阅首次失败不阻止服务启动，之后尝试重连。

CrossInstanceSseIntegrationTest新增两个通过场景：执行端与订阅端使用同一JVM中的独立Spring容器，共享隔离MySQL/Redis，订阅端走真实HTTP/SSE且不启动模型；关闭周期核对后仍通过Pub/Sub及时收到终稿。另一分支主动停止订阅使通知丢失，再触发会话核对，恢复同一终稿、仅一次模型调用。这不是两个独立进程或机器的部署验收。

### 跨实例公开正文预览

已增加独立conversation-preview:v1通道，只传输出适配器明确允许公开的ReplyPreview。输入限65536个UTF-16单元，接收限JSON最坏转义大小并验证版本、身份、全文替换语义、偏移与修订。发送预览和持久提示使用独立有界线程队列，预览积压不占用终稿通知的发布容量。每个SSE订阅仍按用户/会话授权，并只保留当前执行的最新预览。

预览没有持久sequence，可丢失，不作为完成证据；重连由持久终稿纠正。接收方不重新发布Spring事件，不回环广播。两实例HTTP测试已增加“模型仍被阻塞时，另一实例先收到准确首段正文”的断言，检查未包含内部推理标记；后续仍收到唯一终稿。停止订阅丢失通知的分支保留，用核对恢复正式答案。
