# D0 验证记录

更新时间：2026-10-20

## 已完成

- `server/pom.xml` 固定 Java 21、Spring Boot 3.5.6 与 AgentScope 2.1.0-BETA1。
- `D0DependencyProbeTest` 验证 AgentScope `SessionKey` 可编译调用，生成的框架标识长度不超过设计上限 255。
- `D0DurableCommandRecoveryTest` 验证三个底座语义：
  - 命令先写入持久介质，再进入后续执行阶段；
  - 使用新的 `FileCommandStore` 实例模拟进程重启后，可以找回 `ACCEPTED` 命令；
  - 同一 `commandId` 同一输入重复提交返回原收据，输入变化被拒绝，执行标记可重复调用而不重复执行。

## 验证命令与结果

在 `server` 目录执行：

```text
mvn -B -ntp test
```

结果：3 个测试通过，构建成功。

## 明确边界

`FileCommandStore` 是 D0 的确定性离线探针，不是生产实现，也没有声明支持多进程并发、MySQL 崩溃恢复或 AgentScope 原生日志写入。D1 将把命令受理接入 MyBatis 事务、平台事件投影和 AgentScope `AgentSession.submit`，并补充真实 SDK 的持久恢复实验。当前尚未启动服务、连接模型或执行数据库迁移。
