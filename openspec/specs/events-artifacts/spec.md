# 持久输出、产物与治理（Events & Artifacts）

## Purpose

交付治理能力负责快照/SSE、产物、预算与审计；不把原生实时事件直接当持久结果，真实增量先经原生日志持久再投影。（M08）

## Requirements

### Requirement: 统一正文与真实增量

Web 工作台提供一个目标的统一正文、真实增量、快照、回放及交互入口。（R10）

#### Scenario: 快照与 SSE
- **WHEN** 页面打开任务
- **THEN** 先读 snapshot（一致前缀与 watermark），再以 seq>cursor 订阅 SSE；游标未来值 400、过期 410，跨任务/用户拒绝

#### Scenario: 断线重连（A13）
- **WHEN** 断线、跨实例读取或半答案重生成
- **THEN** 一致水位无重复业务，answerRevision 替换而不拼接

#### Scenario: 正文隔离
- **WHEN** 投影公开事件
- **THEN** 正文只来自 Master 授权回答轮次，Worker 日志与工具输出脱敏为进度/证据，不串入主正文

### Requirement: 投影幂等与持久证据

SessionProjector 按 bindingId/nativeEventId 去重，平台事务追加公开事件、更新读投影并持久 sourceSeq；公开 seq 在每个 Task 内递增，与原生 seq 不互换。（A12）

#### Scenario: 幂等补投影
- **WHEN** 公开导出失败后重建
- **THEN** 从原生日志幂等补投影，不再调用模型

### Requirement: 产物治理

Artifact 上传 READY 前验证允许类型、大小与摘要；Worker 通过 stage/commit 受控端口交付；归属删除先禁读、清运行引用后物理清理。

#### Scenario: 越权下载拒绝（B10）
- **WHEN** 下载不存在或越权产物
- **THEN** 显示明确错误，无内部路径泄露

#### Scenario: 交付校验
- **WHEN** Worker 交付文件
- **THEN** Operation/Job guard、写空间与文件 hash 全部匹配才置 READY

### Requirement: 预算治理

父 Task 全局预算由 Reservation 事务预留，所有 Master/Worker 共享；模型 middleware 每次真实请求前预留、结束按可信用量结算，未知收费保守占用。

#### Scenario: 并发预算不超（A16）
- **WHEN** 并行模型/摘要/工具重试
- **THEN** 全局预算不超，未知计费不提前释放，供应商切换不重置预算

#### Scenario: 超限拒绝（B12）
- **WHEN** 预算预留超过任务上限
- **THEN** 拒绝新预留，不无预算启动

### Requirement: 保留、备份与审计

任务/审批保留 30 天、公开事件 7 天、已交付产物 30 天、审计 180 天；备份覆盖平台表、原生 journal/inbox/水位、Skill 固定版本与对象引用。

#### Scenario: 备份缺窗口（A18）
- **WHEN** DB/对象存储失败或备份缺窗口
- **THEN** 无持久保障不发写操作，缺失文件阻塞，外部未知先核对
