# M07 工具、审批与外部操作

## 工具与连接

ToolDefinition 由受控发布配置提供 toolKey/version、JSON Schema、effect(READ/WRITE/SANDBOX)、scope、超时、结果上限、externalIdempotency(NONE/KEY/QUERY)。Connection 属于用户，包含 provider/environment/resourcePolicy/secretRef/revision/enabled；密钥用加密/密钥服务保管，API 只返回 secretPresent。

GET /api/v1/operations/{operationId} 返回OperationDetail（脱敏参数、证据和canReconcile）；Interaction.APPROVAL携approvalContext供页面确认影响，禁止返回Secret或内部执行凭证。

GET/POST /api/v1/connections，PATCH /connections/{connectionId}；GET /tasks/{taskId}/operations；POST /operations/{operationId}/reconcile（仅具资源核对权限）；GET /tasks/{taskId}/interactions；POST /interactions/{interactionId}/responses。工具实际执行为同 JVM 端口，不公开任意 HTTP/SQL/shell 代理。

实际权限取 当前用户 ∩ GoalTask 冻结上限 ∩ Job 作用域 ∩ 工具策略 ∩ Connection 策略。服务端注入身份和 endpoint；模型不能选择任意服务地址。框架 ALLOW/ASK/DENY 是附加门禁，不替代真实资源授权。普通 Worker 不可核对外部事实或直接读密钥。

## Operation 的稳定键与事务

框架 tool 调用进入 wrapper 时按 bindingId/frameworkTurnId/toolCallId 查唯一 Operation；恢复读取同一记录，不能为原调用生成新 ID。若 Master 主动重新发起同业务写入，工具还须 businessIntentKey + requestHash 幂等（在 taskId + toolKey 范围唯一），避免新 toolCallId 绕过重复检测；KEY/QUERY 连接器须声明键语义和有效期。

事务 A：检查 guard、任务/计划/授权版本，规范化参数并计算 hash，预留预算，保存 PREPARED。若需要确认，创建与参数/计划/Connection revision/authVersion 绑定的 APPROVAL，Operation=WAITING_APPROVAL。事务提交前不发送。

执行器用独立 executorFence/lease 原子领取，重新校验审批、撤权和取消后，提交 IN_FLIGHT，再发外部请求。结果事务写 SUCCEEDED/FAILED、result/evidence、预算结算及恢复 Command。数据库事务不包外部网络。

IN_FLIGHT 后失联统一 UNKNOWN，既不直接失败也不自动重发。KEY 需确认外部幂等仍有效才可同键补发；QUERY 按关联号查证；NONE 需人工有证据处置。人工只能提供 CONFIRMED_SUCCEEDED / CONFIRMED_NOT_EXECUTED / UNRESOLVED；不能用一句“忽略错误”清掉未知事实。确认未执行后若要重试，仍重验授权、审批和幂等约束。

禁用未受控的 pending-tool 自动执行恢复；READ 重试也遵守预算，WRITE/SANDBOX 未知结果必须走本账本。框架实际工具入口全部经 wrapper，不能留下旁路。

结果已写平台而 SDK 丢 tool result 时，通过可信 adapter 映射原 toolCallId 调用 reconcileToolOutcomes，等原生日志确认后再 resume。不能因为模型说“完成了”而更新 Operation。

## 输入与审批

INPUT 来自受控 request_input；APPROVAL 来自工具 effect 策略；两者都不占持久 HTTP 请求。Interaction 含 taskId/jobId/bindingId、frameworkRequestId(可空)、planVersion、requestHash、authVersion、connectionRevision、answerSchema、expiresAt、status/version。

平台业务追问用持久 Interaction + 后续受控协调 Command，不伪造框架 pending request。框架原生 ASK 挂起时，由已提交 interaction/requested 事件建立映射；只允许答复真实原 requestId，平台收据不能冒充框架已消费。

回复事务校验 owner/版本/期限/取消/计划/授权与 Schema，写 RESPONDED 和 RESPOND/MASTER_SIGNAL Command。bridge 将原生请求交给 session.respond；业务请求提交固定的协调输入。响应重复同内容返回原回执，不同内容 409；已经消费或失效的回答不能触发新 Turn。

审批单一决定：受控工具预检在产生原生ASK前建立稳定Operation和Interaction（来源FRAMEWORK，先不伪造frameworkRequestId），原生持久interaction/requested再按binding/Turn/toolCallId绑定真实request。投影器更新同一记录，不另外建第二张审批。执行wrapper读取已审批原hash并重新校验授权，不再要求第二次确认；平台拒绝不能被原生ALLOW绕过。预检Hook在固定标签的接入点由D0实验确认，未确认前不开放WRITE。

APPROVAL reply 含 approve 与已确认 requestHash，前端不能编辑参数。参数、资源或授权变化使审批失效并重建；拒绝取消本 Operation，不自动取消整个目标。原生建议永久放行不在产品 API 开放。

## 沙箱

使用框架 Sandbox 接口，执行环境与应用隔离。工具票据只授予此 Operation 的工作目录/时限/网络，不能包含平台数据库凭据。固定镜像与依赖、非 root、只读根文件系统、受限挂载和出口、CPU/内存/进程配额；禁用宿主 shell/特权容器。具体驱动是否支持每项需实现实验，未满足时不开放不可信脚本。

快照恢复不等于外部操作回滚；Operation 状态与运行文件均需核验。默认 NoopSnapshot 不用于需要恢复的执行；有引用的快照/产物不可被普通 TTL 清掉。

验收：重复审批仅一次发送；旧确认/撤权后调用拒绝；外部成功但本地回执丢失停在 UNKNOWN；旧执行器不能覆盖核对结果；原生 pending 与业务 Interaction 都可重启续答且不串任务。
