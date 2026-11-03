# Tasks：同一用户请求重复最终交付修复

> 状态：待办，尚未实施。本变更按 `openspec-propose` 规划，后续由 `openspec-apply-change` 执行。优先复盘运行事实，不预设根因。

## 1. 复现与执行链路审计
- [ ] **FD.1.1 复盘重复作文案例**：针对原始 `conversationId`，关联 `sq_message`、`sq_response_execution`、`sq_goal_task`、Native Binding/Command 和公共事件；按同源 `execution_id/task_id` 区分 RESPONSE+TASK、重复 RESPONSE、Task 事件重放或纯前端重复。**输入**：脱敏会话 ID 与执行记录；**输出**：`evidence/duplicate-answer-trace.md`（包含证据链、时间线和根因置信度）；**验证**：能够解释每条可见消息的唯一发布入口。无原始数据则明确标记未证实并提供确定性复现。
- [ ] **FD.1.2 审计关键竞态**：逐项检查 `MessageAcceptanceWriter`、`GoalTaskCreationService`、`ResponseExecutionService.finish`、`TaskFinalizerService`、`TaskEventPublishingService`、`ResponseRetryService` 及 Web SSE/snapshot 去重。**输出**：可重现的路径矩阵和事务/恢复窗口；**验证**：每条可疑路径有对应自动化测试或明确排除证据。

## 2. 持久交付权与最终结果裁决
- [ ] **FD.2.1 固化交付权数据契约**：确定 `(userId, sourceMessageId, deliveryGeneration)` 的交付位、`RESPONSE/TASK` 所有者、状态、`publishedMessageId`、唯一约束与版本语义；区分 `ACK/PROGRESS/FINAL`。**涉及**：`openspec/specs/master`、`runtime`、`events-artifacts` 和必要的 `api/openapi.json`；**验证**：直接答复/委派/显式重做场景均有唯一可判定最终所有者。
- [ ] **FD.2.2 实现原子创建与转移**：普通消息默认归属 RESPONSE，`create_goal_task` 成功时与 Task 受理在同一事务转交；与已发布 FINAL 的竞态按 CAS/锁结果拒绝。**涉及**：`MessageAcceptanceWriter`、`GoalTaskCreationService`、Mapper/存储及必要的 `V1__baseline.sql`；**验证**：并发、重投、崩溃恢复不会出现双重持有或无主交付。
- [ ] **FD.2.3 统一完成发布**：`ResponseExecutionService.finish` 只在持有权时发布 FINAL；已委派 RESPONSE 仅完成自身状态并发表可识别的简短受理通知或复用任务状态；`TaskFinalizerService` / `TaskEventPublishingService` 按交付位幂等发布 Task FINAL。**涉及**：服务层、事件映射和消息投影；**验证**：故意让 Master 在建 Task 后仍提交两篇完整作文，Web 最终只出现一份完整终稿，且 Task 仍正常执行。
- [ ] **FD.2.4 处理失败及显式重做边界**：覆盖任务取消/失败、旧代际迟到、重试不同 `executionId`、公开事件重复回放；明确显式重新生成如何创建新代际及前端展示。**验证**：没有无声吞掉失败或把自动重试变成第二份终稿，已有任务不会被普通 Response 误终结。

## 3. 端到端回归与交付
- [ ] **FD.3.1 后端确定性集成测试**：至少覆盖普通写作不建任务、委派后双候选、完成先后竞争、同键重送、重复事件、异常恢复、同会话任务在途时普通问答。**输出**：测试类及可执行命令；**验收**：每个有效交付位恰有一条权威 FINAL，合法 ACK/PROGRESS 可以多条。
- [ ] **FD.3.2 Web 回归**：校验 `conversations.ts`、`MessageThread.vue` 的 SSE 重连、快照合并和消息身份展示；不得通过隐藏相似正文掩盖后端双写。**输出**：自动化用例及浏览器可见证据；**验收**：一份最终作文、正确进度通知、刷新与回放结果稳定。
- [ ] **FD.3.3 设计/契约/数据库/测试一致性检查**：按仓库现有检查脚本执行适用的设计检查与后端/前端测试，记录通过、失败、跳过和真实模型验证情况到 `evidence/verification.md`；仅验证后标记完成并申请归档。
