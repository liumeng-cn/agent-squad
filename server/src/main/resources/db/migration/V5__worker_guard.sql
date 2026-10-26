-- Worker 业务 guard 与结果候选归属；原生会话 writer lease 仍由 AgentScope 管理。
ALTER TABLE sq_worker_job
    ADD COLUMN guard_token CHAR(36) NULL COMMENT '当前业务执行 guard 令牌';

ALTER TABLE sq_worker_job
    ADD COLUMN guard_lease_until DATETIME(3) NULL COMMENT '当前业务 guard 到期时间';

ALTER TABLE sq_worker_job
    ADD COLUMN candidate_guard_token CHAR(36) NULL COMMENT '提交结果候选时的 guard 令牌';

ALTER TABLE sq_worker_job
    ADD COLUMN candidate_turn_id VARCHAR(64) NULL COMMENT '提交结果候选的原生回合ID';
