# M05 上下文与显式长期记忆

## 分工和数据

框架管理消息、工作上下文、压缩及局部检查点。平台 ContextSnapshot 保存可审计的输入材料及不可变引用，不复制整套 SDK 状态。平台 Memory 保存用户显式长期偏好/事实，不能与聊天历史、摘要、技能说明混为一张大文本。

ContextSnapshot：snapshotId/taskId/targetNodeKey、conversationGeneration、memoryRevision、items、tokenEstimate、contentHash。每项含 kind/sourceId/sourceVersion/trust/content 或 artifactId。平台受控准备输入，精确载入当前目标、否定约束、必要依赖，按授权投影给节点；Worker 不得加载 Master 整段对话。

## 框架集成

通过 ContextSource/输入装配提供动态材料；来源只读，不能在装配提示词时触发外部写入。使用框架 token 预算、压缩和大工具结果卸载。保留工具调用/结果配对、用户纠正和未解决项；所有固定材料含版本与来源，不将检索结果提升为系统指令。

生产配置关闭自动记忆 hooks、内置记忆写工具和 MEMORY.md 自动注入路径，使用平台受控显式记忆工具与 ContextSource。必须按固定标签确认关闭范围覆盖调用后、压缩前和后台合并，不仅是 per-call flush。Worker 彻底没有全局 memory_save 权限。

## 公共接口与版本

GET/POST /api/v1/memories → MemoryPage / MemoryCreate；PATCH/DELETE /memories/{memoryId} 携带 expectedVersion；删除返回 204，永久保留 tombstone/version 到清理完成。scope=USER，kind=EXPLICIT，记录 key/content/sourceTaskId/status/expiresAt/version。

自然语言“以后报告用中文”经 write_explicit_memory 受控工具形成明确写入，附用户原话来源；普通日志或 Skill 里的指令不构成记忆授权。模型自动提炼只可作为后续候选功能，不能主动写 ACTIVE。

更新事务锁记录校验 owner/version，递增 Account.memoryRevision、写审计及 memory.changed 事件。每次新模型请求的 ContextSource 检查 revision；旧 ContextSnapshot 若引用被撤销记忆则失效重建，不能继续注入旧摘要。

模型已经在途时无法撤回已发送内容；若旧记忆已进入 SDK 工作上下文，先中断/标为 DIRTY，在新授权视图上创建替代 Binding 并只迁移经清理的摘要/目标与原 Job 进度引用；保持产品 taskId，不把旧 SDK 检查点不加清理地 resume。完成后的历史仅在授权界面可看，不自动重新注入。此路径必须验证，未实现前删除相关任务进入 RECOVERY_BLOCKED，不能承诺无条件继续。

## 检索

一期按 userId/ACTIVE/key 精确查找及限量文本检索；向量库不强制。未来检索服务只召回 ID，回到权威 Memory 验证归属、版本、有效期和 tombstone 后再注入。本轮明确指令高于一般偏好；硬权限由工具控制，不靠召回。

验收：跨用户隔离；用户纠正覆盖旧值；删除后旧检查点/摘要/索引不能使内容回流；长对话压缩不丢否定约束；工具结果超长只卸载内容，不丢证据引用。
