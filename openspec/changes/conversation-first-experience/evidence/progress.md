# 对话体验改造实施证据

## 2026-10-28：1.1 消息与执行基础

完成 1.1，整体 1/16。此处是数据及传输基础，不代表消息受理、普通回复或新页面已经可用；其余任务保持未勾选。

### 实现与边界

- V7 新增消息、独立回复执行、统一 Scope、BudgetAccount、会话事件和消息附件关联，补充会话顺序/标题/最近活动字段及旧对象的 Scope 引用；V1–V6 不修改。
- V8 Java 迁移在平台事务中回填旧 Task 的原始用户消息与最新公开答案、Scope/账户和旧运行对象引用；不把内部工具事件或完成候选当正文，不重跑模型或创建 Command，不虚构历史 ResponseExecution。
- 稳定业务 ID 与来源键保证重放不重复；原始 Session、Command ID、幂等 submit key、原生 Turn/Run、水位和 Reservation 金额保持不变。账户额度由旧 Task 固定，SETTLED/RESERVED/UNKNOWN 仍是原结算事实。
- Scope 查询检查用户、会话状态/代际、Scope 和真实 owner 的逻辑删除；数据库复合外键阻止跨用户引用。
- OpenAPI 新增消息与执行 DTO schema，明确标为 foundation-dto；同步数量清单、模块表归属与需求追踪。未添加尚不可执行的新 HTTP 路径。
- 尚未切换运行归属：新旧服务不能据此无任务调用模型；旧入口新建的 Task 目前仍走旧受理链路。1.2–1.4 接入后才进入普通回复验收，实际开发数据库和当前页面本轮未升级/切换。

### 已执行验证

| 验证 | 结果 | 实际覆盖 |
| --- | --- | --- |
| H2 V6 → V8 | 通过 | 旧任务/公开答案/附件归属、顺序、scope/account 引用、预算守恒、稳定 SDK 身份、回填重放、跨用户/删除/代际拒绝 |
| H2 错误数据回滚 | 通过 | 历史 JSON 不是受支持对象时整批 V8 回滚，不遗留账户、Scope、消息或事件，UNKNOWN 原事实保留 |
| MySQL 8.4 V6 → V8 | 通过 | 专用隔离容器、全新测试库执行相同迁移与归属/预算/重放验收；未访问实际 agent_squad 开发库 |
| 后端 clean test | 40 项，0 失败/错误，1 跳过 | 39 项实际通过，包括专用 MySQL 迁移和 D3 派发、复用、恢复、取消、交互、预算回归；跳过未设置 SQUAD_DB_URL 的 MySqlGoalTaskIntegrationTest |
| 静态契约检查 | 通过 | 39 个操作、67 个 schema、24 张业务表、需求追踪、正反例及页面映射；不代表这些规划接口全已实现 |
| 新 Java 文件 IDEA 格式化 | 16/16 成功 | 仅本切片新文件，未全项目格式化 |
| OpenSpec strict / git diff --check | 通过 | 新变更规范与文本差异 |

命令：

```bash
SQUAD_MIGRATION_TEST_URL='<专用测试库>' SQUAD_MIGRATION_TEST_USER='<专用测试用户>' SQUAD_MIGRATION_TEST_PASSWORD='<专用测试密码>' \
  mvn -o -Dmaven.repo.local=/private/tmp/agentsquad-m2 -f server/pom.xml -B -ntp clean test
PYTHONPATH=/private/tmp/agentsquad-design-deps /usr/bin/python3 tools/check_design.py
openspec validate conversation-first-experience --strict
git diff --check
```

测试使用 AgentScope 2.1.0-BETA1、固定的本地模拟模型服务，不是实际供应商模型功能验收。编译产物 major version 65（Java 21），本机执行 JVM 是 Oracle Java 27；真实 Java 21 运行仍待 D6 补证。Flyway 对 MySQL 8.4 发出版本兼容提示，本次实际迁移实验通过，不等于完整数据库兼容/发布验证。

本地日志：`/private/tmp/agentsquad-conversation-full-test.log`、`/private/tmp/agentsquad-conversation-foundation-verified.log`。专用迁移容器实验后清理，实际开发 MySQL 保留。

### 下一切片

1.2 将真实运行链路改为 Scope/Account 归属，使无 GoalTask 的回复也具有预算、授权、交互、附件和 guard；完成后再开放消息受理和普通回复执行。对话界面切换在 4.1–4.4，不能提前将这次迁移称为体验验收完成。
