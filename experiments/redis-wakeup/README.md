# Redis 唤醒协议实验（P07 第一阶段）

运行：

```bash
python3 experiments/redis-wakeup/run_probe.py
```

仅使用已安装 Docker 与 Python 标准库。镜像固定为官方 Redis 8.2 Alpine 的 digest，见脚本的 `IMAGE`；不会隐式升级镜像。每次创建独立容器、随机密码、临时配置，使用 `--network none`，不映射端口，不连接项目现有 Redis、MySQL 或模型。结束时清理专用容器和临时配置，测试失败返回非零。

配置启用认证、AOF、64MB 上限和 `noeviction`。本实验使用 `appendfsync always`，只验证同一容器停止/重启，不证明 `everysec` 下断电不丢最近写入，也不证明容器删除后的持久卷恢复。

## 引用与确认协议

- `enqueue.lua` 为同一用户、通道、绑定增加 `dirtyVersion`，未排队时写一个 Stream 引用，已排队时只增加版本。键使用环境/分区 hash tag；COMMAND、PROJECTION、CONTROL 各有 Stream 和消费者组。
- 引用包含 schemaVersion、kind、userId、bindingId、commandId、traceparent，禁止携带模型正文、Prompt、工具参数和凭据。这里是协议原型，完整 trace/tracestate/root/headHint 传输与可信字段校验尚未接入生产。
- 消费者先读取当前版本，从业务持久记录补读工作。消息里的 commandId 是定位提示；合并通知不能意味着同一绑定的其他命令被跳过。
- 业务成功提交后调用 `finish.lua`：版本已更新或批次仍有尾部则原子续排，否则清除标记；最后确认并删除旧引用。迟到旧确认不会清除后继引用。
- 不使用 `MAXLEN` 裁剪未确认消息。达到容量时显式返回 `WAKEUP_CAPACITY`，交给持久来源补偿；状态不设置 TTL，不能依赖标记过期实现可靠恢复。
- 消费者组的 pending 接管只恢复投递，不是业务互斥锁。正式接入仍须使用现有 Binding admission/guard、持久游标和命令幂等；同一消息被两消费者执行不能靠 Lua 确认去重。

## 已验证

2026-11-05 本地运行 8 组通过：32 个并发生产者合并；处理中/清理后的新到达及迟到确认；批次尾部续排；生产与确认并发竞态 8 轮；未 ACK 接管；AOF 重启；CONTROL 独立通道及用户命名空间；容量拒绝不裁剪 pending。脚本会实际断言 Stream 长度、版本、就绪标记和 pending 数量，不以 PING 代替验证。

## 尚未完成的准入

这不是生产 Redis 接入，也不是整个 P07 通过。以下仍须使用真实业务服务和隔离 MySQL 验证：

1. 持久 Command/业务提交后 Redis 发布失败，由独立扫描补发。
2. 业务事务提交后未 ACK 崩溃，接管不得重复模型、工具或正式交付。
3. Redis 数据完全丢失后，按登记 Binding、官方 journal head 与业务 applied 重建；必须包含业务终态尾部。
4. 取消优先级、同一 Binding 多消费者竞争、毒引用、权限及计划版本拒绝、跨实例通知和慢 SSE。
5. noeviction/OOM、恢复时容量上限、AOF everysec 故障窗口、持久卷重建和保留清理。

这些条件通过前，不替换已有持久收件、业务消费确认及低频补偿，不删除 V6 表，不勾选任务 0.1。
