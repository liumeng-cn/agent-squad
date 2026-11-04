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
