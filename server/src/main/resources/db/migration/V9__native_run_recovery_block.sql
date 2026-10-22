-- 原生回合异常结束后保留可诊断的恢复阻塞原因，不把框架失败直接当作产品终态。
ALTER TABLE sq_goal_task
    ADD COLUMN blocked_reason VARCHAR(1000) NULL COMMENT '当前恢复阻塞原因；不保存原生异常正文';
