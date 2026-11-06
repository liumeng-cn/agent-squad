# 任务失败收尾验证记录

日期：2026-11-06，OpenSpec 任务 5.2。

## 本次结果

隔离 MySQL 与 SDK 本地模型桩验证，共 16 项，失败、错误、跳过均为 0，进程退出 0：

| 测试 | 项数 |
| --- | ---: |
| WorkerDependencyPolicyTest | 3 |
| WorkerDependencyFailureIntegrationTest | 2 |
| PlanPublishIntegrationTest | 2 |
| MultiWorkerExecutionIntegrationTest | 3 |
| GoalTaskNativeCreationIntegrationTest | 5 |
| WorkerGuardIntegrationTest | 1 |

命令：`python3 tools/verification/run_mysql_probe.py --server-tests WorkerDependencyPolicyTest,WorkerDependencyFailureIntegrationTest,PlanPublishIntegrationTest,MultiWorkerExecutionIntegrationTest,GoalTaskNativeCreationIntegrationTest,WorkerGuardIntegrationTest`。

`npm test`：32 项组件测试、2 项 Node 测试通过。`npm run build`、后端离线 Maven package（跳过重复测试）、`git diff --check` 通过。开发数据库未清理，临时测试容器由夹具回收。

## 历史问题与证据边界

GoalTaskNativeCreationIntegrationTest 的 HTTP 桩原先未适配普通回复流式协议，本阶段补齐 SSE 与 usage，并使用自然正文。失败后的 MultiWorker 桩增加明确放弃调用，使用唯一 toolCallId，避免 SDK 拒绝重复调用。未放松业务完成条件，未修改 SDK。

先前浏览器等待超时仍属失败记录，不作为业务断言通过。本次未进行浏览器外观验收，未调用真实付费模型。以上为针对性验证，不代表全量回归或多机部署验收。

5.2 已完成，计划进度 6/46。5.1 统一完成规则、5.3 逐项验收证据、5.4 统一应用仍未完成；8.5 只完成真实步骤列表部分，不勾选。实现规则见 [任务失败收尾与步骤展示](task-completion-progress.md)。

## 后续完成：逐项验收与正式发布

5.1—5.4 已完成，OpenSpec 复核 9/46。这里记录的是任务收尾、验收、发布和真实步骤这一组，其他阶段不因此自动完成。

- 49 项后端针对性回归通过，失败、错误、跳过均为 0：包括逐项验收、真实 SDK 串并行执行、依赖失败、计划替换、交付竞争、用户修改、完成纠正和 Worker 崩溃恢复。日志 `/tmp/squad-mainline-final-49.log`。
- 前端 35 项组件/状态测试与 2 项 Node 测试及构建通过；覆盖历史版本切换、复用说明、未知验收和用户拒绝。最后的可选工作标记另做相关测试复核。
- 浏览器实际验证最终答案、保留的步骤列表、三种检查方式及刷新恢复，截图 `/tmp/squad-final-acceptance.png`。使用隔离 MySQL、人工构造的 Worker 结果与合成身份；不证明真实模型规划质量或正常网关登录。
- 首轮浏览器发现旧任务完成后不再进入任务历史，已修复按正式答案关联读取任务；首轮扩大回归的 5 个错误来自交付测试仍调用旧回复完成协议，夹具改为当前自然回复证据后 7 项交付测试全部通过，未放宽业务检查。
- 新计划同事务使旧完成候选失效；迟到旧回合不干扰新候选。发布前发现工作重新进入恢复受限会阻止答案发布。API 契约、枚举和公开载荷同步；没有新增数据库结构或实际库 DDL。
- 公开 `plan.published` 保留版本、汇总策略、节点和条件；`job.reused` 保留新旧工作关联。历史步骤按请求版本读取，不能读取外部用户或未来版本。
- 最后补充可选工作标记后，PlanAcceptanceIntegrationTest、PlanPublishIntegrationTest、MultiWorkerExecutionIntegrationTest 共 7 项再次通过；前端 37 项及构建再次通过。当前后端打包通过。接口 19 个正例、13 个反例通过；OpenSpec strict、运行安全和差异空白检查通过。临时容器与浏览器代理均已停止，根目录未重新创建 experiments。

整个仓库的 `check_design.py` 尚有历史基线问题：manifest 的表数量与当前 schema 不一致，且后续检查依赖已删除的旧规范。没有删断言或降低要求来冒充通过。接口正反例、OpenSpec 变更校验、运行安全检查和业务测试分别验证，不将本阶段等同于全量发布准入。


## 2026-11-06 修改反馈与取消收尾

本轮沿原主线实现，不恢复已删除的 recovery-and-streaming-plan.md，不改变消息提交与会话 SSE 的交互方式。

- 6.1 完成：快照与完整事件公开修改处理状态、等待原因、来源消息及实际应用版本；页面在原消息下简短显示，旧快照/历史不能覆盖新状态，未新增消息卡片。隔离 MySQL 验证并发修改只应用一个、拒绝陈旧目标、回滚、停止后拒绝、读取不派发及旧计划隔离。
- 6.3 推进但不勾选：取消意图覆盖全部绑定、立即阻止 Worker guard 继续领取/续租、关闭未派发工具与开放交互。已知未派发工具按零消耗结算，未知收费和已有结算保留。SDK 控制入口不启动会话或领取 Worker。
- 真实 SDK 实验先复现仅发 interrupt 时双 Worker 停在 CANCELLING；补本进程实际执行登记，通过公开 AgentSession.close 合作等待约十秒后取消阻塞运行。后台独立核对 SDK 写入者、操作与沙箱事实，确认后才结束取消；删除复用停止路径，已有停止命令不因删除而被撤销。
- 取消核对采用会话→所有者锁序与当前锁定读，避免旧事务快照漏掉新提交操作；补查分页轮转，未知操作不能长期挤占前二十条，回复与目标分别处理。
- 本轮最终后端选定 12 类共 49 项通过、0失败、0错误、0跳过。包括真实 SDK 双 Worker 网络阻塞取消、阻塞回答取消、纯控制不启动工作、远端 writer 租约与未知操作/沙箱阻止提前终态、已发生写入不撤销、显式重试预算继承、并行汇总和重启恢复。模型请求均为本地 HTTP 桩；外部结果核对部分为明确夹具，不冒充真实外部系统或跨机器验收。
- 前端 38 项 Vitest + 2 项 Node 测试通过，生产构建通过；OpenAPI 21 个有效和 15 个无效样例符合预期，Mapper XML、运行安全检查及 diff 检查通过。
- 全量 check_design.py 仍在原有 plannedBusinessTableCount 与实际表数不一致处失败；未降低或绕过门禁。SDK 在阻塞请求取消时仍有 onErrorDropped ERROR 噪声，未全局屏蔽真实错误，日志治理仍未完成。
- 本轮完成进度 10/46，不能宣称主线全完成。真实多进程取消竞争、完整页面核对动作及真实模型复杂场景仍需验收。现有 SSE 异步鉴权、EOF 错误分类、逐字渲染问题保持在问题记录中。

本地验证日志：/tmp/squad-controls-closure.log、/tmp/squad-final-web.log、/tmp/squad-final-web-build.log。完整交互图见 [复杂聊天交互](complex-chat-interaction.md)。当前业务服务未因本轮改动重新启动；不将磁盘代码和隔离测试视为当前页面已经运行新版本。
