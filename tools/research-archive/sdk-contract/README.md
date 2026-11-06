# 固定 SDK 契约实验

此目录是独立 Maven 实验项目，不加入业务 reactor，不启动 Spring 应用。直接编译生产completion、inbox与journal适配器源码，同时使用固定 `2.1.0-BETA1`、回环 HTTP 桩、临时内存/文件日志；不读取应用私有配置、不调用真实模型、不操作开发数据库。

## 运行

项目编译目标 Java 21，本轮使用已安装 JDK 27。依赖已在本机缓存时可离线运行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home \
mvn -o -Daether.syncContext.named.factory=noop -f tools/research-archive/sdk-contract/pom.xml test
```

`noop` 仅用于本轮离线只读依赖缓存，避免在受限的共享 Maven 缓存目录创建锁文件；不要据此认为在线并发下载也无需依赖锁。其他机器可使用常规 Maven 命令解析依赖。

## 历史实验结果

历史记录：64项非数据库针对性回归通过，专用MySQL容器验证11项收件/journal/Provider测试及1项不同工作目录的SDK崩溃恢复。没有包含原始SDK已知失败，也不等于产品端到端通过。MySQL入口为 `python3 tools/verification/run_mysql_probe.py`；读取生产Mapper XML，不复制生产SQL到测试算法。

停止原因规范化修复后，针对性回归58项全部通过：33项边界测试、6项真实SDK适配、19项基础回归。明确排除下述已知上游缺陷，未计作跳过或已修复；选择命令见OpenSpec的validation.md。无筛选全实验命令仍保留该SDK契约失败。

首批历史结果（2026-11-04）：20 项，19 通过、1 失败、0 错误、0 跳过。失败保留为上游契约证据，不能修改断言掩盖 SDK 行为。

- `SdkExecutionContractTest`：11 项，普通自然输出、停止原因、异常、迭代上限、工具直接返回及混合批次。
- `SdkExportContractTest`：5 项，确认丢失重投、游标重开、并发 exporter、运行上下文 sink 与末次失败补偿、两个独立 JVM 的文件日志恢复。
- `SdkStreamContractTest`：4 项，内容类型、usage-only 尾块、EOF、取消和 Harness 原生日志完整性；其中 `harnessPreservesFinishReasonWithUsageOnlyTail` 不通过。

失败输入：文本块 → 明确 stop 块 → usage-only 尾块 → DONE。SDK 已提交 model/chunk 中存在 stop，但 model/end 的 finishReason 为 null。现已实现并验证按modelCallId核对已提交块的生产规范化算法；SDK未修改，原始失败断言不变。

测试结果在 `target/surefire-reports`，不提交构建目录。正式说明和待验证范围见 OpenSpec 变更中的 validation.md。

## 边界

专用MySQL 8.4容器实验已验证收件提交后、SDK确认前halt，以及新JVM重投和重复消费幂等：1项通过、无跳过。运行 `python3 tools/verification/run_mysql_probe.py` 自动创建并清理独占容器；直接Maven运行没有专用环境变量时跳过该项。实验收件和效果表仅是夹具，不代表生产消费者已实现。未验证业务预算、审批并发、真实页面和性能。恢复/拒绝工具、并发补充输入及完整输出重连仍需后续实验。

## Journal存储修正

只配置JdbcDistributedStore与RemoteFilesystemSpec时，固定SDK默认journal仍可能走本地文件。数据库恢复实验现在显式注入生产RuntimeJournalStore，三个JVM采用不同工作目录，并断言没有本地journal；先前共用目录实验仅证明MySQL收件与本地日志恢复，不能当成数据库journal证据。当前Provider联测不等于Master/Worker业务入口已切换，完整说明见validation.md。

## 现有路径纯读回归

`CommittedSessionReaderTest`新增6项，直接验证生产纯读读取器和真实SDK的挂起/答复收据、跨Agent workspace与用户隔离。连同64项既有非数据库回归，本轮70项通过；MasterAgent的readEvents/inspectTask已接入该读取器，恢复由后台显式startPending调用。未移除后台轮询，也未切换旧日志后端。

## 持久消费回归

现有后台已接入先收件再事务消费；最新18项生产收件/消费/journal/Provider测试、1项实际崩溃恢复在专用MySQL容器通过，另70项非数据库回归通过。合计89项针对性验证，不含已知原始SDK停止原因失败，也不是全部业务/页面验收。消费业务回调使用测试效果表夹具；真实NativeProjectionService仅完成编译，完整业务回归未执行。启用现有投递路径需先具备V6表；Redis尚未安装或联调。
