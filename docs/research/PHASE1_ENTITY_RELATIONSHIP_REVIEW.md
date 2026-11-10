# AgentSquad 一期客服：核心实体关系评审（v0.2）

> 本文在 `PHASE1_CORE_DATA_MODEL_DRAFT.md` 基础上细化实体关系。目标是先确认逻辑模型，不定义数据库厂商、物理字段类型或 SQL。
>
> 设计范围：一期 AgentSquad 自身的多用户智能客服。AgentScope Java `v2.1.0-BETA1` 负责原生 Agent 运行时；AgentSquad 管理产品用户、Agent 配置和产品会话目录。

## 1. 本轮建议确定的核心原则

1. 产品配置与运行时执行记录分开。产品不重复建立 Session、Turn、Run、Message、Checkpoint、Worker、DAG 等运行事实表。
2. Agent Definition 是产品级配置，由管理员维护；普通用户只使用已发布的 Agent 创建会话。
3. Agent Definition 的发布版本不可变；每个 Conversation 固定绑定一个具体 Revision。
4. Revision 与模型、工具、Skill、知识库采用明确的引用关系，便于配置页面查询和资源引用校验。
5. 发布时生成完整运行配置快照，供 AgentScope Builder/Workspace 装配；关系数据是产品配置来源，快照是发布产物，不是第二套可编辑配置。
6. AgentScope 身份建议直接映射稳定产品 ID：userId = user_account.user_id；agentId = agent_definition.agent_code；sessionId = conversation.conversation_id。实际接入前仍需用当前 SDK 版本做集成验证。
7. 知识库内容默认可以持续更新；是否要求历史会话固定知识库内容版本，作为单独产品决策，不与 Agent Definition 版本混为一谈。

## 2. 推荐实体关系图

~~~mermaid
erDiagram
    USER_ACCOUNT ||--o{ CONVERSATION : owns
    AGENT_DEFINITION ||--o{ AGENT_DEFINITION_REVISION : has_revisions
    AGENT_DEFINITION ||--o{ CONVERSATION : selected_agent
    AGENT_DEFINITION_REVISION ||--o{ CONVERSATION : pinned_revision

    AGENT_DEFINITION_REVISION }o--|| MODEL_PROFILE : model_ref
    AGENT_DEFINITION_REVISION ||--o{ AGENT_REVISION_TOOL_REF : tool_refs
    TOOL_DEFINITION ||--o{ AGENT_REVISION_TOOL_REF : tool_ref
    AGENT_DEFINITION_REVISION ||--o{ AGENT_REVISION_SKILL_REF : skill_refs
    SKILL_REVISION ||--o{ AGENT_REVISION_SKILL_REF : skill_revision_ref
    AGENT_DEFINITION_REVISION ||--o{ AGENT_REVISION_KNOWLEDGE_REF : knowledge_refs
    KNOWLEDGE_BASE ||--o{ AGENT_REVISION_KNOWLEDGE_REF : knowledge_base_ref

    SKILL_DEFINITION ||--o{ SKILL_REVISION : has_revisions
    KNOWLEDGE_BASE ||--o{ KNOWLEDGE_DOCUMENT : contains
    FILE_ASSET ||--o{ KNOWLEDGE_DOCUMENT : source_file
    USER_ACCOUNT ||--o{ FILE_ASSET : created_by
~~~

图中Ref 关系表是产品配置关系，不是 AgentScope 运行时表。若评审后决定某类资源不需要独立查询或管理，可以再合并简化。

## 3. 实体职责与关系说明

### 3.1 用户与会话

**USER_ACCOUNT 1:N CONVERSATION**

- 一个用户可以创建多个客服会话。
- 一个会话只属于一个用户。
- 查询、修改、归档和删除会话时，必须同时校验 conversation_id 与 user_id，不能只凭会话 ID 操作。
- Agent Definition 不属于普通用户；它是产品级配置。

**CONVERSATION 绑定 AGENT_DEFINITION 与指定 REVISION**

- conversation.agent_id 表示本次会话使用哪个产品 Agent。
- conversation.agent_revision_id 表示创建会话时使用的具体发布版本。
- 两者都保存，是为了明确产品归属并能直接定位版本；需要约束 revision 必须属于对应 agent。
- 发布新 Revision 只影响新会话；旧会话继续使用原 Revision，除非未来增加明确的会话升级操作。

### 3.2 Agent Definition 与 Revision

**AGENT_DEFINITION 1:N AGENT_DEFINITION_REVISION**

Agent Definition 是长期稳定身份；Revision 是某次可发布的完整配置版本。

建议规则：

- agent_code 全局唯一且创建后不变，作为 AgentScope agentId 的候选映射。
- 同一 Agent 的 revision_no 唯一且递增。
- 草稿 Revision 可以编辑；发布后不可原地修改。
- current_revision_id 指向当前发布版本。
- 被会话引用的 Revision 不允许物理删除。
- 配置发布必须生成 runtime_config_snapshot 和 config_hash，记录发布人及发布时间。

### 3.3 Revision 与资源引用

建议用明确的引用关系表达 Agent Revision 使用了哪些资源，而不是只把所有资源 ID 塞进一个不可查询的 JSON。

| Ref 关系实体 | 关系 | 关键职责 | 建议约束 |
|---|---|---|---|
| model_ref（Revision 字段） | Revision N:M Model Profile | 指定主模型及其他模型用途 | 同一 Revision + model_role 唯一 |
| AGENT_REVISION_TOOL_REF | Revision 1:N Tool Definition | 指定可用工具及启用配置 | 同一 Revision + tool_ref 唯一 |
| AGENT_REVISION_SKILL_REF | Revision 1:N Skill Revision | 固定使用的 Skill 内容版本 | 同一 Revision + skill_revision_ref 唯一 |
| AGENT_REVISION_KNOWLEDGE_REF | Revision 1:N Knowledge Base | 指定可检索知识库及检索参数 | 同一 Revision + knowledge_base_ref 唯一 |

Ref 记录可以包含少量本次 Agent 使用时的覆盖配置，例如工具启用状态、模型角色、知识检索 TopK。不要把模型密钥、MCP 密钥等明文放入Ref 记录或快照。

**为什么既有Ref 关系表又有运行配置快照？**

- Ref 关系表服务于管理页面：查看一个 Agent 用了哪些model_ref、tool_refs、skill_refs 和 knowledge_base_refs，支持启停、校验和资源反查。
- 发布快照服务于运行：将发布时的配置解析为完整、不可变的装配输入，避免运行时再拼装出不同结果。
- 快照由Ref 关系数据和 Revision 自身配置生成；不允许管理员直接编辑快照来反向修改引用关系。

### 3.4 Skill 与 Skill Revision

**SKILL_DEFINITION 1:N SKILL_REVISION**

- Skill Definition 保存稳定编码、名称和管理状态。
- Skill Revision 保存某一版 SKILL.md 内容及资源清单。
- Agent Revision 绑定具体 Skill Revision，而不是只绑定 Skill Definition。
- 发布后的 Skill Revision 不可原地修改。
- Skill 内容管理源归 AgentSquad；发布时再按 AgentScope Workspace/Skill 加载机制提供给运行时。Workspace 不应成为另一套独立可编辑的管理源。

### 3.5 知识库与文档

**KNOWLEDGE_BASE 1:N KNOWLEDGE_DOCUMENT**

- 一个知识库包含多份文档。
- 每份文档当前归属一个知识库；若未来需要一份文档同时进入多个知识库，可以把文档源与知识库文档关系拆开。
- 文档原文件通过 FILE_ASSET 引用；URL/纯文本来源可以没有 file_asset_id。
- 文档解析、切分、Embedding、索引状态属于知识处理生命周期；分块和向量的物理结构待检索方案确定后再设计。

**知识库版本语义需要单独决定**：建议一期先采用动态知识库，即会话固定 Agent Revision，但检索时使用知识库当前可用内容。若业务要求历史会话可完全复现当时知识，需要增加知识库发布版本或文档版本，并让 Agent Revision/Conversation 固定知识库版本。不要默认 Agent Revision 版本就冻结了知识内容。

### 3.6 文件资产

- FILE_ASSET 保存文件元数据和内部 storage_key，不保存文件二进制。
- 对产品级知识文档和 Skill 资源，建议记录 created_by，而不是简单使用 owner_user_id 表达个人所有权。
- 若一期支持用户私有聊天附件，需要另行确定附件归属和 Conversation-File 关联；目前不提前增加该关联表。
- 文件是否可被某个 Agent 使用，由知识库/Skill/会话附件的业务关联和权限校验决定，不由文件 URL 是否可访问决定。

## 4. 建议的逻辑实体清单

| 分组 | 逻辑实体 | 是否建议纳入一期核心模型 | 说明 |
|---|---|---:|---|
| 用户 | USER_ACCOUNT | 是 | 用户身份和状态；认证凭据另按认证方案决定 |
| 会话 | CONVERSATION | 是 | 产品会话目录、归属、标题、Agent 和 Revision 绑定 |
| Agent | AGENT_DEFINITION | 是 | Agent 稳定身份和当前发布版本 |
| Agent | AGENT_DEFINITION_REVISION | 是 | Agent 配置版本和发布快照 |
| 模型 | MODEL_PROFILE | 是 | 产品可管理的模型连接配置 |
| 工具 | TOOL_DEFINITION | 是 | 产品可管理的工具目录 |
| Skill | SKILL_DEFINITION | 是 | Skill 稳定身份 |
| Skill | SKILL_REVISION | 是 | Skill 内容版本 |
| 知识 | KNOWLEDGE_BASE | 是 | 知识库目录与检索配置 |
| 知识 | KNOWLEDGE_DOCUMENT | 是 | 文档来源和处理状态 |
| 文件 | FILE_ASSET | 视一期上传需求 | 文件元数据；没有上传能力可延后 |
| 绑定 | model_ref（Revision 字段） | 是 | Revision 与模型关系 |
| 绑定 | AGENT_REVISION_TOOL_REF | 是 | Revision 与工具关系 |
| 绑定 | AGENT_REVISION_SKILL_REF | 是 | Revision 与 Skill 版本关系 |
| 绑定 | AGENT_REVISION_KNOWLEDGE_REF | 是 | Revision 与知识库关系 |

共 14 个核心逻辑实体（含 4 个Ref 关系实体），FILE_ASSET 可按一期文件能力决定是否进入首批。它们不是最终物理表数量；后续可以根据数据库能力和查询需求合并。

## 5. 明确不纳入产品核心实体的运行数据

| 数据 | 归属 | 设计处理 |
|---|---|---|
| AgentSession / Session Log | AgentScope | 使用原生持久化，不再建产品事实表 |
| Turn / Run / 输入队列 | AgentScope | 使用原生任务与恢复机制 |
| 消息历史 / 工具调用事件 | AgentScope Session Log | 页面需要时读取并转换为产品展示结构 |
| Checkpoint / 恢复状态 | AgentScope | 使用原生能力 |
| SubAgent 执行实例 | AgentScope | 不建 Worker Job 或 SubAgent Run 副本 |
| SSE 事件流 | AgentSquad API 适配层 + AgentScope 原生日志 | 用原生事件序号恢复；是否需要读模型由压测和查询需求决定 |

## 6. 当前草案中需要修正的三个点

1. **模型、工具和知识库不能只藏在 Revision 的 JSON 里。** 产品需要配置页面、资源反查和发布校验，因此建议增加引用关系；JSON 仅作为发布后的运行配置快照。
2. **文件不应一律按个人拥有。** 知识库和 Skill 是产品级资源，建议使用 created_by 表达创建人；用户附件另行建关联。
3. **Agent Revision 不等于知识内容版本。** Revision 可以固定检索配置和知识库引用，但知识库文档可能继续更新。是否冻结知识内容需要独立决定。

## 7. 暂时保留的产品决策

| 编号 | 决策问题 | 当前建议 |
|---|---|---|
| R1 | 一个 Agent Definition 是否代表一个面向用户的 Master Agent？SubAgent 是否需要可复用预设？ | 一期先把 Master 定义作为产品入口；预设 SubAgent 配置放入 Revision 快照，暂不单独建 SubAgent 表。运行时自主创建的 SubAgent 由 AgentScope 管理。 |
| R2 | Agent 是否允许配置多个模型用途？ | 一期一个 Revision 配置一个主模型，直接使用 model_ref；确有多模型需求时再增加 agent_revision_model_ref，并通过 model_role 区分用途。 |
| R3 | 知识库是否需要版本化、历史会话是否固定知识版本？ | 一期先动态知识库；如需历史可复现，再引入 KB Revision。 |
| R4 | 工具实现升级是否需要版本化？ | 配置快照固定非敏感工具配置；工具代码版本由应用发布版本追踪。若需独立回滚，再引入 Tool Revision。 |
| R5 | 一期是否支持用户上传聊天附件？ | 未确认前不增加 Conversation-File 关联。 |
| R6 | 用户认证和角色体系如何实现？ | 先确认认证来源和管理员/普通用户角色边界，再决定 identity、role 等实体。 |

## 8. 评审通过后再做的工作

1. 根据本关系图确认最终实体范围，删掉不需要的一期资源模块。
2. 为每个实体补齐字段、逻辑类型、必填、默认值、唯一约束和索引需求。
3. 确认配置版本、资源删除、会话归档/删除、文件清理的生命周期。
4. 最后选择数据库和存储技术，生成 DDL。
5. 数据结构确认后，再以用户操作流程为基础设计 HTTP API 请求/响应，不直接照搬 AgentScope SDK 方法名。

## 9. 官方依据

- [AgentScope Harness 架构](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/architecture.md)
- [AgentScope Session Log、Turn/Run、事件和恢复](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/session-log.md)
- [AgentScope Workspace](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/workspace.md)
- [AgentScope Memory](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/docs/v2/zh/docs/harness/memory.md)
- [AgentScope JDBC 方言和表初始化](https://github.com/agentscope-ai/agentscope-java/blob/v2.1.0-BETA1/agentscope-extensions/agentscope-extensions-jdbc/src/main/java/io/agentscope/extensions/jdbc/dialect/AbstractJdbcDialect.java)

> 本文是关系评审稿，不代表以上所有实体已获最终批准。未确认项保持开放，不提前固化为 DDL。
