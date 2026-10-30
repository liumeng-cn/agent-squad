# D5 本地验收记录

记录时间：2026-10-30。沙箱驱动、产物交付、迁移 V17 与页面均已提交；本轮补齐验收证据，重点是 D5 任务 1.1 要求的真实容器隔离实验。测试结果见 `test-results.json`。

## 验收范围与边界

- 采用 conversation-first-experience 的 ExecutionScope/BudgetAccount、会话交付与故障动作契约，及 D4 的固定上下文。
- 沙箱默认关闭；脚本仅经受控工具与 Sandbox 执行，宿主 shell/Docker socket 不作为默认安全配置。
- 真实容器实验使用固定摘要的 `public.ecr.aws/docker/library/python:3.12-slim`（image ID `sha256:a6e34c598f24...`），通过 `SQUAD_SANDBOX_TEST_IMAGE` 传入；未固定摘要时实验跳过，保持默认禁用语义。
- 未调用真实模型；模型响应由 loopback HTTP 桩提供。

## 沙箱隔离执行

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 默认关闭/拒绝可变镜像 | 驱动默认禁用；未固定摘要或可变镜像（`python:latest`）时 `requireAvailable` 抛 CAPABILITY_UNAVAILABLE | HarnessDockerSandboxDriverTest.disabledOrMutableImageCannotExecute |
| 实际执行限制/A16 | 真实容器内：内存 128M、CPU 1、PID 64、uid 65534、nonewprivs、只读根、网络 none、无 docker.sock、无宿主 home；脚本写宿主被拒、出网被拒 | HarnessDockerSandboxDriverTest.actualContainerEnforcesPolicyAndReturnsOnlyNamedFiles |
| 超时与逃逸阻断 | `while True` 触发超时；符号链接逃逸被拒；输出上限 64KiB 截断 | HarnessDockerSandboxDriverTest.actualTimeoutAndSymlinkCannotEscapeAndOutputIsBounded |
| 资源限制 | 256M 分配触发 OOM 非零退出；PID 上限阻止子进程派生 | HarnessDockerSandboxDriverTest.actualMemoryAndProcessLimitsAreEnforced |
| 分离写入器清除 | double-fork/setsid 后代在提取前被清除，工作区不被篡改 | HarnessDockerSandboxDriverTest.detachedWritersAreKilledBeforeExtraction |
| 日志篡改拒绝 | stdout/stderr 符号链接与 FIFO 被拒，不做无界读取 | HarnessDockerSandboxDriverTest.logSymlinkAndFifoAreRejectedInsteadOfUnboundedRead |

## 产物交付与下载

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| stage/commit 受控交付 | Worker/Sandbox stage/commit 受控端口交付；提交失败不标 READY；附件未 READY 不提交无效引用 | ArtifactDeliveryIntegrationTest.durableClaimStageCommitAndRetention |
| 归属与清理 | 下载后端重验授权；删除先禁读再物理清理；保留策略生效 | ArtifactDeliveryIntegrationTest |

## 页面

- 对话输入附件卡与助手交付卡（B03/B10）位于 `web/console/src/features/chat/`；沙箱/产物故障接入统一分类与 allowedActions 在对话内说明。前端 `npm run build` 通过。

## 汇总与复现

- D5 定向测试：10 项，0 失败，0 跳过（真实容器实验在固定镜像下全部运行）。
- 真实容器实验 6 项全部通过；未固定摘要时其中 5 项跳过（保持默认禁用语义），仅"默认关闭/拒绝可变镜像"1 项运行。
- 完整套件：131 项，0 失败，0 错误，19 项跳过（默认配置外的专用 MySQL 集成测试）。
- 前端 `npm run build` 通过；未调用真实模型、真实连接器或生产系统。
- Java 21 运行时复验仍未完成（见 D3 证据同项限制）。

复现运行：

```sh
# 真实容器隔离实验（需固定摘要镜像）
SQUAD_SANDBOX_TEST_IMAGE=sha256:<image-id> mvn -f server/pom.xml -B -ntp test \
  -Dtest='HarnessDockerSandboxDriverTest' -DfailIfNoTests=false
# 产物交付（需 MySQL）
SQUAD_TEST_MYSQL_PASSWORD=<mysql根密码> mvn -f server/pom.xml -B -ntp test \
  -Dtest='Sandbox*,Artifact*' -DfailIfNoTests=false
cd web/console && npm run build
```

沙箱真实容器实验依赖本机 Docker 与已固定的 python 镜像摘要；`SQUAD_SANDBOX_TEST_IMAGE` 未设置或不匹配 `sha256:[0-9a-f]{64}` 时，真实容器实验跳过，驱动保持默认禁用。
