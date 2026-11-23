-- 已执行 20261121_add_agent_configuration.sql 的数据库升级到可生成工具的配置结构。
-- 使用最终 baseline 初始化的数据库无需执行。旧 NATIVE/JAVA 工具统一归为内置工具。
BEGIN;

-- MCP 服务目录：说明与修订号
ALTER TABLE squad_chat.sq_mcp_server
    ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN revision    BIGINT       NOT NULL DEFAULT 0 CHECK (revision >= 0);

COMMENT ON COLUMN squad_chat.sq_mcp_server.description IS '服务用途说明';
COMMENT ON COLUMN squad_chat.sq_mcp_server.revision IS '目录并发修订号';

-- MCP 连接版本：请求头与超时
ALTER TABLE squad_chat.sq_mcp_server_version
    ADD COLUMN request_headers JSONB   NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(request_headers) = 'array'),
    ADD COLUMN timeout_ms      INTEGER NOT NULL DEFAULT 30000 CHECK (timeout_ms BETWEEN 1000 AND 120000);

COMMENT ON COLUMN squad_chat.sq_mcp_server_version.credential_ref IS '连接凭据引用名，密钥值不入库';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.request_headers IS '附加请求头列表，值可引用凭据占位符';
COMMENT ON COLUMN squad_chat.sq_mcp_server_version.timeout_ms IS '连接与调用超时毫秒数';

-- 工具类型改为内置、HTTP、MCP，先解除依赖旧取值的约束
ALTER TABLE squad_chat.sq_tool_version
    DROP CONSTRAINT ck_tool_version_implementation,
    DROP CONSTRAINT fk_tool_version_type;

ALTER TABLE squad_chat.sq_tool
    DROP CONSTRAINT sq_tool_tool_type_check;

UPDATE squad_chat.sq_tool
SET tool_type = 'BUILTIN'
WHERE tool_type IN ('NATIVE', 'JAVA');

UPDATE squad_chat.sq_tool_version
SET tool_type = 'BUILTIN'
WHERE tool_type IN ('NATIVE', 'JAVA');

ALTER TABLE squad_chat.sq_tool
    ADD CONSTRAINT sq_tool_tool_type_check CHECK (tool_type IN ('BUILTIN', 'HTTP', 'MCP')),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0);

COMMENT ON COLUMN squad_chat.sq_tool.tool_type IS '工具类型：内置实现、HTTP 接口或远程 MCP';
COMMENT ON COLUMN squad_chat.sq_tool.revision IS '目录并发修订号';

-- 工具版本：模型可见定义与按类型的实现配置
ALTER TABLE squad_chat.sq_tool_version
    ADD COLUMN description  TEXT    NOT NULL DEFAULT '',
    ADD COLUMN input_schema JSONB   NOT NULL DEFAULT '{"type": "object", "properties": {}}'::jsonb
        CHECK (jsonb_typeof(input_schema) = 'object'),
    ADD COLUMN read_only    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN http_spec    JSONB CHECK (http_spec IS NULL OR jsonb_typeof(http_spec) = 'object'),
    ADD CONSTRAINT fk_tool_version_type
        FOREIGN KEY (tool_id, tool_type) REFERENCES squad_chat.sq_tool (tool_id, tool_type),
    ADD CONSTRAINT ck_tool_version_implementation CHECK (
        (tool_type = 'BUILTIN' AND implementation_key IS NOT NULL
            AND mcp_server_version_id IS NULL AND remote_tool_name IS NULL AND http_spec IS NULL)
        OR (tool_type = 'HTTP' AND http_spec IS NOT NULL
            AND implementation_key IS NULL AND mcp_server_version_id IS NULL AND remote_tool_name IS NULL)
        OR (tool_type = 'MCP' AND mcp_server_version_id IS NOT NULL AND remote_tool_name IS NOT NULL
            AND implementation_key IS NULL AND http_spec IS NULL)
    );

COMMENT ON COLUMN squad_chat.sq_tool_version.description IS '模型可见的工具用途说明';
COMMENT ON COLUMN squad_chat.sq_tool_version.input_schema IS '模型调用参数的 JSON Schema';
COMMENT ON COLUMN squad_chat.sq_tool_version.read_only IS '是否只读取信息、不产生外部副作用';
COMMENT ON COLUMN squad_chat.sq_tool_version.implementation_key IS '内置工具的实现标识';
COMMENT ON COLUMN squad_chat.sq_tool_version.http_spec IS 'HTTP 工具的请求模板、凭据引用、超时与结果提取配置';

-- 每个资源最多保留一个未发布草稿，并发新建草稿由唯一约束拒绝。
CREATE UNIQUE INDEX uk_agent_version_draft
    ON squad_chat.sq_agent_version (agent_id)
    WHERE status = 'DRAFT';

CREATE UNIQUE INDEX uk_mcp_server_version_draft
    ON squad_chat.sq_mcp_server_version (mcp_server_id)
    WHERE status = 'DRAFT';

CREATE UNIQUE INDEX uk_tool_version_draft
    ON squad_chat.sq_tool_version (tool_id)
    WHERE status = 'DRAFT';

COMMIT;
