# AgentScope 原生持久化正式链路实测

> 本文是对话交互改造前的历史记录，原 POST SSE 复跑协议已被替换。当前交互实现与复跑入口见 [原生对话交互实现与验收](../chat-interactions-20261116.md)。

验证日期：2026-11-16。使用正式 `agent-squad-v2` 打包产物、正式配置类和 `POST /api/v2/chat/messages`，没有另建 Agent 实验实现。模型为讯飞 `spark-x2.6`，本次显式关闭 thinking。SDK 为项目固定的 `2.1.0-BETA1`。

## 结果

| 场景 | 实际结果 | 证据 |
| --- | --- | --- |
| 记住合成验证编号 | HTTP 200，回复“已记住”，收到 RUN_FINISHED | seed-http.json |
| 调用 test_event_tool | 产生 TOOL_CALL_START、TOOL_CALL_RESULT，工具完成后模型回复 | seed-http.json |
| 重复相同 requestId 与输入 | 又产生一次 Run 和模型调用；没有再次调用测试工具。不是提交幂等 | duplicate-submit.json |
| 委派 java-tester | 主会话出现 subagent/spawned、subagent/completed；子会话单独落日志 | data-samples.json |
| 重启正式服务 | 进程从 37170 更换为 37458，同一会话准确返回原编号 PERSIST-4997d9516a | reopen-http.json |
| 同 sessionId 换用户 | 回答不知道，未返回原编号 | reopen-http.json |
| 同用户新 sessionId | 回答不知道，未返回原编号 | reopen-http.json |

上述隔离结论仅覆盖本次合成场景，不代表完成全部租户鉴权、文件权限或长期记忆隔离测试。

## 实际落盘

主会话为 `store-00c8c5d52a864ae7`，用户为 `1`，Agent 为 `squad-master`。

```text
server/agent-squad-v2/.agentscope/v2/1/.agentscope-runtime/
  agents/s_c3F1YWQtbWFzdGVy/sessions/s_MQ/s_c3RvcmUtMDBjOGM1ZDUyYTg2NGFlNw/
    session.json
    head.json
    commits/<hash>.json
```

每个文件前 8 字节为大端 long 存储版本，之后才是 JSON。扩展名 `.json` 不意味着可以直接按纯 JSON 打开。文件旁还有 `.lock`，记录脚本不把锁文件计为数据对象。

完成重启后的主会话有 181 条连续事件、40 个 commit、14 个 state/checkpoint；42 个数据对象合计 523,668 字节，约 511 KiB。这个大小包含模型请求与多次检查点，不是纯聊天文本大小，也不能据此直接推算长期容量。

子 Agent 位于独立工作区 `agents/java-tester/workspace/1/.agentscope-runtime/`，独立 Journal 有 28 条事件。主会话通过 childAgentId、childLogAgentId、childSessionId 关联它，不能只扫描主会话目录就认为拿到了全部子执行数据。

最末检查点的 `context` 有 13 条消息：4 条 USER、7 条 ASSISTANT、2 条 TOOL。摘要为空，本轮没有触发上下文压缩。状态还包含 permission_context、tool_context、tasks_context 和 plan_mode_context。

数据文件：

- [data-samples.json](data-samples.json)：少量实际字段值、消息样例、模型请求摘要、检查点摘要、子 Agent 关联及文件大小。
- [seed-http.json](seed-http.json)：重启前 4 次正式 HTTP 请求的公开事件与合成回复。
- [reopen-http.json](reopen-http.json)：重启后 3 次正式 HTTP 请求的公开事件与匹配结果。
- [seed-journal.json](seed-journal.json)：重启前主会话的对象清单、存储版本、hash、事件信封与 payload 结构。
- [after-restart-before-request.json](after-restart-before-request.json)：新进程启动后、继续发送前的主会话快照。
- [reopen-journal.json](reopen-journal.json)：继续会话后的主会话快照，带模型与子执行标识。
- [duplicate-submit.json](duplicate-submit.json)：重复提交前后的 head 和事件计数。

公开记录保留合成输入产生的公开回复，不包含认证信息、完整模型请求、完整检查点上下文或模型思考正文。原始 SDK 文件仍保存在 Git 忽略的工作区；服务运行日志在 `.local/store-research-20261116/`。

## 哪些可以直接使用

已实测：当前正式配置默认启用本地 Journal；消息、模型调用、工具结果、检查点和子执行关联已落盘；关闭并重启应用后，同一身份和会话可以自动恢复模型上下文，无需平台重新拼接历史消息。

当前正式接口走 AG-UI 流调用，本轮没有产生 `inbox/` 或 `exports/` 对象。因此，不能把 SDK 的 AgentSession.submit 持久排队、提交幂等和导出确认能力算作当前 HTTP 链路已经接入。重复 requestId 的实测也证实仍会再调用模型。

当前没有提供历史列表或消息查询接口。原生日志足以提供重建历史的事实，但本次没有通过不存在的接口验证页面刷新后的历史展示，也没有另建替代服务来冒充正式能力。

本轮没有接数据库 Store，没有验证 MySQL 表结构或跨机器恢复；没有验证运行中强杀、待审批恢复、未知工具副作用核对、长上下文压缩和沙箱快照。此次使用正常关闭后的新 JVM，不能描述为崩溃恢复通过。

建议保留原生 Journal 作为运行事实来源，下一步在正式代码接入历史读取与稳定会话管理；如果选择 MySQL，再显式接入数据库 SessionLogStore，并在同一正式接口下复跑此次流程。无需先重写上下文和工具执行持久化。

## 复跑

先按项目正常方式启动 v2 服务，确认本地配置已设置讯飞模型并保持工作目录为 `server/agent-squad-v2`。以下脚本仅向正式 HTTP 接口发送合成消息并只读记录 SDK 文件，不创建替代模型、Agent 或数据库。

```sh
python3 tools/verification/record_native_store.py seed
# 正常停止并重新启动同一个 v2 服务，保持工作区与身份配置一致。
python3 tools/verification/record_native_store.py reopen
```

脚本会覆盖本目录中的流程快照，首次运行生成新的会话和合成编号；本文的固定数字对应本次实测，复跑后须同步更新。`data-samples.json` 是本次记录中挑选的实际样例，不由复跑命令自动更新。

## 构建与回归

正式应用打包通过。现有 AguiSessionTest、ChatHttpTest、ChatEventConverterTest、EventTestToolTest、TestSubagentDefinitionsTest 共 7 项通过，0 失败、0 错误、0 跳过。这些是独立的受控模型回归；真实讯飞结果以上述 7 次 HTTP 请求为证据，两者不混算。
