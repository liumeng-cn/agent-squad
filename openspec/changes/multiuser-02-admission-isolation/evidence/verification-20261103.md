# C2 局部修复与验证（2026-11-03）

## 本次修复

- 移除正式 OperationExecutor 的 after-inflight-exit 开关和 Runtime.halt 入口。崩溃试验应从隔离进程外部执行，不允许配置触发业务进程自杀。
- UNKNOWN_ACTION 和 UNDELIVERABLE_ACTION 改为持有当前 delivery_token 时 CAS 写 BLOCKED，清理投递租约并保留 last_error_code。永不合法命令不再永久按 256 秒退避循环。
- 新增 tools/check_runtime_safety.py，静态检查正式 Java 中的 halt 与旧故障开关。

## 实际验证

采用本机 Maven/JDK27，隔离测试 MySQL 8.4 容器端口 23306；不操作原有开发数据库。

公共 Maven 参数：`mvn -f server/pom.xml -B -ntp -o -Daether.syncContext.named.factory=noop -Dsurefire.failIfNoSpecifiedTests=false`。

| 检查 | 结果 |
| --- | --- |
| `python3 tools/check_runtime_safety.py` | PASS |
| `-Dtest=OperationExecutorTransactionTest test` | 1 项通过，0 skipped |
| `-Dtest=DeliveryFairScanIntegrationTest,StopDeliveryIntegrationTest,ScopeRuntimeOwnershipIntegrationTest test` | 9 项通过，0 skipped（新增用例前） |
| `-Dtest=DeliveryFairScanIntegrationTest test` | 6 项通过，0 skipped；含新增非法命令 token fencing/终态回归 |

## 仍未完成

本次没有足够证据勾选任何完整 C2 任务。C2.2.4 的 SDK 异常分类尚未完成；慢 SDK 阻塞实验、有界调度、完整配额、慢投影/SSE 压测、两用户全资源越权矩阵、真实 SDK 双用户并发与独立进程崩溃恢复尚未执行。运行容量仍默认关闭，多业务实例恢复能力未验收，不声明生产可用。

本次未新建 Java 文件；仅修改已有 Java，不对整个文件运行 IDEA 格式化。

## 后续有界投递批次

- CommandDeliveryService 使用普通投递池（默认4）与独立控制池（默认2），均为 SynchronousQueue，拒绝时未调用 claim、不改变已受理命令。进程内 commandId 去重，DB token/CAS 仍为跨进程权威；shutdown 最多分别等待10秒后中断，未确认结果按持久命令恢复。
- 新增 CommandDeliveryConcurrencyTest，以投递入口 Stub 持续阻塞 A，验证 B、STOP、下一轮 RESPOND 可在1秒断言窗口内获得执行机会；实际1项测试通过。该测试不经过真实 SDK，因此不能代替 C2.0.2 的 SDK submit 返回时点实验，也不能证明单例 Harness 的线程安全。
- 新 Java 测试已使用独立 IDEA config/system/log 路径、用户 Default.xml 格式化，输出1文件成功。没有格式化已有Java。
- 首轮测试因第二次扫描重复模拟已完成 STOP，导致 RESPOND 无队列竞争失败；修正 Stub 按真实持久状态只返回尚待执行候选后通过。
- 该批仍缺 per-user 投递并发上限、跨扫描持久公平游标、慢投影隔离与真实SDK双用户验证。不能将普通4/控制2视为发布容量结论；门槛任务保持未勾选。

## 控制候选窗口修复（待统一回归）

审查发现独立控制线程池仍可能无用：普通 findDueIds 固定20候选，被其他用户更早命令占满时 STOP/RESPOND 根本不会进入派发。新增独立 findDueControlIds（20条、相同到期/禁用用户条件）并优先合并去重，再走已有 claim/CAS。新增控制命令位于普通窗口外仍被控制扫描找到的真实 MySQL 测试，暂未运行。

同 Binding 的 MESSAGE_SUBMIT 顺序仍由 claim 中的 scope 锁及 earlierBlockingReplies 事务复核控制，线程池并不替代它。投递租约仍为60秒；SDK调用超过租约时其他进程可以恢复同命令。进程内去重只保护本进程，跨进程仍依赖稳定SDK commandId及原生writer fencing；本轮未证明此窗口的多进程安全，不声称多实例运行能力已验收。

## 统一回归补充

主代理统一定向日志 `/tmp/agent-squad-final-targeted.log`：DeliveryFairScanIntegrationTest 8/8（含Slot与独立控制扫描）、CommandDeliveryConcurrencyTest 1/1、ResponseNativeExecutionIntegrationTest 8/8（含真实SDK双用户并发），BUILD SUCCESS。

双用户用例使用真实SDK与本地HTTP模型协议桩，两个用户首轮请求通过并发屏障确认同时在途，验证独立Session、同幂等键按用户隔离、消息与完成工具回合上下文无交叉。不接外部模型供应商；没有覆盖Worker完整回合或独立操作系统进程接管，C2.2.2/C2.2.3仍不可整体勾选。

## 非法载荷真实入口修复（待主代理回归）

审查发现旧blockInvalid分支受claim动作白名单限制，未知action并不能实际到达；字段不全和坏内层JSON也仍会无限重试。新增到期PENDING未知动作（RuntimeCommandAction枚举外）命令的受控阻断（每轮100上限，状态条件CAS，不扩大SDK可执行动作）；SUBMIT/RESPOND无效字段与JSON解析错误在调用SDK前终结为BLOCKED，并清理确定未提交的Slot。新增5个经真实deliver/deliverDue入口的本地集成用例，尚未运行。

删除意图边界修正：DELETE_CONVERSATION是枚举内的平台意图，已明确排除在未知动作阻断之外，也未加入原生执行白名单。新增经过deliverDue及deliver入口的保护用例，尚待统一回归。

## 最终统一验证与审查结论

主代理最终定向日志 `/tmp/agent-squad-review-regression.log`：ResponseNativeExecutionIntegrationTest 14项全部通过（包含非法JSON、SUBMIT/RESPOND字段、未知动作与DELETE平台意图保护），FinalDelivery 7项、TaskSnapshot 1项、FairScan 8项，共30项通过。最终合并报告193项，0失败、0错误、0跳过。

只读审查交付仲裁：FinalDelivery锁位在会话/执行所有者之后；发布权转交与目标创建同事务；重试以当前ownerId防止旧代际重新发布；取消仅关闭匹配所有者。会话删除增加generation，正常删除链路的迟到Task结果会被代际检查挡住。本次未发现可证实的新增双FINAL路径。

保留限制：真实SDK普通回复双用户并发已有本地协议桩证据；Worker并发、跨独立进程接管、慢投影/SSE压力、完整越权矩阵、每用户投递上限及大于扫描窗口的有界公平性仍未验收。测试通过不代表所有C2任务完成，持久运行容量仍默认关闭，不能宣称多业务实例或生产就绪。
