# 需求、范围与主流程 v0.2

2026-10-20，Java + AgentScope 详细设计评审稿。需求保持不变，实施与生产验证尚未开始。状态归属以[运行协议](runtime-contract.md)为准。

## 需求

| 编号 | 必须可观察的行为 | 主模块 |
| --- | --- | --- |
| R01 | 用户只能访问自己的会话、任务、记忆与产物；服务不接受模型自报身份 | M01 |
| R02 | 一个目标持久接受、可离线等待；重送不重复受理 | M02 |
| R03 | Master 理解目标、直接回答或委派、核验与聚合 | M03 |
| R04 | Master 掌握全局语义；Worker 仅取得必要局部材料 | M05 |
| R05 | 执行者自动识别、按需读取和使用 Skill，无需逐次绑定 | M06 |
| R06 | 串行依赖、并行、明确汇合和有效结果复用 | M03/M04 |
| R07 | 缺信息追问、敏感操作确认、答复后继续原目标 | M07 |
| R08 | 实例退出后从持久提交点恢复；未知副作用先核对 | M02/M04/M07 |
| R09 | 会话压缩与用户显式长期记忆；可纠正/删除，后续增强检索 | M05 |
| R10 | Web工作台提供一个目标的统一正文、真实增量、快照、回放及交互入口 | M08/页面 |
| R11 | 取消后阻断新工作，明确已发生的操作，不把取消视为回滚 | M02/M07 |
| R12 | 新 Worker 按能力契约接入，不修改 Master 业务分支 | M04 |

不做公司业务接入、云效专属逻辑、生成操作页面、自动修代码、项目共享记忆、任意依赖安装或无界递归委派。Python/向量库/消息中间件不作为必需组件。

## 核心组成

Java 平台模块化单体，嵌入 Harness SDK；Master/Worker 是逻辑角色，Sandbox 是隔离执行边界。平台的 GoalTask/Plan/Job 保存跨 Worker 业务协作，AgentScope Session/Turn/Run/journal 保存单 Agent 执行；不复制原生 ReAct 与检查点引擎。

[技术选型](architecture.md)、[项目结构](project-structure.md)、[运行协议](runtime-contract.md)、[模块](modules/README.md)构成现行设计。原 Python/LangGraph 与旧接口/旧表不再作为基线。

## 示例：上传日志与配置检查

1. 登录，上传日志得到 READY artifactId，创建 Conversation。
2. 提交目标，事务建立 taskId、Master Binding、持久 Command 和公开事件；返回 202，用户可关页面。
3. bridge 用 commandId 向固定 Master Session submit；Master 自动发现能力，准备两个授权局部 ContextSnapshot。
4. publish_plan 创建并行日志分析与配置核查 Job；WorkerDispatcher 经 guard 和能力工厂构建叶子 HarnessAgent。
5. Worker 按任务选 Skill、读固定版本，调用受控工具；缺信息原生挂起，平台映射交互给用户。
6. 用户回答先持久写 Interaction/Command，bridge respond 原请求；保留 jobId/taskId，不新建相同业务。
7. Job 结果候选得到原生 Turn 成功证据后才结算；可推进依赖节点。一个实例退出只恢复其持久 Job，不重跑成功分支。
8. 汇合器把固定结果引用提交 Master 的下一协调轮次；仍属于原 taskId。
9. Master 给完成候选；Finalizer 校验必需节点、原生完成和无 UNKNOWN 后，提交最终答案及 Task 终态。
10. 浏览器 snapshot + SSE 恢复显示，查询与重连不执行模型。若外部结果未知，等待核对，不伪装完成。

## 设计状态

当前完成范围是行为、接口、业务数据与故障窗口设计；不是框架已接入或生产验收。SDK 固定候选为 BETA1，正式版本能力比较、依赖可获取性、框架表迁移、恢复及隔离实验仍有硬门槛，见[评审](review.md)。
