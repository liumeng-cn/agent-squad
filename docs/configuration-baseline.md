# 启动配置与资源边界

2026-10-20。配置设计，不是已创建的Spring配置或已配置的环境。机器定义见[runtime-config.schema.json](../contracts/runtime-config.schema.json)，[示例](../contracts/runtime-config.example.json)仅为离线设计fixture，不能直接当作可运行应用配置。

## 1. 模式与版本

| 模式 | 模型与工具 | 存储 / 启动规则 |
| --- | --- | --- |
| OFFLINE | 确定性MockModel，无真实外部调用，沙箱关闭 | 用显式本地测试库或测试后端；界面必须标识离线样例 |
| DEVELOPMENT | 配置化真实模型，受控测试工具 | 本地专用MySQL、文件目录；凭据通过环境引用 |
| PRODUCTION | 固定模型/工具版本与预算、隔离执行 | 共享耐久日志、TLS/安全Cookie、对象存储、禁自动建表与本地shell |

Java编译/运行21；AgentScope固定评估候选2.1.0-BETA1及提交摘要。Spring Boot和MyBatis精确patch在D0依赖树/构建实验后锁定，不能因为POM可下载就宣称兼容。前端Vue/Router/Pinia/Vite/TypeScript使用一致锁文件；当前不创建虚假的pom/package锁定记录。

## 2. 启动校验

- 配置按Schema严格解析，未知字段拒绝；版本/命名空间与旧Binding不兼容时不推进旧任务。
- 数据库和原生日志后端均可持久且支持CAS；验证所需表和索引存在，运行账号无DDL权限。未通过时readiness失败，不受理目标或外部写入。
- app.instanceId仅标识进程实例，不参与持久namespace。重启更换instanceId不改变会话身份。
- Secret字段只接受env引用，实际值不在设计示例/仓库中。启动时校验必需引用存在；密钥轮换采用keyId+版本，旧Connection密文需能解密直到轮换完成，不自动丢弃旧版本。
- DEVELOPMENT/PRODUCTION的真实模型必须有baseUrl、apiKeyRef、冻结模型名称及计价配置。不能悄悄退回MockModel；OFFLINE不允许真实模型或网络工具。
- PRODUCTION必须启用Secure Cookie、同源HTTPS入口、共享对象后端；未通过的Sandbox策略保持关闭。驱动开关不能自动获得宿主Docker权限。
- Profile金额/Token/调用次数/总期限为正且冻结。模型计价缺失时拒绝目标；离线示例采用TEST_UNIT，不代表任何实际供应商价格。

health存活与readiness分开；readiness反映DB、journal和必需模型配置可用性，不每次探针都发一次付费推理。恢复阻塞任务要可查询，不因为依赖失败展示成空会话。

## 3. 线程、连接与限流初值

| 资源 | 设计初值 | 边界 |
| --- | --- | --- |
| 数据库连接预算 | 32，保留4；业务池12、SDK池10、Sandbox锁池2 | 池上限之和+保留≤预算；不是MySQL服务器已修改的max_connections |
| Binding所有者 | 每实例最多12 | 等待/终态在安全点释放；不得HTTP结束直接close仍执行的Agent |
| 全局模型并发 | 每实例8 | 所有Master/Worker/摘要/重试共享；不等于每个Agent各8 |
| 命令桥接 | 4个工作线程 | 有界队列，拒绝/退避可见，不能占用续租线程 |
| 平台guard | 60秒，20秒续租 | 续租<ttl/2，使用独立调度；不改变SDK自身writer lease |
| 扫描周期 | 1000ms | 每次有界批量，不全表忙轮询；到期扫描+索引 |
| 模型请求 / 工具 | 90秒 / 30秒 | 超时不把WRITE改成未发生；未知先核对 |
| SSE连接 / 心跳 | 100 / 15秒 | 与任务执行资源分离，慢连接不能阻塞journal导出 |
| 沙箱单任务 | CPU1、内存512MiB、PID64、60秒 | 默认关闭；需要实验确认驱动真的执行限制 |

线程池拆分平台事务/桥接、SDK阻塞存储、模型IO、续租扫描、SSE观察及隔离执行。具体SDK调度器能否替换需D0确认；无法注入时用全局信号量和Binding上限约束，不声称已经配置了SDK不存在的线程开关。

这些是单实例初值。多副本模型并发、用户配额和费用上限必须仍受数据库GoalTask/Reservation全局约束，不能扩容一倍就绕过用户总预算。资源不足排队，guard续租保持独立。

## 4. 超时与等待

Profile.absoluteDeadlineSeconds为目标从接受到结束的总墙钟期限，包含人工等待，示例为24小时。Interaction有效期取配置上限与目标剩余期限较小者，示例1小时；到期将交互失效并由Master明确说明，不无限等候。单次模型/工具超时不等于目标超时。

Task超期先停止新派发；在途操作仍遵循核对规则。审批后重验目标期限和资源授权。自动恢复最多3次，等待人工输入不消耗一次模型重试名额；真正再次推理及SDK重试仍计入费用/调用预算。

## 5. 配置变化和证据

模型/工具/Skill/权限版本引用写入冻结输入，SDK配置摘要写Binding；敏感凭据只保存引用。发布不兼容版本先排空或保留旧执行模板，不能让滚动升级中的旧任务读取新配置解释旧检查点。

D0需保存：依赖树与锁定版本、配置校验结果、脱敏实际池/线程配置、SQL迁移检查、Mock恢复与失效实例实验。此文与示例通过静态校验只代表设计参数满足关系，不代表服务已经跑过。
