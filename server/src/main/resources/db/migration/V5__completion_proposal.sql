-- D1 直接回答的完成候选。候选与最终结果分离，由原生 Turn 完成事实触发裁决。
ALTER TABLE sq_goal_task
    ADD COLUMN completion_proposal_json JSON NULL COMMENT 'Master 提交的完成候选，非最终结果';
ALTER TABLE sq_goal_task
    ADD COLUMN proposal_binding_id CHAR(36) NULL COMMENT '候选来源绑定ID';
ALTER TABLE sq_goal_task
    ADD COLUMN proposal_turn_id VARCHAR(64) NULL COMMENT '候选来源原生TurnID';
ALTER TABLE sq_goal_task
    ADD COLUMN proposal_tool_call_id VARCHAR(128) NULL COMMENT '候选来源工具调用ID';
ALTER TABLE sq_goal_task
    ADD COLUMN proposal_hash CHAR(64) NULL COMMENT '候选内容摘要，用于重放校验';
ALTER TABLE sq_goal_task
    ADD COLUMN answer_revision BIGINT NOT NULL DEFAULT 0 COMMENT '用户可见最终答案版本';
ALTER TABLE sq_goal_task
    ADD COLUMN cancel_requested_at DATETIME(3) NULL COMMENT '取消意图时间，完成裁决屏障';
