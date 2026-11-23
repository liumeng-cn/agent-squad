# Agent 基础配置方案

状态：本文是 Agent 动态加载前置的基础配置设计，覆盖 MCP 服务、工具、子 Agent 与主 Agent 的管理配置、版本发布、依赖校验和试运行；运行时按配置装配 Master 属于下一阶段。

## 目标与边界

1. 管理端所有页面使用统一布局：顶栏、按目录分组的侧栏、面包屑、页头、筛选栏、表格、分页与弹窗共用样式。
2. 配置遵循依赖顺序：MCP 服务 → 工具 → 子 Agent → 主 Agent。下游只能绑定上游**已发布且启用**的版本；被引用的上游不能停用。
3. 工具由配置自动生成可执行实现：内置工具选择代码登记实现；HTTP 工具按参数定义与请求模板生成接口调用；MCP 工具从远程服务发现并带出定义。三类工具都支持试运行。
4. 不做：任意脚本执行（需沙箱，后续单独设计）、模型选择、运行时按配置装配 Master、凭据保险库页面。

## 资源与状态

| 资源 | 目录字段 | 版本内容 |
| --- | --- | --- |
| MCP 服务 | 编码、名称、说明、启停、修订号 | 传输方式、地址、凭据引用、请求头、超时 |
| 工具 | 编码、名称、类型、启停、修订号 | 模型工具名、描述、参数 JSON Schema、只读标记、按类型的实现配置 |
| Agent | 编码、名称、类型、启停、当前版本、修订号 | 职责描述、系统提示词、最大步数、工具版本绑定、子 Agent 委派 |

状态规则：

- 目录状态 `ENABLED`/`DISABLED`；版本状态 `DRAFT`/`PUBLISHED`。每个资源最多一个草稿（部分唯一索引保证）；草稿可多次保存，发布后冻结不可修改。
- 新建资源时同一事务创建目录和第一个草稿（v1）。已有发布版本时，“基于当前版本修改”由前端带上发布内容创建新草稿。
- Agent 发布即切换为当前生效版本；可切换回任一已发布版本（回滚）。工具和 MCP 没有“当前版本”，下游按版本 ID 固定绑定，列表展示最新发布版本。
- 启停属于影响运行的操作，使用发布权限。停用保护：MCP 服务被启用工具的已发布版本引用时不能停用；工具被启用 Agent 的当前版本绑定时不能停用；子 Agent 被启用主 Agent 的当前版本委派时不能停用。冲突返回 409 及引用方名称。

## 数据结构

只列本次变更，其余字段沿用 `V1__baseline.sql`。增量见 `server/v2-server/src/main/resources/db/updates/20261121_refine_agent_configuration.sql`。

| 表 | 变更 |
| --- | --- |
| sq_mcp_server | 新增 `description VARCHAR(500) NOT NULL DEFAULT ''`、`revision BIGINT NOT NULL DEFAULT 0` |
| sq_mcp_server_version | 新增 `request_headers JSONB NOT NULL DEFAULT '[]'`（`[{name,value}]`）、`timeout_ms INTEGER NOT NULL DEFAULT 30000`（1000～120000）；部分唯一索引 `(mcp_server_id) WHERE status='DRAFT'` |
| sq_tool | `tool_type` 取值改为 `BUILTIN`/`HTTP`/`MCP`；新增 `revision` |
| sq_tool_version | 新增 `description TEXT NOT NULL DEFAULT ''`、`input_schema JSONB NOT NULL`、`read_only BOOLEAN NOT NULL DEFAULT TRUE`、`http_spec JSONB`；实现约束改为 BUILTIN 仅 `implementation_key`，HTTP 仅 `http_spec`，MCP 仅 `mcp_server_version_id`+`remote_tool_name`；单草稿部分唯一索引 |
| sq_agent_version | 单草稿部分唯一索引 |

凭据不入库：`agentsquad.v2.credentials.entries` 配置“引用名 → 密钥”，数据库只保存引用名；接口只返回引用名列表，日志和试运行回显中的密钥值替换为 `******`。

## 工具类型与生成规则

### 内置工具 BUILTIN

代码中的 `BuiltinToolRegistry` 登记可配置实现（当前为 `current_time`、`test_event_tool`）。选择实现后带出模型工具名、描述、参数和只读标记：参数与只读标记以实现为准，名称和描述可按业务调整；`ask_user` 是平台默认能力，不进入目录。

### HTTP 接口工具 HTTP

`httpSpec` 结构：

| 字段 | 说明 |
| --- | --- |
| method | `GET`/`POST`/`PUT`/`PATCH`/`DELETE` |
| url | 绝对 http/https 地址模板，主机部分不允许占位符；路径和查询可用 `{{参数名}}`、`{{credential}}` |
| headers | `[{name,value}]`，最多 20 个；值可用占位符；禁止 Host、Content-Length、Connection、Transfer-Encoding |
| bodyTemplate | 可空；仅 POST/PUT/PATCH；JSON 文本，`{{参数名}}` 替换为该参数的 JSON 值（缺失为 null），替换后必须是合法 JSON |
| credentialRef | 可空；使用 `{{credential}}` 时必填，须为已配置的引用名 |
| timeoutMs | 1000～60000，默认 15000 |
| resultPointer | 可空；JSON Pointer（如 `/data/items`），用于从 JSON 响应提取结果 |

执行步骤：

1. 校验必填参数存在；未知参数忽略。
2. URL 占位符替换为 URL 编码后的值；请求头占位符替换为原始字符串。
3. 未被 URL 使用的参数：GET/DELETE 追加为查询参数；POST/PUT/PATCH 无模板时组成 JSON 请求体，有模板时按模板生成。
4. 默认 `Accept: application/json`，有请求体时 `Content-Type: application/json`；不跟随重定向；最多读取 1 MiB 响应。
5. 非 2xx 返回工具错误 `HTTP 状态码 + 截断响应`；2xx 且配置了 `resultPointer` 时提取节点，路径不存在返回工具错误。
6. 输出文本超过 8000 字符截断并标注；日志只记录工具名、方法、主机、状态码和耗时。

### MCP 工具 MCP

先选择已发布且启用的 MCP 服务版本，再从远程服务发现工具（`tools/list`）。选中远程工具后带出远程工具名、描述和参数 Schema；模型工具名默认取远程工具名并可修改。执行时按服务版本的传输方式、地址、请求头（凭据替换）和超时建立客户端，调用 `tools/call`。

### 参数定义

`inputSchema` 为 JSON Schema 对象：顶层 `type` 必须为 `object`，`properties` 不超过 64 个，属性名匹配 `^[A-Za-z_][A-Za-z0-9_]{0,63}$`，序列化后不超过 32 KB。HTTP 工具由页面的参数表格生成（名称、类型、必填、说明、枚举值）；内置与 MCP 工具的参数来自实现，只读展示。

### 试运行

`POST /tools/test-run` 提交未保存的工具定义和参数，服务端用与运行时相同的生成逻辑构建工具并调用一次，返回是否成功、耗时、请求摘要和输出。试运行不写库，不进入 Agent 会话。

## 接口

前缀 `/v2-server/api/v2/agents/management`，统一返回 `ApiResult<T>`；ID 均为字符串；`revision` 为乐观锁修订号。前端类型以 `web/workbench/src/features/management/model/agentConfig.ts` 为准。

### 通用模型

- `KeyValue`：`{ name, value }`
- `VersionSummary`：`{ versionId, versionNo, status, revision, publishedAt, lastUpdateTime, current }`；`current` 对 Agent 表示当前生效版本，对工具和 MCP 表示最新发布版本。
- `ConfigUsage`：`{ ownerId, ownerCode, ownerName, ownerStatus, versionNo, versionStatus, referencedVersionNo, active }`；`active` 表示生效引用：MCP 服务被启用工具的已发布版本引用；工具被启用 Agent 的当前版本绑定；子 Agent 被启用主 Agent 的当前版本委派。列表中的 `usageCount` 为存在生效引用的下游数量。
- `RevisionRequest`：`{ revision }`

### MCP 服务（权限 management:mcp:*）

| 接口 | 权限 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET /mcp-servers | query | — | `McpServer[]` |
| POST /mcp-servers | edit | `{ serverCode, serverName, description, draft: McpServerDraft }` | `McpServerDetail` |
| POST /mcp-servers/probe | edit | `McpServerDraft` | `McpProbe` |
| GET /mcp-servers/credential-options | query | — | `string[]` |
| GET /mcp-servers/{mcpServerId} | query | — | `McpServerDetail` |
| PUT /mcp-servers/{mcpServerId} | edit | `{ serverName, description, revision }` | `McpServer` |
| POST /mcp-servers/{mcpServerId}/enable | publish | `RevisionRequest` | `McpServer` |
| POST /mcp-servers/{mcpServerId}/disable | publish | `RevisionRequest` | `McpServer` |
| GET /mcp-servers/{mcpServerId}/versions/{versionId} | query | — | `McpServerVersion` |
| POST /mcp-servers/{mcpServerId}/versions | edit | `McpServerDraft` | `McpServerVersion` |
| PUT /mcp-servers/{mcpServerId}/versions/{versionId} | edit | `McpServerDraft`（含 revision） | `McpServerVersion` |
| POST /mcp-servers/{mcpServerId}/versions/{versionId}/publish | publish | `RevisionRequest` | `McpServerVersion` |

- `McpServerDraft`：`{ revision?, transportType: STREAMABLE_HTTP|SSE, endpoint, credentialRef, headers: KeyValue[], timeoutMs }`；请求头值可写 `{{credential}}`。
- `McpServer`：`{ mcpServerId, serverCode, serverName, description, status, revision, publishedVersionId, publishedVersionNo, draftVersionId, usageCount, lastUpdateTime }`
- `McpServerVersion`：`McpServerDraft` 字段加 `{ mcpServerVersionId, mcpServerId, versionNo, status, revision, publishedAt, lastUpdateTime }`
- `McpServerDetail`：`{ server, versions: VersionSummary[]（版本号倒序）, usages: ConfigUsage[]（引用它的工具） }`
- `McpProbe`：`{ durationMs, tools: McpRemoteTool[] }`；`McpRemoteTool`：`{ name, title, description, inputSchema, readOnly }`

### 工具（权限 management:tool:*）

| 接口 | 权限 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET /tools | query | — | `Tool[]` |
| POST /tools | edit | `{ toolCode, toolName, toolType, draft: ToolDraft }` | `ToolDetail` |
| POST /tools/test-run | edit | `{ toolType, draft: ToolDraft, arguments }` | `ToolTestRun` |
| GET /tools/builtin-options | query | — | `BuiltinToolOption[]` |
| GET /tools/mcp-options | query | — | `McpServerOption[]` |
| GET /tools/mcp-options/{mcpServerVersionId}/remote-tools | query | — | `McpRemoteTool[]` |
| GET /tools/credential-options | query | — | `string[]` |
| GET /tools/{toolId} | query | — | `ToolDetail` |
| PUT /tools/{toolId} | edit | `{ toolName, revision }` | `Tool` |
| POST /tools/{toolId}/enable | publish | `RevisionRequest` | `Tool` |
| POST /tools/{toolId}/disable | publish | `RevisionRequest` | `Tool` |
| GET /tools/{toolId}/versions/{versionId} | query | — | `ToolVersion` |
| POST /tools/{toolId}/versions | edit | `ToolDraft` | `ToolVersion` |
| PUT /tools/{toolId}/versions/{versionId} | edit | `ToolDraft`（含 revision） | `ToolVersion` |
| POST /tools/{toolId}/versions/{versionId}/publish | publish | `RevisionRequest` | `ToolVersion` |

- `ToolDraft`：`{ revision?, exposedName, description, inputSchema, readOnly, implementationKey, mcpServerVersionId, remoteToolName, httpSpec }`，按类型只填对应实现字段，其余为 null。内置工具的参数与只读标记以实现为准，名称和描述可调整；MCP 工具的参数为发现时的快照。
- `HttpToolSpec`：`{ method, url, headers: KeyValue[], bodyTemplate, credentialRef, timeoutMs, resultPointer }`
- `Tool`：`{ toolId, toolCode, toolName, toolType, status, revision, exposedName, description, publishedVersionId, publishedVersionNo, draftVersionId, usageCount, lastUpdateTime }`；exposedName、description 取最新发布版本，尚未发布时取草稿。
- `ToolVersion`：`ToolDraft` 字段加 `{ toolVersionId, toolId, toolType, versionNo, status, revision, publishedAt, lastUpdateTime, mcpServerName, mcpServerVersionNo }`
- `ToolDetail`：`{ tool, versions: VersionSummary[], usages: ConfigUsage[]（绑定它的 Agent） }`
- `ToolTestRun`：`{ success, durationMs, request, output }`；request 为请求摘要，凭据已遮蔽。
- `BuiltinToolOption`：`{ implementationKey, exposedName, description, inputSchema, readOnly }`
- `McpServerOption`：`{ mcpServerVersionId, mcpServerId, serverCode, serverName, versionNo, latest }`；`latest` 表示该服务的最新发布版本。

### Agent（权限 management:agent:*）

| 接口 | 权限 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET /agents | query | — | `Agent[]` |
| POST /agents | edit | `{ agentCode, agentName, agentType, draft: AgentDraft }` | `AgentDetail` |
| GET /agents/tool-options | query | — | `ToolOption[]` |
| GET /agents/subagent-options | query | — | `SubagentOption[]` |
| GET /agents/{agentId} | query | — | `AgentDetail` |
| PUT /agents/{agentId} | edit | `{ agentName, revision }` | `Agent` |
| POST /agents/{agentId}/enable | publish | `RevisionRequest` | `Agent` |
| POST /agents/{agentId}/disable | publish | `RevisionRequest` | `Agent` |
| GET /agents/{agentId}/versions/{versionId} | query | — | `AgentVersion` |
| POST /agents/{agentId}/versions | edit | `AgentDraft` | `AgentVersion` |
| PUT /agents/{agentId}/versions/{versionId} | edit | `AgentDraft`（含 revision） | `AgentVersion` |
| POST /agents/{agentId}/versions/{versionId}/publish | publish | `RevisionRequest`（版本修订号） | `Agent` |
| POST /agents/{agentId}/versions/{versionId}/activate | publish | `RevisionRequest`（Agent 修订号） | `Agent` |

- `AgentDraft`：`{ revision?, description, systemPrompt, maxIters, toolVersionIds: string[], subagents: [{ subagentVersionId, delegationName, delegationDescription }] }`
- `Agent`：`{ agentId, agentCode, agentName, agentType, status, revision, description, currentVersionId, currentVersionNo, draftVersionId, toolCount, subagentCount, usageCount, lastUpdateTime }`；description 与数量取当前生效版本，尚未发布时取草稿。
- `AgentVersion`：`{ agentVersionId, agentId, versionNo, status, revision, publishedAt, lastUpdateTime, description, systemPrompt, maxIters, tools: AgentToolBinding[], subagents: AgentDelegation[] }`
- `AgentToolBinding`：`{ toolVersionId, toolId, toolCode, toolName, toolType, exposedName, description, versionNo, latestVersionNo, usable }`；`usable` 为工具启用、版本已发布且依赖可用。
- `AgentDelegation`：`{ subagentVersionId, subagentId, subagentCode, subagentName, description, versionNo, currentVersionNo, delegationName, delegationDescription, usable }`；`description` 为子 Agent 该版本的职责描述。
- `AgentDetail`：`{ agent, versions: VersionSummary[], usages: ConfigUsage[]（委派它的主 Agent） }`
- `ToolOption`：`{ toolVersionId, toolId, toolCode, toolName, toolType, exposedName, description, versionNo, latest }`，仅列出已发布、启用且依赖可用的版本。
- `SubagentOption`：`{ subagentVersionId, agentId, agentCode, agentName, description, versionNo, current }`，仅列出启用子 Agent 的已发布版本。

## 校验、事务与并发

1. 保存草稿：校验目录存在 → 校验内容（字段、Schema、HTTP 模板、凭据引用、依赖版本）→ 新建时取下一版本号插入，单草稿唯一索引冲突返回 409“已有草稿”；编辑时按 `status='DRAFT' AND revision=?` 条件更新，影响 0 行返回 409。Agent 编辑在同一事务替换工具和委派绑定。
2. 发布：工具与 MCP 重新校验依赖后按 `DRAFT+revision` 条件改为 `PUBLISHED`；Agent 在同一事务内发布版本并以 Agent 修订号条件切换 `current_version_id`，任一步失败整体回滚。
3. 发布前置：工具描述非空；MCP 工具引用的服务版本已发布且启用；Agent 提示词非空，子 Agent 描述非空，绑定工具和委派目标均可用，模型工具名与委派名不重复。
4. 目录修改：按 `revision` 条件更新名称、说明和状态；停用时执行停用保护。
5. 禁止 `FOR UPDATE`，一致性依赖条件更新、唯一约束和事务。

## 页面

- 列表页：页头（标题、说明、主操作）→ 配置流程条（MCP 服务 → 工具 → 子 Agent → 主 Agent，显示各环节已发布数量并可跳转）→ 筛选栏 → 表格（名称/编码、类型、最新版本、草稿、引用数、状态、更新时间、操作）→ 分页。
- 编辑页 `/management/{resources}/{id}`、`/management/{resources}/new`：顶部返回、标题、状态与版本标签和操作按钮（保存草稿、发布、基于当前版本修改）；内容分“配置”“版本记录”“引用关系”三个页签；配置页按分区卡片组织，右侧为发布检查清单。
- 工具编辑：类型选择卡片 → 模型可见定义 → 实现配置（内置选择、HTTP 请求、MCP 服务与远程工具）→ 参数表格与 Schema 预览 → 试运行（按参数生成输入框，展示结果）。
- MCP 编辑：基本信息 → 连接配置（传输方式、地址、凭据、请求头、超时）→ 测试连接（展示发现的工具）。
- Agent 编辑：类型卡片 → 基本信息与职责描述 → 系统提示词 → 工具（已选卡片、添加工具弹窗、可升级提示）→ 子 Agent 委派（仅主 Agent）→ 运行参数 → 发布检查。
