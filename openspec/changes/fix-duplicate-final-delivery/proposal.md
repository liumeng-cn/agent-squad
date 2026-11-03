# Proposal：修复同一用户请求重复最终交付

## Why

用户在同一轮提出“写《北平的秋》和《西安的秋》两篇约 800 字记叙文”后，工作台出现两条不同内容、但都包含完整两篇作文的助手回复。这属于真实用户可见的异常交付。

静态代码核查发现两条独立的回复发布路径：

- `ResponseExecutionService.finish`：将普通 RESPONSE 执行的完成候选投影为助手消息。
- `TaskFinalizerService` → `TaskEventPublishingService`：将 GoalTask 的 `answer.completed` 投影为另一条助手消息。

`MasterAgent` 目前主要靠 Prompt 约束“创建 GoalTask 后仅回复受理说明”，缺少两个发布路径之间的持久、确定性交付仲裁。因此，Master 如果一边创建 GoalTask、一边提交完整答案，可能造成同源消息双份最终交付。**这只是代码层面的高概率成因，必须先查实际运行记录确认**；还需排除重复 ResponseExecution、显式重试和前端投影问题。

本变更落实 R02/R03/R06/R10 的用户可见交付边界：**允许多个过程通知，但一个用户请求在同一有效交付代际只能有一个权威最终答案**。

## Scope

- 基于真实会话的 `sq_message / sq_response_execution / sq_goal_task` 与事件证据定位重复来源，写入脱敏诊断记录。
- 明确“直接回复”与“委派 GoalTask”之间的最终交付权归属及原子转移规则；最终交付裁决由平台事务执行，而不是仅依赖 Prompt。
- 统一 RESPONSE 与 TASK 两条完成投影的幂等、重试、终态及恢复约束；受理/进度通知和最终结果采用可判定的不同语义。
- 在现有 `openspec/specs/master`、`runtime`、`events-artifacts` 的 delta 中补充验收场景；按需要同步 `api/openapi.json` 与 `V1__baseline.sql`。
- 添加服务端集成测试与 Web SSE/快照回归，验证用户实际看不到双份最终成果。

## Non-goals

- 不把整个系统改成“每次用户消息只能有一条助手消息”；受理说明、澄清、进度和多轮互动仍合法。
- 不用前端文本相似度、内容去重或隐藏消息作为后端竞态的替代修复。
- 不改变 AgentScope 原生 Journal 的运行权威，不把每条普通问答强行升级为 GoalTask。

## Dependencies / Acceptance

- 有原始案例 `conversationId` 时优先用库内事实验证；缺少原始记录时，先构造可复现的确定性测试并将历史根因标记为“未证实”。
- 直接写作/问答：只创建 RESPONSE 权威最终交付，不因写作内容较长自动产生 GoalTask。
- 委派写作：允许一次受理/状态通知和后续一次 Task 最终交付；Response 的完整模型正文不能绕过交付权裁决形成另一份终稿。
- 终态重放、重复事件、超时重试、SSE 重连/刷新不会增加第二份权威最终答案；未获权的完成尝试有可观测拒绝记录。
- 不破坏正在运行的 GoalTask、用户后续补充指令、取消与失败恢复语义。
