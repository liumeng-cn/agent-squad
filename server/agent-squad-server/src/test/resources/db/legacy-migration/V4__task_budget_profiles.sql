-- D3 固定父目标预算 Profile 与额度；已存在的目标保留 STANDARD 和原默认 5000000 微单位上限。
ALTER TABLE sq_goal_task
    ADD COLUMN budget_profile_key VARCHAR(80) NOT NULL DEFAULT 'STANDARD' COMMENT '目标受理时固定的预算Profile键';

ALTER TABLE sq_goal_task
    ADD COLUMN budget_limit_microunits BIGINT NOT NULL DEFAULT 5000000 COMMENT '父目标共享预算上限，整数微单位';

-- 真实模型请求预留固定定价版本和输入摘要；工具预留保持这些字段为空。
ALTER TABLE sq_budget_reservation
    ADD COLUMN model_profile_key VARCHAR(80) NULL COMMENT '模型请求使用的服务端Profile键';

ALTER TABLE sq_budget_reservation
    ADD COLUMN pricing_version VARCHAR(80) NULL COMMENT '模型定价版本，结算时不得切换';

ALTER TABLE sq_budget_reservation
    ADD COLUMN request_hash CHAR(64) NULL COMMENT '模型请求输入规范化摘要，不保存完整Prompt';
