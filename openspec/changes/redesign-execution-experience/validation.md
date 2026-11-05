# 实验记录

## 2026-11-04：现有事件处理接入持久收件、事务消费和失败分类

### 已接入的实际路径

NativeProjectionPoller现在只走一条路径：明确启动获授权绑定 → 纯读官方已提交信封 → RuntimeEventReceiptService逐条短事务收件 → RuntimeEventConsumptionService有界批次消费 → NativeEventBusinessWriter复用NativeProjectionService更新业务/公开事件和连续游标 → 同事务确认APPLIED。旧的直接循环调用projectionService.accept已移除，没有并行的第二套裁决。

本轮沿用已经核实的旧workspace日志位置，不切数据库v2后端；协议与日志后端的持久绑定仍待2.2/10.1，不能声称本轮已完成。旧绑定可以从已有业务游标继续收件，不要求复制已消费历史。

### 事务和故障处理

- 单批上限100条，核对完整信封摘要、事件ID、映射版本与会话；未收件或内容冲突不写业务。
- 同绑定由既有业务锁与连续游标串行；先业务锁，再锁inbox确认，避免把收件行锁提前到所有者/绑定之前。业务、公开事件、游标及APPLIED处于同一个外层Spring事务；任何一条失败则整批回滚，已独立提交的收件仍存在。
- 重放与两个消费者竞争不重复业务效果；缺号等待补齐；未知必需事件不消费，可选未知事件仍可由现有投影推进水位而不解码未知载荷。
- 消费回滚后独立事务记录安全error_code、attempts、next_attempt_at：RETRY表示等待重试，BLOCKED表示事实冲突或必需类型不支持。已经APPLIED的记录不被迟到失败降级；成功后清除错误码、保留历史重试次数。
- NativeProjectionService的成功日志改在整个事务提交后输出，避免批次后半段失败回滚却已经打印“已更新到业务状态”。日志不输出原始Prompt或工具参数。

### 验证及边界

- 生产MyBatis XML、收件/消费/失败服务与真实Spring事务代理在独立MySQL容器运行：18项通过；另1项不同目录JVM崩溃恢复通过。新增消费用例覆盖同事务提交、整批回滚后重放、缺号/未收件/冲突、未知必需事件与跨绑定隔离、两消费者竞争、失败记录及已应用状态不回退。
- 消费测试的业务回调是测试效果表与游标夹具，不冒充完整回复/计划业务回归。生产适配器确实委托原NativeProjectionService，其业务规则与前端效果仍需完整集成验收。
- 同轮70项非数据库针对性回归通过；加上19项MySQL，共89项针对性验证通过，0失败、0错误、0跳过。原始SDK停止原因缺陷断言保留且不计入89项。业务主代码编译通过，未执行全量业务测试和真实页面验收。
- 保留用户对schema.sql的既有编辑，仅对齐新收件表在baseline/schema/V6中的定义；原生事件ID列显式使用二进制排序规则，防止不同大小写的原生ID被错误合并。
- 现有路径启用前必须具备V6的sq_runtime_event_inbox。生产门禁新增该表且期望版本6，测试预期同步；本轮只在专用容器执行DDL，没有修改实际开发或共享库。

3.3仍为部分完成：活动表投影、字节/耗时预算和完整业务端到端尚未完成；后台仍靠原有轮询，终态后的独立尾部补偿未实现，3.4不能勾选。页面尚未展示消费故障，也未宣称聊天耗时已降低。

### Redis方向

按用户明确要求，全局AGENTS允许主动采用Redis且不限于缓存。design第9节已记录以Streams承载跨实例唤醒引用、Pub/Sub传递即时更新通知的方向，以及提交/发布间隙的补偿规则。本轮未安装或连接Redis，用户将自行安装；不把尚未接入的能力算作已交付，也不因准备使用Redis而撤掉已经验证的业务事务与幂等。

---

## 2026-11-04：现有运行路径的纯读改造已接入

本轮改动已进入当前MasterAgent和NativeProjectionPoller，不只是新增未使用的适配器。

- MasterAgent.readEvents和inspectTask改为CommittedSessionReader，直接打开实际workspace后端的固定SessionKey；不再经WorkerFactory.sessionFor，因此查询不构建Worker、不领取Job、不续租guard、不调用session.start。
- 新增明确执行入口NativeSessionGateway.startPending，由后台投影器在requireBindingCurrent通过后调用，保留原有重启恢复/Worker资格检查。后台轮询仍可推进工作，但推进动作不再隐藏在查询方法中；目前未移除轮询。
- 旧日志后端继续通过Master实际WorkspaceManager获得，未切换为数据库v2日志，未复制、迁移或删除历史。实测采用与当前生产一致的USER隔离RemoteFilesystemSpec，从Master的workspace可读Worker已提交记录，且其他用户读不到该记录。
- 纯读收据按原提交所属Turn选取最新resume/respond，已提交run/end裁决结果；writer失效为interrupted，未知原生结果为unknown，不推断成功。不会将另一Turn的完成覆盖原Turn，也不将未启动的恢复误认成上一运行已经完成。
- 新增6项生产读取器测试全部通过：排队不消费输入、不领取writer；writer丢失；恢复排队/完成；未知状态；跨Agent实际workspace读取；真实SDK挂起、答复恢复及另一Turn后收据对照。纯读后的模型调用数不增加。
- 同轮64项既有非数据库针对性回归通过，连同新增6项共70项通过，0失败、0错误、0跳过。主代码reactor compile、diff空白检查通过；已知SDK停止原因失败用例继续保留，不在此通过计数内。没有重跑全量业务/页面/数据库验收。

3.2只完成其中的现有读写路径分离；数据库v2后端/持久sink全面接入、attempt/trace/权限快照仍待完成，任务不勾选。下一步需要固定Binding协议和后端归属，再接事件消费者及尾部补偿。本轮没有改回复完成规则、页面进度或宣称聊天耗时已降低。

---

## 2026-11-04：修正journal持久化结论并实现Provider底座

### 必须纠正的旧结论

先前P03实验确实验证了MySQL收件事务，但仅配置JdbcDistributedStore与RemoteFilesystemSpec的Harness journal仍落本地。三个JVM复用同一目录，因此先前“SDK journal与确认游标已在MySQL恢复”的表述证据不足，现撤回该部分结论；不是产品已经具备跨机器恢复。SDK源码证据：CompositeFilesystem.sessionStorage选择`agents/`默认路由，而RemoteFilesystemSpec只将更具体的Agent路径列为远程路由，默认后端是LocalFilesystem。

### 本轮生产代码与替代证据

- 实现RuntimeJournalStore，显式组合官方WorkspaceSessionLogStore、RemoteFilesystem和JDBC BaseStore；固定`squad/native-journal-v2/用户ID`namespace，SessionKey继续隔离Agent与会话；拒绝缺失/错配用户和会话。
- 修正MySqlJournalProbe，显式注入上述生产store；SdkMySqlContractTest的三个JVM分别使用全新的不同工作目录，不共享本地journal，断言实验目录不存在`.agentscope-runtime`。仍在第2条收件提交后halt(23)，恢复后cursor=3/inbox=3/effects=3/models=0，第三次恢复不重复。替代实验通过，P03仅在明确配置该数据库journal条件下成立。
- 实现RuntimeSessionProvider：装配数据库校验的基础执行上下文与持久sink；纯读直接通过官方SessionLogStore读取，不创建Agent、不start、不领取或续租Job；Agent身份也必须匹配持久Binding。
- 真实Harness使用Provider上下文、数据库日志后端和生产sink，完成一次本地模型调用；上下文复制后sink仍写入MySQL。纯读前缀、读空尾部不增加模型调用；未知Agent与非法范围拒绝。
- await后实测一次日志23条、收件19条，说明运行静止不承诺异步导出已追上。测试明确独立drain后核对全部收件，不篡改为“自动立即完整”；正式尾部扫描仍由3.4实现。
- MySQL当前共12项通过：原8项生产收件、新增2项journal隔离/身份、1项Provider真实Harness联测、1项不同目录的实际崩溃恢复，0失败、0错误、0跳过。容器已清理，没有使用既有库或真实模型。

本轮业务reactor compile、OpenSpec严格校验及diff空白检查通过；64项非数据库回归重新通过，连同12项MySQL合计76项针对性验证通过。原始SDK停止原因缺陷断言仍保留且未纳入该通过计数，不等于全量或端到端通过。

任务3.2仍未完成：Master/Worker现有入口尚未切换，原readEvents的隐式start、Worker读取时领取/续租问题仍存在；trace、attempt、权限快照的完整装配和旧/新日志后端的持久协议绑定也尚未接入。禁止把本轮Provider底座描述成所有入口已完成替换。需先在2.2/10.1固定绑定后端，再完成3.2及3.3/3.4，避免旧会话历史丢失或双消费。

---

## 2026-11-04：开始生产收件实现，补齐P02的SDK边界

用户已授权开始执行。固定SDK的可行性证据支持先开发底座；完整预算、页面和端到端验收随对应实现推进，不要求先完成全部42项才开发。

### P02新增6项实测

- 审批拒绝：初次挂起、工具调用0次；respond拒绝后模型总调用2次，唯一turn/output不是工具结果。
- 审批通过：工具调用1次、模型总调用1次；唯一turn/output带TOOL_RETURN_DIRECT和实际结果。
- 自定义工具挂起后提供外部结果：恢复不再调用模型，工具总调用1次。
- 两个明确声明externalTool的终结工具分两次提供结果：两工具本地调用均0次，第一次恢复仍挂起；补齐后回模型，总调用2次，不以returnDirect假定整批直接完成。
- 终结工具运行中steer：已接受输入有inbox/applied证据，模型总调用2次，仅一个turn/output。
- 终结工具运行中submit：保持两个不同Turn，各完成且各一个turn/output，不把新请求吞入旧终稿。

初版分批实验使用仅抛ToolSuspendException的普通工具，发现剩余待处理工具恢复时会重新进入callAsync。正式externalTool声明阻止本地执行，两者不能混称。自定义挂起必须在副作用前发生，或由既有operationId账本保证重入安全；不把挂起本身当成效果幂等证明。产品候选、唯一交付与审批业务账本尚未联接本探针，任务1.2继续保留未完成。

### 生产实现：任务3.1完成

- 新增inbox包中的绑定身份、消费状态枚举、持久投影、MyBatis Mapper、事务收件服务及独立sink工厂，固定名称squad-execution-v2。
- 数据库绑定校验userId/bindingId/sessionId；完整原生信封及载荷只计算SHA256，平台表不复制原始载荷。未知必需事件仅收件，不能视为消费完成。
- 相同事件重投不回退APPLIED；同序号、同ID、信封或内容冲突抛错，事务回滚。独立工厂持有Spring服务代理，避免服务this装配回调绕过事务。
- 新增V6__runtime_event_inbox.sql，并同步最终baseline与schema.sql；完整建表断言更新到52表。仅生成SQL，没有对已有库执行迁移。
- 直接编译上述生产源码、加载生产Mapper XML和Spring事务代理，在专用MySQL容器执行8项测试：官方exporter失败不推进cursor、提交后确认、幂等不回退、冲突、越权、插入后失败回滚、两个事务并发、未知事件定位及DDL一致/中文注释（部分断言在同一用例）。8项全部通过。
- 加上既有1项数据库崩溃恢复，本轮MySQL共9项通过；64项非数据库针对性回归通过，均0失败、0错误、0跳过。原始SDK停止原因缺陷未纳入该通过计数，原失败断言保留。
- 业务reactor compile通过。全量业务测试、全链路历史迁移parity未执行：common测试依赖缺失及当前deploy目录缺少V1-V4仍属既有基线问题；本次只验证V6表定义与最终结构对应一致，不能宣称全库升级链已通过。

收件组件尚未接入Master/Worker运行上下文，旧投影仍工作；接入、连续消费游标、后台消费者和尾部补偿分别由3.2-3.4实现。任务2.2只完成收件表部分，其余计划表未创建。preview仍未启用。

---

## 2026-11-04：P03 MySQL 持久确认与实际崩溃实验

使用缓存的 MySQL 8.4 镜像建立独占、随机端口的临时容器；随机密码仅经子进程环境传递，不读取应用配置。实验结束容器已自动删除，没有操作既有数据库。

- 官方 `JdbcDistributedStore` 与 `RemoteFilesystemSpec` 保存 SDK 日志和导出游标；实验 sink 在独立 MySQL 表中事务提交收件后才返回。
- 第一 JVM 提交3条日志，在第2条收件提交后、SDK确认前执行 `Runtime.halt(23)`，确认为退出码23。此时收件2条、消费效果0条。
- 第二 JVM 重新创建 Harness、读取数据库日志并 drain：游标到3，收件3条、业务效果3条，模型调用0次；重投第2条未重复插入。
- 第三 JVM 再次恢复及重复执行消费，仍为游标3、收件3、效果3、模型0次。
- `SdkMySqlContractTest`：1项通过，0失败、0错误、0跳过。三个新Java实验文件已用IDEA格式化后编译验证。

复现：`python3 experiments/sdk-contract/run_mysql_probe.py`。脚本只创建和清理自身临时容器，需要Docker及已缓存依赖；普通Maven运行缺少专用环境时会跳过该数据库实验，不能当作通过。

结合先前5项导出实验，任务1.3的SDK/数据库可行性准入通过，可开展第3组实现。这里的收件器、去重表和消费效果是隔离夹具，不是生产Inbox实现；多绑定身份、防毒事件、生产事务和并发消费者仍归第3组验证，不能宣称产品具备可靠消费能力。

整体准入仍未通过：P02审批拒绝/挂起恢复/并发输入，P04预算和公开渠道端到端，P05/P06仍待完成。停止原因适配修复已通过，原始SDK缺陷断言继续保留。

---

## 2026-11-04：停止原因丢失适配修复

任务1.4a已完成。生产源码位于 `server/agent-squad-server/src/main/java/com/agentsquad/squad/integration/agentscope/completion/`，为无数据库、日志或发布副作用的纯适配器，不启用v2运行或preview。

- 按同一绑定的连续已提交区间、Run/Turn/modelCallId核对dispatch、chunk和end，停止原因与用量分别保存来源序号。
- stop后usage-only覆盖原生end原因时，适配器取已提交chunk的stop和end的真实用量，不修改原始SDK日志。
- length/content_filter/tool_calls不算正常停止；未知、冲突、缺号、格式错误、缺少结束或无效生命周期不能确认正常成功。取消、失败不被stop覆盖。
- 没有可信用量时保持缺失，不能编造0；明确0可以保留。累计块不求和，最终来源矛盾不能结算。
- 只解码目标Run/Turn中的模型事件，不顺带解码上下文或Prompt。重复相同事件幂等，乱序重投排序，同ID或同序号异内容拒绝。
- 外层仍需校验turn/output、当前尝试、交付资格和开放效果；hasConfirmedNormalStop不是任务成功，hasTrustedUsage不自行执行预算结算。

### 本轮验证

| 范围 | 数量 | 结果 |
| --- | --- | --- |
| ModelCompletionNormalizerTest：边界、隔离、幂等、格式及计数 | 33 | 全部通过 |
| SdkNormalizedStreamContractTest：真实Harness流式日志 | 6 | 全部通过 |
| 原执行、导出及流式基础回归，不含已知上游缺陷 | 19 | 全部通过 |
| 针对性回归合计 | 58 | 0失败、0错误、0跳过 |

真实流式用例覆盖stop、length、content_filter、tool_calls、新原因、缺失原因并附usage-only尾块；每个都断言原生end原因仍为null，而适配结果独立正确、用量7/4且无额外模型调用。tool_calls用例只验证原因映射，不证明真实工具循环。

实验pom直接将生产completion目录作为sourceDirectory编译，没有复制算法。业务reactor主代码compile通过；全量测试仍受已有common测试依赖缺失影响，本轮未修改该既有pom。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home \
mvn -o -Daether.syncContext.named.factory=noop -f experiments/sdk-contract/pom.xml \
'-Dtest=ModelCompletionNormalizerTest,SdkNormalizedStreamContractTest,SdkExecutionContractTest,SdkExportContractTest,SdkStreamContractTest#separatesTextThinkingToolArgumentsAndUsageOnlyTail+eofWithoutFinishCanCompletePublisherWithoutProvingAnswerComplete+downstreamCancellationIsObservable' test
```

原始harnessPreservesFinishReasonWithUsageOnlyTail仍使用expected stop断言，不计入58项通过或跳过。无筛选全实验命令仍保留此上游契约失败；这与适配器准入通过是两个结论，不能声称SDK本身修复。

剩余P04的业务预算、完整输出渠道、重连、迟到块及客户端取消端到端未完成；MySQL收件、P05/P06仍未通过。不得凭1.4a完成打开preview或新发布链路。

---

## 2026-11-04：第一批固定 SDK 行为实验

### 环境与范围

- SDK：实际依赖 `2.1.0-BETA1`，项目固定 commit 对应 `5451c73abe47053340683a1cc6d67b290b056648`；本轮未修改 SDK。
- JDK：本机 JDK 27 运行，实验编译目标21；独立 Maven 项目 `experiments/sdk-contract`，未加入业务 reactor。
- 模型：仅 `127.0.0.1` 随机端口 HTTP 桩，固定安全测试文本，无真实模型调用。
- 存储：SDK内存日志及临时目录文件日志；两个独立 JVM 验证文件序号恢复。未操作实际数据库。
- 新建 Java 文件已调用 IDEA 2026.2.3 格式化，使用独立临时配置目录与现有默认代码样式；未关闭用户 IDE。
- 业务基线先运行失败：common已有测试因缺失Jackson/JUnit/AssertJ依赖无法编译，未修补用户已有pom修改。改用独立实验项目。
- 未配置 `SQUAD_TEST_MYSQL_PASSWORD`，未读取应用私有数据库配置替代；MySQL相关验证尚未执行。

### 可重复命令

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home \
mvn -o -Daether.syncContext.named.factory=noop -f experiments/sdk-contract/pom.xml test
```

该命令本轮预期非零：存在一项真实失败，未通过修改期望值把设计缺口变绿。离线noop仅避免受限Maven缓存的锁文件写入，不作为线上并发依赖下载建议。

### 汇总

| 实验类 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- | --- |
| SdkExecutionContractTest | 11 | 11 | 0 | 0 | 0 |
| SdkExportContractTest | 5 | 5 | 0 | 0 | 0 |
| SdkStreamContractTest | 4 | 3 | 1 | 0 | 0 |
| 合计 | 20 | 19 | 1 | 0 | 0 |

### P01：普通回复原生语义

| 输入 | 实测事实 | 判断 |
| --- | --- | --- |
| finish_reason=stop | 1次模型调用、有turn/output、Run completed | 支持自然回复不需要完成工具；仍需平台发布资格校验 |
| length | 1次调用，model/end保留length，但Run仍completed | 原生completed不是完整成功，长度截断必须单独处置 |
| content_filter | 1次调用，model/end保留content_filter，Run仍completed | 必须保留受限原因，不据completed发布完整成功 |
| null或空停止原因 | 1次调用、有turn/output，Run仍completed | 需要UNKNOWN/不完整政策，不能默认成功 |
| 非法模型JSON | Run failed，调用1次 | 协议错误不能产出成功；本探针重试上限1 |
| 达到最大迭代 | Run failed，run/end原因MAX_ITERATIONS | 技术失败有明确原因，不能取最后文本冒充正常答案 |

这里证明的是 SDK 行为；Reply v2 的完成分类及发布尚未实现和验证，不能把这11个通过描述成产品链路已通过。

### P02：终结工具

- 默认工具成功：模型调用2次，工具调用1次。
- returnDirect成功：模型调用1次，最终turn/output含工具实际结果及TOOL_RETURN_DIRECT。
- returnDirect工具返回error：模型调用2次，不直接终结。
- returnDirect与普通工具同批：模型调用2次，两工具各执行1次。
- 未覆盖：审批拒绝、挂起后恢复、并发补充输入；平台候选与最终交付也未接入本实验，因此任务1.2保持未完成。

### P03：导出确认与恢复

- 第2条事件已被sink接收但故意抛异常：SDK导出游标停在1；重新drain交付序号为1、2、2、3，内存去重结果3条。
- 同一内存store重新打开日志：导出游标保持3，不重复已确认事件；这不是进程重启证明。
- 两个独立exporter并发：第1条确实交付两次，最终游标3；对象级synchronized不能保证跨导出器唯一消费。
- 实际Harness上下文：sink引用经过复制进入真实运行；故意拒绝run/end，Run仍completed且游标落后；手工补drain追上head，模型调用仍1次。
- 文件日志独立进程：第一个JVM留下cursor=1、deliveries=[1,2]，第二个JVM读取同临时目录后cursor=3、deliveries=[2,3]。是正常进程结束后的持久游标恢复，不冒充kill/crash实验。
- 未覆盖：MySQL收件事务及持久幂等、真正崩溃窗口、多实例业务消费者；任务1.3保持未完成。

### P04：流式契约与准入失败

通过的基础行为：

1. 思考、文本、工具参数在SDK内容块中能区分；仅选TextBlock的实验拼接未混入思考与工具参数；usage-only尾块可提供真实7/4输入输出用量。此事实不代表任意模型正文都已经过产品安全筛选。
2. EOF没有finish_reason或DONE时，Publisher可以正常完成且所有块原因仍null；流完成不证明答案完整。
3. 下游take(1)能观察到CANCEL；未证明业务预算取消结算、客户端断开或完整页面行为。

失败用例：`SdkStreamContractTest.harnessPreservesFinishReasonWithUsageOnlyTail`。

```text
input: text --> finish_reason=stop --> usage-only --> DONE
committed model/chunk: finishReason=stop exists
committed model/end: finishReason=null
expected by original design: model/end.finishReason=stop
actual: null
```

原因与源码一致：SessionModels结束时使用最后一个ChatResponse；usage-only尾响应提供用量但未保留前面stop。失败用例先断言已提交model/chunk确实含stop，再断言model/end，独立重跑仍在stop/null断言失败。

**结论：P04准入不通过，禁止直接依赖model/end的停止原因启用流式成功发布。** 修订方向是在SDK适配层按modelCallId核对已提交块和model/end，保留明确观察到的停止原因及用量来源；缺失/冲突继续UNKNOWN。该规范化尚未实现，不更改SDK，不修改失败断言掩盖问题。

### P05 / P06

尚未执行。新汇合政策、异步交互、取消收口、持久收件和前端reducer均未实现，不能用模拟业务规则测试宣称新设计已端到端成立。现有业务回归还受common依赖基线与显式MySQL测试环境限制。

### 对方案与任务的影响

- 保留自然回复、终结工具与官方导出总体方向；新增模型结束事实规范化作为P04及Reply v2的前置工作。
- 保留失败测试，后续应另测规范化后结果，不要求原SDK突然满足并不存在的契约。
- 任务1.1-1.4当前只完成部分证据，所有准入任务暂不勾选；不以19个通过换算方案完成百分比。
- 本轮只新增独立实验和记录；无生产代码、业务schema、远程Git或真实库变更。


## 2026-11-04 普通聊天事件直达实施检查点

本节是早期实验记录之后的增量。任务0.3仍未完成整体验收，不勾选完成。

已实现但未完成整体验收：

- 接收消息、排队、执行状态、最终消息使用schemaVersion=2完整增量；前端按消息revision和执行version归并，已支持事件不再触发全量快照。未知事件或序号缺口进入重连恢复。
- 事务提交后唤醒会话级共享读取与推送；有界发送队列隔离慢订阅者，断开只清理订阅。移除逐连接500ms数据库读取，保留15秒补偿核对。唤醒目前仅限当前进程，Redis跨实例分发尚未接入。
- 新受理回复标记NATURAL，旧数据默认PROPOSAL；新增V7脚本与schema/baseline列，启动门禁同步版本7，未对真实库执行DDL。自然输出收口仍需模型替身集成验证。

局部验证证据：前端store测试8项通过，生产构建通过；ApiResultTest 4项与ConversationStreamHubTest 5项通过，后端测试源码可编译。Hub覆盖共享读取、读取期间新唤醒、慢订阅者边界、断开清理与序号缺口。common补齐的依赖仅为测试作用域。

尚待实施或验证：真实HTTP/SSE与浏览器断线重连、自然回答单次模型调用、真实正文分块预览、运行事件主动唤醒投影、OpenAPI同步、V7隔离MySQL结构比对、取消重试与任务协作事件、Redis故障恢复与跨实例分发。不得将本轮单测通过描述成以上项目已完成。


## 2026-11-05 普通聊天主动推送与自然输出验证

### 实现与范围

默认新受理与显式重新回答固化NATURAL协议，旧行保持PROPOSAL。自然回复隐藏并拒绝完成候选工具，以当前Run已提交turn/output与规范化模型结束原因发布。普通回复开启真实模型流，只选公开TextBlockDelta，临时正文与持久消息分开，终稿覆盖预览。命令受理提交后投递；Master/Worker上下文接入官方持久sink，收件提交后合并唤醒独立投影；会话公开事件提交后唤醒共享hub。前端普通已支持事件直接归并，不每事件重拉快照。进程内通知已接通，Redis跨实例尚未接入。

### 已通过证据

- 隔离MySQL后端组合回归26项通过：旧协议ResponseNativeExecutionIntegrationTest 14、真实HTTP/SSE 1、V7结构与旧行默认1、结构门禁3、共享hub5、预览UTF-16/容量2。旧协议夹具明确关闭主动唤醒并固定PROPOSAL，不修改原断言来宣称新协议通过。
- 独立官方SDK/MySQL与持久收件实验19项通过，包含SdkMySqlContractTest 1和RuntimeEventReceiptMySqlTest 18。
- 前端全量23项通过，生产构建通过；包含预览替换、重复/迟到版本、旧流、未知身份、终稿覆盖、断线清理与正常事件不刷快照。
- ConversationSsePushIntegrationTest使用真实HTTP、真实SSE、真实SDK与本地流式模型桩；命令扫描、普通投影扫描、SSE补偿均设为一小时。先看到第一段正文且模型仍被阻塞，再关闭连接，按Last-Event-ID重新订阅后释放模型，观察同一终稿；整个过程只调用模型一次，只有一条助手消息，快照读取没有再次推理。推理标记TEST_PRIVATE_REASONING未出现在公开预览。
- 追加隔离夹具：终稿已发布后删除末条收件并回退测试游标，独立尾部核对重新收件并补齐；助手消息仍一条，模型调用仍一次。此为尾部恢复输入模拟，不声称真实进程崩溃。
- V7仅在隔离库执行，确认最终列定义匹配baseline，旧RUNNING行默认为PROPOSAL且状态不改。OpenAPI已同步普通事件独立DTO和临时正文；JSON语法检查与OpenSpec严格校验通过。

### 实际发现与修复

1. 收件与投影同时重复INSERT引发死锁：绑定收件短事务改为独占锁序；主动补读优先复用已有收件，只对尚未收到的已提交事件补收，消费继续核对摘要。
2. 空SSE没有事件时HTTP响应头迟迟不返回：订阅后立即发心跳建立连接，不等待后续业务事件。
3. 页面断开触发AsyncRequestNotUsableException被全局包装为JSON并刷ERROR：单独结束已不可写响应，正常断线不记录业务失败。
4. 组合回归复现SDK末次导出遗漏：SessionRecorder.exporting合并并发flush，exporter只读取一次head水位，终结flush晚于该水位且close停止heartbeat。独立15秒尾部核对按所有已投递绑定的持久游标恢复，包含业务终态、不要求尚有RECEIVED；只读日志、幂等收件与投影，不构造/启动Worker或模型。仍需P07/P09进一步测故障与容量，不能据局部通过删除补偿。

### 未通过、受限与未执行

- 真实浏览器已实际提交消息，并见到排队、正在回复和第一段正文；截图/tmp/squad-chat-preview.png。浏览器后半段截图/关闭操作连续两次自动权限审核超时，未判定操作不安全，但未获继续操作许可；没有绕过检查。人工浏览器夹具因未能及时释放而失败，不能标为完整端到端通过。真实HTTP的断线、续读与终稿恢复已独立通过。浏览器登录身份由仅回环测试代理提供，未验证真实网关登录。
- 旧SchemaParityTest因仓库缺少deploy/db/migrations/V1至V4失败，完整历史迁移链未通过。本次不补造旧迁移，不把V7局部验证替代完整链。
- 未执行真实模型、外部发布、现有开发库DDL或生产操作，未提交推送当前工作。应用既有库前需审查V7列变更。
- Redis跨实例、全工具活动分类、任务计划步骤UI、完整Trace跨线程/跨服务、取消/重试/审批完整新协议、所有有限值清理与全项目回归仍未完成。任务0.3保留未勾选，原3/45不以局部单测扩大。


### 最终增补检查

真实HTTP/SSE现有2个参数化分支均通过：全部订阅关闭后让模型完成再重连，以及运行中重新订阅并收到终稿。两分支都核对公开首段、一次模型调用、一条终稿和终态尾部补齐。先前26项组合回归之后新增此分支，当前后端针对性覆盖合计27项（不是全项目测试）；末次HTTP2项通过，前端23项与build保持通过。OpenAPI内部引用解析检查通过。

浏览器夹具与仅回环的合成身份代理已保存至experiments/chat-browser/，复现步骤见README。临时验证容器均已停止，Vite已停止；没有遗留运行中的专用MySQL容器。当前变更未提交、未推送，真实库未执行V7。


### 2026-11-05 用户验收启动

按用户“服务启动，我验收一下”的要求，核对本地squad-mysql/agent_squad，补齐缺失V6收件表与V7协议列；现有账号、会话与业务数据保留，表数51变52，旧回复协议默认为PROPOSAL，新受理默认NATURAL。此记录更新前文“尚未对开发库执行V7”的历史状态；没有删除、重建或清空数据库。

使用JDK27构建当前代码并启动Server 18080、Gateway 18081、Vite 5173。两个后端health均UP，页面与同源认证路由可访问；未代用户发送真实模型请求。验收地址http://127.0.0.1:5173/login，沿用现有数据库账号。


### 2026-11-05 讯飞接入与异常反馈调整

验收中两次DeepSeek请求实际为HTTP402余额不足，先前被误归类为请求校验失败。新增MODEL_BILLING_UNAVAILABLE安全分类，后端按模型失败记录ERROR和业务身份/问题编号；前端只显示通用服务异常，不显示账户余额、供应商原始响应或密钥。去掉异常卡片边框、问题编号和重复失败答案，仅留轻量提示及后端允许的重新尝试/复制原消息；余额类错误不自动重试，显式重试仍受预算、版本、幂等及未知副作用屏障约束。

使用官方OpenAI兼容地址https://maas-api.cn-huabei-1.xf-yun.com/v2，模型spark-x2.6。用户提供的密钥直接存于本地application-spark-local.yml（600权限、Git忽略、未打包或提交），Master和Worker共同覆盖；启动参数加载该文件。没有用环境变量占位，也没有操作讯飞账号授权或验证码。

真实最小供应商请求HTTP200，stop结束且带usage。通过本地demo账号、真实Gateway、SDK和该供应商完成一个新的测试会话，状态SUCCEEDED、一条助手答案、无故障；只发送受控测试问候，不将旧业务会话作为探针材料。后端异常专项5项、前端26项及生产构建通过。当前Server18080/Gateway18081/Vite5173运行。


### 本地模型配置位置更正

按用户明确要求，将讯飞Master/Worker的API Key、base-url和模型名直接写入既有server/agent-squad-server/src/main/resources/application-local.yml；删除单独的application-spark-local.yml及其忽略规则。当前业务服务已重启并显式读取既有源配置文件，健康检查UP。密钥未写入文档、日志或提交，未重新打包该密钥。


### 2026-11-05 模型选择交付与回归边界

模型目录、受理时固定选择、SDK路由、预算Profile和重试沿用选择已实现，新增V8列并同步baseline/schema/OpenAPI。当前真实授权可用目录仅Spark-X2.6；其他模型配置后可进入目录，Worker自动按业务选模尚未实现。既有真实页面OFF/ON两次回复均成功，V8已用于本地库，无清空数据。

本次重新运行前端29项组件/Store测试、2项Node测试及生产构建通过。真实页面重新展开选择框，将自动切换为深入后标签立即更新；修复composer裁切后弹层完整可见，截图/tmp/squad-model-selector-final.png。没有为本次视觉检查发送额外模型请求。已有DynamicReplyModelIntegrationTest与ExecutionFailureIntegrationTest报告分别1项、5项通过，不能作为全量回归。0.4完成，0.3与其他整体任务不随之勾选。

并行问题按用户要求转入最终验收：本次隔离MySQL重新运行MultiWorkerExecutionIntegrationTest，串行、真并行和失败分支共3项通过；该测试直接发布计划，不证明聊天入口端到端。GoalTaskNativeCreationIntegrationTest的4个入口分支全部失败，第一轮报“模型响应缺少可信用量”，任务未创建；本地JSON模型桩与当前自然回复流式请求的协议是否匹配仍待检查，不将该结果直接认定为真实供应商故障。最终必须覆盖从页面消息建立目标、实际并行、汇总、公开答案和断线恢复。

本地业务库只读核对：当前目标/WorkerJob/WorkerDefinition均为0，检索到两首诗的来源消息有成功回复执行及answer_message_id，不存在可据此归因为Worker汇合卡住的证据。未改动该会话或重试历史，按用户要求暂不扩大专项排查。

### 2026-11-05 P07 Redis 唤醒协议第一阶段

新增experiments/redis-wakeup，固定官方Redis镜像digest，使用独立容器、认证、无网络、无映射端口、AOF always与noeviction，不连接现有Redis或业务库。enqueue/finish Lua验证dirtyVersion合并、原子续排与ACK、迟到确认隔离及容量拒绝。命令python3 experiments/redis-wakeup/run_probe.py本地8组通过，原始摘要/tmp/squad-redis-p07.log。初次测试对redis-cli RESP3返回格式读取错误，在测试适配后完整重跑通过，未改弱断言。

实际验证32并发生产者、生产确认8轮竞态、未ACK接管、重启pending保留及CONTROL独立通道。实验容器已清理，既有squad-mysql保留运行，未创建业务常驻Redis。

这只是Redis协议层证据，不证明业务幂等或完整故障恢复。MySQL提交/Redis发布间隙、业务提交后未ACK、完全丢失后的journal尾部补齐、同绑定业务互斥和真实取消优先级仍待联测；AOF always同容器重启不覆盖everysec断电或持久卷重建。任务0.1继续未完成，整体保持4/46，不移除旧消费和补偿路径。详见experiments/redis-wakeup/README.md。

### 2026-11-05 Redis命令唤醒实际接入

生产增加RedisCommandWakeup、独立配置与COMMAND/CONTROL通道。消息受理提交后优先发布引用；消费者复用原命令授权/租约/SDK幂等入口；ACK在持久领取/投递尝试结束后，容量不足不ACK；发布故障直接本地派发，扫描保留。其他目标与Worker入口尚未全部发提交后事件，补偿周期维持1秒，不能宣称数据库查询已减少。

隔离MySQL+Redis业务回归5项通过：RedisCommandWakeupIntegrationTest两分支验证实际Redis接手、重复引用不重复模型、pending接管和连接停用降级；DynamicReplyModelIntegrationTest一项；ConversationSsePushIntegrationTest两分支。Redis业务测试将定时补偿延长到十分钟，验证不依赖周期扫描启动模型。新Java均用IDEA明确路径格式化后编译；Maven package成功，git diff --check通过。Redis Lua协议实验与业务联测是不同证据层级。

本地`squad-redis`已按固定官方镜像启动，仅回环16379、认证、持久卷、AOF everysec及noeviction；密钥仅写既有本地配置，不输出或提交。Server已启用该链路并重启，health UP；Redis PING及两通道消费者组正常、pending为0。没有新增真实模型调用或数据库结构变更。

尚未完成的故障注入和跨实例通知见docs/redis-command-wakeup.md。任务0.1及3.5保留未完成，整体4/46，不把一条生产链路交付当作整个调度架构完成。
