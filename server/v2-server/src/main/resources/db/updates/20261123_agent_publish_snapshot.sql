-- 已执行 20261121_refine_agent_configuration.sql 的数据库升级到“配置版本 + Master 发布定格”模型。
-- 使用最终 V1__baseline.sql 初始化的数据库无需执行。
-- 注意：本脚本仅生成供人工核验和按需执行，不会在应用启动时自动删除或清空已有数据。
BEGIN;

-- 1. MCP 目录与配置版本表调整
ALTER TABLE squad_chat.sq_mcp_server
    ADD COLUMN IF NOT EXISTS latest_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS latest_version_no INTEGER NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    ADD COLUMN IF NOT EXISTS latest_readiness  VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    ADD COLUMN IF NOT EXISTS deleted_at        TIMESTAMPTZ(3);

ALTER TABLE squad_chat.sq_mcp_server_version
    ADD COLUMN IF NOT EXISTS readiness VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK (readiness IN ('READY', 'INCOMPLETE'));

ALTER TABLE squad_chat.sq_mcp_server_version
    ALTER COLUMN transport_type DROP NOT NULL,
    ALTER COLUMN endpoint SET DEFAULT '';

DROP INDEX IF EXISTS squad_chat.uk_mcp_server_version_draft;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_mcp_server_version_owner'
    ) THEN
        ALTER TABLE squad_chat.sq_mcp_server_version
            ADD CONSTRAINT uk_mcp_server_version_owner UNIQUE (mcp_server_id, mcp_server_version_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_mcp_server_latest_version'
    ) THEN
        ALTER TABLE squad_chat.sq_mcp_server
            ADD CONSTRAINT fk_mcp_server_latest_version
                FOREIGN KEY (mcp_server_id, latest_version_id)
                REFERENCES squad_chat.sq_mcp_server_version (mcp_server_id, mcp_server_version_id);
    END IF;
END $$;

-- 2. 工具目录与配置版本表调整
ALTER TABLE squad_chat.sq_tool
    ADD COLUMN IF NOT EXISTS display_alias     VARCHAR(60) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS latest_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS latest_version_no INTEGER NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    ADD COLUMN IF NOT EXISTS latest_readiness  VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    ADD COLUMN IF NOT EXISTS deleted_at        TIMESTAMPTZ(3);

ALTER TABLE squad_chat.sq_tool_version
    ADD COLUMN IF NOT EXISTS readiness     VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK (readiness IN ('READY', 'INCOMPLETE')),
    ADD COLUMN IF NOT EXISTS mcp_server_id BIGINT REFERENCES squad_chat.sq_mcp_server (mcp_server_id);

DROP INDEX IF EXISTS squad_chat.uk_tool_version_draft;

UPDATE squad_chat.sq_tool_version tv
SET mcp_server_id = mv.mcp_server_id
FROM squad_chat.sq_mcp_server_version mv
WHERE tv.mcp_server_id IS NULL
  AND tv.mcp_server_version_id = mv.mcp_server_version_id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_tool_version_owner'
    ) THEN
        ALTER TABLE squad_chat.sq_tool_version
            ADD CONSTRAINT uk_tool_version_owner UNIQUE (tool_id, tool_version_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_tool_latest_version'
    ) THEN
        ALTER TABLE squad_chat.sq_tool
            ADD CONSTRAINT fk_tool_latest_version
                FOREIGN KEY (tool_id, latest_version_id)
                REFERENCES squad_chat.sq_tool_version (tool_id, tool_version_id);
    END IF;
END $$;

-- 3. Agent 目录与配置版本表调整
ALTER TABLE squad_chat.sq_agent
    ADD COLUMN IF NOT EXISTS display_alias      VARCHAR(60) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS latest_version_id  BIGINT,
    ADD COLUMN IF NOT EXISTS latest_version_no  INTEGER NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    ADD COLUMN IF NOT EXISTS latest_readiness   VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    ADD COLUMN IF NOT EXISTS current_release_id BIGINT,
    ADD COLUMN IF NOT EXISTS deleted_at         TIMESTAMPTZ(3);

ALTER TABLE squad_chat.sq_agent_version
    ADD COLUMN IF NOT EXISTS readiness       VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK (readiness IN ('READY', 'INCOMPLETE')),
    ADD COLUMN IF NOT EXISTS prompt_sections JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(prompt_sections) = 'object');

DROP INDEX IF EXISTS squad_chat.uk_agent_version_draft;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_latest_version'
    ) THEN
        ALTER TABLE squad_chat.sq_agent
            ADD CONSTRAINT fk_agent_latest_version
                FOREIGN KEY (agent_id, latest_version_id)
                REFERENCES squad_chat.sq_agent_version (agent_id, agent_version_id);
    END IF;
END $$;

-- 4. 配置版本关联表补齐业务 ID 字段
ALTER TABLE squad_chat.sq_agent_version_tool
    ADD COLUMN IF NOT EXISTS tool_id BIGINT REFERENCES squad_chat.sq_tool (tool_id),
    ADD COLUMN IF NOT EXISTS sort_no INTEGER NOT NULL DEFAULT 0;

UPDATE squad_chat.sq_agent_version_tool b
SET tool_id = tv.tool_id
FROM squad_chat.sq_tool_version tv
WHERE b.tool_id IS NULL
  AND b.tool_version_id = tv.tool_version_id;

ALTER TABLE squad_chat.sq_agent_version_subagent
    ADD COLUMN IF NOT EXISTS subagent_id   BIGINT REFERENCES squad_chat.sq_agent (agent_id),
    ADD COLUMN IF NOT EXISTS display_alias VARCHAR(60) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS sort_no       INTEGER NOT NULL DEFAULT 0;

UPDATE squad_chat.sq_agent_version_subagent d
SET subagent_id = sv.agent_id
FROM squad_chat.sq_agent_version sv
WHERE d.subagent_id IS NULL
  AND d.subagent_version_id = sv.agent_version_id;

-- 5. 新建 Master 发布定格快照表
CREATE TABLE IF NOT EXISTS squad_chat.sq_agent_release
(
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    release_id           BIGINT       NOT NULL UNIQUE CHECK (release_id > 0),
    master_agent_id      BIGINT       NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    release_no           INTEGER      NOT NULL CHECK (release_no > 0),
    master_version_id    BIGINT       NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    master_name          VARCHAR(120) NOT NULL,
    master_display_alias VARCHAR(60)  NOT NULL DEFAULT '',
    dependency_digest    VARCHAR(64)  NOT NULL,
    publish_operation_id VARCHAR(64),
    published_at         TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator_id           BIGINT       NOT NULL DEFAULT 0,
    creator_name         VARCHAR(50)  NOT NULL DEFAULT '',
    create_time          TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id      BIGINT       NOT NULL DEFAULT 0,
    last_updater_name    VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time     TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_release_no UNIQUE (master_agent_id, release_no),
    CONSTRAINT uk_agent_release_owner UNIQUE (master_agent_id, release_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_release_operation
    ON squad_chat.sq_agent_release (master_agent_id, publish_operation_id)
    WHERE publish_operation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS squad_chat.sq_agent_release_subagent
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    release_id             BIGINT       NOT NULL REFERENCES squad_chat.sq_agent_release (release_id),
    subagent_id            BIGINT       NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    subagent_version_id    BIGINT       NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    subagent_name          VARCHAR(120) NOT NULL,
    delegation_name        VARCHAR(80)  NOT NULL,
    delegation_description VARCHAR(500),
    display_alias          VARCHAR(60)  NOT NULL DEFAULT '',
    sort_no                INTEGER      NOT NULL DEFAULT 0,
    creator_id             BIGINT       NOT NULL DEFAULT 0,
    creator_name           VARCHAR(50)  NOT NULL DEFAULT '',
    create_time            TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id        BIGINT       NOT NULL DEFAULT 0,
    last_updater_name      VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_release_subagent UNIQUE (release_id, subagent_id),
    CONSTRAINT uk_agent_release_delegation UNIQUE (release_id, delegation_name)
);

CREATE TABLE IF NOT EXISTS squad_chat.sq_agent_release_tool
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    release_id             BIGINT       NOT NULL REFERENCES squad_chat.sq_agent_release (release_id),
    owner_agent_id         BIGINT       NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    owner_agent_version_id BIGINT       NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    tool_id                BIGINT       NOT NULL REFERENCES squad_chat.sq_tool (tool_id),
    tool_version_id        BIGINT       NOT NULL REFERENCES squad_chat.sq_tool_version (tool_version_id),
    tool_name              VARCHAR(120) NOT NULL,
    tool_display_alias     VARCHAR(60)  NOT NULL DEFAULT '',
    mcp_server_id          BIGINT       REFERENCES squad_chat.sq_mcp_server (mcp_server_id),
    mcp_server_version_id  BIGINT       REFERENCES squad_chat.sq_mcp_server_version (mcp_server_version_id),
    mcp_server_name        VARCHAR(120),
    sort_no                INTEGER      NOT NULL DEFAULT 0,
    creator_id             BIGINT       NOT NULL DEFAULT 0,
    creator_name           VARCHAR(50)  NOT NULL DEFAULT '',
    create_time            TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id        BIGINT       NOT NULL DEFAULT 0,
    last_updater_name      VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_release_tool UNIQUE (release_id, owner_agent_id, tool_id)
);

-- 6. 回填目录表最新配置版本指针
UPDATE squad_chat.sq_mcp_server s
SET latest_version_id = v.mcp_server_version_id,
    latest_version_no = v.version_no,
    latest_readiness  = v.readiness
FROM squad_chat.sq_mcp_server_version v
WHERE v.mcp_server_id = s.mcp_server_id
  AND v.version_no = (SELECT max(x.version_no) FROM squad_chat.sq_mcp_server_version x WHERE x.mcp_server_id = s.mcp_server_id);

UPDATE squad_chat.sq_tool t
SET latest_version_id = v.tool_version_id,
    latest_version_no = v.version_no,
    latest_readiness  = v.readiness
FROM squad_chat.sq_tool_version v
WHERE v.tool_id = t.tool_id
  AND v.version_no = (SELECT max(x.version_no) FROM squad_chat.sq_tool_version x WHERE x.tool_id = t.tool_id);

UPDATE squad_chat.sq_agent a
SET latest_version_id = v.agent_version_id,
    latest_version_no = v.version_no,
    latest_readiness  = v.readiness
FROM squad_chat.sq_agent_version v
WHERE v.agent_id = a.agent_id
  AND v.version_no = (SELECT max(x.version_no) FROM squad_chat.sq_agent_version x WHERE x.agent_id = a.agent_id);

COMMIT;
