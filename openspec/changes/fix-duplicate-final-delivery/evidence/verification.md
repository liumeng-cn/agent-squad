# 最终交付验证记录

日期：2026-11-03。使用本机JDK27、隔离MySQL8.4；未读取开发/生产数据库，未调用真实模型。

## 已验证

- `FinalDeliveryIntegrationTest` 首轮4项通过：真实MySQL委派双候选、Task重复事件、直接回答、幂等受理、发布与转交竞争、事务回滚、新代际拒绝旧owner、在途Task期间独立问题。
- `GoalTaskCreationIntegrationTest` 4项通过：来源/预算继承、工具重放、事务整体回滚、附件有效性。
- `ExecutionFailureIntegrationTest` 4项通过：故障分类、有限自动重试、显式后继、未知副作用阻断。
- 共12项通过，0失败、0错误、0跳过。命令：
  ```bash
  mvn -f server/pom.xml -o -Daether.syncContext.named.factory=noop \
    -pl agent-squad-server -am \
    -Dtest=FinalDeliveryIntegrationTest,GoalTaskCreationIntegrationTest,ExecutionFailureIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```
  依赖显式 `SQUAD_TEST_MYSQL_ADMIN_URL/USER/PASSWORD`，管理员URL仅指向本次隔离实例系统库；测试自动分配并清理自己创建的schema。原始日志：`/tmp/final-delivery-tests2.log`。
- `python3 tools/check_design.py` 通过：51张最终结构表；静态检查不等同实际迁移验证。
- `openspec validate fix-duplicate-final-delivery --strict` 通过。
- 本次新建的3个生产Java文件与1个测试Java文件均已调用IDEA格式化，使用独立临时IDE配置，未关闭或改动用户IDE。

## 补充验证与未运行

- Task取消后迟到完成、Task失败结果保留及原Response不可重试：最终版 `FinalDeliveryIntegrationTest` 6项全部通过，0失败、0错误、0跳过（主代理串行定向日志 `/tmp/agent-squad-final-targeted.log`）。同时真实SDK协议桩 `ResponseNativeExecutionIntegrationTest` 8项通过。
- Web消息身份/回放/store自动化由C3变更共同验证，浏览器可见证据尚未取得，不将FD.3.2标完成。
- 主代理负责整合分支全量后端、前端及V1→V5 schema parity，结果以其最终记录为准。
- 历史原始作文案例未给会话ID；真实模型端到端未运行，不能宣称历史案例根因已被运行事实证实。

## 首轮失败说明

第一次编译发现测试泛型断言推断歧义，已显式指定String泛型修复。之后一轮12项中11项应用启动异常（运行时`UserStatusMapper.class`资源缺失），与共享target并行编译时间重叠；停止并行Maven后12项全部通过，原因推测为构建输出竞争，尚未作独立故障复现实验。另该轮`ResponseNativeExecutionIntegrationTest`7项通过，不据此声称后续新增改动已经覆盖。

## 部署限制

V5仅新增交付表，不自动猜测历史结果。旧RESPONSE和带origin的在途Task缺少交付位时会明确冲突，投影事务回滚而不产生第二份答案；生产迁移前必须排空旧运行或按脱敏证据受控回填，并验证发布前置检查。未完成此项不得直接发布或归档。

## 共同前端验证

C3代理实际执行 `npm --prefix web/workbench test`：Node键盘2项 + Vitest13项，共15项通过、0跳过。其中store6项覆盖持久messageId重复、旧revision、历史分页合并、切会话、完成payload及SSE序列缺口/generation恢复；另含Markdown4项、Composer3项。`npm --prefix web/workbench run build`通过。未取得实际浏览器可见证据，FD.3.2继续保持未完成。

## 最终整合复验

主代理执行的完整后端全量186项通过、0跳过；随后最终定向日志 `/tmp/agent-squad-review-regression.log` 30项通过，包括FinalDelivery7、ResponseNative14、TaskSnapshot1、DeliveryFair8。合并去重后的最终覆盖为193项全通过。前端15项及build、设计检查和OpenSpec strict均通过；真实模型仍为NOT_RUN。没有自动归档，浏览器可见验收仍待完成。

复审额外风险：会话投影已拒绝旧完成正文和不递增revision，但TaskSnapshot当前仍从Task事件按event_seq读取最后答案；若内部调用产生新序号的旧完成/修订事件，Task详情快照可能取旧正文。现有第7项只断言会话消息和公共会话事件，未证明此Task详情场景，已向主代理报告，不能宣称完整重放验证闭环。

上述Task详情风险已实施最小修复：快照按Task权威answerRevision筛选，并固定同修订最早事件身份；缺revision的历史完成事件仅属于代际内修订0，新完成事件显式携带revision。第7项补充TaskSnapshot正文及修订断言；修改后等待主代理统一定向复验，不沿用前一轮通过结论。

复验发现首次完成事务会把answer_revision加1，已将新完成事件修订映射修正为提交前row.answerRevision+1。历史缺revision事件不再一概按0：只有正文与该Task已持久助手消息逐字一致时，借用该消息的确切revision；无此证据才按0兼容旧合成记录。这样不把旧不同正文映射到当前修订。第7项补充历史同正文/缺revision的快照断言，等待主代理新一轮定向复验。

## Task详情快照最终回归

`/tmp/agent-squad-snapshot-final.log`：FinalDelivery 7、TaskSnapshotAnswer 1、CompletionCorrection 6、TaskDetails 1、GoalTaskNativeCreation 4，共19项通过，0跳过。完成事件显式记录递增后的权威revision；详情只读当前修订首个事件。历史缺revision事件只有正文与该Task持久消息逐字一致时采用消息确切revision，拒绝将旧正文套用新版本。第一次回归发现旧row revision与完成后revision不一致，已修正后重新全部通过。
