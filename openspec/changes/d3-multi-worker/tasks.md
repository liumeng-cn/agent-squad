# Tasks

## 1. WorkerDefinition 契约与生命周期

- [x] 1.1 实现 WorkerDefinition 发布/查询，同一 workerKey/version 不可重复发布、版本内容不可变、停用只改状态且 REVOKED 不可恢复。验证：发布、幂等与状态接口测试。
- [x] 1.2 实现管理员能力页与普通用户访问隔离。验证：B14，无权限直达被前端守卫转回工作台，发布/状态变更由后端 ADMIN 校验拒绝。
- [x] 1.3 将已发布 Definition 接入 WorkerFactory 的运行时发现，不修改 Master 业务分支。验证：A17，本地 OpenAI 兼容桩上新能力从计划选择、持久派发、原生调用到 Job SUCCEEDED。
- [x] 1.4 建立配置型 modelProfileKey/runtimeProfileKey 注册表；发布与启动时拒绝未知引用，密钥不进入 Definition 或日志。验证：新增 review Profile 原生执行与定价结算，未知引用拒绝。

## 2. Plan/Job 持久化与发布校验

- [x] 2.1 实现 Plan/Job 持久化与基础 DAG 校验：无环、版本、节点数、并行宽度与依赖；根节点 READY，其余 BLOCKED，按 logicalKey 重放。验证：H2 落库与重放测试。
- [x] 2.2 实现 Plan 版本化及旧 Job 的数据库状态更新：未开始置 SUPERSEDED，运行中写取消屏障。验证：旧版未开始节点的数据库状态测试。
- [x] 2.3 实现最小 `prepare_context` 权威快照，并在 `publish_plan` 校验 Schema、权限、上下文和总预算；同事务创建 Plan/Jobs、根节点 Binding/Command/事件。验证：A04、A09；无快照/越权/无预算拒绝，受理后崩溃可由原 Command 恢复。
- [x] 2.4 完成在途旧 Job 的取消屏障及迟到结果拒绝，保留已发生副作用证据。验证：A10，新计划不被旧结果推进。
- [x] 2.5 将模型请求前 Reservation 预留、可信用量结算、未知收费保留和工具预留统一到父 Task 微单位上限；`publish_plan` 做总预算准入。验证：A16/B12 Master/Worker/工具竞争同一上限、同键重试拒绝再次调用、预算不足拒绝计划，本地桩完成两次结算。

## 3. WorkerDispatcher 与恢复

- [x] 3.1 实现 WorkerDispatcher：从 READY Job 绑定 guard，经能力工厂构建叶子 HarnessAgent，Command ID 作 SDK submit key。验证：A05 同 Job 两实例仅一个有效 guard，原生日志拒绝旧 writer。
- [x] 3.2 实现 Job 失败分支恢复：崩溃后仅恢复未完成分支，不重跑成功分支。验证：A06 结果候选不提前算成功。

## 4. 汇合、重规划与结果复用

- [x] 4.1 实现汇合信号：Job 结果持久完成时递增 signalVersion，满足条件时创建唯一 MASTER_SIGNAL Command（taskId+signalVersion）。验证：一个目标多个协调轮次仍只显示一个用户目标。
- [x] 4.2 实现结果复用：成功结果仅在输入摘要/能力版本/Skill 版本/权限/证据时效全匹配时复用，记录 reusedFromJobId。验证：A09 计划重放返回相同 Plan/Jobs，无重复节点。

## 5. 页面交付与证据

- [x] 5.1 交付任务详情（Job 列表、进度时间线、交互、脱敏操作详情、产物与核对入口）与子任务列表；提前交付最小文本产物上传、目标引用、归属鉴权和下载。验证：与真实 API 联调，B05/B08 状态与正文行为正确，B09 显式核对授权和 B10 越权下载拒绝。
- [x] 5.2 记录 D3 证据：单/并行/串行/失败分支恢复实验、固定 SDK/配置版本、故障注入点、平台与原生日志、外部操作次数。验证：A04–A06/A09/A10/A17 全部通过且证据目录齐全。

## Workflow follow-up

- 归档本 change 前按项目评审要求复核并同步 D3 证据。

## 6. 用户表达与交互体验

- [x] 6.1 回答直接交付内容，内部协作只作为核验材料；默认页面隐藏执行身份、节点、版本和内部轮次，保留真实结果、必要追问与操作确认。验证：前端构建及原生协作回归。
