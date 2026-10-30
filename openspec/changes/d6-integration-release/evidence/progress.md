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

## 3.2 安全回归

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 越权 | 跨用户读取/改名/重试被拒（404/403），不泄露存在性 | ConversationApiSecurityTest（D3 既有） |
| CSRF | 变更请求缺 X-CSRF-Token 被拒 | ConversationApiSecurityTest、RuntimeHttpSecurityTest（D3 既有） |
| 日志脱敏 | OperationPreview 只公开白名单字段（title/description/query/environment/projectKey）的类型和长度，隐藏实际内容，不回显 apiKey/password/secret/token 等凭据 | OperationPreviewMaskingTest（本轮新增） |
| Skill 恶意输入 | 导入规范化校验拒绝绝对路径/`..`/符号链接/重复路径/非文本 | SkillPackageValidatorTest（D4） |

### 边界

- 日志脱敏测试聚焦 OperationPreview 参数摘要；运行日志的关键字段（traceId/taskId/jobId/operationId/commandId）按约定记录，不输出密钥/Cookie/密码/完整 Prompt。
- 越权与 CSRF 沿用 D3 既有 HTTP 安全测试，本轮未重复。

## 3.1 负载回归

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 普通回复与 Task 并发 | 两个模型请求同时竞争同一任务余额，数据库行锁只允许一笔预留通过 | BudgetReservationConcurrencyTest（D3 既有） |
| 会话 SSE 连接 | 两实例共享 MySQL，SSE 水位跨实例一致 | ConversationSseCrossInstanceIntegrationTest（本轮 1.1） |
| 共享预算与用户总上限 | 同一用户两个 scope 并发预留，用户行锁只放行一笔、用户总账不超上限 | BudgetUserTotalConcurrencyTest（本轮新增） |

### 边界

- 负载回归聚焦预算上限与 SSE 水位；未做真实模型并发压测与生产负载回归（属 D6 发布门槛，需独立环境）。
- 用户总上限实验覆盖跨 scope 并发；单 scope 内任务上限由 D3 BudgetReservationConcurrencyTest 覆盖。

## 汇总与复现

- 完整套件：138 项，0 失败，0 错误，19 项跳过（默认配置外的专用 MySQL 集成测试）。
- 本轮新增 OperationPreviewMaskingTest（5 项）、BudgetUserTotalConcurrencyTest（1 项）。
- 未调用真实模型、真实连接器或生产系统。

复现运行：

```sh
SQUAD_TEST_MYSQL_PASSWORD=<mysql根密码> mvn -f server/pom.xml -B -ntp test \
  -Dtest='ConversationSseCrossInstanceIntegrationTest' -DfailIfNoTests=false
```
