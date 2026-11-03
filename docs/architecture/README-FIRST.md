# 开始执行 Agent Squad 多用户实施包

**先读**：`AGENT_SQUAD_MULTIUSER_EXECUTION_PLAN.md`（架构与阶段门槛）。

**交给本地执行体**：复制 `LOCAL_EXECUTOR_PROMPT.md` 全文作为执行提示词；确保执行体实际能访问完整本地仓库、隔离测试 MySQL、JDK/Maven 和 Node。设计来自 GitHub `main@23c057bf5d138f12d772edf4b673a16b4533f0c5`，执行前须核对 `git status` 和最新代码。

**把变更安装到本地 OpenSpec**：将本包 `openspec/changes/multiuser-*` 目录复制到仓库 `openspec/changes/`，在仓库根目录阅读各 change 的 `proposal.md`、`design.md`、`tasks.md`、`specs/`。执行顺序 C1→C2→C3→C4；所有 tasks 初始未完成，不代表代码已修改。

**最小安全门槛**：不重建/清空本机现有开发数据库；不执行生产迁移/上线；不推送密钥；不在测试失败时无根据勾选任务。正式对外发布必须由用户另行审批。

交付包目录：

```text
AGENT_SQUAD_MULTIUSER_EXECUTION_PLAN.md
LOCAL_EXECUTOR_PROMPT.md
README-FIRST.md
openspec/changes/
  multiuser-01-identity-trust/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/access/spec.md}
  multiuser-02-admission-isolation/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/runtime/spec.md,specs/tools-interactions/spec.md}
  multiuser-03-chat-agent-experience/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/{web-console,events-artifacts,context-memory,master}/spec.md}
  multiuser-04-release-readiness/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/{runtime,access}/spec.md}
```