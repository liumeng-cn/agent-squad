-- 会话删除类命令不属于具体目标；放宽命令表关联字段允许为空，删除意图作为幂等锚。
ALTER TABLE sq_command MODIFY task_id CHAR(36) NULL COMMENT '所属目标ID；删除类命令可为空';
ALTER TABLE sq_command MODIFY binding_id CHAR(36) NULL COMMENT '原生会话绑定ID；删除类命令可为空';
