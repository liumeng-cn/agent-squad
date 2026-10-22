-- Master 回合正常结束却未提交候选时，仅允许一次持久纠正。
ALTER TABLE sq_goal_task
    ADD COLUMN completion_correction_count INT NOT NULL DEFAULT 0 COMMENT '完成候选缺失后的系统纠正次数';
