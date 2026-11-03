# Tasks：C4 CI、部署、监控、灾备与发布验收

## 0. 工程化基线
- [x] C4.0.1 确认 C1–C3 已通过/已知阻碍，制作 release feature 分支；对照全部 OpenSpec、OpenAPI、DDL、README 清除陈旧的单账号、Flyway、Java21 等虚假声明。
- [ ] C4.0.2 新增 CI：Node22 `npm ci/test/build`、JDK27 Maven 全量、隔离 MySQL8.4、设计检查与 skipped 审计；测试没有必需环境时 CI 必须失败而非成功跳过。验证一轮干净 CI 日志。
- [ ] C4.0.3 单独跑 JDK21 runtime 兼容性冒烟，记录真实 SDK/框架问题；不兼容即将正式运行环境明确标为 JDK27 并开兼容修复 issue，不能虚称 Java21 支持。

## 1. 配置与部署
- [ ] C4.1.1 添加 local/test/prod 分层配置、生产 fail-closed 开关、Secret 注入和内部身份密钥轮换指南；prod 禁用 schema-init/DLL，安全 CORS。
- [ ] C4.1.2 创建独立可重现 Gateway/Server 镜像和隔离 staging Compose/部署清单；只暴露 Gateway 经 TLS Proxy，Server/DB 内网可达；镜像非 root、健康检查、优雅关停与回滚说明。
- [ ] C4.1.3 生产 schema 迁移 dry-run 与 schema-parity；备份前检查、无损回滚方案写入 `deploy/runbooks/`；不执行真实生产库操作。

## 2. 可观测性与安全
- [ ] C4.2.1 增加 requestId 贯穿 Gateway、业务 HTTP、持久 Command、SDK 投影、Operation、Worker 日志，避免记录 Prompt/Token/Secret；日志结构化字段可关联。
- [ ] C4.2.2 Actuator liveness/readiness、Micrometer 指标和只在内网可访问的监控端点；按实测设最小告警集并做告警触发试验。
- [ ] C4.2.3 完成两用户完整越权矩阵、XSS/Markdown、CSRF、CORS、认证头、文件访问、模型工具权限测试，导出 `evidence/security-matrix.md`。

## 3. 容量、故障、恢复
- [ ] C4.3.1 staging 使用可控 Stub 做逐级压测，记录 HTTP p50/p95/p99、模型/Worker 并发、SSE 连接、MySQL CPU/连接和队列延迟，确认实际发布容量参数。
- [ ] C4.3.2 经隔离 staging 完整备份→还原→验证数据（44 + 新增表）、SDK journal、UNKNOWN 账本、密钥与会话撤销语义，记录真实 RPO/RTO。
- [ ] C4.3.3 演练重启 Gateway、单业务执行实例重启、SSE 断线以及一个受控 UNKNOWN 故障；不触发真正外部写副作用。报告包含崩溃窗口和恢复结果。
- [x] C4.3.4 编写 `evidence/release-gate.md`：逐项 `PASS/FAIL/BLOCKED/NOT_RUN`，附执行命令/日志文件/日期/部署容量与限制。

## 4. 发布决策
- [ ] C4.4.1 仅在全部 blocker 解除且用户明确批准后可安排正式生产迁移和部署。执行体不得自行部署生产、删除/重建任何数据库或把业务端口开放公网。
- [ ] C4.4.2 在尚未生产发布时可以提交经过验证的代码、staging 配置、OpenSpec 变更与验收证据，明确标注“production not yet approved”；只有验收结束才归档 OpenSpec changes。
