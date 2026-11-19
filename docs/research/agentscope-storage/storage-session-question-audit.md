# Session 与 Inbox 逐题证据审计

版本固定为 2.1.0-BETA1；路径均为目标 Source Jar 相对路径，官方 Commit `5451c73abe47053340683a1cc6d67b290b056648`。本清单补充已有结构与生命周期专题；每题区分实现事实与保证边界。

## Session 1 如何创建

【源码确认】HarnessAgent.session 缓存 AgentSession，构造时取得逻辑 Key、log 和 Inbox；open 并不立即创建物理对象，首次 acquire 创建 header/head。

证据：`agentscope-harness/io/agentscope/harness/agent/HarnessAgent.java`，`session`，268–281 行；`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`AgentSession`，82–90 行；`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`acquire`，106–142 行。

## Session 2 谁创建 SessionLog

【源码确认】ReActAgent.sessionLog 调 SessionLogStore.open；默认 WorkspaceSessionLogStore.open new JournalSessionLog。

证据：`agentscope-core/io/agentscope/core/ReActAgent.java`，`sessionLog`，364–370 行；`agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java`，`open`，90–93 行。

## Session 3 Store 与 Log

【源码确认】Store 提供逻辑会话定位和发现，Log 提供单个日志的 head、writer、commit 和 scan；不是一份对象等于整个用户仓库。

证据：`agentscope-core/io/agentscope/core/session/SessionLogStore.java`，`接口`，18–34 行；`agentscope-core/io/agentscope/core/session/SessionLog.java`，`接口`，22–34 行。

## Session 4 Journal 职责

【源码确认】将事件写成不可变 Commit 链，以单个 fenced Head CAS 暴露稳定前缀；读取校验链和内容哈希。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`commit/scan/readCommit`，193–375 行。

## Session 5 AtomicStorage 目的

【源码确认】向 Journal 提供版本字节读取与跨所有共享 Writer 的原子 CAS，隐藏文件或远端存储差异；契约要求 ACK 前持久化。

证据：`agentscope-core/io/agentscope/core/session/AtomicSessionStorage.java`，`接口及Value`，21–45 行。

## Session 6 唯一身份

【源码确认】userId、agentId、sessionId 组成逻辑身份；agentId/sessionId 非空；实际空间还受 filesystem namespace 控制。

证据：`agentscope-core/io/agentscope/core/session/SessionKey.java`，`构造器/storagePath`，23–46 行。

## Session 7 保存位置

【源码确认】默认 workspace filesystem 提供 sessionStorage；LocalFilesystem 在其当前隔离根的 .agentscope-runtime 目录；本地对象是8字节版本前缀加payload，不是纯JSON文件。

证据：`agentscope-harness/io/agentscope/harness/agent/filesystem/local/LocalFilesystem.java`，`sessionStorage`，597–599 行；`agentscope-harness/io/agentscope/harness/agent/session/LocalSessionStorage.java`，`read/compareAndSet`，87–935 行。

## Session 8 是否追加

【源码确认】逻辑事件单调追加，Commit 不可变；Head 是CAS覆盖，不是所有文件只追加，也不是JSONL。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`commit`，193–257 行。

## Session 9 四种对象

【源码确认】Commit保存连续批次及parent/hash；Head保存seq/epoch/owner/leaseUntil/commitId；Blob保存大于65536字节payload；Seal永久封禁owner且CAS推进Head版本fence竞态。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`Commit/sealWriter/commit`，41–48、146–162、222–257 行。

## Session 10 Inbox关系

【源码确认】同一AtomicStorage、父前缀/inbox、独立head和seq及writer，不占执行Writer lease。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`inbox`，65–66 行；`agentscope-core/io/agentscope/core/session/SessionInbox.java`，`构造器/locked`，56–58、98–106 行。

## Session 11 消息与工具

【源码确认】captureState生成message/user、assistant、system、tool/result，ToolUseBlock另外生成tool/requested；模型流事件由observe记录；消息快照和模型事件不是同一层。

证据：`agentscope-core/io/agentscope/core/session/SessionRecorder.java`，`captureState/observe`，320–387、518–591 行。

## Session 12 完整历史

【源码确认】固定through=head.seq，scan(0,through)，从head反向parent再正向事件；补全blob后校验哈希；对话视图由SessionViews.transcript提供。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`scan/readCommit`，274–375 行；`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`transcript`，100–102 行。

## Session 13 是否存在

【源码确认】没有独立exists方法；head缺失返回空Head，因此seq=0不能区分不存在和空会话。发现以session.json为标记，且可能只有Inbox标记；可直接read标记核验，但不是公开Store.exists能力。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`head`，77–85 行；`agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java`，`list`，54–72 行。

## Session 14 用户列表

【源码确认】list在当前namespace查agents前缀的session.json，解码Key按调用userId过滤去重排序；SESSION隔离无法天然跨所有会话发现。

证据：`agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java`，`list`，47–72 行。

## Session 15 删除级联

【源码确认】SessionLog/SessionLogStore/AtomicSessionStorage没有delete入口；不能宣称存在官方级联清理。普通filesystem删除不等价于安全删除运行日志，LocalFilesystem禁止工具访问.runtime保留目录。

证据：`agentscope-core/io/agentscope/core/session/SessionLog.java`，`接口`，22–88 行；`agentscope-core/io/agentscope/core/session/AtomicSessionStorage.java`，`接口`，21–88 行；`agentscope-harness/io/agentscope/harness/agent/filesystem/local/LocalFilesystem.java`，`保留目录检查`，601–606 行。

## Inbox 1 用户输入

【源码确认】submit创建冻结Command，在Inbox短租约锁下accept，accepted事件commit后才返回；执行另外读取该命令。

证据：`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`submit`，142–151 行；`agentscope-core/io/agentscope/core/session/SessionInbox.java`，`Transaction.accept/append`，134–521 行。

## Inbox 2 所有操作

【源码确认】submit/resume/respond/steer/inject/interrupt都以Command持久化；interrupt还写closed；不是每个操作都直接作为用户对话消息。

证据：`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`submit/resume/respond/steer/inject/interrupt`，142–330 行。

## Inbox 3 是否独立

【源码确认】独立Journal子前缀与序列，可在执行Writer占用时接受输入，但底层可能共用同一个storage。

证据：`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`inbox`，65–66 行。

## Inbox 4 不丢失条件

【源码确认】accept先commit后ACK；重试同ID同内容返回已有Command，不同内容报错。保证依赖AtomicStorage兑现持久ACK；不含网络响应必达保证。

证据：`agentscope-core/io/agentscope/core/session/SessionInbox.java`，`Transaction.accept/append`，134–185 行；`agentscope-core/io/agentscope/core/session/AtomicSessionStorage.java`，`契约`，21–24 行。

## Inbox 5 游标

【源码确认】没有数字消费游标；snapshot全扫accepted/rejected/handled/opened/closed，执行log的started/applied集合表达处理状态。

证据：`agentscope-core/io/agentscope/core/session/SessionInbox.java`，`snapshot`，60–94 行；`agentscope-core/io/agentscope/core/session/SessionExecution.java`，`prepare/beforeStep`，50–123 行。

## Inbox 6 重复消费

【源码确认】command.id接受幂等，started排除重复执行；inject/steer按message ID去重，先checkpoint再applied；interrupt有handled标记。不是外部工具exactly-once。

证据：`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`dispatch`，391–430 行；`agentscope-core/io/agentscope/core/session/SessionExecution.java`，`beforeStep`，97–123 行。

## Inbox 7 重启

【源码确认】调用AgentSession.start重新扫描队列；未started执行命令可调度，已started命令需显式继续而非自动重放；不确定工具结果阻断恢复。

证据：`agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java`，`start/dispatch`，117–123、391–430 行；`agentscope-core/io/agentscope/core/session/SessionRecorder.java`，`构造器`，117–133 行。

## Inbox 8 多实例

【源码确认】Inbox acquire独立两分钟lease并有限重试；执行调度最终依赖执行日志Writer原子争抢；旧Writer由epoch/owner/lease和seal拒绝。

证据：`agentscope-core/io/agentscope/core/session/SessionInbox.java`，`acquire`，108–127 行；`agentscope-core/io/agentscope/core/session/JournalSessionLog.java`，`owned/acquire`，96–142 行。

## 生命周期子问逐项覆盖

以下各行均为【源码确认】；涉及外部效果的边界按行注明。【源码推导】跨节点使用还要求后端兑现共享 namespace 原子 CAS，以及部署方处理主机时钟与故障条件。

|场景与子问|逐项结论|完整源码证据|
|---|---|---|
|A1 Builder装配|非legacy模式显式store优先，否则new WorkspaceSessionLogStore(wsManager)|agentscope-harness/io/agentscope/harness/agent/HarnessAgent.java，Builder.build，2722–2728|
|A2 生成Key|ReActAgent使用RuntimeContext userId、配置sessionLogAgentId及sessionId生成Key|agentscope-core/io/agentscope/core/ReActAgent.java，sessionKey，373–376|
|A3 创建Log|WorkspaceSessionLogStore.open选择filesystem.sessionStorage并构造Journal|agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java，open，90–93|
|A4 初始化Head|head缺失逻辑为零；首次acquire CAS写入epoch1 owner及lease|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，head/acquire，77–85、106–142|
|A5 初始化Inbox|父log.inbox创建子前缀；首次Inbox acquire持久化自身header/head|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，inbox/acquire，65–66、106–142|
|A6 首次数据|submit先提交Inbox accepted，再启动dispatcher；无输入只构造对象不等于落库|agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，构造器/submit，82–90、142–151|
|B1 消息入口|AgentSession.submit重载进入带幂等key版本|agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，submit，125–151|
|B2 进入Inbox|locked tx.accept写accepted事件并commit|agentscope-core/io/agentscope/core/session/SessionInbox.java，Transaction.accept/append，134–185|
|B3 获取消息|dispatch扫描snapshot命令，launch将命令放SessionExecution；prepare产生inputs|agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，dispatch/launch，391–473；agentscope-core/io/agentscope/core/session/SessionExecution.java，prepare，50–95|
|B4 产生事件|Recorder.captureState与observe采集状态、消息、模型工具事件|agentscope-core/io/agentscope/core/session/SessionRecorder.java，captureState/observe，320–387、518–591|
|B5 提交日志|flushNow先renew后commit，成功后清空pending|agentscope-core/io/agentscope/core/session/SessionRecorder.java，flushNow，253–268|
|B6 Commit生成|验证连续seq，SHA256(batchId)作对象id、events哈希作校验，写不可变对象|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，commit，193–252|
|B7 Head更新|以读到的storage version CAS head，单次暴露批次|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，commit，253–269|
|B8 最新历史|读取head.seq固定上界后scan；transcript投影构造对话消息|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，scan，321–375；agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，transcript，100–102|
|C1 宕机已有数据|可能有accepted命令、提交前缀、未可见Commit/blob及内存pending；后两者不等于已提交事件|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，commit/scan，222–257、321–340；agentscope-core/io/agentscope/core/session/SessionRecorder.java，flushNow，253–268|
|C2 Commit未完成|Head没有引用的对象不参与恢复；内存未flush事件无持久保证|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，scan，321–340|
|C3 成功响应丢失|重读Head确认；同batch重试沿链核对hash/firstSeq|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，commit，206–218、258–268|
|C4 Head失败|CAS失败重读重试，所有权丢失或expectedSeq过时抛错，最多32轮|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，commit，203–271|
|C5 Writer Lease|两分钟lease、每秒heartbeat flushNow续租；进程死亡后不会无限续租|agentscope-core/io/agentscope/core/session/SessionRecorder.java，构造器/flushNow，62、97–150、253–256|
|C6 新实例接管|过期后acquire新owner并epoch+1；打开同Key恢复投影；已started任务不自动重新执行|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，acquire，117–139；agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，dispatch，409–418|
|C7 判断提交事件|唯一依据Head可达链及seq、hash校验；不靠目录listing|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，scan/readCommit，274–375|
|C8 避免重复执行|started与消息ID去重；未知外部工具结果要求reconciliation，不能保证外部副作用exactly-once|agentscope-core/io/agentscope/core/session/SessionExecution.java，prepare/beforeStep，50–123；agentscope-core/io/agentscope/core/session/SessionRecorder.java，构造器，127–133|
|D1 查找Session|Store.list按namespace与user过滤标记文件，也可已知Key直接open|agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java，list/open，54–93|
|D2 打开Log|不更改原Key，创建同storage及prefix的Journal对象|agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java，open，90–93|
|D3 扫描历史|Head逆向parent收集Commit，再按正序scan输出|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，scan，321–375|
|D4 恢复消息|AgentSession.transcript调用SessionViews.transcript|agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，transcript，100–102；agentscope-core/io/agentscope/core/session/SessionViews.java，transcript，31行|
|D5 恢复State|SessionProjection折叠state/checkpoint等事件，再AgentState.fromJsonString；不隐式读取AgentStateStore|agentscope-core/io/agentscope/core/session/SessionProjection.java，read/restore，46–115|
|D6 继续原Session|同上下文调用session复用Key；resume明确继续最新未结束turn；不会把已完成turn重开|agentscope-harness/io/agentscope/harness/agent/HarnessAgent.java，session，268–281；agentscope-harness/io/agentscope/harness/agent/session/AgentSession.java，resume，154–185|
|E1 同时打开|open只是构造对象无Writer锁，多实例可以open/read|agentscope-harness/io/agentscope/harness/agent/session/WorkspaceSessionLogStore.java，open，90–93|
|E2 Writer竞争|read Head后CAS(version)，有活跃owner拒绝|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，acquire，117–142|
|E3 旧Writer|owned同时校验seal、epoch、owner及到期时间|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，owned，96–102|
|E4 原子CAS|接口契约要求跨共享Writer原子；POSIX实现进程锁加OS文件锁，版本比对后原子rename与fsync|agentscope-core/io/agentscope/core/session/AtomicSessionStorage.java，接口，21–45；agentscope-harness/io/agentscope/harness/agent/session/LocalSessionStorage.java，compareAndSet，106–45|
|E5 Lease与fencing|到期允许新epoch接管；seal永久禁止owner并推进Head version，防暂停写入竞态|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，acquire/sealWriter，106–162|
|E6 读写分离|读无需Writer lease、scan稳定前缀；没有数据库副本路由或副本一致性协议|agentscope-core/io/agentscope/core/session/SessionLog.java，head/scan/readAfter，22–91行；agentscope-core/io/agentscope/core/session/JournalSessionLog.java，scan，321–375|
|E7 失败恢复|重读Head及提交链；重用batch处理丢ACK；重新acquire使用新owner，工具不确定状态显式核对|agentscope-core/io/agentscope/core/session/JournalSessionLog.java，acquire/commit，106–142、193–271；agentscope-core/io/agentscope/core/session/SessionRecovery.java，类职责及cancelPending，32–49|

【运行验证】已补官方故障注入测试及三个独立JVM竞争、SIGKILL、租约接管、旧writer fencing，见 [运行证据](validation-evidence.md)。它们不是跨机器多节点验收；外部副作用和远程故障仍只有源码机制边界。时序图见 [生命周期专题](storage-lifecycle.md)。
