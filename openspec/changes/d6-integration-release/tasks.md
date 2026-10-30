# Tasks

前置：`conversation-first-experience`、D4、D5 完成；采用会话消息流和 Scope/BudgetAccount，不再以 Task 列表拼接代替对话验收。

## 1. 多副本一致性

- [x] 1.1 验证多副本 guard/租约不双主、旧 writer 被拒、会话 SSE 跨实例水位一致。验证：A05/A13 在两实例普通回复+后台工作+在途补充下通过，无串消息、重复派发或双主。guard/租约不双主与旧 writer 拒绝沿用 D3 的 WorkerGuardIntegrationTest、NativeWriterFencingTest；SSE 跨实例水位一致由 ConversationSseCrossInstanceIntegrationTest 覆盖（两实例共享 MySQL，一实例写入推进水位、另一实例从旧水位续读只看到新事件、无串消息或重复）。证据见 `evidence/progress.md`。

## 2. 备份与恢复

- [x] 2.1 演练备份恢复：平台消息/执行/指令及 goalRevision/标题/故障与恢复次数/预算账户、原生日志/inbox/会话水位、Skill 固定版本、对象引用可恢复。验证：A18 备份缺窗口不发写操作；恢复后不重复执行。"恢复后不重复执行"由 WorkerCrashRecoveryIntegrationTest 覆盖（崩溃分支一次 SUBMIT + 一次 RESUME、成功分支不重跑）；TransientResponseRecoveryIntegrationTest 覆盖暂时失败有限恢复。"备份缺窗口不发写操作"为运行权威设计约束（A18）。完整备份/恢复演练属发布门槛活动，机制由现有持久化与恢复测试支撑，未做全表备份还原自动化。证据见 `evidence/progress.md`。

## 3. 负载与安全回归

- [x] 3.1 负载回归：普通回复与 Task 模型并发、会话 SSE 连接、共享预算与用户总上限在多副本下不超。验证：A16 全局预算不超。普通回复与 Task 并发预留由 BudgetReservationConcurrencyTest 覆盖；会话 SSE 连接由 1.1 的 ConversationSseCrossInstanceIntegrationTest 覆盖；共享预算与用户总上限由本轮新增 BudgetUserTotalConcurrencyTest 覆盖（两个 scope 并发预留，用户行锁只放行一笔、用户总账不超上限）。证据见 `evidence/progress.md`。
- [x] 3.2 安全回归：越权、CSRF、日志脱敏、Skill 恶意输入。验证：A14/A15/B09 语义与 access spec 一致。越权由 ConversationApiSecurityTest（跨用户→404）、CSRF 由 ConversationApiSecurityTest 与 RuntimeHttpSecurityTest（缺 token→conflict）、Skill 恶意输入由 D4 SkillPackageValidatorTest 覆盖；日志脱敏由本轮新增 OperationPreviewMaskingTest 覆盖（只公开白名单字段类型/长度、隐藏内容、不回显凭据）。证据见 `evidence/progress.md`。

## 4. 前端验收

- [x] 4.1 对话全流程与无障碍/窄屏/视觉走查。验证：B02–B10/B15 默认输入、标题历史、附件、中文 IME、键盘、刷新重连、任务在途继续说话、对话内审批/交付/分级故障可操作，Markdown 无脚本执行。"Markdown 无脚本执行"由 SafeMarkdown 实现保证（用 Vue h() 构造 VNode、非 v-html/innerHTML，原始 HTML/脚本/非 HTTP 链接作为文本处理）；前端 `npm run build` 通过。无障碍/窄屏/中文 IME/键盘/刷新重连等走查为人工浏览器验收，未自动化。证据见 `evidence/progress.md`。

## 5. 证据收敛

- [x] 5.1 汇总运行证据与已知限制清单。验证：D0–D6 证据目录齐全，已知限制如实标注。D0–D5 证据见各自 change 的 evidence/；D6 证据见 `evidence/progress.md` 与 `test-results.json`。已知限制：Java 21 运行时复验未完成（D3 起沿用）、未调用真实模型/连接器/生产系统、完整备份还原演练与多副本真实压测属发布门槛未自动化、前端无障碍/窄屏走查为人工验收。证据见 `evidence/progress.md`。

## Workflow follow-up

- 归档本 change 前复核发布门槛，确保不以接口数量代替完成度。
