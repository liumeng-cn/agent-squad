# 发布门槛（2026-11-03）

状态：**production not yet approved / NOT PRODUCTION READY**。当前只记录本次实际执行；历史分支的测试结果不继承为整合验收。

| 门槛 | 状态 | 本次证据或限制 |
|---|---|---|
| OpenSpec strict | PASS | `openspec validate --all --strict`：13 项通过 |
| 设计与契约 | PASS | `python3 tools/check_design.py`：52 operations、82 schemas、51 张最终表；修复旧 manifest/traceability 漂移 |
| 正式故障入口静态检查 | PASS | `python3 tools/check_runtime_safety.py`，正式 Java 无 Runtime.halt 路径 |
| Gateway 回归 | PASS | 本机 JDK27/Maven，隔离 MySQL，首轮修复后 11 项通过；生产参数负向测试随最终整合回归 |
| 前端测试及构建 | PASS | C3 证据记录；消息回放、SSE、草稿、幂等、Markdown |
| 全量后端及 schema parity | PASS | 最终全量 Gateway 12 + Server 174 = 186 项，0失败/错误/跳过；后续交叉审查修复定向30项通过；Task快照修订修复另有定向回归，最终报告见下方记录 |
| 远程 CI | NOT_RUN | 新增 ci.yml，执行 test/build/verify 和零 skipped 审计；尚未推送或运行远程 workflow |
| Compose 配置 | PASS | 假占位值 `docker compose config --quiet` 通过；未启动 staging |
| 生产认证配置 | PASS | Gateway负向测试与SchemaVersionGateTest/SchemaParityTest通过；仅表示本地自动化，不代替真实部署配置验收 |
| 镜像与实际 HTTPS | NOT_RUN | 新增 readiness 健康检查和优雅关停；未构建/运行镜像，未配置真实 TLS |
| Java21 运行兼容性 | BLOCKED | 本机只有 JDK27/8；正式已验证基线标为 JDK27，不宣称21运行支持 |
| V5 历史数据升级 | BLOCKED | 旧请求需审计回填/排空；拒绝自动猜测历史终稿权威，见发布验证手册 |
| C2 完整多用户隔离/跨进程 SDK | NOT_RUN | 占位开关默认关闭；普通4/控制2只是配置初值，无容量承诺 |
| 真实模型流式/真实双用户浏览器 | NOT_RUN | 未新增真实模型调用或费用，无完整流式声明 |
| 压力、完整备份还原、故障演练 | NOT_RUN | 无真实 staging RPO/RTO、p95/p99 或发布容量数据 |
| 正式发布批准 | BLOCKED | 用户未批准生产部署；未操作生产/共享数据库、未公开端口 |

日志：本次 Maven 输出保存在 `/tmp/agent-squad-final-full.log`、`/tmp/agent-squad-review-regression.log`；临时日志不进入 Git，测试使用新建隔离容器的 23306 端口，不读取 `.env.local`。

运行限制：维持单业务执行实例；不能把同 JVM 的 Stub 或共享 MySQL 测试当作真实跨进程 SDK 接管证明。发布前必须解除 blocker，并保留 `PASS/FAIL/BLOCKED/NOT_RUN` 原始结论。

## 目录整理

按用户要求删除8个已归档变更目录（82个文件），保留4个在途变更与现行规格；根README及OpenSpec入口已重写。构建输出与缓存清理前，完整测试报告存入临时验证目录，不以报告文件删除影响既有通过结论。

## 检查范围补充

生产版本迁移 dry-run 通过隔离 MySQL 的 `SchemaParityTest` 执行真实 V1→V5 SQL，与最终 baseline 比较全部表、列及索引；不代表生产数据库已经升级。

目录整理保留了 `.env.local`、IDE/智能体配置、`node_modules` 与 `.agentscope` 执行事实，只删除明确的归档、系统垃圾和可再生构建输出。

## 最终结果

最新 Task 快照修订回归19项通过；完整全量186项与后续增量回归汇总193个测试全部通过，0失败、0错误、0跳过。前端15项测试、生产构建和后端打包通过；后端打包使用已验证测试结果后的 `-DskipTests package`，不据此重复计算测试。

Maven XML报告已复制到 `/tmp/agent-squad-verification-20261103/`，构建输出清理后仍可复核。删除两个模块target和前端dist，共释放约171 MB（163 MiB）；开发依赖与运行事实保留。

本地修复提交：`1ca5775`，分支 `codex/parallel-runtime-fixes-20261103`。未推送远程、未部署生产；在途变更继续保留，不将部分任务验收冒充全部发布门槛通过。
