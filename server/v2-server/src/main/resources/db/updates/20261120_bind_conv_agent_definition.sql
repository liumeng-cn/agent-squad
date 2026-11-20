-- 从旧版目录结构升级，保留既有 Agent 绑定及全部对话数据。
-- 已使用最终 baseline 初始化或已完成本次本地升级的数据库不重复执行。
BEGIN;

ALTER TABLE squad_chat.sq_chat_conv
    RENAME COLUMN agent_id TO agent_definition_id;

COMMENT ON COLUMN squad_chat.sq_chat_conv.agent_definition_id IS '绑定的逻辑Agent标识';

COMMIT;
