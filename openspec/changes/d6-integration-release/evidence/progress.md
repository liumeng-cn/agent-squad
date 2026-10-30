# D6 本地验收记录（进行中）

记录时间：2026-10-30。D6 是集成与发布准备，不是新功能开发；以运行证据衡量完成度，不以接口数量代替。本文件随任务推进累积。

## 1.1 多副本一致性

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| guard/租约不双主 | 同 Job 两个 Spring 实例仅一个有效业务 guard；旧 guard 不能提交 | WorkerGuardIntegrationTest（D3） |
| 旧 writer 被拒 | SDK JournalSessionLog 拒绝被接管的旧 writer（fenced） | NativeWriterFencingTest（D3） |
| SSE 跨实例水位一致 | 两实例共享 MySQL：一实例写入推进水位，另一实例从旧水位续读只看到新事件；两实例读同一会话得到相同事件集合与水位；无串消息、重复或缺失 | ConversationSseCrossInstanceIntegrationTest（本轮新增） |

### 边界

- SSE 水位一致性实验覆盖普通回复事件；后台工作与在途补充使用同一持久 cursor 水位机制（`ConversationQueryService.events` 按 generation+sequence 读取），未单独再验。
- guard/租约与旧 writer fence 沿用 D3 的两实例证据，本轮未重复。
- 模型响应由 loopback HTTP 桩提供；未调用真实模型。
- Java 21 运行时复验仍未完成（见 D3 证据同项限制）。

## 汇总与复现

- 完整套件：132 项，0 失败，0 错误，19 项跳过（默认配置外的专用 MySQL 集成测试）。
- 本轮新增 ConversationSseCrossInstanceIntegrationTest：1 项通过。
- 未调用真实模型、真实连接器或生产系统。

复现运行：

```sh
SQUAD_TEST_MYSQL_PASSWORD=<mysql根密码> mvn -f server/pom.xml -B -ntp test \
  -Dtest='ConversationSseCrossInstanceIntegrationTest' -DfailIfNoTests=false
```
