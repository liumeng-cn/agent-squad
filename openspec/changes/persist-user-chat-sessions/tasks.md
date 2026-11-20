# Tasks

以下任务仅在用户明确进入 apply 后实施。顺序为数据库契约 → 远端 SDK 接入 → 目录与鉴权 → 历史返显 → 前端 → 集成验收；规划完成不代表任务已执行。

## 1. 数据契约与显式初始化

- [ ] 1.1 按 design.md 的最小目录字段精简 v2 ChatSessionPO，新增 v2 `db/migration/V1__baseline.sql` 和 `db/schema.sql`，保留业务 ID、用户与 Agent 关联、标题版本及公共审计字段；唯一约束和创建时间索引与规格一致，不新增消息/执行投影表。验收：DDL 在专用 PostgreSQL 测试库执行通过，字段/枚举/中文注释齐全，用户与会话唯一性测试通过。
- [ ] 1.2 将固定 SDK 版本的官方基础表纳入 v2 schema，并新增 `deploy/db/migrations/v2-server/` 独立迁移集合与 v2 schema parity 验证；不改归档迁移链、不操作已有真实数据。验收：空测试库与已存在兼容结构的测试库得到相同结构，不兼容结构明确报差异，不自动删除或覆盖。
- [ ] 1.3 扩展 tools/services.py 的显式 init-db 入口并补充现有部署使用说明，区分用户 schema 和 v2 schema；配置 v2 连接默认 schema，不修改用户服务连接身份或秘密。验收：专用库初始化可重复，正常启动不执行 DDL，已有用户数据与表结构保持不变。
- [ ] 1.4 更新 api/openapi.json 的 v2 会话创建、分页、改名、详情和 SSE 错误契约，以及 SessionSummary/ChatSessionVO 字段含义；明确创建时间排序、空历史、404/409 与幂等创建。验收：JSON 可解析，Java/TypeScript/OpenAPI 的有限值与字段一致，不扩展归档 API。

## 2. SDK 原生远端存储

- [ ] 2.1 在 ChatAgentConfiguration 使用官方 dialect 的 autoCreateTable(false)、JdbcDistributedStore 与显式 WorkspaceSessionLogStore 绑定 PostgreSQL，固定用户/Agent/会话命名空间，保留 EVENT_LOG。验收：缺表启动失败且没有 DDL/本地回退；AguiSessionTest 证明提交后的输入、journal 和检查点在 SDK 远端存储可读。
- [ ] 2.2 主 Agent 通过纯 RemoteFilesystem 装配恢复依赖的工作区；适配现有三类子 Agent 的远端 workspace/journal，使用带父 RuntimeContext 的公开工厂保持隔离，Markdown 定义从发布资源读取，不扩大到角色管理或 SDK 修改。验收：扩展 TestSubagentDefinitionsTest 验证三个角色、同 sessionId 不同用户、不同父会话均隔离，主/子恢复数据不落本地；公开接口存在阻塞时记录具体原因并停止，不改为 SHARED 绕过。
- [ ] 2.3 扩展现有 AguiSessionTest，使用真实 PG、ScriptedChatModel 和不同空工作目录打开同一身份会话，再主动提交下一轮。验收：历史和下一轮上下文正确，读取阶段模型/工具调用数不增长，journal 和 Inbox 游标不因读取推进；测试明确启用 PG，不能以条件跳过计为通过。

## 3. 用户目录与全链路归属

- [ ] 3.1 在 ChatSessionService 与新增 ChatSessionMapper/XML 实现真实建档，使用当前用户和固定 Agent；短事务 INSERT ON CONFLICT 后读原记录，不调用模型或重写已存在标题。验收：扩展 ChatHttpTest 或对应 PG 集成测试，覆盖并发同键创建、响应丢失重试、两个用户相同 sessionId 和创建后尚无消息。
- [ ] 3.2 实现目录分页和游标校验，按 create_time/id 倒序查询，limit+1 判定下一页；只访问当前页的原生会话并即时计算摘要，不存 hasMessages、执行状态或 lastActivityAt 副本。验收：多页无重复、翻页期间新增不挤占、空列表/非法 limit/损坏与跨用户 cursor 正确，改名和聊天不改变排序。
- [ ] 3.3 实现标题规范化与乐观版本条件更新，更新归属用户的审计字段；成功返回最新摘要，冲突不得覆盖他人修改。验收：空白/超长标题 400、不可访问 404、两个页面只有一个成功且另一个 409，重启及创建重试仍保留手动标题。
- [ ] 3.4 在详情、发送、SSE、停止和继续的 Service 入口统一目录归属校验，移除 preview 运行分支；SSE 在提交流响应前拒绝，Agent 配置不匹配时明确失败。验收：逐个入口用第二个用户/不存在会话验证 404 且无 SDK 读取或执行，SSE 不先发事件，认证及正常聊天回归通过。

## 4. 历史返显与只读恢复

- [ ] 4.1 复用 ChatSessionConverter 与原生 SessionLog/Inbox 读取，GET 和 SSE 填充业务 summary、稳定消息 ID、双游标和允许的公开过程；摘要时间来自输入接受事件，不解析内部 KV。验收：扩展 ChatProjectionTest/ChatEventConverterTest，覆盖空历史、submit/steer、重复收据、消息顺序和敏感字段不泄露。
- [ ] 4.2 保留共享订阅与游标去重，协调标题版本与原生日志游标；读取失败保持服务故障，不能回退 mock/本地或变成空成功。验收：断流重连、相同双游标租约变化、GET/SSE 交错与数据库暂不可用不重复消息、不错误标记执行成功。
- [ ] 4.3 验证重启只观察不执行：在专用实例构造已完成、已接收未开始、执行中退出三类会话；保留 QUEUED 与已提交正文，失效租约仅影响公开中断状态。验收：列表/GET/SSE 不调用 start/submit/resume、不增加模型或工具次数，显式继续仍走 SDK 准入；扩展 NativeSteeringTest 或关联恢复用例验证未知工具结果不被自动重试。

## 5. 前端保存与刷新返显

- [ ] 5.1 调整 ChatView/useChat 的首发流程与已建档标记，建档成功后保持 sessionId 路由，详情完整快照先进入现有 apply 再订阅；处理路由替换卸载和建档/发送结果未知，不丢草稿或稳定 interactionId。验收：浏览器首发、创建回执丢失、提交回执丢失、刷新同一路由均无重复提交，SSE 尚未建立时已有消息可见。
- [ ] 5.2 调整 sessions store 依服务端创建顺序分页，不按 lastActivityAt 重排；默认标题和改名真正保存，409 重新取最新版本；删除演示目录展示分支但保留页面布局。验收：浏览器新用户空列表、分页、改名刷新、两个页面冲突正确，前端构建通过。
- [ ] 5.3 统一详情、列表、改名、发送和订阅的用户/会话代数保护；订阅解析业务 JSON 拒绝并停止无效重连，404 回新对话、503 保留已显示历史。验收：浏览器快速切换会话、退出换号、晚到响应、清缓存重新登录和断网恢复不串数据；现有停止、补充、显式继续、清空页面与草稿行为不回归。

## 6. 集成验收与交付

- [ ] 6.1 使用专用验证进程与真实 PG，执行真服务 + curl 和浏览器联调：保存两轮对话、改名、刷新、重启后列表/详情返显，再主动发送验证原上下文；额外用不同工作目录实例只读历史。验收：记录用户隔离、消息 ID/顺序、无旧目录依赖和读取零执行证据；未经授权不调用真实模型或停止用户 IDE 进程，无法完成的验证明确列出。
- [ ] 6.2 运行 `mvn -f server/pom.xml -pl v2-server -am test`、目标 PG 测试及 `npm --prefix web/workbench run build`，通读本次涉及的类和前端状态流。验收：各阶段测试通过、跳过项单列，静态检查无生产 JDBC SQL/内部 SDK KV 访问，不新增消息投影、后台接管或无关改动。
- [ ] 6.3 执行 `openspec validate persist-user-chat-sessions --strict` 并核对设计中的迁移与回退步骤，确认所有实现任务有可复核证据。验收：规格与真实行为一致、无 mock 回退、旧数据未被自动导入或清理，SDK 容量/自动接管等非目标不被描述成已完成。

## Workflow follow-up

- 本期只创建规划；等待用户明确要求 apply 后实施，全部验收完成后再由用户决定归档。
- 归档同步前处理 design-chat-workspace 的旧 JSON-only 差量，避免其后续归档覆盖正式 chat-sessions 规格；不在本次规划中修改旧变更或标记旧任务完成。
