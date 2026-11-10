# AgentSquad 一期客服核心数据结构（从零设计草案）

> 状态：领域模型草案 v0.1，供评审；不是最终 DDL。
>
> 设计范围：一期只做 AgentSquad 自身的智能客服，不做任意外部平台接入。Agent Definition、模型、工具、Skill、知识库属于产品级配置，不是每个终端用户各自维护一套。
>
> 数据库与文件/向量存储技术均未选型。本文件只定义逻辑实体、字段语义、关系和生命周期。

## 1. 先固定几个边界

### 1.1 产品数据与运行时数据分开

- **AgentSquad 管理**：用户、Agent Definition 及版本、模型配置、工具配置、Skill 资产、知识库和文档、产品会话目录。
- **AgentScope 管理**：Agent 执行、Turn/Run、消息和工具执行事实、Session Log、Checkpoint、输入队列、运行时 Memory/Workspace。
- **暂不建立副本**：不默认创建产品自己的 Run、Task、Message、Session Log、Checkpoint、Worker Job、DAG 表。
- **可选读模型**：若会话列表、全文检索、统计或页面快照确有性能/查询需求，再从原生日志投影；投影不是第二个事实源。

### 1.2 AgentScope 身份映射

AgentScope 的会话身份由稳定的 userId + agentId + sessionId 确定。产品侧建议直接映射：

| AgentScope 身份 | AgentSquad 对应值 | 规则 |
|---|---|---|
| userId | user_account.user_id | 用户稳定 ID，不使用登录名 |
| agentId | agent_definition.agent_code | Agent 稳定编码，发布新版本时不变 |
| sessionId | conversation.conversation_id | 产品会话 ID，同时作为运行时 sessionId |

因此不另建一张产品 AgentSession 表，也不在会话表里重复保存三份 runtime identity 字段。若后续发现 SDK 身份约束不允许直接复用，再增加显式映射。

### 1.3 Agent Definition 是产品级配置

一期只有产品管理员维护 Agent Definition。普通用户创建会话时选择/使用已发布的 Agent，不复制或拥有 Agent Definition。当前会话固定绑定创建时使用的 Definition 版本，配置发布不应悄悄改变历史会话。

## 2. 核心实体关系

~~~mermaid
erDiagram
    USER_ACCOUNT ||--o{ CONVERSATION : owns
    AGENT_DEFINITION ||--o{ AGENT_DEFINITION_REVISION : versions
    AGENT_DEFINITION ||--o{ CONVERSATION : serves
    AGENT_DEFINITION_REVISION ||--o{ CONVERSATION : pinned_by
    MODEL_PROFILE ||--o{ AGENT_DEFINITION_REVISION : selected_in_snapshot
    KNOWLEDGE_BASE ||--o{ KNOWLEDGE_DOCUMENT : contains
    SKILL_DEFINITION ||--o{ SKILL_REVISION : versions
    FILE_ASSET ||--o{ KNOWLEDGE_DOCUMENT : source_file
    FILE_ASSET ||--o{ SKILL_REVISION : resource
    TOOL_DEFINITION ||--o{ AGENT_DEFINITION_REVISION : included_in_snapshot
~~~

图中资源关联表达逻辑关系。Agent Revision 保存发布时的配置快照；不代表所有资源都必须采用图中的物理外键。

## 3. 第一批核心实体

### 3.1 用户与权限

#### user_account — 产品用户

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| user_id | string/UUID | 是 | 稳定用户 ID；映射 AgentScope userId |
| display_name | string | 是 | 展示名称 |
| status | enum | 是 | ENABLED / DISABLED |
| created_at | datetime | 是 | 创建时间 |
| updated_at | datetime | 是 | 修改时间 |

登录标识、密码/SSO 等认证数据暂不塞进用户表；先确定认证方式，再决定是否单独建 user_identity。密码只能保存安全哈希，不能保存明文。

**角色边界：** 一期如只有管理员和普通用户两种固定角色，可以先采用简单角色字段或最小用户角色关系；不急着设计可视化权限树、资源级 ACL、租户体系。确认确实需要多角色组合后，再确定 role / user_role / permission 结构。

### 3.2 Agent 定义与版本

#### agent_definition — Agent 稳定定义

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| agent_id | string/UUID | 是 | 产品主键 |
| agent_code | string | 是 | 稳定业务编码；映射 AgentScope agentId，创建后不可随版本变化 |
| name | string | 是 | Agent 名称 |
| description | string | 否 | 用途说明 |
| status | enum | 是 | ENABLED / DISABLED / ARCHIVED |
| current_revision_id | string/UUID | 否 | 当前已发布版本 |
| created_by | user ID | 是 | 创建人 |
| created_at / updated_at | datetime | 是 | 时间 |

#### agent_definition_revision — Agent 不可变版本

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| revision_id | string/UUID | 是 | 版本主键 |
| agent_id | string/UUID | 是 | 所属 Agent |
| revision_no | integer | 是 | Agent 内递增版本号，唯一 |
| config_snapshot | JSON | 是 | 发布时完整运行配置快照 |
| config_hash | string | 是 | 配置摘要，便于判断配置是否变化 |
| status | enum | 是 | DRAFT / PUBLISHED / RETIRED |
| change_note | string | 否 | 本次修改说明 |
| created_by | user ID | 是 | 创建人 |
| created_at / published_at | datetime | 是/否 | 创建、发布时间 |

**版本规则**

1. 草稿可以修改；已发布版本不可原地修改。
2. 发布时生成完整 config_snapshot，固定 Prompt、模型配置、工具、Skill、知识库引用及运行策略。
3. agent_definition.current_revision_id 指向当前发布版本。
4. 会话创建时固定保存 agent_id + revision_id；老会话不会自动切换新版本。
5. 被会话引用的版本不可物理删除。

**配置快照示意（字段仍需按 AgentScope Builder 能力核对）：**

~~~json
{
  "prompt": { "system": "客服主提示词" },
  "model": {
    "profileId": "model-profile-id",
    "provider": "provider-code",
    "modelName": "model-name",
    "parameters": {}
  },
  "tools": [{ "toolId": "tool-id", "enabled": true }],
  "skills": [{ "skillId": "skill-id", "revisionId": "skill-revision-id" }],
  "knowledgeBases": [{ "knowledgeBaseId": "knowledge-base-id" }],
  "memory": {},
  "subAgents": {},
  "permissions": {},
  "context": {}
}
~~~

该快照是“已发布版本当时实际使用的配置”，不是把运行时消息、执行状态再复制一份。

### 3.3 模型配置

#### model_profile — 可供 Agent 使用的模型连接配置

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| model_profile_id | string/UUID | 是 | 主键 |
| name | string | 是 | 管理界面展示名称 |
| provider_code | string | 是 | 模型供应商/适配器标识 |
| model_name | string | 是 | 供应商侧模型名 |
| endpoint | string | 否 | 自定义服务地址 |
| credential_ref | string | 否 | 密钥管理系统引用；禁止保存明文密钥 |
| default_parameters | JSON | 否 | temperature、topP、maxTokens 等 |
| capabilities | JSON | 否 | 工具调用、视觉等能力声明 |
| status | enum | 是 | ENABLED / DISABLED |
| created_at / updated_at | datetime | 是 | 时间 |

模型配置被 Agent Revision 引用时，建议发布快照固定供应商、模型名、endpoint 和推理参数；凭据只保留可轮换的 secret reference，不复制密钥值。是否给模型配置本身增加版本表，后续根据配置审计需求决定。

### 3.4 工具配置

#### tool_definition — 产品可管理的工具

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| tool_id | string/UUID | 是 | 主键 |
| tool_code | string | 是 | 稳定工具编码 |
| name | string | 是 | 展示名称 |
| tool_type | enum | 是 | BUILTIN / MCP / HTTP（具体类型以一期实际支持范围为准） |
| description | string | 否 | 工具用途 |
| config | JSON | 否 | 非敏感连接和调用配置 |
| credential_ref | string | 否 | 凭据引用，不保存明文 |
| status | enum | 是 | ENABLED / DISABLED |
| created_at / updated_at | datetime | 是 | 时间 |

一期不建设“任意业务平台接入/租户集成”模型。工具只是 Agent 可调用能力；工具授权与参数校验由服务端执行，不能让模型决定越权访问。工具配置是否也需要独立版本，需结合发布快照策略决定。

### 3.5 Skill 管理

#### skill_definition — Skill 稳定身份

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| skill_id | string/UUID | 是 | 主键 |
| skill_code | string | 是 | 稳定编码，对应 Workspace 中的 Skill 目录名 |
| name | string | 是 | 名称 |
| description | string | 否 | 用途说明 |
| status | enum | 是 | ENABLED / DISABLED / ARCHIVED |
| created_at / updated_at | datetime | 是 | 时间 |

#### skill_revision — Skill 内容版本

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| skill_revision_id | string/UUID | 是 | 主键 |
| skill_id | string/UUID | 是 | 所属 Skill |
| revision_no | integer | 是 | 版本号 |
| skill_md | text | 是 | SKILL.md 正文 |
| resource_manifest | JSON | 否 | 附属资源清单/文件引用 |
| status | enum | 是 | DRAFT / PUBLISHED / RETIRED |
| created_by / created_at | user ID / datetime | 是 | 创建信息 |

AgentScope Workspace 是运行时工作区；建议产品侧 Skill 版本作为管理源，发布时物化到对应 Workspace。需要验证原生 Skill 加载与资源解析方式，避免数据库和 Workspace 两边都可独立修改，形成双重事实源。

### 3.6 知识库与文档

#### knowledge_base — 知识库

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| knowledge_base_id | string/UUID | 是 | 主键 |
| name | string | 是 | 名称 |
| description | string | 否 | 用途说明 |
| status | enum | 是 | ENABLED / DISABLED / DELETING |
| retrieval_config | JSON | 否 | TopK、阈值、分段策略等 |
| embedding_model_profile_id | string/UUID | 否 | Embedding 模型配置引用 |
| rerank_model_profile_id | string/UUID | 否 | Rerank 模型配置引用 |
| created_by / created_at / updated_at | user ID / datetime | 是 | 管理信息 |

#### knowledge_document — 知识文档

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| document_id | string/UUID | 是 | 主键 |
| knowledge_base_id | string/UUID | 是 | 所属知识库 |
| file_asset_id | string/UUID | 否 | 上传文件引用；网页/文本来源可为空 |
| source_type | enum | 是 | FILE / URL / TEXT |
| source_value | string/text | 否 | URL 或文本来源标识；大正文不建议直接塞元数据表 |
| title | string | 是 | 文档名称 |
| index_status | enum | 是 | PENDING / PROCESSING / READY / FAILED |
| error_message | string | 否 | 最近一次处理错误摘要 |
| created_by / created_at / updated_at | user ID / datetime | 是 | 管理信息 |

文档分块、向量、倒排索引、Embedding 结果属于检索实现层，暂不定义数据库表。等检索方案、数据量、更新/删除策略确定后，再决定由哪种存储承载。

### 3.7 文件资产

#### file_asset — 上传文件和附件元数据

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| file_asset_id | string/UUID | 是 | 主键 |
| owner_user_id | user ID | 是 | 上传者/归属人 |
| original_name | string | 是 | 原始文件名，仅用于展示 |
| media_type | string | 是 | 服务端识别的媒体类型 |
| size_bytes | integer | 是 | 字节数 |
| sha256 | string | 是 | 内容摘要 |
| storage_key | string | 是 | 对象存储中的内部定位键，不是公开 URL |
| purpose | enum | 是 | KNOWLEDGE_SOURCE / CHAT_ATTACHMENT / SKILL_RESOURCE |
| status | enum | 是 | UPLOADING / READY / FAILED / DELETED |
| created_at | datetime | 是 | 上传时间 |

文件内容放文件存储，不放关系表。知识文档和 Skill 资源通过引用关联文件。聊天附件是否要单独建立关联表，等确认一期是否支持附件以及 AgentScope 输入如何引用文件后再决定。

### 3.8 产品会话目录

#### conversation — 产品会话

| 字段 | 逻辑类型 | 必填 | 说明 |
|---|---|---:|---|
| conversation_id | string/UUID | 是 | 主键，同时映射 AgentScope sessionId |
| user_id | user ID | 是 | 所属用户，同时映射 AgentScope userId |
| agent_id | string/UUID | 是 | 使用的 Agent 定义 |
| agent_revision_id | string/UUID | 是 | 创建会话时固定的配置版本 |
| title | string | 否 | 会话标题，可由用户修改或系统生成 |
| title_source | enum | 否 | USER / AUTO |
| lifecycle_status | enum | 是 | ACTIVE / ARCHIVED / DELETED |
| last_activity_at | datetime | 否 | 会话目录排序用的更新时间/投影字段 |
| created_at / updated_at | datetime | 是 | 时间 |

**明确不保存：** 当前 Run 状态、Turn 状态、消息正文、工具调用参数、Checkpoint、原生事件序号。这些由 AgentScope Session Log 提供。

last_activity_at 只是列表排序所需的产品读模型字段，可由已提交的原生事件更新；它不是执行事实。若后续发现需要消息摘要/最后消息预览，可先从日志构建投影，不因此建立完整消息副本。

## 4. 原生数据与产品表的最终分工

| 数据 | 归属 | AgentSquad 是否建事实表 |
|---|---|---:|
| 用户、角色 | AgentSquad | 是 |
| Agent Definition、发布版本 | AgentSquad | 是 |
| 模型、工具、Skill、知识库管理元数据 | AgentSquad | 是 |
| 产品会话目录、归属、标题、归档状态 | AgentSquad | 是 |
| Session / Turn / Run | AgentScope | 否 |
| 用户/Agent/工具消息历史 | AgentScope Session Log | 否 |
| 工具调用、模型调用、执行事件 | AgentScope Session Log | 否；需要报表时再做投影 |
| Checkpoint、输入队列、恢复记录 | AgentScope | 否 |
| 原生 Memory、Workspace 文件 | AgentScope | 不复制；产品若要编辑/治理需定义同步边界 |
| 知识文档元数据、文件元数据 | AgentSquad | 是 |
| 向量、分块、Embedding、全文索引 | 检索实现层 | 暂不决定 |
| SSE 实时通知 | 产品接口层读取/投影原生日志 | 不单独建持久事实表 |

## 5. 一期建议先评审的表范围

### 第一批（客服最小闭环）

1. user_account
2. agent_definition
3. agent_definition_revision
4. model_profile
5. conversation

这批足以定义用户、Agent 配置版本、模型选择和会话归属；运行事实由 AgentScope 保存。

### 第二批（客服知识和能力配置）

6. tool_definition
7. skill_definition
8. skill_revision
9. knowledge_base
10. knowledge_document
11. file_asset

若一期确定不支持 Skill 管理、工具管理或文件上传，对应实体可延后，不必为架构图上的页面强行建表。

## 6. 暂时不做的设计

- 不定义数据库厂商相关字段类型、DDL、索引语法。
- 不新增产品级 Run、Turn、Worker、DAG、Checkpoint、Session Log 表。
- 不新增完整消息表和事件表副本。
- 不设计多租户、外部平台连接实例、平台级数据映射。
- 不预设向量数据库、分块表、Embedding 表。
- 不把 AgentScope Workspace 和产品配置库设计成两个都可编辑的真相源。
- 不把所有配置都塞进一个万能 JSON 表；只有 Agent Revision 的不可变运行配置快照使用 JSON。

## 7. 待确认的关键决策

| 编号 | 问题 | 当前建议 |
|---|---|---|
| D1 | 一期用户认证方式是什么？ | 暂不把登录凭据字段写死；先定认证接入 |
| D2 | 一期角色是否只有管理员和普通用户？ | 先按两类角色考虑，不建设权限树 |
| D3 | Agent Definition 是不是只有一个客服 Agent，还是支持多个客服 Agent？ | 数据模型支持多个，产品初始可只发布一个 |
| D4 | 已有会话是否永远固定 Agent Revision？ | 默认固定；显式升级会话另议 |
| D5 | 模型配置更新是否需要独立版本？ | Agent 发布快照冻结非敏感配置；凭据引用允许轮换 |
| D6 | Skill 内容以产品库为真相源，发布后同步到 Workspace，是否接受？ | 建议接受，避免双向编辑 |
| D7 | 一期知识库是否支持文件、URL、纯文本三种来源？ | 先按三种来源抽象，实际 UI 可分期开放 |
| D8 | 一期聊天是否支持文件附件？ | 未确认前不建会话附件关联表 |
| D9 | 是否需要消息全文检索、会话摘要和运营统计？ | 没有明确查询需求前不建消息/事件投影表 |
| D10 | 软删除与原生日志清理如何联动？ | 产品先标记删除；物理清理按保留策略异步处理 |

## 8. 官方资料依据

- [Harness 架构](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/architecture.md)
- [会话操作、事件与恢复](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/session-log.md)
- [Workspace](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/workspace.md)
- [Memory](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/memory.md)
- [AgentScope JDBC 方言](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/dialect/AbstractJdbcDialect.java)

> 本稿将官方框架事实、产品建模建议和待确认项分开。完成评审后再输出字段级最终模型、约束/索引、DDL 和接口参数定义。
