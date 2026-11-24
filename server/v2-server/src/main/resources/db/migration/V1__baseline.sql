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

-- Agent 配置版本与 Master 发布定格模型。
-- MCP / Tool / Sub / Master 每次保存新增不可变配置版本；仅 Master 可发布，发布时定格完整依赖版本图。
CREATE TABLE squad_chat.sq_mcp_server
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mcp_server_id     BIGINT       NOT NULL UNIQUE CHECK (mcp_server_id > 0),
    server_code       VARCHAR(80)  NOT NULL UNIQUE CHECK (server_code ~ '^[a-zA-Z0-9_-]+$'),
    server_name       VARCHAR(120) NOT NULL,
    description       VARCHAR(500) NOT NULL DEFAULT '',
    status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    latest_version_id BIGINT,
    latest_version_no INTEGER      NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    latest_readiness  VARCHAR(16)  NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    revision          BIGINT       NOT NULL DEFAULT 0 CHECK (revision >= 0),
    deleted_at        TIMESTAMPTZ(3),
    creator_id        BIGINT       NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT       NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE squad_chat.sq_mcp_server IS 'MCP 服务目录';
COMMENT ON COLUMN squad_chat.sq_mcp_server.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_mcp_server.mcp_server_id IS 'MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.server_code IS '系统自动生成的 MCP 服务编码';
COMMENT ON COLUMN squad_chat.sq_mcp_server.server_name IS '后台展示名称';
COMMENT ON COLUMN squad_chat.sq_mcp_server.description IS '服务用途说明';
COMMENT ON COLUMN squad_chat.sq_mcp_server.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_mcp_server.latest_version_id IS '最新配置版本ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.latest_version_no IS '最新配置版本号';
COMMENT ON COLUMN squad_chat.sq_mcp_server.latest_readiness IS '最新配置版本就绪状态';
COMMENT ON COLUMN squad_chat.sq_mcp_server.revision IS '目录并发修订号';
COMMENT ON COLUMN squad_chat.sq_mcp_server.deleted_at IS '逻辑删除时间，未删除为空';
COMMENT ON COLUMN squad_chat.sq_mcp_server.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_mcp_server_version
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mcp_server_version_id BIGINT        NOT NULL UNIQUE CHECK (mcp_server_version_id > 0),
    mcp_server_id         BIGINT        NOT NULL REFERENCES squad_chat.sq_mcp_server (mcp_server_id),
    version_no            INTEGER       NOT NULL CHECK (version_no > 0),
    readiness             VARCHAR(16)   NOT NULL DEFAULT 'INCOMPLETE' CHECK (readiness IN ('READY', 'INCOMPLETE')),
    transport_type        VARCHAR(24)   CHECK (transport_type IS NULL OR transport_type IN ('STREAMABLE_HTTP', 'SSE')),
    endpoint              VARCHAR(2048) NOT NULL DEFAULT '',
    credential_ref        VARCHAR(255),
    request_headers       JSONB         NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(request_headers) = 'array'),
    timeout_ms            INTEGER       NOT NULL DEFAULT 30000 CHECK (timeout_ms BETWEEN 1000 AND 120000),
    creator_id            BIGINT        NOT NULL DEFAULT 0,
    creator_name          VARCHAR(50)   NOT NULL DEFAULT '',
    create_time           TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id       BIGINT        NOT NULL DEFAULT 0,
    last_updater_name     VARCHAR(50)   NOT NULL DEFAULT '',
    last_update_time      TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mcp_server_version_number UNIQUE (mcp_server_id, version_no),
    CONSTRAINT uk_mcp_server_version_owner UNIQUE (mcp_server_id, mcp_server_version_id)
);

COMMENT ON TABLE squad_chat.sq_mcp_server_version IS 'MCP 配置版本历史';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.mcp_server_version_id IS 'MCP 配置版本业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.mcp_server_id IS '所属 MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.version_no IS '配置版本号';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.readiness IS '该版本就绪状态 READY就绪 INCOMPLETE未就绪';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.transport_type IS '远程 MCP 传输方式';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.endpoint IS '远程连接地址';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.credential_ref IS '连接凭据引用名，密钥值不入库';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.request_headers IS '附加请求头列表，值可引用凭据占位符';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.timeout_ms IS '连接与调用超时毫秒数';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.last_update_time IS '最后更新时间';

ALTER TABLE squad_chat.sq_mcp_server
    ADD CONSTRAINT fk_mcp_server_latest_version
    FOREIGN KEY (mcp_server_id, latest_version_id)
    REFERENCES squad_chat.sq_mcp_server_version (mcp_server_id, mcp_server_version_id);

CREATE TABLE squad_chat.sq_tool
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tool_id           BIGINT       NOT NULL UNIQUE CHECK (tool_id > 0),
    tool_code         VARCHAR(80)  NOT NULL UNIQUE CHECK (tool_code ~ '^[a-zA-Z0-9_-]+$'),
    tool_name         VARCHAR(120) NOT NULL,
    display_alias     VARCHAR(60)  NOT NULL DEFAULT '',
    tool_type         VARCHAR(16)  NOT NULL CHECK (tool_type IN ('BUILTIN', 'HTTP', 'MCP')),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    latest_version_id BIGINT,
    latest_version_no INTEGER      NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    latest_readiness  VARCHAR(16)  NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    revision          BIGINT       NOT NULL DEFAULT 0 CHECK (revision >= 0),
    deleted_at        TIMESTAMPTZ(3),
    creator_id        BIGINT       NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT       NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_type UNIQUE (tool_id, tool_type)
);

COMMENT ON TABLE squad_chat.sq_tool IS '工具目录';
COMMENT ON COLUMN squad_chat.sq_tool.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_tool.tool_id IS '工具业务ID';
COMMENT ON COLUMN squad_chat.sq_tool.tool_code IS '系统自动生成的工具编码';
COMMENT ON COLUMN squad_chat.sq_tool.tool_name IS '后台展示名称';
COMMENT ON COLUMN squad_chat.sq_tool.display_alias IS '聊天前端事件展示别称，留空回退工具名称';
COMMENT ON COLUMN squad_chat.sq_tool.tool_type IS '工具类型：内置实现、HTTP 接口或远程 MCP';
COMMENT ON COLUMN squad_chat.sq_tool.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_tool.latest_version_id IS '最新配置版本ID';
COMMENT ON COLUMN squad_chat.sq_tool.latest_version_no IS '最新配置版本号';
COMMENT ON COLUMN squad_chat.sq_tool.latest_readiness IS '最新配置版本就绪状态';
COMMENT ON COLUMN squad_chat.sq_tool.revision IS '目录并发修订号';
COMMENT ON COLUMN squad_chat.sq_tool.deleted_at IS '逻辑删除时间，未删除为空';
COMMENT ON COLUMN squad_chat.sq_tool.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_tool.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_tool.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_tool.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_tool.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_tool.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_tool_version
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tool_version_id    BIGINT       NOT NULL UNIQUE CHECK (tool_version_id > 0),
    tool_id            BIGINT       NOT NULL,
    tool_type          VARCHAR(16)  NOT NULL,
    version_no         INTEGER      NOT NULL CHECK (version_no > 0),
    readiness          VARCHAR(16)  NOT NULL DEFAULT 'INCOMPLETE' CHECK (readiness IN ('READY', 'INCOMPLETE')),
    exposed_name       VARCHAR(64)  NOT NULL DEFAULT '' CHECK (exposed_name = '' OR exposed_name ~ '^[a-zA-Z0-9_-]+$'),
    description        TEXT         NOT NULL DEFAULT '',
    input_schema       JSONB        NOT NULL DEFAULT '{"type": "object", "properties": {}}'::jsonb
        CHECK (jsonb_typeof(input_schema) = 'object'),
    read_only          BOOLEAN      NOT NULL DEFAULT TRUE,
    implementation_key VARCHAR(160),
    mcp_server_id      BIGINT       REFERENCES squad_chat.sq_mcp_server (mcp_server_id),
    remote_tool_name   VARCHAR(255),
    http_spec          JSONB        CHECK (http_spec IS NULL OR jsonb_typeof(http_spec) = 'object'),
    creator_id         BIGINT       NOT NULL DEFAULT 0,
    creator_name       VARCHAR(50)  NOT NULL DEFAULT '',
    create_time        TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id    BIGINT       NOT NULL DEFAULT 0,
    last_updater_name  VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time   TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_version_number UNIQUE (tool_id, version_no),
    CONSTRAINT uk_tool_version_owner UNIQUE (tool_id, tool_version_id),
    CONSTRAINT fk_tool_version_type FOREIGN KEY (tool_id, tool_type) REFERENCES squad_chat.sq_tool (tool_id, tool_type),
    CONSTRAINT ck_tool_version_implementation CHECK (
        (tool_type = 'BUILTIN' AND mcp_server_id IS NULL AND remote_tool_name IS NULL AND http_spec IS NULL)
        OR (tool_type = 'HTTP' AND implementation_key IS NULL AND mcp_server_id IS NULL AND remote_tool_name IS NULL)
        OR (tool_type = 'MCP' AND implementation_key IS NULL AND http_spec IS NULL)
    )
);

COMMENT ON TABLE squad_chat.sq_tool_version IS '工具配置版本历史';
COMMENT ON COLUMN squad_chat.sq_tool_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_version_id IS '工具配置版本业务ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_id IS '所属工具业务ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.tool_type IS '工具实现来源，与所属工具一致';
COMMENT ON COLUMN squad_chat.sq_tool_version.version_no IS '配置版本号';
COMMENT ON COLUMN squad_chat.sq_tool_version.readiness IS '该版本就绪状态 READY就绪 INCOMPLETE未就绪';
COMMENT ON COLUMN squad_chat.sq_tool_version.exposed_name IS '暴露给模型的工具名称';
COMMENT ON COLUMN squad_chat.sq_tool_version.description IS '模型可见的工具用途说明';
COMMENT ON COLUMN squad_chat.sq_tool_version.input_schema IS '模型调用参数的 JSON Schema';
COMMENT ON COLUMN squad_chat.sq_tool_version.read_only IS '是否只读取信息、不产生外部副作用';
COMMENT ON COLUMN squad_chat.sq_tool_version.implementation_key IS '内置工具的实现标识';
COMMENT ON COLUMN squad_chat.sq_tool_version.mcp_server_id IS '远程工具关联的 MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.remote_tool_name IS 'MCP 服务返回的工具名';
COMMENT ON COLUMN squad_chat.sq_tool_version.http_spec IS 'HTTP 工具的请求模板、凭据引用、超时与结果提取配置';
COMMENT ON COLUMN squad_chat.sq_tool_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_tool_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_tool_version.last_update_time IS '最后更新时间';

ALTER TABLE squad_chat.sq_tool
    ADD CONSTRAINT fk_tool_latest_version
    FOREIGN KEY (tool_id, latest_version_id)
    REFERENCES squad_chat.sq_tool_version (tool_id, tool_version_id);

CREATE TABLE squad_chat.sq_agent
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id           BIGINT       NOT NULL UNIQUE CHECK (agent_id > 0),
    agent_code         VARCHAR(80)  NOT NULL UNIQUE CHECK (agent_code ~ '^[a-zA-Z0-9_-]+$'),
    agent_name         VARCHAR(120) NOT NULL,
    display_alias      VARCHAR(60)  NOT NULL DEFAULT '',
    agent_type         VARCHAR(16)  NOT NULL CHECK (agent_type IN ('MASTER', 'SUB_AGENT')),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    latest_version_id  BIGINT,
    latest_version_no  INTEGER      NOT NULL DEFAULT 0 CHECK (latest_version_no >= 0),
    latest_readiness   VARCHAR(16)  NOT NULL DEFAULT 'INCOMPLETE' CHECK (latest_readiness IN ('READY', 'INCOMPLETE')),
    current_release_id BIGINT,
    revision           BIGINT       NOT NULL DEFAULT 0 CHECK (revision >= 0),
    deleted_at         TIMESTAMPTZ(3),
    creator_id         BIGINT       NOT NULL DEFAULT 0,
    creator_name       VARCHAR(50)  NOT NULL DEFAULT '',
    create_time        TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id    BIGINT       NOT NULL DEFAULT 0,
    last_updater_name  VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time   TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_release_master_only CHECK (agent_type = 'MASTER' OR current_release_id IS NULL)
);

COMMENT ON TABLE squad_chat.sq_agent IS 'Agent 目录';
COMMENT ON COLUMN squad_chat.sq_agent.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent.agent_id IS '稳定 Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent.agent_code IS '系统自动生成的 Agent 编码';
COMMENT ON COLUMN squad_chat.sq_agent.agent_name IS '后台展示名称';
COMMENT ON COLUMN squad_chat.sq_agent.display_alias IS '聊天前端事件展示别称，留空回退 Agent 名称';
COMMENT ON COLUMN squad_chat.sq_agent.agent_type IS '主 Agent 或子 Agent 类型';
COMMENT ON COLUMN squad_chat.sq_agent.status IS '启停状态';
COMMENT ON COLUMN squad_chat.sq_agent.latest_version_id IS '最新配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent.latest_version_no IS '最新配置版本号';
COMMENT ON COLUMN squad_chat.sq_agent.latest_readiness IS '最新配置版本就绪状态';
COMMENT ON COLUMN squad_chat.sq_agent.current_release_id IS '当前生效的 Master 发布定格快照ID，子 Agent 恒为空';
COMMENT ON COLUMN squad_chat.sq_agent.revision IS '并发编辑版本';
COMMENT ON COLUMN squad_chat.sq_agent.deleted_at IS '逻辑删除时间，未删除为空';
COMMENT ON COLUMN squad_chat.sq_agent.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_version
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_version_id  BIGINT       NOT NULL UNIQUE CHECK (agent_version_id > 0),
    agent_id          BIGINT       NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    version_no        INTEGER      NOT NULL CHECK (version_no > 0),
    readiness         VARCHAR(16)  NOT NULL DEFAULT 'INCOMPLETE' CHECK (readiness IN ('READY', 'INCOMPLETE')),
    description       VARCHAR(500) NOT NULL DEFAULT '',
    prompt_sections   JSONB        NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(prompt_sections) = 'object'),
    system_prompt     TEXT         NOT NULL DEFAULT '',
    max_iters         INTEGER      CHECK (max_iters IS NULL OR max_iters > 0),
    creator_id        BIGINT       NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT       NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_number UNIQUE (agent_id, version_no),
    CONSTRAINT uk_agent_version_owner UNIQUE (agent_id, agent_version_id)
);

COMMENT ON TABLE squad_chat.sq_agent_version IS 'Agent 配置版本历史';
COMMENT ON COLUMN squad_chat.sq_agent_version.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version.agent_version_id IS 'Agent 配置版本业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.agent_id IS '所属 Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.version_no IS '配置版本号';
COMMENT ON COLUMN squad_chat.sq_agent_version.readiness IS '该版本就绪状态 READY就绪 INCOMPLETE未就绪';
COMMENT ON COLUMN squad_chat.sq_agent_version.description IS 'Agent 职责描述';
COMMENT ON COLUMN squad_chat.sq_agent_version.prompt_sections IS '结构化提示词分块 JSON（role/goal/workflow/constraints/outputFormat）';
COMMENT ON COLUMN squad_chat.sq_agent_version.system_prompt IS '按结构化提示词拼装后的完整系统提示词';
COMMENT ON COLUMN squad_chat.sq_agent_version.max_iters IS '最大执行步数，空值使用平台默认值';
COMMENT ON COLUMN squad_chat.sq_agent_version.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version.last_update_time IS '最后更新时间';

ALTER TABLE squad_chat.sq_agent
    ADD CONSTRAINT fk_agent_latest_version
    FOREIGN KEY (agent_id, latest_version_id)
    REFERENCES squad_chat.sq_agent_version (agent_id, agent_version_id);

CREATE TABLE squad_chat.sq_agent_version_tool
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_version_id  BIGINT  NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    tool_id           BIGINT  NOT NULL REFERENCES squad_chat.sq_tool (tool_id),
    sort_no           INTEGER NOT NULL DEFAULT 0,
    creator_id        BIGINT  NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50) NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT  NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_tool UNIQUE (agent_version_id, tool_id)
);

COMMENT ON TABLE squad_chat.sq_agent_version_tool IS 'Agent 配置版本的工具关联';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.agent_version_id IS '所属 Agent 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.tool_id IS '关联的工具业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.sort_no IS '展示顺序';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_tool.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_version_subagent
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    master_version_id      BIGINT      NOT NULL REFERENCES squad_chat.sq_agent_version (agent_version_id),
    subagent_id            BIGINT      NOT NULL REFERENCES squad_chat.sq_agent (agent_id),
    delegation_name        VARCHAR(80) NOT NULL CHECK (delegation_name ~ '^[a-zA-Z0-9_-]+$'),
    delegation_description VARCHAR(500),
    display_alias          VARCHAR(60) NOT NULL DEFAULT '',
    sort_no                INTEGER     NOT NULL DEFAULT 0,
    creator_id             BIGINT      NOT NULL DEFAULT 0,
    creator_name           VARCHAR(50) NOT NULL DEFAULT '',
    create_time            TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id        BIGINT      NOT NULL DEFAULT 0,
    last_updater_name      VARCHAR(50) NOT NULL DEFAULT '',
    last_update_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_version_subagent UNIQUE (master_version_id, subagent_id),
    CONSTRAINT uk_agent_version_delegation UNIQUE (master_version_id, delegation_name)
);

COMMENT ON TABLE squad_chat.sq_agent_version_subagent IS 'Master 配置版本的 Sub 委派关联';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.master_version_id IS '所属 Master 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.subagent_id IS '关联的 Sub Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.delegation_name IS '主 Agent 内唯一的委派名称';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.delegation_description IS '在当前主 Agent 下的委派描述，留空使用子 Agent 职责描述';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.display_alias IS '当前主 Agent 下的委派展示别称，留空回退子 Agent 别称或名称';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.sort_no IS '展示顺序';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_version_subagent.last_update_time IS '最后更新时间';

-- Master 发布定格快照：仅 Master 允许发布，发布时一次性冻结全链路版本图。
CREATE TABLE squad_chat.sq_agent_release
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

COMMENT ON TABLE squad_chat.sq_agent_release IS 'Master 发布定格快照';
COMMENT ON COLUMN squad_chat.sq_agent_release.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_release.release_id IS '发布快照业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release.master_agent_id IS '所属 Master Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release.release_no IS 'Master 发布序号';
COMMENT ON COLUMN squad_chat.sq_agent_release.master_version_id IS '定格的 Master 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_release.master_name IS '发布时定格的 Master 名称';
COMMENT ON COLUMN squad_chat.sq_agent_release.master_display_alias IS '发布时定格的 Master 展示别称';
COMMENT ON COLUMN squad_chat.sq_agent_release.dependency_digest IS '发布定格的全链路版本摘要指纹';
COMMENT ON COLUMN squad_chat.sq_agent_release.publish_operation_id IS '发布操作幂等标识';
COMMENT ON COLUMN squad_chat.sq_agent_release.published_at IS '发布定格时间';
COMMENT ON COLUMN squad_chat.sq_agent_release.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_release.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release.last_update_time IS '最后更新时间';

ALTER TABLE squad_chat.sq_agent
    ADD CONSTRAINT fk_agent_current_release
    FOREIGN KEY (agent_id, current_release_id)
    REFERENCES squad_chat.sq_agent_release (master_agent_id, release_id);

CREATE UNIQUE INDEX uk_agent_release_operation
    ON squad_chat.sq_agent_release (master_agent_id, publish_operation_id)
    WHERE publish_operation_id IS NOT NULL;

CREATE TABLE squad_chat.sq_agent_release_subagent
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

COMMENT ON TABLE squad_chat.sq_agent_release_subagent IS 'Master 发布定格的 Sub 委派快照';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.release_id IS '所属发布快照业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.subagent_id IS '定格的 Sub Agent 业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.subagent_version_id IS '定格的 Sub 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.subagent_name IS '发布时定格的 Sub 名称';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.delegation_name IS '定格的委派名称';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.delegation_description IS '定格的委派描述';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.display_alias IS '定格的前端展示别称';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.sort_no IS '展示顺序';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release_subagent.last_update_time IS '最后更新时间';

CREATE TABLE squad_chat.sq_agent_release_tool
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

COMMENT ON TABLE squad_chat.sq_agent_release_tool IS 'Master 发布定格的工具与 MCP 快照';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.release_id IS '所属发布快照业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.owner_agent_id IS '工具归属的 Agent 业务ID（Master 或 Sub）';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.owner_agent_version_id IS '工具归属的 Agent 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.tool_id IS '定格的工具业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.tool_version_id IS '定格的工具配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.tool_name IS '发布时定格的工具名称';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.tool_display_alias IS '发布时定格的工具前端展示别称';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.mcp_server_id IS '若为 MCP 工具，定格的 MCP 服务业务ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.mcp_server_version_id IS '若为 MCP 工具，定格的 MCP 配置版本ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.mcp_server_name IS '发布时定格的 MCP 服务名称';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.sort_no IS '展示顺序';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.creator_id IS '创建人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.creator_name IS '创建人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.create_time IS '创建时间';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.last_updater_id IS '最后更新人ID';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.last_updater_name IS '最后更新人姓名';
COMMENT ON COLUMN squad_chat.sq_agent_release_tool.last_update_time IS '最后更新时间';

CREATE INDEX idx_tool_version_mcp_server
    ON squad_chat.sq_tool_version (mcp_server_id)
    WHERE mcp_server_id IS NOT NULL;

CREATE INDEX idx_agent_version_tool_reverse
    ON squad_chat.sq_agent_version_tool (tool_id);

CREATE INDEX idx_agent_version_subagent_reverse
    ON squad_chat.sq_agent_version_subagent (subagent_id);

CREATE INDEX idx_agent_release_subagent_reverse
    ON squad_chat.sq_agent_release_subagent (subagent_id);

CREATE INDEX idx_agent_release_tool_reverse
    ON squad_chat.sq_agent_release_tool (tool_id);

CREATE INDEX idx_agent_release_mcp_reverse
    ON squad_chat.sq_agent_release_tool (mcp_server_id)
    WHERE mcp_server_id IS NOT NULL;
