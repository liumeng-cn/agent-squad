# 对话体验契约收敛复核（2026-10-29）

## 本轮结论

完成任务 5.2 的静态一致性复核，未归档变更。4.4 的原生中文输入法实机验证与 5.1 的端到端验收仍独立保留，不以本次检查替代。

- 六个能力 delta 的 Requirement 与 Scenario 均已存在于对应主 spec；主规范保留原有未被替换的场景，没有重复创建并行规范。
- OpenAPI 46 个操作、74 个 Schema；ui-manifest 的 7 个规划路由完整覆盖操作。Skill、记忆和连接规划路由不代表当前前端已实现。
- 新增自动检查覆盖会话及任务 SSE 的 after/Last-Event-ID、对话内发送/停止/重试/审批/下载/核对动作；聊天工作台不绑定 createTask。
- 迁移最终字段检查覆盖 Scope、共享预算账户、目标修订号、标题来源及独立版本。当前源码最高迁移为 V13，标题字段在 V7；修正 README 与历史记录中的 V14 证据边界。本轮没有连接或更改实际开发数据库，不能据此报告当前开发库版本。
- D4 tasks 已要求普通回复与任务双路径固定 Skill、Message 来源与 Scope 删除屏障；D5 要求共享 BudgetAccount、对话内交付与故障；D6 覆盖消息/指令/goalRevision/预算备份及并发聊天、会话水位、IME。三阶段均保持未实现。
- D3 原验收基线保留在 d3-multi-worker/evidence，旧验收与本轮对话验收分开。真实模型、确定性 SDK 测试、静态检查和生产演练仍分别记载。

## 本轮验证

- `openspec validate --all --strict`：14/14 通过。
- `tools/check_design.py`：通过，包括 17 个正例、11 个反例、28 张表及新增一致性断言；使用已有 `/private/tmp/agent-squad-design-venv`，没有安装应用依赖。
- `npm --prefix web/console run build`：类型检查与生产构建通过。
- 键盘规则自动化：2/2 通过；不等同原生输入法实机验证。
- 后端全量 `mvn -o -Dmaven.repo.local=/private/tmp/agentsquad-m2 -f server/pom.xml -B -ntp test`：99 项，81 通过、18 跳过，无失败/错误。MySQL 专项因本轮未配置专用库而跳过；未调用真实供应商模型。日志 `/private/tmp/chat-continuation-regression.log`。
