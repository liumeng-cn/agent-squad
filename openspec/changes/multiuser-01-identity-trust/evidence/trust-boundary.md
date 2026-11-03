# C1.3 可信边界与执行安全点证据

验证时间：2026-11-02；真实 MySQL 隔离库 `as_test_*`，测试 JVM 正常退出后自动删除；未触碰开发库。

## 1. 网关清理与短效断言（C1.3.1）

实现位置：

- `server/agent-squad-gateway/src/main/java/com/agentsquad/squad/gateway/filter/GatewayAuthenticationFilter.java`
  - 校验持久会话与 CSRF 后，删除浏览器传入的 `Cookie`、`X-CSRF-Token`、所有
    `X-User-*` / `X-Squad-*` 头，仅写入 `X-Squad-Assertion`。
  - 断言绑定实际 HTTP 方法、`getRawPath()`、`X-Request-Id`、用户/角色与 `authVersion`。
- `server/agent-squad-server/src/main/java/com/agentsquad/squad/identity/AssertionVerifier.java`
  - 只接受单个断言头；校验长度、base64url、JSON 对象、kid、常量时间 HMAC、
    iss/aud、UUID sub、角色白名单、精确方法与路径、TTL 与时钟偏移。
  - 任一失败记录原因并返回 401；业务侧不从裸头构建身份。
- `server/agent-squad-server/src/main/java/com/agentsquad/squad/identity/UserContextInterceptor.java`
  - `/api/**` 统一验签后写入 `UserContext`；OPTIONS 预检除外。

真实 HTTP 集成测试（Netty 上游 + 真实网关 + 真实 Cookie）：

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- GatewayProxyHttpTest
```

覆盖：登录转发、浏览器伪造 `X-User-Id`/`X-User-Role` 被移除、
上游只收到签名断言、multipart 原始字节保持、SSE 首帧透传、
302 状态与 `Location` 保持不变、未登录 401。

业务侧真实 HTTP：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- MessageAcceptanceHttpTest
```

其中伪造裸 `X-User-Id`/`X-User-Role` 的请求返回 401。

## 2. 密钥轮换与负例（C1.3.2）

`AssertionProperties` 支持 current/previous 两把密钥；验签按 kid 选择密钥，
轮换窗口内新旧断言都可接受，未知 kid 直接拒绝。

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- AssertionVerifierTest
```

覆盖：当前/上一把密钥接受；未知 kid；签名被篡改；重复断言头；缺失断言头；
裸 `X-User-Id`；方法或路径不匹配；过期、未来时间与超长 TTL 全部 401。
直接访问业务端口 18080 伪造 `X-User-Id` 的 401 证据见 `acceptance.md`。

## 3. `UserStatusGuard` 安全点（C1.3.3）

实现：`server/agent-squad-server/src/main/java/com/agentsquad/squad/identity/UserStatusGuard.java`
每次读取共享 `sq_user.status`，非 `ACTIVE` 或缺行即抛 `FORBIDDEN`。

接入位置：

| 安全点 | 文件 |
| --- | --- |
| 消息受理 | `conversation/service/MessageAcceptanceService.java` |
| 目标创建 | `conversation/service/GoalTaskCreationService.java` |
| 命令领取 | `conversation/service/CommandClaimService.java` |
| 执行授权 | `runtime/service/ExecutionAuthorizationService.java` |
| 模型预算调用 | `integration/agentscope/ModelBudgetMiddleware.java` |
| 工具执行 | `integration/agentscope/ExecutionContextMiddleware.java` |
| 外部操作派发 | `tools/operation/OperationDispatchService.java` |
| 沙箱执行 | `sandbox/service/SandboxExecutionService.java` |

禁止语义：停用后不新增受理、不领取新命令、不发起模型/工具/外部调用；
已处于 `DELIVERING` 的命令不回写或取消，保留在途账本供核对。

真实 MySQL + 真实 HTTP 竞态测试：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- UserStatusGuardIntegrationTest
```

流程：受理成功（1 个会话）→ 停用 → 再受理 `FORBIDDEN` 且会话数不变 →
领取 `FORBIDDEN` 且命令保持 `PENDING`/无 token → 恢复 → 领取成功并进入
`DELIVERING` → 再次停用 → 领取 `FORBIDDEN`，命令仍为 `DELIVERING` 且 token 不变。

测试替身说明：`src/test/java/com/agentsquad/squad/testsupport/TestUserStatusMapper.java`
是 `@Primary` 的测试夹具，仅为历史测试中随机 userId 返回 `ACTIVE`；存在的
`sq_user` 行仍按真实状态判断。该类只存在于 test sources，生产构建不包含，
不存在运行时开关或生产绕过路径。

## 4. 旧裸头测试迁移（C1.3.4）

- 业务 HTTP 测试统一改用 `testsupport/TestAssertion` 签发真实 HMAC 断言，
  经真实 `AssertionVerifier` 验签；未保留生产旁路。
- `MessageAcceptanceHttpTest`、`ConversationApiSecurityTest`、`ResponseControlHttpTest`、
  `TaskSnapshotHttpTest`、`RuntimeHttpSecurityTest`、`SkillHttpIntegrationTest`、
  `WorkerDefinitionHttpTest`、`MemoryHttpTest` 等已完成迁移。
- 纯内部单元测试（如 `ModelBudgetMiddlewareTest`）仍可直接构造依赖，
  但注入的是真实 `UserStatusGuard`，不是身份绕过。

## 5. C1.4.1 全量门槛

命令（凭据来自 `.env.local`，未写入日志）：

```bash
mvn -f server/pom.xml -B -ntp test
npm --prefix web/workbench run build
python3 tools/check_design.py
```

真实结果（2026-11-02 17:18）：

```text
agent-squad-gateway  SUCCESS
agent-squad-server   SUCCESS
server: Tests run: 146, Failures: 0, Errors: 0, Skipped: 23
BUILD SUCCESS (04:36 min)
```

前端构建：

```text
vue-tsc --noEmit && vite build 通过
83 modules transformed，built in 414ms
```

设计检查：

```text
PASS: 52 operations, 83 schemas, 45 table definitions, 0 FK references,
17 valid / 11 invalid schema examples; 12 requirements traced;
8 UI routes cover all APIs; SDK key fixture max 252/255; Markdown links valid
Final schema: .../V1__baseline.sql (48 tables)
Static design validation only; no SQL execution or runtime verification.
```

跳过项（23，逐项记录，不视为通过）：

| 测试类 | 数量 | 原因 |
| --- | --- | --- |
| `MySqlConversationReadTest` | 2 | 需要 `SQUAD_MESSAGE_TEST_URL` |
| `MySqlGoalTaskCreationTest` | 2 | 同上 |
| `MySqlGoalTaskIntegrationTest` | 1 | 同上 |
| `MySqlKnowledgeGovernanceTest` | 1 | 同上 |
| `MySqlMessageAcceptanceTest` | 3 | 同上 |
| `MySqlMessageOrderingTest` | 3 | 同上 |
| `MySqlResponseContextTest` | 1 | 同上 |
| `MySqlScopeRuntimeOwnershipTest` | 3 | 同上 |
| `MySqlTaskDirectiveTest` | 2 | 同上 |
| `HarnessDockerSandboxDriverTest` | 5 | 需要 `SQUAD_SANDBOX_TEST_IMAGE=sha256:<64位>` |

说明：本轮 C1.4.2 使用真实 MySQL 部署实例补充验证了消息受理、会话快照与
SSE 链路；`HarnessDockerSandboxDriverTest` 的容器镜像门控仍未提供，保留为
环境跳过，不以其他测试冒充。
