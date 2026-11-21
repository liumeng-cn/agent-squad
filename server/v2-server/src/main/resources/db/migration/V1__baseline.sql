CREATE SCHEMA IF NOT EXISTS squad_chat;

CREATE TABLE squad_chat.sq_chat_conv
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conv_id           VARCHAR(64)    NOT NULL CHECK (conv_id ~ '^[a-zA-Z0-9-]{1,64}$'),
    session_id        VARCHAR(64)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    agent_definition_id VARCHAR(80) NOT NULL,
    title             VARCHAR(120)   NOT NULL DEFAULT '新对话',
    title_source      VARCHAR(16)    NOT NULL DEFAULT 'AUTO',
    version           BIGINT         NOT NULL DEFAULT 0 CHECK (version >= 0),
    remark            TEXT,
    archived_at       TIMESTAMPTZ(3),
    deleted_at        TIMESTAMPTZ(3),
    creator_id        BIGINT         NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50)    NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT         NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50)    NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_conv_user_conversation UNIQUE (user_id, conv_id),
    CONSTRAINT uk_chat_conv_native_session UNIQUE (user_id, agent_definition_id, session_id)
);

CREATE INDEX idx_chat_conv_user_created
    ON squad_chat.sq_chat_conv (user_id, create_time DESC, id DESC);

COMMENT ON TABLE squad_chat.sq_chat_conv IS '用户聊天会话目录';
COMMENT ON COLUMN squad_chat.sq_chat_conv.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_chat_conv.conv_id IS '业务对话ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.session_id IS '原生会话ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.user_id IS '归属用户ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.title IS '展示标题';
COMMENT ON COLUMN squad_chat.sq_chat_conv.title_source IS '标题来源 AUTO自动生成 MANUAL用户命名';
COMMENT ON COLUMN squad_chat.sq_chat_conv.version IS '标题编辑版本';
COMMENT ON COLUMN squad_chat.sq_chat_conv.remark IS '对话备注';
COMMENT ON COLUMN squad_chat.sq_chat_conv.archived_at IS '归档时间，未归档为空';
COMMENT ON COLUMN squad_chat.sq_chat_conv.deleted_at IS '删除时间，未删除为空';
COMMENT ON COLUMN squad_chat.sq_chat_conv.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_chat_conv.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_chat_conv.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_chat_conv.last_update_time IS '最后更新时间';

-- AgentScope v2.1.0-BETA1 PostgreSQL 方言（5451c73）
CREATE TABLE squad_chat.agentscope_store
(
    namespace_path VARCHAR(2048) NOT NULL,
    item_key       VARCHAR(255)  NOT NULL,
    value_json     TEXT          NOT NULL,
    version        BIGINT        NOT NULL,
    updated_at     BIGINT        NOT NULL,
    PRIMARY KEY (namespace_path, item_key)
);

CREATE INDEX agentscope_store_namespace_idx
    ON squad_chat.agentscope_store (namespace_path);

CREATE TABLE squad_chat.agentscope_sessions
(
    session_id VARCHAR(255) NOT NULL,
    state_key  VARCHAR(255) NOT NULL,
    item_index INTEGER      NOT NULL DEFAULT 0,
    state_data TEXT         NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, state_key, item_index)
);

CREATE INDEX agentscope_sessions_session_idx
    ON squad_chat.agentscope_sessions (session_id);

CREATE TABLE squad_chat.agentscope_snapshots
(
    snapshot_id VARCHAR(512) NOT NULL PRIMARY KEY,
    data        BYTEA        NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE squad_chat.agentscope_store IS 'SDK分布式键值存储';
COMMENT ON COLUMN squad_chat.agentscope_store.namespace_path IS '命名空间路径';
COMMENT ON COLUMN squad_chat.agentscope_store.item_key IS '条目键';
COMMENT ON COLUMN squad_chat.agentscope_store.value_json IS 'SDK序列化载荷';
COMMENT ON COLUMN squad_chat.agentscope_store.version IS '条目版本';
COMMENT ON COLUMN squad_chat.agentscope_store.updated_at IS '更新时间戳';

COMMENT ON TABLE squad_chat.agentscope_sessions IS 'SDK会话状态存储';
COMMENT ON COLUMN squad_chat.agentscope_sessions.session_id IS 'SDK会话标识';
COMMENT ON COLUMN squad_chat.agentscope_sessions.state_key IS '状态键';
COMMENT ON COLUMN squad_chat.agentscope_sessions.item_index IS '状态项序号';
COMMENT ON COLUMN squad_chat.agentscope_sessions.state_data IS 'SDK序列化状态';
COMMENT ON COLUMN squad_chat.agentscope_sessions.version IS '状态版本';
COMMENT ON COLUMN squad_chat.agentscope_sessions.created_at IS '创建时间';
COMMENT ON COLUMN squad_chat.agentscope_sessions.updated_at IS '更新时间';

COMMENT ON TABLE squad_chat.agentscope_snapshots IS 'SDK快照存储';
COMMENT ON COLUMN squad_chat.agentscope_snapshots.snapshot_id IS '快照标识';
COMMENT ON COLUMN squad_chat.agentscope_snapshots.data IS '快照二进制数据';
COMMENT ON COLUMN squad_chat.agentscope_snapshots.created_at IS '创建时间';


COMMENT ON COLUMN squad_chat.sq_chat_conv.agent_definition_id IS '绑定的逻辑Agent标识';

-- Agent 配置及发布版本。已发布版本引用由管理事务校验并冻结。
CREATE TABLE squad_chat.sq_agent
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id           BIGINT NOT NULL UNIQUE CHECK (agent_id > 0),
    agent_code         VARCHAR(80) NOT NULL UNIQUE CHECK (agent_code ~ '^[a-zA-Z0-9_-]+$'),
    agent_name         VARCHAR(120) NOT NULL,
    agent_type         VARCHAR(16) NOT NULL CHECK (agent_type IN ('MASTER', 'SUB_AGENT')),
    status             VARCHAR(16) NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    current_version_id BIGINT,
    revision           BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    creator_id         BIGINT NOT NULL DEFAULT 0,
    creator_name       VARCHAR(50) NOT NULL DEFAULT '',
    create_time        TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id    BIGINT NOT NULL DEFAULT 0,
    last_updater_name  VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time   TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE squad_chat.sq_agent IS 'Agent 目录';
COMMENT ON COLUMN squad_chat.sq_agent.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent.agent_id IS '稳定 Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent.agent_code IS '能力选择编码';
COMMENT ON COLUMN squad_chat.sq_agent.agent_name IS '展示名称';
COMMENT ON COLUMN squad_chat.sq_agent.agent_type IS '主 Agent 或子 Agent 类型';
COMMENT ON COLUMN squad_chat.sq_agent.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_agent.current_version_id IS '当前生效的 Agent 版本ID';
COMMENT ON COLUMN squad_chat.sq_agent.revision IS '并发编辑版本';
COMMENT ON COLUMN squad_chat.sq_agent.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_version
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_version_id  BIGINT NOT NULL UNIQUE CHECK (agent_version_id > 0),
    agent_id          BIGINT NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    version_no        INTEGER NOT NULL CHECK (version_no > 0),
    status            VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    revision          BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    published_at      TIMESTAMPTZ(3),
    system_prompt     TEXT NOT NULL,
    description       VARCHAR(500) NOT NULL DEFAULT '',
    max_iters         INTEGER CHECK (max_iters > 0),
    creator_id        BIGINT NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50) NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_number UNIQUE (agent_id, version_no),
    CONSTRAINT ck_agent_version_published_at CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT uk_agent_version_owner UNIQUE (agent_id, agent_version_id)
);

COMMENT ON TABLE squad_chat.sq_agent_version IS 'Agent 版本';
COMMENT ON COLUMN squad_chat.sq_agent_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version.agent_version_id IS 'Agent 版本业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.agent_id IS '所属 Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.version_no IS '实体内的版本号';
COMMENT ON COLUMN squad_chat.sq_agent_version.status IS '版本发布状态';
COMMENT ON COLUMN squad_chat.sq_agent_version.revision IS '并发编辑版本';
COMMENT ON COLUMN squad_chat.sq_agent_version.published_at IS '发布时间';
COMMENT ON COLUMN squad_chat.sq_agent_version.system_prompt IS '业务系统提示词';
COMMENT ON COLUMN squad_chat.sq_agent_version.description IS 'Agent 职责描述';
COMMENT ON COLUMN squad_chat.sq_agent_version.max_iters IS '最大执行步数，空值使用平台默认值';
COMMENT ON COLUMN squad_chat.sq_agent_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_update_time IS '最后更新时间';

ALTER TABLE squad_chat.sq_agent
    ADD CONSTRAINT fk_agent_current_version
    FOREIGN KEY (agent_id, current_version_id)
    REFERENCES squad_chat.sq_agent_version (agent_id, agent_version_id);

CREATE TABLE squad_chat.sq_mcp_server
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mcp_server_id     BIGINT NOT NULL UNIQUE CHECK (mcp_server_id > 0),
    server_code       VARCHAR(80) NOT NULL UNIQUE CHECK (server_code ~ '^[a-zA-Z0-9_-]+$'),
    server_name       VARCHAR(120) NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    creator_id        BIGINT NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50) NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE squad_chat.sq_mcp_server IS 'MCP 服务目录';
COMMENT ON COLUMN squad_chat.sq_mcp_server.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_mcp_server.mcp_server_id IS 'MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.server_code IS 'MCP 服务编码';
COMMENT ON COLUMN squad_chat.sq_mcp_server.server_name IS '展示名称';
COMMENT ON COLUMN squad_chat.sq_mcp_server.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_mcp_server.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_mcp_server_version
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mcp_server_version_id BIGINT NOT NULL UNIQUE CHECK (mcp_server_version_id > 0),
    mcp_server_id         BIGINT NOT NULL REFERENCES squad_chat.sq_mcp_server (mcp_server_id),
    version_no            INTEGER NOT NULL CHECK (version_no > 0),
    status                VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    revision              BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    published_at          TIMESTAMPTZ(3),
    transport_type        VARCHAR(24) NOT NULL CHECK (transport_type IN ('STREAMABLE_HTTP', 'SSE')),
    endpoint              VARCHAR(2048) NOT NULL,
    credential_ref        VARCHAR(255),
    creator_id            BIGINT NOT NULL DEFAULT 0,
    creator_name          VARCHAR(50) NOT NULL DEFAULT '',
    create_time           TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id       BIGINT NOT NULL DEFAULT 0,
    last_updater_name     VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time      TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mcp_server_version_number UNIQUE (mcp_server_id, version_no),
    CONSTRAINT ck_mcp_server_version_published_at CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

COMMENT ON TABLE squad_chat.sq_mcp_server_version IS 'MCP 连接版本';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.mcp_server_version_id IS 'MCP 连接版本业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.mcp_server_id IS '所属 MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.version_no IS '实体内的版本号';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.status IS '版本发布状态';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.revision IS '并发编辑版本';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.published_at IS '发布时间';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.transport_type IS '远程 MCP 传输方式';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.endpoint IS '远程连接地址';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.credential_ref IS '连接凭据引用';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_tool
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tool_id           BIGINT NOT NULL UNIQUE CHECK (tool_id > 0),
    tool_code         VARCHAR(80) NOT NULL UNIQUE CHECK (tool_code ~ '^[a-zA-Z0-9_-]+$'),
    tool_name         VARCHAR(120) NOT NULL,
    tool_type         VARCHAR(16) NOT NULL CHECK (tool_type IN ('NATIVE', 'JAVA', 'MCP')),
    status            VARCHAR(16) NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    creator_id        BIGINT NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50) NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_type UNIQUE (tool_id, tool_type)
);

COMMENT ON TABLE squad_chat.sq_tool IS '工具目录';
COMMENT ON COLUMN squad_chat.sq_tool.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_tool.tool_id IS '工具业务ID';
COMMENT ON COLUMN squad_chat.sq_tool.tool_code IS '工具稳定编码';
COMMENT ON COLUMN squad_chat.sq_tool.tool_name IS '后台展示名称';
COMMENT ON COLUMN squad_chat.sq_tool.tool_type IS '工具实现来源';
COMMENT ON COLUMN squad_chat.sq_tool.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_tool.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_tool.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_tool.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_tool.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_tool.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_tool.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_tool_version
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tool_version_id       BIGINT NOT NULL UNIQUE CHECK (tool_version_id > 0),
    tool_id               BIGINT NOT NULL,
    tool_type             VARCHAR(16) NOT NULL,
    version_no            INTEGER NOT NULL CHECK (version_no > 0),
    status                VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    revision              BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    published_at          TIMESTAMPTZ(3),
    exposed_name          VARCHAR(64) NOT NULL CHECK (exposed_name ~ '^[a-zA-Z0-9_-]+$'),
    implementation_key    VARCHAR(160),
    mcp_server_version_id BIGINT REFERENCES squad_chat.sq_mcp_server_version (mcp_server_version_id),
    remote_tool_name      VARCHAR(255),
    creator_id            BIGINT NOT NULL DEFAULT 0,
    creator_name          VARCHAR(50) NOT NULL DEFAULT '',
    create_time           TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id       BIGINT NOT NULL DEFAULT 0,
    last_updater_name     VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time      TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_version_number UNIQUE (tool_id, version_no),
    CONSTRAINT ck_tool_version_published_at CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT fk_tool_version_type FOREIGN KEY (tool_id, tool_type) REFERENCES squad_chat.sq_tool (tool_id, tool_type),
    CONSTRAINT ck_tool_version_implementation CHECK (
        (tool_type IN ('NATIVE', 'JAVA') AND implementation_key IS NOT NULL
            AND mcp_server_version_id IS NULL AND remote_tool_name IS NULL)
        OR (tool_type = 'MCP' AND implementation_key IS NULL
            AND mcp_server_version_id IS NOT NULL AND remote_tool_name IS NOT NULL)
    )
);

COMMENT ON TABLE squad_chat.sq_tool_version IS '工具版本';
COMMENT ON COLUMN squad_chat.sq_tool_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_version_id IS '工具版本业务ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_id IS '所属工具业务ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_type IS '工具实现来源，与所属工具一致';
COMMENT ON COLUMN squad_chat.sq_tool_version.version_no IS '实体内的版本号';
COMMENT ON COLUMN squad_chat.sq_tool_version.status IS '版本发布状态';
COMMENT ON COLUMN squad_chat.sq_tool_version.revision IS '并发编辑版本';
COMMENT ON COLUMN squad_chat.sq_tool_version.published_at IS '发布时间';
COMMENT ON COLUMN squad_chat.sq_tool_version.exposed_name IS '暴露给模型的工具名称';
COMMENT ON COLUMN squad_chat.sq_tool_version.implementation_key IS '原生或 Java 工具的实现版本标识';
COMMENT ON COLUMN squad_chat.sq_tool_version.mcp_server_version_id IS '远程工具使用的 MCP 连接版本';
COMMENT ON COLUMN squad_chat.sq_tool_version.remote_tool_name IS 'MCP 服务返回的工具名';
COMMENT ON COLUMN squad_chat.sq_tool_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_tool_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_version_tool
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_version_id  BIGINT NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    tool_version_id   BIGINT NOT NULL REFERENCES squad_chat.sq_tool_version (tool_version_id),
    creator_id        BIGINT NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50) NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_tool UNIQUE (agent_version_id, tool_version_id)
);

COMMENT ON TABLE squad_chat.sq_agent_version_tool IS 'Agent 版本工具授权';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.agent_version_id IS '获授权的 Agent 版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.tool_version_id IS '授权的工具版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_version_subagent
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    master_version_id      BIGINT NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    subagent_version_id    BIGINT NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    delegation_name        VARCHAR(80) NOT NULL CHECK (delegation_name ~ '^[a-zA-Z0-9_-]+$'),
    delegation_description VARCHAR(500),
    creator_id             BIGINT NOT NULL DEFAULT 0,
    creator_name           VARCHAR(50) NOT NULL DEFAULT '',
    create_time            TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id        BIGINT NOT NULL DEFAULT 0,
    last_updater_name      VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_delegation UNIQUE (master_version_id, delegation_name),
    CONSTRAINT ck_agent_version_delegation_self CHECK (master_version_id <> subagent_version_id)
);

COMMENT ON TABLE squad_chat.sq_agent_version_subagent IS 'Agent 版本委派关系';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.master_version_id IS '主 Agent 版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.subagent_version_id IS '子 Agent 版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.delegation_name IS '主 Agent 内唯一的委派名称';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.delegation_description IS '在当前主 Agent 下的委派描述';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_update_time IS '最后更新时间';

CREATE INDEX idx_tool_version_mcp_server
    ON squad_chat.sq_tool_version (mcp_server_version_id)
    WHERE mcp_server_version_id IS NOT NULL;

CREATE INDEX idx_agent_version_tool_reverse
    ON squad_chat.sq_agent_version_tool (tool_version_id);

CREATE INDEX idx_agent_version_subagent_reverse
    ON squad_chat.sq_agent_version_subagent (subagent_version_id);
