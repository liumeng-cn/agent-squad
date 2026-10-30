-- 删除仅保留历史身份与墓碑，不永久占用用户的记忆键。
-- ACTIVE行仍按用户和记忆键唯一；同键重新创建必须获得新的memory_id，不复活旧记录。
ALTER TABLE sq_memory
    DROP INDEX uk_memory_key,
    MODIFY COLUMN memory_key VARCHAR(100) NOT NULL COMMENT '用户记忆键；删除后可由新记忆重复使用',
    ADD COLUMN active_key VARCHAR(100)
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN memory_key ELSE NULL END) STORED
        COMMENT '有效记忆键；已删除记录为空以允许显式重新创建',
    ADD UNIQUE KEY uk_memory_active_key (user_id, active_key);
