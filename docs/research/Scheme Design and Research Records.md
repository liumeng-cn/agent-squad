# Scheme Design and Research Records

> AgentSquad 架构方案设计与技术调研记录
>
> 本文是持续更新的研究记录，不是最终架构规范，也不代表文中提到的目标能力已经落地。每次调研应记录源码版本、实际证据、判断依据和未确认事项。
>
> 本次记录日期：2026-11-10
> 项目分支：`feat/20261103_log_optimize`
> 本轮审计基线：AgentScope Java `2.1.0-BETA1`；AgentSquad 提交 `20de964f`
> 记录来源：上一轮源码审计结果。本文先固化已有结论；后续专题继续阅读当前分支代码与指定版本源码，确认分支演进是否改变结论。

## 0. 研究记录约定

### 0.1 结论状态

| 标记 | 含义 |
|---|---|
| 已从源码确认 | 审计记录指出已检查对应实现；后续代码版本变化时仍需复核 |
| 初步判断 | 基于当前证据形成的设计判断，尚未完成专项验证 |
| 待验证 | 缺少压测、故障演练或完整调用链证据 |
| 目标方向 | 希望达到的架构形态，不代表当前代码已经实现 |

### 0.2 每轮调研需要记录

1. 调研问题和范围。
2. 代码仓库、分支、提交 SHA、依赖版本。
3. 实际调用链与关键源码位置。
4. 已确认事实、推断、风险和未解决问题。
5. 可选方案、取舍依据及暂定结论。
6. 后续验证步骤，以及是否需要形成独立 OpenSpec 变更。

不要把“框架提供某个 API”直接等同于“产品已经具备对应能力”；不要把持久化记录等同于运行中任务可恢复；不要把目标架构写成当前实现。

---

# 1. 本轮研究主题：AgentScope 原生执行能力与 AgentSquad 运行架构

## 1.1 研究目标

明确 AgentScope 应承担哪些 Agent 执行职责，AgentSquad 应保留哪些产品和业务协调职责。重点检查：

- SubAgent 后台任务是否能跨进程恢复。
- AgentSession 的执行资格、事件记录、插话、中断和恢复机制。
- Redis Stream 当前在 AgentSquad 中的实际职责。
- AgentScope 原生 Redis Store 是否值得用于 Session Journal。
- Master 的动态 SubAgent 与持久 Worker 任务是否应该统一。
- 数据库锁、条件更新和全局热点的实际风险。
- 1000、5000、10000 个活跃会话下 PostgreSQL / Redis 的读写压力。

## 1.2 关键结论摘要

| 主题 | 当前研究结论 | 状态 |
|---|---|---|
| SubAgent 后台任务 | 任务记录持久化不代表本地 Future 能在其他 JVM 自动恢复；默认失联处理是标记失败 | 已从源码确认（以本轮审计版本为准） |
| AgentSession | 原生 Session 的执行资格、事件日志、输入队列和恢复机制值得优先复用 | 已检查关键实现；需补完整生命周期验证 |
| Writer / Lease / Epoch / CAS | 通过带版本的条件更新管理执行资格，旧执行者失去资格后不能继续提交有效数据 | 已从源码确认 |
| Session 心跳 | 审计发现活跃 Session 可能每秒续租并触发数据库写入，需专项评估 | 已发现，影响待压测 |
| Redis Stream | 当前主要传递 commandId / Binding 唤醒通知，不承载完整运行事件正文 | 已从当前实现检查 |
| Redis Journal | AgentScope 提供 Redis Store 和原子 CAS 能力，可作为原生 Journal 存储候选 | 方向成立，接入与故障语义待验证 |
| Master / Worker | Master 适合使用 AgentSession，并评估开放原生 SubAgent；需要持久执行和验收的 Worker 暂不应直接替换为普通 SubAgent | 初步架构判断 |
| 原生 steer | AgentScope 有 steer 能力，但当前产品层 Gateway 尚未暴露 | 已从代码对照发现 |
| 原生 SubAgent | 当前 Master 和 Worker Builder 都关闭了原生 SubAgent | 已从代码对照发现 |
| 数据库锁 | 不应一刀切删除 FOR UPDATE；应按业务一致性、竞争范围和热点逐项审计 | 初步判断 |
| 总体边界 | AgentScope 管 Agent 执行；Redis 承担运行时高频数据与通知；PostgreSQL 保存业务事实；AgentSquad 管产品规则和交付 | 目标方向 |

---

# 2. SubAgent：异步任务持久化不等于故障后自动续跑

审计了 `WorkspaceTaskRepository` 后，需要明确区分三件事：

1. **任务状态持久化**：记录任务是否运行、是否完成、结果是什么。
2. **执行过程管理**：通过本地 `CompletableFuture` 和本地 Executor 执行任务。
3. **故障检测**：通过心跳和定时扫描识别失联任务。

审计记录中的执行流程：

```text
提交任务
  -> TaskRecord 写入 Workspace
  -> 当前节点使用 CompletableFuture + 本地 Executor 执行
  -> 运行期间定时更新心跳
  -> 节点故障后，超过默认失联阈值
  -> 任务被标记为 FAILED
```

审计版本的默认心跳间隔为 30 秒，超过 10 分钟没有心跳会被标记为 FAILED。需要在后续复核中确认这些默认值是否受配置影响。

由此得到：

- 任务记录可以跨 JVM 保留。
- 其他节点可以查询任务状态。
- 其他节点可以发起取消。
- 但其他节点读取 TaskRecord，不会让原 JVM 的本地 Future 自动恢复。
- 默认故障处理是标记失败，不是从中断位置继续执行。

**结论：**不能把 AgentScope 原生 SubAgent 的任务记录持久化能力直接等同于分布式无状态执行和自动故障接管。复用 SubAgent 前，必须证明它满足 AgentSquad 对独立 Session、执行上下文、计划版本、结果验收和故障恢复的要求。

源码：
- [WorkspaceTaskRepository](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java)

---

# 3. AgentSession：原生会话机制是优先研究和复用的部分

本轮串联检查了 `AgentSession`、`SessionInbox`、`SessionExecution`、`SessionRecorder` 和 `JournalSessionLog`。

## 3.1 执行资格与多节点接管

AgentScope 使用以下机制管理 Session 的执行资格：

- Writer：当前执行者身份。
- Lease：执行资格的有效期。
- Epoch：执行资格的版本。
- CAS：基于版本号进行原子更新。

审计记录中的执行过程：

```text
节点 A 申请执行资格
  -> CAS 更新 Session Head
  -> owner=A，epoch=1，lease=2 分钟
  -> 节点 A 获得执行资格

运行期间
  -> 定期刷新执行记录并续租 Writer

节点 A 故障
  -> Lease 到期
  -> 节点 B 申请接管
  -> CAS 更新 Head，epoch=2
  -> 旧节点 A 的 Writer 失效
```

值得保留的设计点：

1. 不需要每次执行都通过业务 SQL 的 `SELECT FOR UPDATE` 抢占记录。
2. 使用版本比较和条件更新，竞争失败的一方重新读取。
3. Lease 到期后允许其他节点接管。
4. Epoch 用于防止旧执行者在失去资格后继续提交数据。

**重要边界：执行资格与任务数据本身分离。**任务可以保存在持久存储中，执行资格通过 Journal 的 CAS 管理，不需要把整个执行过程放进一个长数据库事务。

## 3.2 当前 JDBC Store 的 CAS 实现

本轮审计的配置链路：

```text
RuntimeSessionProvider
  -> JdbcDistributedStore
  -> RuntimeJournalStore
  -> WorkspaceSessionLogStore
  -> StoreSessionStorage
```

底层 `JdbcStore.putIfVersion()` 使用带版本条件的 UPDATE，核心逻辑如下：

```sql
UPDATE agentscope_store
SET value_json = ?,
    version = version + 1
WHERE namespace_path = ?
  AND item_key = ?
  AND version = ?
```

这说明当前 AgentSquad 的 AgentScope 原生 Journal 并非简单依赖 `FOR UPDATE` 实现并发控制，而是使用条件更新实现 CAS。

但 CAS 仍会产生数据库写入和行级竞争，并不意味着数据库没有压力。

## 3.3 Session 心跳可能带来的数据库写入

审计记录发现 `SessionRecorder` 中存在：

```java
private static final Duration LEASE = Duration.ofMinutes(2);
```

运行期间通过周期任务每秒执行一次心跳；心跳过程会调用 `flushNow()` 和 `exportCommitted()`。即使没有待提交事件，`flushNow()` 也可能先续租 Writer。

按审计记录中的实现估算：

| 活跃 Session 数 | 每秒潜在续租操作 |
|---:|---:|
| 1000 | 约 1000 次 |
| 5000 | 约 5000 次 |
| 10000 | 约 10000 次 |

这还没有计算真正的事件提交、业务状态更新和事件投影。

**当前判断：**这是比单独讨论某几个 `FOR UPDATE` 更值得专项研究的数据库压力来源。

**不能直接降低续租频率。**需要同时考虑 Lease 过期、节点暂停、长时间 GC、网络抖动和旧执行者失效等风险。应通过源码确认、压测和故障演练决定参数。

源码：
- [AgentSession](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)
- [SessionRecorder](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-core/src/main/java/io/agentscope/core/session/SessionRecorder.java)

---

# 4. Redis Stream：当前主要是唤醒通知，不是运行时消息正文

本轮对照检查了 `RedisCommandWakeup`、`RedisProjectionWakeup`、`RuntimeProjectionDispatcher` 和事件收件服务。

当前数据分工：

| 数据 | 当前存储位置 | Redis 是否承载实际内容 |
|---|---|---|
| 用户提交的命令 | PostgreSQL `sq_command` | 否，只传 commandId |
| AgentScope 执行日志 | PostgreSQL `agentscope_store` | 否 |
| 原生事件收件 | PostgreSQL `sq_runtime_event_inbox` | 否 |
| Worker 任务状态 | PostgreSQL | 否 |
| 业务执行状态 | PostgreSQL | 否 |
| 命令唤醒 | Redis Stream | 是，但主要传引用 |
| 事件投影唤醒 | Redis Stream | 是，但主要传 Binding 引用 |
| SSE 消息读取 | PostgreSQL 事件投影 | 否 |

当前流程：

```text
用户提交消息
  -> PostgreSQL 创建 Command
  -> Redis Stream 发布 commandId
  -> CommandDeliveryService 从 PostgreSQL 领取命令、校验权限、维护投递状态
  -> AgentScope AgentSession 执行
  -> PostgreSQL 持久化原生 Journal
  -> 事件导出到 PostgreSQL Runtime Event Inbox
  -> Redis Stream 发布 Binding 唤醒通知
  -> RuntimeProjectionDispatcher 从原生 Journal 补读事件
  -> 更新业务状态和会话展示数据
  -> 前端通过 SSE 读取事件投影
```

优点：Redis 丢失唤醒消息，不会直接丢失业务事实，业务状态和原生执行事件仍在 PostgreSQL。

风险：实时执行的关键操作仍多次访问 PostgreSQL，可能产生读写放大。

需要继续测量的三个环节：

1. `SessionLogExporter`：每条事件独立调用收件器，成功后再推进导出游标。
2. `RuntimeEventReceiptService`：每条事件进行绑定校验和数据库事务。
3. `RuntimeProjectionDispatcher`：Redis 只负责唤醒，真正的事件读取仍回到 PostgreSQL。

**后续重点：**统计每个 Agent 事件带来的数据库读写次数、事务次数、消息大小和端到端延迟；不能只用“Redis 更快”作为迁移理由。

---

# 5. AgentScope 原生 Redis Store：值得做专项 PoC

本轮检查发现 AgentScope Redis 扩展提供：

- `RedisDistributedStore`
- `RedisStore`
- `RedisAgentStateStore`
- `RedisSnapshotSpec`

其中 `RedisStore` 使用 Lua 脚本完成原子 CAS：读取当前版本，只有版本匹配时才更新值并递增版本。

这意味着 Redis Store 不只是普通 KV 存储，也具备 AgentScope Session Journal 所需的原子版本更新能力。官方 `StoreSessionStorage` 可以基于 `BaseStore` 建立 SessionLog。

## 5.1 候选分层

```text
AgentScope 原生存储 + Redis
  - Session Journal
  - Session Inbox
  - Agent State
  - 实时执行状态
  - Agent 间通信

异步持久化、事件投影、恢复补偿

PostgreSQL 业务持久层
  - 用户与会话业务关系
  - 任务与计划
  - 执行快照
  - 最终结果
  - 业务审计记录
```

## 5.2 必须先验证的前提

- Redis 持久化、复制、故障恢复和数据保留策略。
- Redis 故障、主从切换、网络分区时 CAS 和 Lease 的行为。
- AgentScope Journal 在 Redis 上的读写模式、数据量和过期策略。
- Session 恢复是否完整，是否存在只恢复元数据而丢失事件正文的情况。
- PostgreSQL 业务事实与 Redis 运行时记录之间的补偿和一致性边界。
- 运行中事件、Inbox、Journal 的清理和归档策略。
- Redis 在 1000 / 5000 / 10000 活跃会话下的内存、网络和命令压力。

**暂定结论：**优先评估 AgentScope 原生 Redis Store，不先自行实现一套 Journal 或分布式锁。Redis 不能简单当成可随时丢弃的缓存；迁移 Journal 前必须完成持久性与恢复演练。

源码：
- [RedisStore](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/store/RedisStore.java)

---

# 6. SubAgent 与 AgentSession：两条不同的执行路径

不能把两者当作同一套任务机制。

| 对比 | 原生 SubAgent | AgentSession |
|---|---|---|
| 创建方式 | agent factory | agent.session(context) |
| 执行方式 | call / stream | submit / resume |
| 任务管理 | TaskRepository | SessionInbox |
| 会话记录 | AgentState 等机制 | Session Journal |
| 异步任务 | BackgroundTask | Session Task |
| 插话 | 通过 Agent 间消息交互 | steer |
| 中断 | task_cancel | interrupt |
| 继续执行 | 依赖任务执行机制 | resume |
| 故障后继续 | 默认本地任务不支持自动恢复 | 支持显式恢复未完成 Turn，仍需验证完整故障边界 |

当前 Worker 需要：

- 固定 Binding。
- 独立 Session。
- 可恢复的执行上下文。
- 结果候选和业务验收。
- 计划版本隔离。
- 故障后继续执行。

这些要求明显高于普通 SubAgent 后台任务。

## 6.1 暂定角色分工

**Master：**

优先使用 AgentScope AgentSession，保留原生会话控制能力；进一步评估启用原生 SubAgent，让 Master 可以自主分派一些独立、短期的调查和分析工作。

**Worker：**

当前独立 AgentSession 方案有存在价值。Worker 执行明确任务，Master 负责计划、决策和验收。

Worker 是否改用原生 SubAgent，必须先证明它能满足现有持久化、恢复和业务隔离要求。不要为了统一实现方式牺牲这些能力。

## 6.2 产品层尚未接通的原生能力

AgentScope 原生接口包括：

```java
session.steer(message);
session.interrupt();
session.resume(turnId);
```

本轮审计发现 AgentSquad 的 `NativeSessionGateway` 只暴露：

- submit
- stop
- resume
- respond

尚未暴露 steer。因此当前产品层还不能直接利用 AgentScope 原生能力实现真正的执行中插话。

另外，当前 Master 和 Worker Builder 都调用了：

```java
.disableSubagents()
```

Worker 禁止继续委派符合当前组员定位；但 Master 也禁用了原生 SubAgent，意味着目前只能通过自建 Plan 和 Worker 工具进行多 Agent 协作。

后续需要评估 Master 同时拥有两种能力：

1. 自主创建短期 SubAgent，完成并行调查、信息分析等工作。
2. 使用 Plan / Worker 体系，执行需要持久化、恢复和业务验收的复杂任务。

不必强行统一成一种执行模式。

---

# 7. 数据库锁：按业务目的逐项审计，不做一刀切删除

本轮初步分类：

| 位置 | 当前方式 | 初步判断 |
|---|---|---|
| Command 领取 | 条件 UPDATE + 租约 | 合理，优先保留 |
| Worker Guard | 条件 UPDATE + Token | 合理，优先保留 |
| Task / Plan | FOR UPDATE | 需要保证业务状态一致性 |
| Worker Jobs | 整组 FOR UPDATE | 需要研究并行节点竞争 |
| Runtime Event Inbox | FOR UPDATE | 存在优化空间 |
| Runtime Admission | 全局计数器 FOR UPDATE | 需要重点关注 |

特别关注 `RuntimeAdmissionService`：

- 当前默认关闭。
- 启用后会锁定 `GLOBAL` 容量计数器。
- 所有用户的新执行申请都需要经过这个全局计数器。
- 可能形成热点行，影响大量并发用户的申请速度。

**暂定原则：**先明确每个锁保护的业务不变量，再判断是否能改成条件更新、分段计数、乐观并发控制或其他机制。不要因为 AgentSession 使用 CAS，就推导出业务表不再需要事务或锁。

---

# 8. AgentSquad 目标架构边界

```text
产品与交互层
  聊天、客服、复杂任务、文件与多媒体交付、实时消息、插话和中断

AgentScope 运行时层
  Master：规划、决策、调度、交互
  SubAgent：并行调查、专项分析
  AgentSession：执行、插话、中断、恢复、原生事件记录

Redis 运行时数据层（目标方向）
  Session Journal、运行事件、任务通知、消息流、执行状态

PostgreSQL 业务持久层
  用户、平台配置、业务任务、计划、执行快照、最终结果、审计记录

AgentSquad 产品协调层
  产品规则、权限、业务协调、任务验收和结果交付
```

核心原则：

- AgentScope 负责 Agent 的执行机制。
- Redis 负责运行时高频数据和消息通知。
- PostgreSQL 负责业务事实和长期业务持久化。
- AgentSquad 负责产品规则、业务协调和结果交付。

这是目标方向，不代表当前代码已经实现，也不意味着必须一次性把所有运行时数据迁移到 Redis。

---

# 9. 下一轮研究计划

| 编号 | 专题 | 必须回答的问题 | 预期产出 |
|---|---|---|---|
| A | AgentSession 生命周期 | submit、steer、interrupt、resume 的状态变化、异常恢复、跨节点接管、SSE 反馈如何衔接？ | 生命周期图、状态表、故障场景表 |
| B | SubAgent 原生能力 | Session Mode、动态创建、跨实例通信、后台唤醒、任务恢复有哪些边界？ | Master/SubAgent/Worker 能力对照与建议 |
| C | Redis / PostgreSQL | 原生 RedisStore 接入、事件异步持久化、Journal 生命周期和数据库写入量如何变化？ | 存储方案比较、读写估算、压测计划 |

## 9.1 专题 C 的容量评估要求

分别估算并验证 1000、5000、10000 个活跃会话：

- 每个 Session 每秒/每分钟的心跳、CAS、Journal 写入次数。
- 每个 Agent Turn 的事件数量、平均事件大小和峰值事件大小。
- Inbox 收件、事件导出、事件投影每条事件引起的数据库读写次数。
- 每次用户消息、模型调用、工具调用、Worker 派发额外产生的读写。
- Redis 命令量、网络流量、内存占用和持久化开销。
- PostgreSQL QPS、事务数、热点行等待、连接池占用和 WAL 增长。
- 故障接管耗时、恢复期间补读量、重复事件处理成本。
- 监控采样与批量写入对数据完整性和排障能力的影响。

估算时要列清楚假设、公式、平均值和峰值；没有压测证据的数据必须标注为估算，不能伪装成实测结果。

---

# 10. 当前明确不做的事情

1. 暂不修改代码，不急于调整数据库锁。
2. 暂不把 Worker 直接替换成原生 SubAgent。
3. 暂不把所有 AgentScope Journal 和业务数据一次性迁移到 Redis。
4. 暂不把 Redis 当作无持久性要求的普通缓存。
5. 暂不把目标架构描述成当前已完成能力。
6. 暂不为了追求多实例而跳过恢复、幂等、外部操作不确定状态的验证。

先明确 AgentScope 原生能力边界，再确定 AgentSquad 自己需要补齐的产品能力，最后才进入代码重构。

---

# 11. 本轮源码索引

## AgentScope Java

版本：`2.1.0-BETA1`；源码审计提交：`5451c73abe47053340683a1cc6d67b290b056648`

- [AgentSession.java](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)
- [SessionRecorder.java](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-core/src/main/java/io/agentscope/core/session/SessionRecorder.java)
- [WorkspaceTaskRepository.java](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java)
- [RedisStore.java](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/store/RedisStore.java)

## AgentSquad

本轮审计基线：`20de964ff1d4719bf32ac3e9e41ae9d5a4804a1b`

- [MasterAgent.java](https://github.com/liumeng-cn/agent-squad/blob/20de964ff1d4719bf32ac3e9e41ae9d5a4804a1b/server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/MasterAgent.java)
- [RuntimeSessionProvider.java](https://github.com/liumeng-cn/agent-squad/blob/20de964ff1d4719bf32ac3e9e41ae9d5a4804a1b/server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/session/RuntimeSessionProvider.java)
- [RuntimeProjectionDispatcher.java](https://github.com/liumeng-cn/agent-squad/blob/20de964ff1d4719bf32ac3e9e41ae9d5a4804a1b/server/agent-squad-server/src/main/java/com/agentsquad/squad/service/RuntimeProjectionDispatcher.java)

> 注意：上面 AgentSquad 链接固定在本轮审计提交，便于回看当时证据。后续研究应同时检查 log 分支当前 HEAD，避免把旧提交的实现状态误当成最新状态。

---

# 12. 本轮阶段性结论

目前最重要的不是立即改代码，而是确定能力边界：

1. AgentScope 原生 SubAgent 后台任务不等于跨 JVM 自动恢复。
2. AgentSession 的原生 Journal、执行资格、输入队列和恢复能力值得优先复用和深入验证。
3. 当前 Redis Stream 主要承担唤醒通知，尚未承载完整运行事件数据。
4. AgentScope 原生 Redis Store 提供了值得验证的 Journal 存储路径，但必须补齐持久性和故障演练。
5. Master 的短期自主 SubAgent 与 Worker 的长期、可恢复、可验收任务可以并存，不必强行统一。
6. Session 每秒续租可能成为数据库压力来源；先测量，再决定优化。
7. 数据库锁需要按业务不变量分析，不能一刀切删除。
8. 先完成 A、B、C 三个专题研究，再形成正式架构决策和实施计划。

**本轮没有修改业务代码，也没有宣称上述目标能力已经上线。**
