# 原生对话交互实现与验收

## 实现边界

会话由服务端用户、固定 Agent 与 sessionId 定位。interactionId 标识一次发送，新任务将它作为原生 submit 幂等键；turnId 标识输入实际归属的任务轮次，runId 标识一次实际执行。插话返回原生 inputId，不额外建立平台命令表或映射表。

带 observedTurnId 的发送优先调用 steer。只有 SDK 明确拒绝该目标继续接收，且日志确认目标已完成，才调用 submit 开启下一轮。未知存储异常不做回退；插话暂不承诺网络重试幂等。

## 代码职责

- `ChatService`：发送路由、原生停止和显式继续；不持有第二套执行线程。
- `ChatSessionService`：按服务端身份访问原生会话，纯读历史。
- `ChatSessionConverter`：从已提交日志重建公开消息、思考文本、工具状态和插话状态；跳过完整检查点、系统提示词与原始工具载荷。
- `ChatEventStreamService`：同一实例、同一用户和会话共享读取，日志位置变化时生成公开快照。500 ms 检查日志头，不重复写入平台事件表；最后一个订阅断开后释放读取器。长会话的扫描和快照传输成本尚未做规模验证。
- `useChat`：保存当前会话身份，独立管理提交与订阅；断线重连只恢复观察，不重提输入，不将连接中断等同于任务失败。

## 接口

| 接口 | 行为 |
| --- | --- |
| POST `/api/v2/chat/messages` | 返回 ApiResult 接收收据；输入为 sessionId、interactionId、message、可选 observedTurnId |
| GET `/api/v2/chat/sessions/{sessionId}` | 返回公开历史、当前轮次、执行状态、补充要求及双游标 |
| GET `/api/v2/chat/sessions/{sessionId}/events` | SSE 每帧为完整公开快照，id 为执行游标与输入游标；15 秒发送一次注释心跳 |
| POST `/api/v2/chat/interruptions` | 携带 sessionId 和 runId 请求原生 interrupt，接收不等于已经停止 |
| POST `/api/v2/chat/resumptions` | 携带 sessionId 和 turnId 显式 resume，仍受待交互和未知工具结果准入约束 |

传输结构以 `api/openapi.json` 为准。原来的 POST 直接返回 AG-UI SSE 已由提交收据与独立观察接口替换。

## 页面

空闲时发送新任务；运行中同一输入框发送补充要求，旁边提供停止按钮。草稿只在明确接收后清空。尚未进入上下文的补充暂列在输入区上方，应用后状态关联到对应用户气泡。

停止期间显示正在停止，日志确认后才进入中断或其他终态。中断和失败时提供继续本轮入口；存在待答交互时不将普通聊天输入伪装成审批答复。当前保存的是本标签页的会话身份，没有新增会话目录管理和人工交互表单。

## 验收

### 受控模型

15 项后端针对性测试通过，覆盖：

- 原生 submit 幂等、历史读取与用户隔离；目标完成后转下一轮。
- Master 模型生成中插话，接收与应用状态分离。
- 工具执行中插话，工具继续完成后采用要求。
- 子 Agent 运行期间插话给 Master；子输入不被改写，主执行后续应用补充。
- 取消 SSE 订阅不取消执行；停止接收只作为收据。
- 结果未知的插话失败不转为新提交；公开快照不包含系统提示词或原生状态载荷。
- HTTP 参数校验、工具公开状态、原生子 Agent 定义与观测回归。

前端 84 项 Vitest 测试通过，另有既有 Node 测试通过；包含运行中可发送、草稿确认后清空、失败保留、独立停止、会话身份复用、取消订阅不停止执行和网络失败不自动重发。前端类型检查与生产构建通过。新增 Java 文件已通过独立 IDEA 格式化进程处理。

### 本地模型与正式接口

正式打包服务使用当前本地 `qwen/qwen3.6-35b-a3b`，未配置替代 Agent 测试服务。以下均通过真实 HTTP 接口验证：

- Master 运行中插话，APPLIED，原轮次完成。
- test_event_tool 执行中插话，APPLIED，原轮次完成。
- 已完成轮次收到旧 observedTurnId，开启不同的新轮次。
- 简单子任务“子 Agent 只回复完成”执行中插话，仍归属 Master 原轮次，APPLIED，最终完成。
- 简单任务的停止请求接收后最终为 INTERRUPTED。
- 查询历史与重新订阅 SSE 恢复同一轮次，不增加执行日志游标、不启动新执行。

此前较长的本地子任务触发了 SDK 的 30 秒同步等待超时并转后台，后续模型请求出现 BadRequestException。这条较长链路没有算作通过，也没有通过修改成功断言掩盖；本轮最终按简单任务验证基础功能。未验证长任务性能、MySQL 多实例、运行中崩溃自动接管或未知外部副作用的人工核对界面。

运行日志与真实接口记录保存在 Git 忽略的 `.local/chat-implementation/`。测试模型只使用本地服务；SDK 时序与异常边界优先通过门控模型验证，不调用付费模型。

## 复跑

先启动正式 v2 服务并确认配置指向本地模型，然后每次只选一个简单场景：

```sh
python3 tools/verification/verify_chat_interactions.py --scene master
python3 tools/verification/verify_chat_interactions.py --scene tool
python3 tools/verification/verify_chat_interactions.py --scene subagent
python3 tools/verification/verify_chat_interactions.py --scene stop
```

脚本不会改变模型配置，运行前需要确认服务当前使用本地模型。SDK 会持久保存合成测试会话，脚本不会删除已有会话或数据库。

## 交互反馈修复

- 回复按原生模型调用划分为稳定的追加段；插话后不清空已有正文和思考，恢复执行不把旧中断段改成完成。
- 前端按标识原位合并消息、工具和思考对象；向上阅读时不因新增消息强制跳回底部。
- 结束时间来自原生事件，终态耗时固定；全部回复结束时释放页面刷新计时器。
- 已应用的补充不再单独显示文字标注，等待或未应用仍保留轻量提示。
- 页头增加清空按钮：结束当前页面会话并生成新 sessionId；保留原生日志，执行中及发送结果未知时禁用。
- 停止和发送放在输入区右侧同一按钮组。

开发服务曾继续返回旧版 Vite 模块，已重启并直接检查 HTTP 返回的新计时组件。最终浏览器现场复核受 Mac 锁屏阻断，没有将该项描述为已视觉验收；组件回归、构建及接口检查均通过。
