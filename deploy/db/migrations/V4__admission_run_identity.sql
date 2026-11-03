-- C2：原生恢复执行的 Run 身份与领取水位。必须先应用 V3 迁移。
-- 上线前需验证 AgentScope 命令到 Run 的可靠关联，默认不开启本功能。
ALTER TABLE sq_runtime_admission_slot
    ADD COLUMN run_id VARCHAR(64) NULL COMMENT '恢复命令的新原生Run标识' AFTER turn_id,
    ADD COLUMN claim_cursor BIGINT NOT NULL DEFAULT 0 COMMENT '领取时已投影原生事件序号' AFTER run_id;
