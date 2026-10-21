-- D1 任务快照所需字段；直接回答阶段 plan_version 恒为 0，后续 Worker 协作阶段再递增。
ALTER TABLE sq_goal_task ADD COLUMN plan_version BIGINT NOT NULL DEFAULT 0 COMMENT '计划版本';
