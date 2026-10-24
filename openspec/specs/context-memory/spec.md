# 上下文与显式长期记忆（Context Memory）

## Purpose

上下文与显式长期记忆能力负责受控裁剪、不可变引用与用户显式长期记忆治理；不任意自动激活记忆，也不把检索结果提升为系统指令。（M05）

## Requirements

### Requirement: 局部材料授权

Master 掌握全局语义；Worker 仅取得必要局部材料，不得加载 Master 整段对话。（R04）

#### Scenario: 局部快照
- **WHEN** prepare_context 为节点冻结授权局部材料
- **THEN** 返回 ContextSnapshot（含版本、摘要与引用），Worker 按授权投影获取，不加载整段对话

#### Scenario: 来源只读
- **WHEN** 上下文装配提供动态材料
- **THEN** 来源只读，不触发外部写入，检索结果不提升为系统指令

### Requirement: 显式长期记忆治理

会话压缩与用户显式长期记忆可纠正、可删除，后续增强检索；模型自动提炼不主动写 ACTIVE。（R09）

#### Scenario: 显式写入
- **WHEN** 用户经 write_explicit_memory 受控工具写入记忆
- **THEN** 形成明确记录并附用户原话来源，普通日志或 Skill 指令不构成记忆授权

#### Scenario: 纠正覆盖旧值（A15）
- **WHEN** 用户纠正记忆
- **THEN** 新授权视图重建，旧 checkpoint/摘要不复活旧值

#### Scenario: 删除后不回流（A15）
- **WHEN** 用户删除记忆
- **THEN** 旧检查点/摘要/索引不能使内容回流，无法清理时相关任务进入 RECOVERY_BLOCKED

### Requirement: 检索与验证

一期按 userId/ACTIVE/key 精确查找及限量文本检索；检索只召回 ID，注入前回权威 Memory 验证归属、版本、有效期与 tombstone。

#### Scenario: 越权召回拒绝
- **WHEN** 检索召回内容准备注入
- **THEN** 回权威 Memory 验证归属、版本、有效期、tombstone 后再注入，硬权限由工具控制不靠召回
