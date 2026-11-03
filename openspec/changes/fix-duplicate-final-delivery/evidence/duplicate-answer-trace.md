# 重复终稿链路审计

日期：2026-11-03。原案例未提供 conversationId 或执行记录，未读取实际用户会话，历史根因仍未证实。以下为源码事实与确定性回归，不声称复现原真实模型输出。

## 事实链与事务窗口

| 路径 | 修复前事实 | 修复后裁决与验证 |
| --- | --- | --- |
| RESPONSE + TASK | ResponseExecutionService.finish 无条件写助手正文；TaskEventPublishingService 独立写另一助手正文，缺少同源仲裁 | 受理创建 sq_final_delivery；GoalTaskCreationService 同事务转交；Response 完成候选不持有权则不发布正文；Task 用固定 published_message_id 发布 |
| 重复受理 | MessageAcceptanceWriter 的受理幂等键复用原执行 | 同一受理事务创建代际1；原收据重送不会创建第二交付位 |
| 完成与转交竞争 | 会话与来源回复虽然加锁，但缺少明确交付归属 | 统一会话→实际所有者→交付位锁序；已发布后转交冲突，已转交后Response只结束自身 |
| Task事件回放 | 既有 taskAnswer 复用任务消息ID | 新增交付位固定 messageId；任务正文修订仍更新同一消息身份 |
| 发布中崩溃 | 两条独立入口可发布不同消息 | 交付位PUBLISHED与正文、公开事件共用原事务，异常整体回滚；恢复重试复用固定身份 |
| 自动恢复/重试 | 原执行/Binding复用，显式失败重试新executionId | 自动重试复用原代际；用户显式失败重试才建新代际；旧owner不能发布当前代际 |
| 委派后原回复失败 | 原回复失败可能再次发布失败提示，或重试建立第二交付链 | 委派后抑制原回复正文（含失败提示）；禁止从已委派原回复建立后继，Task真实状态独立保留 |
| 取消 | Response/Task均各自更新终态 | 仅关闭当前所有者OWNED交付位，迟到完成不能重新发布；取消Task仍发送真实task状态事件 |
| 同会话新问题 | activeTask只限制新建任务，不应限制普通回答 | 交付位按sourceMessageId独立，旧Task在途时新问题可正常回答 |
| Web重放 | 相同messageId应按身份/版本合并 | 由C3代理补store级自动化，未用正文相似度隐藏任何消息 |

## 确定性案例

`FinalDeliveryIntegrationTest` 故意在建Task后提交“完整两篇作文”Response候选，验证没有公开Response正文，再发布Task结果并重放完成事件。最终只存在一条助手正文，交付表的published_message_id等于真实消息ID。其他测试覆盖直接回答、同键重送、失败事务回滚、旧代际拒绝、并发转交与完成、新问题不误终结已有Task。

## 未取得的历史证据

原始会话消息列表、execution/task来源关联、Native Command/Binding与公共事件时间线均未提供。源码能证明此前双发布路径可达，但不能据此将历史案例根因置信度提升为已证实；真实模型案例仍需单独授权范围内复测。
