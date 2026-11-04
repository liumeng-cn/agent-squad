# 实验记录

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
