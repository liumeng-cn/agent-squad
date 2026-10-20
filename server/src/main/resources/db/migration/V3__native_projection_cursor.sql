-- 原生事件投影水位按 Binding 保存；业务事件序号与已写入的受理事件对齐。
ALTER TABLE sq_runtime_binding
    ADD COLUMN native_event_cursor BIGINT NOT NULL DEFAULT 0 COMMENT '已投影的 AgentScope 日志序号',
    ADD COLUMN next_projection_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次读取原生日志时间';

UPDATE sq_goal_task
SET event_seq = 1
WHERE event_seq = 0;
