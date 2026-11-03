# Spec Delta: Tools & Interactions / R08 R11

## ADDED Requirements

### Requirement: 受控操作在账号禁用与容量受限时安全收敛

系统 MUST 对新的模型/工具/沙箱外发校验账号状态及授权，已 IN_FLIGHT 的写入按操作账本记录为 SUCCEEDED/FAILED/UNKNOWN，不把用户取消或禁用误认为外部回滚。

#### Scenario: 账号禁用与未知写操作
- **WHEN** 管理员禁用用户时其外部 WRITE 已 IN_FLIGHT
- **THEN** 不再产生新的外部写调用；已经发出的操作仍按真实结果或 UNKNOWN 进入核对，不能自动重发或假设未执行

#### Scenario: 线程池或配额饱和
- **WHEN** 工具或 Worker 运行容量耗尽
- **THEN** 未发送的工作保持可恢复等待，不能领取后静默丢弃或重复执行
