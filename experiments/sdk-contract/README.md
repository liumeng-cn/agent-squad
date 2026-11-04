# 固定 SDK 契约实验

此目录是独立 Maven 实验项目，不加入业务 reactor，不启动 Spring 应用。直接编译生产completion适配器源码，同时使用固定 `2.1.0-BETA1`、回环 HTTP 桩、临时内存/文件日志；不读取应用私有配置、不调用真实模型、不操作开发数据库。

## 运行

项目编译目标 Java 21，本轮使用已安装 JDK 27。依赖已在本机缓存时可离线运行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home \
mvn -o -Daether.syncContext.named.factory=noop -f experiments/sdk-contract/pom.xml test
```

`noop` 仅用于本轮离线只读依赖缓存，避免在受限的共享 Maven 缓存目录创建锁文件；不要据此认为在线并发下载也无需依赖锁。其他机器可使用常规 Maven 命令解析依赖。

## 本轮结果

停止原因规范化修复后，针对性回归58项全部通过：33项边界测试、6项真实SDK适配、19项基础回归。明确排除下述已知上游缺陷，未计作跳过或已修复；选择命令见OpenSpec的validation.md。无筛选全实验命令仍保留该SDK契约失败。

首批历史结果（2026-11-04）：20 项，19 通过、1 失败、0 错误、0 跳过。失败保留为上游契约证据，不能修改断言掩盖 SDK 行为。

- `SdkExecutionContractTest`：11 项，普通自然输出、停止原因、异常、迭代上限、工具直接返回及混合批次。
- `SdkExportContractTest`：5 项，确认丢失重投、游标重开、并发 exporter、运行上下文 sink 与末次失败补偿、两个独立 JVM 的文件日志恢复。
- `SdkStreamContractTest`：4 项，内容类型、usage-only 尾块、EOF、取消和 Harness 原生日志完整性；其中 `harnessPreservesFinishReasonWithUsageOnlyTail` 不通过。

失败输入：文本块 → 明确 stop 块 → usage-only 尾块 → DONE。SDK 已提交 model/chunk 中存在 stop，但 model/end 的 finishReason 为 null。现已实现并验证按modelCallId核对已提交块的生产规范化算法；SDK未修改，原始失败断言不变。

测试结果在 `target/surefire-reports`，不提交构建目录。正式说明和待验证范围见 OpenSpec 变更中的 validation.md。

## 边界

未验证 MySQL 收件事务、数据库跨进程恢复、业务预算、审批并发、真实页面和性能。内存幂等集合不等于持久幂等。两个 JVM 的文件恢复不等于数据库或崩溃故障注入。恢复/拒绝工具、并发补充输入及完整输出重连仍需后续实验。
