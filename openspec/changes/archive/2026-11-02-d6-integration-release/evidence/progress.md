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

## 2.1 备份与恢复

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 恢复后不重复执行 | 崩溃分支一次 SUBMIT + 一次 RESUME，成功分支不重跑，状态 SUCCEEDED | WorkerCrashRecoveryIntegrationTest（D3 既有） |
| 暂时失败有限恢复 | 暂时服务失败在有限次数内恢复或停在持久限制 | TransientResponseRecoveryIntegrationTest（D3 既有） |
| 备份缺窗口不发写操作 | 运行权威设计约束（A18），无持久保障不发写操作 | runtime spec / config.yaml |

### 边界

- "恢复后不重复执行"与暂时失败恢复由 D3 恢复测试覆盖；完整全表备份/还原演练属发布门槛活动，机制由现有持久化（Command/Binding/guard、原生日志、预算账户、Skill 固定版本）与恢复测试支撑，未做独立备份还原自动化。
- "备份缺窗口不发写操作"为设计约束，未单独构造实验。

## 3.1 负载回归

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 普通回复与 Task 并发 | 两个模型请求同时竞争同一任务余额，数据库行锁只允许一笔预留通过 | BudgetReservationConcurrencyTest（D3 既有） |
| 会话 SSE 连接 | 两实例共享 MySQL，SSE 水位跨实例一致 | ConversationSseCrossInstanceIntegrationTest（本轮 1.1） |
| 共享预算与用户总上限 | 同一用户两个 scope 并发预留，用户行锁只放行一笔、用户总账不超上限 | BudgetUserTotalConcurrencyTest（本轮新增） |

### 边界

- 负载回归聚焦预算上限与 SSE 水位；未做真实模型并发压测与生产负载回归（属 D6 发布门槛，需独立环境）。
- 用户总上限实验覆盖跨 scope 并发；单 scope 内任务上限由 D3 BudgetReservationConcurrencyTest 覆盖。

## 4.1 前端验收

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| Markdown 无脚本执行 | SafeMarkdown 用 Vue h() 构造 VNode（非 v-html/innerHTML），只构造 strong/code/a/pre/h/ol/ul/li/p；原始 HTML、脚本、非 HTTP 链接作为文本处理 | web/console/src/shared/ui/SafeMarkdown.vue |
| 前端构建 | `npm run build` 通过，SkillsView/MemorySettingsView 正常产物 | web/console 构建输出 |

### 边界

- 无障碍/窄屏/中文 IME/键盘/刷新重连/任务在途继续说话/对话内审批交付等走查为人工浏览器验收，未自动化；前端无测试框架（无 vitest）。
- Markdown 消毒为代码审查确认（h() 不执行脚本），未做运行时注入实验。

## 5.1 已知限制清单

| 限制 | 说明 | 来源 |
| --- | --- | --- |
| Java 21 运行时复验未完成 | 编译目标 Java 21，测试运行时为 JDK 27；官方 JDK 21 下载遇网络 TLS 错误，保留到发布门槛 | D3 证据起沿用 |
| 未调用真实模型/连接器/生产系统 | 全部为 loopback HTTP 桩与隔离 MySQL，未接真实 DeepSeek/云效/生产库 | 本轮及 D3 |
| 完整备份还原演练未自动化 | "恢复后不重复执行"由恢复测试覆盖；全表备份/还原属发布门槛活动 | 本轮 2.1 |
| 多副本真实压测未做 | 预算上限与 SSE 水位并发已验证；真实多副本负载压测属发布门槛 | 本轮 3.1 |
| 前端走查为人工验收 | Markdown 消毒为代码审查确认；无障碍/窄屏/IME 等为人工浏览器走查 | 本轮 4.1 |
| 专用 MySQL 集成测试默认跳过 | 19 项跳过，需显式配置 SQUAD_TEST_MYSQL_PASSWORD 等 | README |

## 汇总与复现

- 完整套件：138 项，0 失败，0 错误，19 项跳过（默认配置外的专用 MySQL 集成测试）。
- 本轮新增：ConversationSseCrossInstanceIntegrationTest（1 项）、OperationPreviewMaskingTest（5 项）、BudgetUserTotalConcurrencyTest（1 项）。
- 未调用真实模型、真实连接器或生产系统。

复现运行：

```sh
SQUAD_TEST_MYSQL_PASSWORD=<mysql根密码> mvn -f server/pom.xml -B -ntp test \
  -Dtest='ConversationSseCrossInstanceIntegrationTest' -DfailIfNoTests=false
```
