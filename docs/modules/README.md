# 模块设计 v0.2

Java + AgentScope 路线。R01–R12 不变；[运行协议](../runtime-contract.md)定义权威、对象映射及跨存储桥接，[OpenAPI](../../contracts/openapi.json)定义公共 HTTP DTO，[数据设计](../../data/README.md)定义业务表。框架 SDK 内部类不作为公共 API。

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| [M01](01-access.md) 接入与身份 | 登录、主体、会话归属和删除屏障 | 模型自报身份 |
| [M02](02-runtime.md) 任务运行集成 | GoalTask、Command、Binding guard、恢复入口 | 重新实现 SDK inbox/journal |
| [M03](03-master.md) Master 协作 | 能力选择、计划、汇合与完成条件 | 模型绕过状态校验 |
| [M04](04-worker.md) Worker | 注册、局部输入、持久派发、结果 | 手写 ReAct / 自由后台 spawn |
| [M05](05-context-memory.md) 上下文记忆 | 裁剪、引用、显式长期记忆治理 | 任意自动记忆激活 |
| [M06](06-skills.md) Skill | 自动发现使用、版本及归属 | Skill 等同 Worker |
| [M07](07-tools-interactions.md) 受控执行 | 连接、操作账本、追问、审批和核对 | 承诺外部事务恰好一次 |
| [M08](08-events-artifacts.md) 交付治理 | 快照/SSE、产物、预算、审计 | 原生实时事件直接当持久结果 |

当前是可评审设计，尚未完成 SDK/数据库实验；详见[评审与验收](../review.md)。
