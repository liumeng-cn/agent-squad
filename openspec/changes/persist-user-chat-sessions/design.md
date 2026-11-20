# Design

## Context

动机和范围见 proposal.md。以下为已核查的现状，不代表已经实现持久化：

- `server/v2-server/.../service/ChatSessionService.java:24-52` 从 preview JSON 返回列表、建档和改名结果，没有 Mapper；`ChatSessionPO` 明确是未启用的候选结构。
- `ChatAgentConfiguration.java:64-75` 只配置本地 workspace，JDBC 与远端文件系统被注释。`ChatRuntimeService.java:30-51` 已按用户和会话打开原生句柄，但未校验目录归属。
- `ChatSessionConverter.java:40-101` 通过 SessionLog 和 Inbox 生成公开快照；这是按请求计算的内存视图，不是需要新增的持久消息投影。`ChatEventStreamService.java:59-72` 以双游标和租约有效性轮询，读取不要求启动执行。
- 前端 `ChatView.vue:37-52` 已使用 sessionId 路由，但 GET 只使用 summary；正文等待 SSE。`useChat.ts:125-164` 已有先建档后发送、稳定 interactionId 与不确定提交保留逻辑。
- `AguiSessionTest.java:80-119` 已有可选 PG 跨工作区测试，但没有真实目录、进程重启和全链路归属测试。
- 主规格 `v2-chat` 约定原生提交与显式继续；`design-chat-workspace` 的 JSON-only 是前一阶段限制。本变更接续该范围，不改写旧变更，不采用归档 server 的任务调度架构。

## Goals / Non-Goals

**Goals:**

- 业务目录和原生执行各自拥有唯一权威，避免会话保存引入跨存储双写补偿系统。
- 所有恢复必需的数据在共享 PostgreSQL；同一身份在不同进程、不同工作目录定位同一历史。
- 列表、详情、SSE 仅观察；显式发送或继续才触发执行。

**Non-Goals:**

- 不保存第二份完整消息，不做后台同步、自动标题生成、最近活动排序索引、全文搜索或自动接管。
- 不承诺进程中的线程、HTTP 请求、工具内部进度或未提交 token 恢复；不新增未知工具结果的处理 UI。
- 不导入旧本地历史，不自动接管旧 SDK 命名空间，不清理真实数据；不整体修复归档部署和旧设计校验脚本。
- “不存本地”约束针对会话、记忆和恢复数据；随应用发布的只读资源与进程内缓存不是恢复依据。不以本方案宣称 JVM 和第三方工具完全零磁盘 IO。

## Decisions

### 1. 原生存储与业务目录分开，避免双写消息

业务目录使用 PostgreSQL 的 `squad_chat.sq_chat_session`，经 Service、MyBatis-Plus Mapper 与 XML 访问。SDK 表保持固定版本官方结构，经公开 API 访问，禁止在业务 SQL 中解析 agentscope_store 或更新 journal。

SDK 连接使用 v2 自有 schema `squad_chat` 与默认 `agentscope_` 表前缀；部署显式创建该 schema 并令 v2 连接默认 schema 与其一致，避免校验或建表误指向 public。原有用户服务 DataSource 不变。SDK 基础表为 agentscope_store、agentscope_sessions、agentscope_snapshots；本期原生 EVENT_LOG 恢复以 journal 为准，不能因 agentscope_sessions 名称而把它当产品会话目录。

业务表字段：

| 字段 | 类型/约束 | 职责 |
| --- | --- | --- |
| id | BIGINT identity，主键 | 内部存储标识，不作为 SDK sessionId |
| session_id | VARCHAR(64)，非空 | 沿用现有客户端 UUID/字母数字连字符契约，不在本期改为数值 ID |
| user_id | BIGINT，非空 | 服务端登录身份，不接受请求体指定 |
| agent_id | VARCHAR(80)，非空 | 建档时固定，重启后不得用另一个 Agent 静默替换 |
| title | VARCHAR(120)，非空 | 默认“新对话”，支持手动改名 |
| title_source | VARCHAR(16)，AUTO/MANUAL | AUTO 本期仅表示默认标题，不调用模型自动命名 |
| version | BIGINT，默认 0 | 仅标题编辑版本，不表示 SDK 执行版本 |
| creator_id、creator_name、create_time | 按公共审计约定 | 创建人及创建时间 |
| last_updater_id、last_updater_name、last_update_time | 按公共审计约定 | 最近业务修改人及时间 |

唯一约束为 `(user_id, session_id)`，列表索引为 `(user_id, create_time DESC, id DESC)`。时间使用 TIMESTAMPTZ(3) 与 UTC。候选 PO 中的 pendingInitialInputId、firstInteractionId、执行水位、状态检查时间、删除标记等不原样复制；本期不提供删除和后台核对，故不存这些字段。

hasMessages、lastActivityAt、execution 不落业务表，通过原生日志按请求计算。这避免了“SDK 已接收但元信息未更新”导致需要补偿写入的窗口。相比把所有消息搬到业务表，代价是读取成本更高，收益是权威和失败语义简单。

### 2. 显式绑定远端 journal，不能只打开 distributedStore

使用 `AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build()` 完成官方 schema 校验，再通过 `JdbcDistributedStore.create(dataSource, dialect)` 装配。已核查 SDK 的 autoCreateTable(false) 仍会校验表，缺表直接失败，不降级本地；不使用默认会执行 DDL 的 create(dataSource) 作为生产启动入口。

journal 经 `WorkspaceSessionLogStore` 显式绑定到无本地索引的 `RemoteFilesystem`，再通过 `sessionLogStore(...)` 注入 Harness。该文件系统基于官方 BaseStore 的 CAS；使用固定版本命名空间，例如 `squad/chat/journal-v1/users/<规范化userId>`，具体分段由 NamespaceFactory 返回列表而非手工拼接路径。SDK SessionKey 继续包含 userId、agentId、sessionId；不得加入进程 ID、随机启动值或工作目录。

主 Agent 工作区通过 `abstractFilesystem(RemoteFilesystem)` 使用独立、稳定的用户/Agent/会话命名空间，恢复依赖的记忆和文件不得回落到 LocalFilesystem。不要同时设置 RemoteFilesystemSpec：该入口会打开本地 SQLite WorkspaceIndex，且默认路由与 sessionStorage 的 agents/ 前缀并非天然一致。

原生 SDK 公开路径已核实：WorkspaceSessionLogStore.open、RemoteFilesystem.sessionStorage、StoreSessionStorage.compareAndSet。沿用 EVENT_LOG，不转为 LEGACY 状态快照，也不自建 AtomicSessionStorage 或拷贝内部 SQL。

子 Agent 显式 journal 会由 SDK 的 sessionHistoryConfigurer 向下传递，但 ISOLATED 声明不会自动继承父 abstractFilesystem。实施时必须验证现有 general-purpose、markdown-tester、java-tester 三条路径：保留父用户/会话隔离和角色，使用公开的、接收父 RuntimeContext 的 SubagentEntry 工厂与 middleware 接入，为保留的子 Agent 绑定远端 workspace/journal；不得用 SHARED 模式绕过隔离，不能只替换主 Agent 后宣称完成。Markdown 定义从 classpath 读取，不再把启动复制文件当生产数据源；本期不提供可编辑角色资源管理。

这部分仅是现有聊天调用路径的存储适配，不引入跨实例子任务接管。若固定 BETA SDK 的公开接口无法保留现有子 Agent 行为并消除恢复数据的本地依赖，停止该实施任务并报告具体阻塞，不修改 SDK 内部实现或扩大到新 Agent 架构。

### 3. 目录稳定分页，运行信息即时读取

本期按创建时间倒序，不按最近聊天活动排序。理由是创建时间是业务事实且不会移动，能够直接索引分页；最近活动来自 SDK，如要大规模按其排序需要新增持久摘要/同步机制，本期不引入。lastActivityAt 保留在响应中用于展示，但前端不得按该字段重新排序分页结果。

cursor 使用有版本的 Base64URL 编码，包含当前用户、排序规则版本及上一页末条 `(createTime, id)`；服务端校验格式、大小、字段类型和用户匹配，并始终在 SQL 追加当前 user_id。cursor 不是访问凭证，不接受其中用户覆盖登录身份。limit 默认 30、范围 1..100；SQL 读取 limit+1 条决定 hasMore。

只读取当前页目录关联的 SDK 会话，不调用全库原生会话发现接口。按已提交执行前缀和 Inbox 构建摘要：

- hasMessages：是否存在已持久接收的用户输入，不能以目录存在或页面草稿为依据。
- lastActivityAt：最后一条已接受 submit/steer 的 Inbox 事件 occurredAt；不使用客户端时间、租约续期时间或最新任意 journal 事件。无输入使用 create_time。SessionInbox.Command 没有时间字段，应经公开 Inbox 日志读取事件时间，不臆造 getter。
- execution：复用公开状态转换；observedAt 为本次观察时间，成功读取 stale=false。已有 UNKNOWN 用于无法识别的新状态，不把读取异常吞成 IDLE；存储失败返回服务故障。
- 列表、GET 和 SSE 中 summary 的含义一致，标题版本来自业务目录。只在当前请求内复用扫描结果，不持久化执行状态或增加后台刷新器。

### 4. 现有 API 形状保留，补充实际语义

所有业务 JSON 仍为 ApiResult，成功 code=0。业务 400/404/409 使用 HTTP 200；认证沿用现有 401/403，数据库或服务故障使用 HTTP 503。SSE 成功返回原始事件流，归属拒绝必须在返回 Flux/提交事件响应前完成，由统一异常处理返回 JSON；前端订阅入口须检查 Content-Type 与错误包装，不能无限重连业务拒绝。

| 接口 | 请求 | 成功数据及关键行为 |
| --- | --- | --- |
| POST /api/v2/chat/sessions | `{sessionId}`，匹配 `[a-zA-Z0-9-]{1,64}` | SessionSummary；幂等建档，提交业务事务后返回，不创建 SDK 运行 |
| GET /api/v2/chat/sessions | limit、cursor | SessionPage：items、nextCursor、hasMore、serverTime |
| PATCH /api/v2/chat/sessions/{sessionId} | `{title,version}` | 更新后的 SessionSummary；空白/超长标题 400，版本冲突 409 |
| GET /api/v2/chat/sessions/{sessionId} | 路径标识 | ChatSessionVO，必须包含对应 summary 与原生公开历史 |
| GET /api/v2/chat/sessions/{sessionId}/events | 路径标识 | 初始完整快照及双游标更新、心跳；不启动执行 |
| POST /api/v2/chat/messages | 保留 sessionId、interactionId、observedTurnId、message | 原生收据；新增目录归属前置条件 |
| POST interruptions / resumptions | 保留现有 DTO | 归属校验后调用原生操作，不增加自动恢复分支 |

SessionSummary 保留 sessionId、title、titleSource、version、hasMessages、createdAt、lastActivityAt、execution。ChatSessionVO 保留 sessionId、summary、acceptedInputIds、cursor、inputCursor、turnId、runId、status、messages、inputs；不向外暴露存储主键和 namespace。现有工具/消息公开字段不扩张。

鉴权放在 Service 边界；ChatSessionService 只处理目录与业务规则，ChatRuntimeService 校验目录后打开原生会话并组装快照。列表编排共享快照读取和纯转换逻辑，避免 ChatSessionService 与 ChatRuntimeService 相互注入。Mapper 不调用 SDK，Converter 不调用数据库；业务摘要组装由 Service 完成。

agent_id 必须与可用 Agent 配置一致；如果配置被换成另一 Agent，则明确返回服务配置不可用，不为旧 sessionId 开新 Agent 历史。未来多 Agent 注册表不在本期引入。

### 5. 事务、幂等与失败窗口

| 场景 | 处理 |
| --- | --- |
| 同一用户重复/并发创建 | 以唯一约束兜底；显式 INSERT ON CONFLICT DO NOTHING 后读取现有记录，不覆盖标题；短业务事务，不含 SDK 或模型调用 |
| 两页面并发改名 | 条件 UPDATE 包含 user_id、session_id、version；递增 version；未更新时区分不可访问 404 与旧版本 409 |
| 创建提交成功，SDK submit 失败或未知 | 保留空目录；客户端保留稳定 interactionId 和草稿，核对原生 acceptedInputIds，不删除目录或自动重发 |
| SDK 已接收但 HTTP 回执丢失 | GET/SSE 直接读取原生输入收据；因为没有消息与执行摘要双写，无需补偿消息表；同键 submit 仍遵循 SDK 幂等 |
| 创建/改名已提交但响应摘要读取失败 | 返回故障但不回滚已提交业务事实；创建可重试，改名通过重新读取版本核对，不重试覆盖 |
| 数据库不可用或 journal 损坏 | 返回服务故障，不退回本地、preview 或伪造空历史；不隐式重新初始化已有会话 |

本期不创建平台消息命令表；沿用 SDK 持久命令作为聊天接收事实。未来业务操作跨事务的 outbox/幂等设计另行提出，不能借此改造当前发送链路。

### 6. 只读返显与主动执行分离

已完成历史：GET/SSE 仅构造句柄、读取固定提交前缀与 Inbox 并转换；下次主动 submit 时由 SDK 从 journal 恢复上下文。不能用历史正文拼一遍输入来“恢复”。

未完成历史：RUNNING 但原生 owner 缺失或租约已过期时沿用现有 INTERRUPTED 展示，仅改变公开视图，不回写原生日志。租约有效时可能仍有其他实例执行，不能以本 JVM 不存在任务为依据判停。服务时钟须同步，不增加平台租约算法。

已接收但未开始的输入保持 QUEUED，页面提示“已接收，尚未开始”；只读打开不会令它启动，本期不添加扫描器、定时唤醒或新队列恢复 API。显式继续接口只处理 SDK 准入允许的轮次，未知工具结果或待答交互仍拒绝，不绕过恢复保护。不能把“历史可见”报告成“故障任务已恢复执行”。

应用启动不遍历目录调用 AgentSession.start/resume。SDK 提交产生的执行生命周期与页面订阅继续分离，保留现有停止、补充和显式继续入口。

### 7. 前端先展示详情，再合并订阅

保留 `/chat/:sessionId` 作为当前保存会话的定位来源；不带 ID 的 `/chat` 保持新对话，最后会话 localStorage 不作为自动跳转依据。清除浏览器缓存后，目录仍可找回旧会话。

首发顺序为：生成 sessionId → 成功建档 → 切换持久路由 → 提交原生输入。要处理当前组件因路由替换卸载的情形，提交和已保存的 pending 不得因组件销毁丢失。建档成功但提交失败保留会话和草稿。

ChatView 的 GET 完整快照必须交给 useChat 应用，而不是只提取 summary。SSE 使用同一 apply 入口；会话不匹配或任一游标倒退时丢弃，相同双游标的租约状态变化可以更新。标题使用独立 version 防止晚到 SSE 覆盖较新手动改名；不得因标题未进入原生日志而修改其事件游标。

详情、列表、改名与提交回执统一校验当前 userId 和选择代数，修补目前 rename 缺少身份代数保护的路径。404 返回新对话并明确提示；503 保留旧消息并允许重读；409 重载标题版本后由用户决定下一次修改。清空页面只切换新会话，不删除原生记录。

运行接口移除 preview 分支；原 preview JSON 可作为测试资源保留，不从其他路径绕回真实接口。不改视觉布局，不新增导入、删除或搜索入口。

## Risks / Trade-offs

- [Risk] 固定 BETA SDK 在不同 filesystem/子 Agent 路径可能回落本地 → 使用明确 journal 注入和隔离验证，在实施门槛中检查主/子 Agent 的数据库记录、工作区文件与换目录恢复，未通过不宣称可分布式。
- [Risk] 原生日志和列表页即时扫描开销随会话增长 → 每次只访问当前页，保持现有订阅游标去重；本期接受有限数据规模下的成本，记录性能结果，不为了优化偷偷新增持久消息投影。
- [Trade-off] 创建时间排序而非最近活动排序 → 首页刷新发现新建会话，lastActivityAt 仅显示；如需按最近聊天排序，后续单独确认持久摘要与一致性策略。
- [Risk] 将恢复历史误认为自动恢复任务 → 用已完成、运行中强杀、已接收未开始三组场景明确验收；特别说明 QUEUED 在重启后可能等待后续人工处理。
- [Risk] 当前 deploy 脚本含 archive 路径、旧迁移缺失 → 新增独立 v2 migration 集合和对应校验，不复用无法完整执行的旧归档链，不删除/重写旧迁移来令测试通过。
- [Risk] 原生日志没有已验证的安全清理策略 → 本期不清理、不按时间删除 KV；容量与保留策略留待后续独立工作。

## Migration Plan

1. 新建 v2 自有 `server/v2-server/src/main/resources/db/migration/V1__baseline.sql` 与 `db/schema.sql`，包含会话业务目录和固定版本官方 SDK 表结构；表与字段补齐中文职责注释，不修改 SDK 字段类型/主键。
2. 在 `deploy/db/migrations/v2-server/` 建立独立的 v2 初始化/增量迁移集合，不把其 V1 混入归档 V5-V10 链。扩展 tools/services.py 的显式 init-db 入口，使初始化用户库和 v2 库有清晰边界，不在正常启动重复执行 DDL。
3. 基线用于新环境。针对真实环境中可能残留的候选表或同名 SDK 表，先做只读结构比对；已有业务数据不兼容时停止并给出差异，未经单独授权不清表或覆盖。部署 SQL 由用户决定执行，规划阶段不操作真实数据库。
4. 应用启用共享存储与归属校验，前端同步上线。旧本地会话不导入，保留原文件和旧库数据；正式目录从新建档会话开始，不以程序扫描自动补目录。
5. 验证新进程、不同工作目录、相同用户/Agent/sessionId 能返显同一历史；停机重启和强杀测试只针对专用验证进程，不能终止用户的 IDE 服务。
6. 回退时停止新写入并保留数据库/文件，不能把新版远端会话静默切回旧本地版本；选择仍能读取同一远端格式的应用版本或暂时关闭入口，不做降级删表或自动逆迁移。

## Verification

- 扩展 ChatHttpTest：真实目录创建、同键并发、两用户同 sessionId、越权的全部入口、分页游标、改名冲突、错误包装和 SSE 拒绝。
- 扩展 AguiSessionTest 与既有 ScriptedChatModel：使用真实 PostgreSQL 和真实 Harness，统计模型/工具调用次数；读取前后 journal head/Inbox 游标不因观察增长，重启后下一次主动发送确实带原上下文。
- 扩展 ChatProjectionTest、ChatEventConverterTest 与 NativeSteeringTest：消息顺序、稳定 ID、双游标、已接收未开始、中断/继续和工具状态不回归；不为新方案重写这些已有协议。
- 扩展 TestSubagentDefinitionsTest：保留三类角色可调用与用户隔离，验证 journal 和恢复依赖文件不落本地；旧“启动复制可编辑模板”断言调整为 classpath/远端路径断言，不新增角色管理功能。
- 增加 v2 schema parity，分别在新临时库和带已存在表的测试库比对基线与部署迁移；验证 autoCreateTable(false) 在缺表时失败且不创建对象。
- 真服务 + curl 验证业务 CRUD、错误、分页和只读历史；浏览器验证首发、刷新、切换会话、重启、清本地缓存、身份切换、并发改名和断网重连。真实模型仅在获得相应授权时调用，否则使用已有隔离脚本模型验证并标明未做真实模型验收。
- 执行模块测试和前端构建；受环境约束跳过的 PG 或浏览器验证必须单列，不能用编译成功代替功能验收。
