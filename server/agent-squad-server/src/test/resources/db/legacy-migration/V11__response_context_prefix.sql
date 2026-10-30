-- 每次回复首次派发前冻结已提交前缀；原键重投不重新读取变化中的聊天记录。
ALTER TABLE sq_response_execution ADD COLUMN context_json LONGTEXT NULL COMMENT '冻结的授权会话消息前缀';
ALTER TABLE sq_response_execution ADD COLUMN context_hash CHAR(64) NULL COMMENT '冻结前缀SHA256摘要';
