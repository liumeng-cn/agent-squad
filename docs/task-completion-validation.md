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

## 2026-11-06 真实千问模型主线复验

- 本地默认模型改为 `qwen3.8-flash`，普通回复、目标协调和默认 Worker 使用同一模型配置。千问思考参数使用 `enable_thinking`，不再套用讯飞 `thinking.type`；默认关闭思考。已受理旧默认模型只有在显式配置指纹完全匹配时才继续使用原模型，否则拒绝静默换模。
- 真实接口验收通过普通聊天：会话 `78d34c31-1fe6-45f5-9140-2ca1f8926a9f`，约 1.5 秒产生最终答案，执行 SUCCEEDED。
- 第一轮真实并行请求复现 Master 只确认受理、不调用计划工具；补强独立协调职责后，第二轮已创建两个并行 Worker。
- 第二轮复现 Master 在 Worker 尚未结束时反复声明完成，最终 MAX_ITERATIONS 连带阻断目标；按持久任务状态隐藏过早完成入口，并使用 SDK `isReturnDirect` 在计划发布成功后立即结束当前协调轮次，等待后台汇合通知。
- Worker 结果工具此前没有声明 result 的具体 Schema，真实模型把对象编码成字符串反复被拒绝；现在公开已发布输出契约，仍保留服务端校验。结果候选成功保存后同样通过 SDK 直接结束回合，避免模型修改后重复上报、占用冲突。
- 上述均保持既有消息提交与会话 SSE 协议，不恢复已舍弃的接口改造方案。真实并行最终交付和修复后的回归结果继续记录在本节，不能把计划发布或候选保存当作完整验收。
- 最终真实模型验收：会话 `5e0d1593-c0cc-49eb-8474-a26f957ecbb0`，目标 `87a295bd-d6d0-4d6c-83eb-1585415e3701`。从与页面相同的消息接口提交“登高、冬雪两首七言律诗”；Master 自主创建目标、发现既有 `regulated_poet 1.0.0`、发布一个含两个独立节点的计划。两个节点均 SUCCEEDED，目标 SUCCEEDED，planVersion=1、answerRevision=1；页面保持打开，经实时更新收到两首完整诗歌及 2/2 已完成步骤。总耗时约 41 秒（20:45:59.437 至 20:46:40.578）。这是复用已发布能力创建执行节点，尚未证明 Master 可自主发布新的 Worker 能力定义。
- 诗歌生成与汇总来自真实千问调用，但格律说明仍有明显知识性错误（例如韵部归类）；结构与任务链路通过不代表专业内容质量验收通过，也不将模型自评等同于独立的专业校验。
- 本轮曾在服务运行期间覆盖同名目标 JAR，导致延迟加载类失败，已停止该进程并改用 `/tmp/squad-server-verify-20261106.jar` 独立副本启动；上述最终成功请求在独立副本上完成，后续构建不覆盖运行包。
- 相关隔离 MySQL 回归最终共 16 项通过、0失败、0错误、0跳过：DynamicReplyModelIntegrationTest 4项、GoalTaskNativeCreationIntegrationTest 5项、MultiWorkerExecutionIntegrationTest 4项、WorkerGuardIntegrationTest 1项、WorkerNativeExecutionIntegrationTest 1项、WorkerCrashRecoveryIntegrationTest 1项。分批执行日志为 `/tmp/squad-mainline-tests.log`、`/tmp/squad-worker-regression.log`、`/tmp/squad-recovery-regression.log`；前两份保留了修正旧夹具前的失败记录，最终结果以各类最新 Surefire 报告为准。候选后崩溃夹具通过真实 WorkerResultService 写入候选、封存阻塞模型调用的 writer，再由第二实例接管，不再依赖结果上报后的多余模型请求。
- 最终后端打包、运行安全检查、diff 空白检查通过。未完成专业诗词质量、全量故障矩阵及跨机器部署验收。

- 页面步骤区按来源消息定位，置于对应最终回答之前；执行时展开，最终正文完成后折叠。手动再次展开不被后续事件刷新收回。历史分页缺少来源消息时放在对应答案前，避免步骤挤到整段对话底部。已在真实成功会话浏览器中确认“处理步骤 2/2 项已完成”为折叠状态且位于诗歌正文上方；新增对应布局与状态切换测试。前端本轮 44项 Vitest + 2项 Node 测试通过，TypeScript 检查及生产构建通过。

## 2026-11-06 AgentScope 运行记录切换数据库

- 所有 Master、Worker、停止控制及纯读恢复入口显式使用官方数据库日志后端；不再从默认复合文件系统落到本地。SDK索引/技能缓存改用每进程临时目录，正常退出清理；服务端不需要持久 workspace 卷。
- 实际停服导入原工作区：19个用户目录、1922个会话、40179个对象、262747389字节（不包含本地版本封装字节）。逐会话核对日志和输入队列，来源保留，不执行删库/重建，不重做模型工具调用。
- 导入专项覆盖大载荷、输入队列、导出水位、重复导入、拒绝覆盖已推进历史。切换后动态模型4项、自主目标5项、并行4项、原生Worker及导入2项、崩溃接管1项，共16项通过；额外共享SSE/双实例5项通过。日志 `/tmp/squad-database-journal.log`、`/tmp/squad-journal-import-test.log`、`/tmp/squad-database-cross-instance.log`。
- 真实千问任务：会话251dece7-792b-48a1-9fba-18c41c144852，目标0b1047f4-01e1-43c3-bb86-69dd66461ecf，两个Worker分别写春雨/秋月，最终SUCCEEDED，已产生完整回答。随后停止并启动另一进程，结果仍可读取。
- 对旧工作区80359个文件逐一比较大小及修改时间，无变更；新缓存目录内 `.agentscope-runtime` 日志文件数量为0，前一进程临时目录已清理。运行安全检查、打包及diff检查通过。0.5完成；3.2的trace等未完部分不据此勾选。

## 2026-11-06 断流恢复专项

修复模型HTTP传输IO分类遗漏和临时草稿终止生命周期；保持SDK一次请求、平台持久最多两次恢复的单一计数，未知收费占额与未知效果保护不放松。真实EOF在首字前/部分正文后各覆盖成功与耗尽，耗尽后手动重答复用预算且不混入旧片段。后端15项、前端45项Vitest+2项Node及构建通过；详细限制见acceptance-issues.md晚间修复记录。该结果不等同于完整跨服务故障矩阵或任务自动换模型能力完成。
