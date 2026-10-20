# AgentScope Java 适配调研

2026-10-20 调研快照；其中“旧草稿处置/下一步”记录调研时状态，后续已由 ADR-003 和 v0.2 设计落实替换，现行规则见运行协议。需求保持 [R01–R12](../overview.md) 不变；本轮只查官方文档、发布记录及源码，不安装框架、不启动服务、不做模型或故障实验。

## 1. 结论

建议以 **Java + AgentScope Harness SDK** 为主。Master/Worker 用 HarnessAgent 和 Subagent 组织，使用框架现成的推理、工具、上下文、Skill 与状态机制。平台补业务身份、授权、凭据、不可重复操作治理及产品 API。Python 不再是必须部署的 Agent 运行时，后续按工具需要引入。

但当前不能声称“稳定版已经完整覆盖生产级恢复”。下面的版本差异是设计前提；框架能力与平台责任必须重新分界，不能保留原手写引擎后再套一个 SDK。

## 2. 调研证据与版本

以官网 v2 文档为主要入口，以发布标签核对可用性。在线文档随分支变化，不能只看示例中的版本号。

| 对象 | 核对结果 | 使用限制 |
| --- | --- | --- |
| v2.0.4 | GitHub 正式 Release，2026-10-08；标签提交 `0b6743b263ba8e39eb417e35048026f1e0196548` | 稳定能力比较基线，不代表本项目已构建通过 |
| v2.1.0-BETA1 | GitHub Pre-release；标签提交 `5451c73abe47053340683a1cc6d67b290b056648` | 新会话机制的固定评估候选，不能当作已验证生产基线 |
| main | 本轮克隆提交 `73cb2da4a33b05be170ae8c43027009b8f8aff12`，POM 为 `2.1.0-SNAPSHOT` | 仅辅助理解，不作为可重复构建依赖 |
| 官网 Release Notes | 源文件仍把 2.0.4 标为 release draft，但 GitHub 已有正式发布 | 发布状态取 Release 页面与标签；这也证明文档需版本核对 |

关键文件存在性：`AgentSession.java`、`JournalSessionLog.java`、`AtomicSessionStorage.java` 在 v2.1.0-BETA1 标签存在，v2.0.4 树中不存在。因此官网 `session.submit/resume/start`、durable inbox、原生日志等新接口不能写成 v2.0.4 已提供。

本轮执行了官方仓库只读克隆、标签获取、文件与实现检索；未编译上游、未解析 Maven Central 制品、未运行其测试。标签存在不等于所有 Maven 模块都可获取，实施前仍须验证。

## 3. SDK、Service、Runtime 的区别

| 名称 | 作用 | 本项目建议 |
| --- | --- | --- |
| AgentScope Core / ReActAgent | 消息、模型、推理与工具循环、权限、中间件 | 随 Harness 使用，不自己重写循环 |
| HarnessAgent | 在 Core 上组织 Workspace、Skill、记忆、上下文、子智能体、会话 | 作为主要嵌入式框架 |
| AgentScope Service | 完整平台，包含托管 Agent、Team、Workflow、任务及控制台等 | 参考设计；不整套引入再重复建设自己的平台 |
| 独立 AgentScope Runtime 项目 | 部署与沙箱等运行基础设施 | 需要具体功能时再对照选用，不与 Harness 混为一个依赖 |

[官方入口](https://java.agentscope.io/v2/en/intro) 区分 Harness SDK 和 Service；[Service 协作说明](https://java.agentscope.io/v2/en/service/orchestration) 区分 Subagent、Team 与 Workflow。Service 的可视化 DAG 或 Team 不能当作嵌入 Harness SDK 后自动拥有的能力。Service 当前发布路线包含其他语言和独立存储组件，不能简单称为“全 Java + MySQL 的现成平台”。

## 4. 需求逐项覆盖

“可复用”表示查到机制，仍须满足项目验收；“需补充”表示框架不替代产品规则。涉及新 Session 的条目以 BETA1 为评估对象。

| 需求 | 框架依据与可复用机制 | 平台需要补充 / 未证实部分 |
| --- | --- | --- |
| R01 身份与隔离 | RuntimeContext 身份维度、Workspace 命名空间、Subagent ISOLATED | 登录身份必须由服务端注入；会话归属、连接资源授权、文件下载鉴权不能由模型传入 userId 决定 |
| R02 持久受理 | BETA1 AgentSession.submit 带幂等键、durable inbox | HTTP 回执与 turnId 对齐；启动发现、容量与排队策略需设计，不能受理后关闭 Agent 实例 |
| R03 主协调 | HarnessAgent 作为 Master，Subagent 声明、agent_spawn/agent_send | 能力目录、结构化委派输入输出、选择与验收策略；不是仅改角色提示词 |
| R04 上下文 | Harness 上下文装配、预算/压缩、ContextSource；子智能体独立会话 | 精确裁剪与脱敏；ISOLATED 是工作区/状态隔离，不自动证明输入最小化 |
| R05 Skill 自动使用 | 描述进入发现范围，按需 load_skill_through_path，支持仓库与个人目录 | 上传安全、归属、版本固定、依赖与工具权限；不得按全局共享目录暴露私有 Skill |
| R06 串并行协作 | 同步/后台子任务、结果回传、等待与取消工具 | 强制 DAG、ALL_REQUIRED/BEST_EFFORT、重规划失效和结果复用不是通用原子保证，需确定性协作规则 |
| R07 追问和审批 | 框架 ASK/HITL；BETA1 pending/respond 支持挂起继续 | 业务审批绑定参数、版本和权限；回复时重新授权，不默认接受永久放行建议 |
| R08 容错恢复 | 状态存储/CAS；BETA1 会话租约、journal、检查点与不确定工具核对 | 后台本地子任务接管有缺口；唤醒扫描、接管策略、连接恢复及写操作幂等需要补齐和实测 |
| R09 长期记忆 | MEMORY.md + 日志、压缩、检索及外部记忆扩展 | 一期保持用户显式记忆；纠正、删除、来源、版本与删除后不再召回，不能完全采用默认自动提取 |
| R10 增量与回放 | 原生事件、子事件来源；BETA1 committed SessionLog | 用户端快照/SSE 是应用层，过滤子 Agent 正文、敏感工具结果和思考内容；实时事件不等于已持久提交 |
| R11 取消 | 中断、后台 task_cancel；BETA1 Turn 控制 | 不自动回滚外部操作；取消意图持久化、阻止后续派发及迟到结果规则必须落实 |
| R12 能力扩展 | 文件/代码/factory 声明子 Agent，可接远程 Agent | Worker 注册和契约版本、权限、能力准入；不新增 Master 业务硬编码 |

参考：[Subagent](https://java.agentscope.io/v2/en/docs/harness/subagent)、[Skill](https://java.agentscope.io/v2/en/docs/harness/skill)、[Context](https://java.agentscope.io/v2/en/docs/harness/context)、[Permission](https://java.agentscope.io/v2/en/docs/building-blocks/permission-system)。以上覆盖判断是针对本项目需求的分析，不是官方对我们的应用作出的保证。

## 5. 恢复的真实边界

### 5.1 会话恢复不是整个任务自动续跑

官网区分 Session、Turn、Run：Session 是会话，Turn 是一个逻辑请求，Run 是一次执行；恢复可以产生新的 Run，保持原 Turn。原草稿“一次目标一个 Run”的命名不能照搬。

BETA1 的 AgentSession 提供后台命令和显式恢复入口；读取历史不会执行推理。应用仍需发现待处理会话、在租约允许后启动调度或恢复。`resume` 不能恢复线程或正在进行的网络请求，且存在 uncertain tool 时先核对。

源码验证点：BETA1 `AgentSession.resume` 检查 uncertainToolCalls；`AtomicSessionStorage` 要求原子 CAS 和持久确认；`JournalSessionLog` 使用租约和 epoch。框架租约存在本地时钟使用，生产需校时、时钟偏差和网络分区实验，不能宣称沿用原稿的数据库时间方案已成立。

### 5.2 子任务共享可见不等于接管执行

v2.0.4 和 main 的 `WorkspaceTaskRepository` 源码均明确：本地执行粘在发起节点，其他节点可读持久状态；孤儿扫描会将超时本地任务标记 FAILED，而不是重建正在运行的 CompletableFuture。远程任务另有传输与恢复机制，不能由此推断本地任务可接管。

因此恢复主线需要一个明确责任人：建议先定义窄的 RecoverableWorkerDispatcher，负责稳定逻辑任务键、执行归属、到期恢复与结果投递，内部继续用 HarnessAgent 执行。它不能再实现模型/工具循环；也不能与框架 TaskRepository 同时推进同一个任务。是否适配 TaskRepository，须以固定版本接口及故障实验决定。

如果只采用稳定版，必须额外补齐其缺失的持久会话机制；不能为了少开发而降低 R02/R08。建议优先评估固定 BETA1 的恢复契约，验证后决定锁定版本或向稳定版适配，而不是直接依赖 main。

[会话恢复官方文档](https://java.agentscope.io/v2/en/docs/harness/session-log)；[稳定版后台任务源码](https://github.com/agentscope-ai/agentscope-java/blob/0b6743b263ba8e39eb417e35048026f1e0196548/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java)；[BETA1 会话源码](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)。

## 6. 上下文、记忆和 Skill 的采用方式

- Master 持有会话语义与目标；Worker 从委派输入获得局部材料。框架的压缩、工具结果卸载优先复用；平台对被注入材料做归属、权限与 token 限制。
- 长期记忆默认机制会自动抽取和合并，不符合一期“用户显式记忆”的约束。须关闭自动记忆写入路径，提供受控显式写入/纠正/删除接口，再把授权记录注入上下文。具体禁用开关必须按选定标签核对，不能只关闭 per-call flush 后留下压缩前提取或后台维护。
- 向量库仍不强制。官方默认 memory_search 是文本匹配，不能宣称已有语义检索；后续引入检索服务仍需最终授权与删除过滤。
- Skill 保持“知识/流程资料”的项目定义；其脚本走受控工具与沙箱。发现使用交给框架，不把 Skill 一对一注册成 Worker。
- Git 仓库可能随远端更新，不能直接作为运行中的可变依赖。发布时固定版本/内容摘要并按用户作用域挂载，只读分发；关闭自学习/自动发布，除非后续明确设计审核流程。

[记忆机制](https://java.agentscope.io/v2/en/docs/harness/memory)、[Skill 机制](https://java.agentscope.io/v2/en/docs/harness/skill)。

## 7. 权限、执行与存储

框架的 ALLOW/ASK/DENY 负责工具执行门禁，平台资源授权仍必须在真实工具入口校验。任何子 Agent、远程适配器、模型参数都不能扩大登录用户的权限。撤权后继续执行及审批后参数改变必须重新检查。

优先复用框架 Docker/Sandbox 接口，不预设再造 Python Runner 服务。但默认本地 shell、Docker socket、共享目录都不是可直接用于生产的安全配置；隔离执行节点、挂载、网络、资源配额、快照和清理需要明确部署策略。快照默认 Noop 时容器消失会冷启动，持久状态存储也不会自动备份所有工作文件。

JDBC 扩展提供状态、Workspace KV、快照与隔离锁；应把它管理的存储结构与平台业务表分开。MyBatis 用于平台业务数据，不强行接管框架内部 SQL。不能再让 sq_run/sq_step/sq_checkpoint 成为框架状态的第二套可写权威。

自动建表开关应关闭，由迁移建立目标标签要求的结构；JDBC 快照以 BLOB 存储的成本要评估，大文件仍走对象存储。权限注入的主体、共享命名空间、框架内部记录与模型可写目录必须隔离。

参考：[Sandbox](https://java.agentscope.io/v2/en/docs/harness/sandbox)、[Filesystem](https://java.agentscope.io/v2/en/docs/harness/filesystem)、[JDBC](https://java.agentscope.io/v2/en/integration/distributed/jdbc)。具体 API/默认值按标签复核。

## 8. 旧草稿如何处置

| 原设计资产 | 处置 |
| --- | --- |
| R01–R12、业务无关验收场景 | 保留，是框架选择的约束 |
| Python/LangGraph、Java→Python 内部协议、固定四角色部署 | 取消作为现行方案 |
| 手写推理循环、Skill 发现循环、重复上下文压缩 | 交给 Harness，不再实现 |
| 通用跨 Worker DAG 引擎 | 暂不沿用；先确定框架协作与最小确定性编排边界 |
| 用户身份、连接凭据、授权、操作幂等、审计、文件归属 | 保留业务责任，字段和 API 重新设计 |
| 57 个接口、86 个 Schema、27 张表 | 调研时暂停实施；后续已由v0.2重映射替换，现行清单见contracts/design-manifest.json |
| 静态检查通过记录 | 仅说明旧草稿结构自洽，不代表 AgentScope 方案已验收 |

## 9. 实现前验证清单

本轮只形成实验设计，不执行实现。

| 验证项 | 必须得到的证据 |
| --- | --- |
| 固定标签与依赖 | Maven 制品可获取、依赖树无冲突；Boot/Jackson/Reactor/MyBatis 版本对齐；记录精确锁定组合 |
| 持久受理 | 同键同内容不重复；同键不同内容拒绝；受理后杀 JVM，新实例可发现并继续 |
| 子任务接管 | 两节点竞争、执行中退出、旧节点迟到；只恢复失败分支且成功分支不重复 |
| 工具不确定状态 | 外部成功回执丢失后停在核对；核对结果提交后显式继续，不重复写入 |
| 上下文与权限 | 两用户相同 sessionId、并行子任务、撤权、恶意 Skill 均不能串数据或放大权限 |
| 交互 | 挂起后重启、旧审批、重复回复、参数改变、取消后回复均符合规则 |
| 事件 | 一致水位快照+回放；原生实时流不能充当持久确认；子正文不串主回答 |
| 记忆 | 只有用户明确要求才写长期记忆；纠正/删除后后台合并或检索不能重新引入 |
| 模型与资源 | DeepSeek 工具调用、真实流、取消、错误映射；总并发、深度、工具次数及费用有上限 |
| 沙箱 | 杀执行节点、快照失败、网络越界和宿主路径访问测试；恢复不能伪装操作回滚 |

目前结论：**Java + AgentScope 路线成立，完整生产要求尚待版本选择与上述实验验证。** 首先修订对象语义、职责与契约，之后再安排应用骨架。

## 10. 主要资料

- [v2.0.4 Release](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.4)
- [v2.1.0-BETA1 Pre-release](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.1.0-BETA1)
- [官方文档索引](https://java.agentscope.io/llms.txt)
- [Going to Production](https://java.agentscope.io/v2/en/docs/others/going-to-production)
- [Plan Mode](https://java.agentscope.io/v2/en/docs/harness/plan-mode)：规划阶段约束，不是已完成的持久 DAG 调度器。
- [DeepSeek 集成](https://java.agentscope.io/v2/en/integration/model/deepseek)：优先复用模型扩展，具体模型名和能力由配置及实测确认。
