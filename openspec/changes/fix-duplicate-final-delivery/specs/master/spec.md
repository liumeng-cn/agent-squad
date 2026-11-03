# Spec Delta: Master / R03 R06

## ADDED Requirements

### Requirement: Master 委派后不得重复完成同源用户请求

Master MUST 可以在 RESPONSE 中直接回答，或通过受控 `create_goal_task` 将本次用户请求的最终交付权转移给 GoalTask。交付权归属 MUST 由平台持久事实与事务裁决，不能仅依赖模型遵守 Prompt。受理说明不构成最终交付。（R03/R06）

#### Scenario: 普通两篇作文直接回答
- **WHEN** 用户要求直接写两篇文章，Master 未创建 GoalTask
- **THEN** RESPONSE 可以发布且只发布该请求的一个权威最终答案；不会因为篇幅长而自动建立 GoalTask

#### Scenario: 委派后 Master 仍提交完整作文
- **WHEN** Master 创建同源 GoalTask 后又在原 RESPONSE 递交包含完整作文的完成候选
- **THEN** 原 RESPONSE 可终结并给出真实受理/状态通知，但不得发布该模型正文为第二个 FINAL；任务完成后由当前交付权持有者发布终稿

#### Scenario: 在途任务期间回答新问题
- **WHEN** GoalTask 未终结且用户另发独立问题
- **THEN** 新问题的 RESPONSE 能独立交付，不抢占旧任务的最终交付权，也不把任务过程当成新用户问题
