# C1.0.1 基线证据

- 基线提交：`23c057bf5d138f12d772edf4b673a16b4533f0c5`（`main`）
- 工作分支：`feature/multiuser-c1-identity`
- 分支创建时间：2026-11-02
- 创建分支前工作区：除未跟踪的 `openspec/changes/multiuser-0*` 与多用户交付包外无改动；
  既有修改未被覆盖。

## 后端全量测试

命令（在 `server/` 下，凭据来自 `.env.local`，未写入日志）：

```bash
mvn -B -ntp test
```

真实结果（2026-11-02 15:26）：

```text
Tests run: 137, Failures: 1, Errors: 0, Skipped: 22
BUILD FAILURE
```

唯一失败：

```text
GoalTaskPersistenceTest.shouldKeepTaskCommandAndEventAcrossApplicationRestart
expected: "PENDING"
 but was: "DELIVERING"
```

隔离重跑 `-Dtest=GoalTaskPersistenceTest`：`Tests run: 1, Failures: 0`，通过。
判定为投递调度与断言检查之间的既有时序 flake，不是本次基线引入的回归；C1 不修改该测试。

跳过项（环境门控，共 22）：

- `MySqlConversationReadTest`、`MySqlGoalTaskCreationTest`、`MySqlKnowledgeGovernanceTest`、
  `MySqlMessageAcceptanceTest`、`MySqlMessageOrderingTest`、`MySqlResponseContextTest`、
  `MySqlScopeRuntimeOwnershipTest`、`MySqlTaskDirectiveTest`：需要 `SQUAD_MESSAGE_TEST_URL`。
- `HarnessDockerSandboxDriverTest`：需要 `SQUAD_SANDBOX_TEST_IMAGE=sha256:<64位>`。

## 数据库基线

- 开发库 `agent_squad`：44 张表，未做任何删除或重建。
- 测试隔离库：本轮结束后 `as_test_%` 数量为 0（shutdown hook 清理生效）。
- 新增 `V1__baseline.sql` 身份四表后，空隔离库结构为 48 张表；生产快照
  `deploy/db/migrations/V1__initial_44_tables.sql` 与 `V2__multiuser_identity.sql`
  已通过 `SchemaParityTest` 与新 baseline 对齐。

## 设计检查（C1.0.3 更新契约后）

```text
PASS: 52 operations, 83 schemas, 45 table definitions, 0 FK references,
17 valid / 11 invalid schema examples; 12 requirements traced; 8 UI routes cover all APIs
Final schema: server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql (48 tables)
```

`openspec validate multiuser-01-identity-trust --strict`：通过。
