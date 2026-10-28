-- 旧操作保留原预算稳定键；新操作使用包含原生调用身份的摘要键，避免不同回合复用工具计费。
ALTER TABLE sq_operation ADD COLUMN budget_usage_key VARCHAR(200) NULL COMMENT '该操作固定的预算预留稳定键';
UPDATE sq_operation SET budget_usage_key = CONCAT(tool_key, ':', COALESCE(business_intent_key, tool_call_id))
WHERE budget_usage_key IS NULL;
