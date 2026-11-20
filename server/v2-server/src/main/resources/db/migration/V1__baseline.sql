CREATE SCHEMA IF NOT EXISTS squad_chat;

CREATE TABLE squad_chat.sq_chat_conv
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conv_id           VARCHAR(64)    NOT NULL CHECK (conv_id ~ '^[a-zA-Z0-9-]{1,64}$'),
    session_id        VARCHAR(64)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    agent_id          VARCHAR(80)    NOT NULL,
    title             VARCHAR(120)   NOT NULL DEFAULT '新对话',
    title_source      VARCHAR(16)    NOT NULL DEFAULT 'AUTO',
    version           BIGINT         NOT NULL DEFAULT 0 CHECK (version >= 0),
    remark            TEXT,
    archived          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    creator_id        BIGINT         NOT NULL DEFAULT 0,
    creator_name      VARCHAR(50)    NOT NULL DEFAULT '',
    create_time       TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updater_id   BIGINT         NOT NULL DEFAULT 0,
    last_updater_name VARCHAR(50)    NOT NULL DEFAULT '',
    last_update_time  TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_conv_user_conversation UNIQUE (user_id, conv_id),
    CONSTRAINT uk_chat_conv_native_session UNIQUE (user_id, agent_id, session_id)
);

CREATE INDEX idx_chat_conv_user_created
    ON squad_chat.sq_chat_conv (user_id, create_time DESC, id DESC);

COMMENT ON TABLE squad_chat.sq_chat_conv IS '用户聊天会话目录';
COMMENT ON COLUMN squad_chat.sq_chat_conv.id IS '内部存储主键';
COMMENT ON COLUMN squad_chat.sq_chat_conv.conv_id IS '业务对话ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.session_id IS '原生会话ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.user_id IS '归属用户ID';
COMMENT ON COLUMN squad_chat.sq_chat_conv.agent_id IS '固定Agent标识';
COMMENT ON COLUMN squad_chat.sq_chat_conv.title IS '展示标题';
COMMENT ON COLUMN squad_chat.sq_chat_conv.title_source IS '标题来源 AUTO默认 MANUAL手动';
COMMENT ON COLUMN squad_chat.sq_chat_conv.version IS '标题编辑版本';
COMMENT ON COLUMN squad_chat.sq_chat_conv.remark IS '对话备注';
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

COMMENT ON COLUMN squad_chat.sq_chat_conv.archived IS '是否归档';
COMMENT ON COLUMN squad_chat.sq_chat_conv.deleted IS '是否删除';
