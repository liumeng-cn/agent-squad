# Tasks：C2 资源治理与并发恢复

## 0. 证据先行
- [ ] C2.0.1 在 `feature/multiuser-c2-admission` 分支记录 C1 commit 与所有测试基线；读取真实 `CommandDeliveryService/NativeSessionGateway`、`NativeProjectionPoller`、`ConversationEventStreamer` 源码。
- [ ] C2.0.2 新增阻塞 SDK Stub + 双用户调度回归：测试 A 提交阻塞与 B 的 SUBMIT/STOP/RESPOND；记录是否需要有界并发改造，保留可复现耗时证据。
- [ ] C2.0.3 新增慢投影与 20/50/100 SSE 连接模拟测试，记录线程池、DB 负载与请求积压，区分缺陷与性能基线。

## 1. 调度与配额
- [ ] C2.1.1 只有实测存在慢命令阻塞时，实施“扫描→公平调度→有容量才 CAS 领取→有界 worker 执行”的调度；STOP/RESPOND 控制优先，保留 MESSAGE_SUBMIT 顺序。测试跨用户公平性、无双重领取与 shutdown 释放。
- [ ] C2.1.2 实施持久化 per-user/per-global 运行 slot（优先复用已有机制，不重复建表），任务/Worker/SSE 配额可配置；测试 2 用户同时请求、达到上限、异常退出、租约过期、取消释放。
- [ ] C2.1.3 队列满/配额满对新请求返回 429 + 有意义 Retry-After；对已 202 受理请求持久等待不丢消息。回归 202 幂等与前端状态。
- [ ] C2.1.4 SQL 查询和索引评估：检查 `DeliveryMapper.findDueIds`、投影扫描、SSE 事件分页、User Budget 行锁；用 MySQL EXPLAIN 和压测确认，才做索引变更。

## 2. 运行时安全与恢复
- [ ] C2.2.1 针对 Session/Binding/Skill/Memory/Artifact/Operation/预算添加两用户越权矩阵测试；管理员不默认穿透用户数据隔离；保持 SDK 真实协议测试。
- [ ] C2.2.2 在两个并发用户真实 SDK Session 上跑 Master/Worker/工具回合，记录单例 Master 的线程安全行为；若发现交叉污染，最小化隔离改造且重跑完整测试。
- [ ] C2.2.3 真正运行两个独立进程、不同工作目录、共享隔离 MySQL 的崩溃恢复试验；明确 SDK workspace 是否恢复必需。失败则更新部署限制为单业务实例，并留下 blocking issue，不得声称多副本可用。
- [ ] C2.2.4 将 Runtime.halt 故障注入从正式执行路径隔离；统一 SDK 异常分类；无效命令有有界终态。测试旧幂等/账本/恢复边界。

## 3. 质量门槛
- [ ] C2.3.1 完整后端测试、前端构建、设计检查；记录每项 skipped 和真实故障试验结果。
- [ ] C2.3.2 记录 per-user 配额、全局上限、压力数据与本次明确支持的部署规模；保持 DB 没有超量/过期 slot 泄漏。
- [ ] C2.3.3 提交 C2 代码与 `evidence/`；只有安全门槛通过才可开始 C3。
