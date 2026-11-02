# D4 本地验收记录

记录时间：2026-10-30。Skill 与显式记忆的实现代码、迁移 V15/V16/V18 与页面均已提交；本轮在隔离 MySQL 8.4 上补齐验收证据。测试结果见 `test-results.json`。

## 验收范围与边界

- 采用 conversation-first-experience 已交付的 Message/ExecutionScope/预算与固定标签核对的 SDK 配置；Skill/记忆模块为 D4 新增。
- 模型响应由 loopback HTTP 桩提供，运行的是正式 HarnessAgent、持久 Command/Binding/guard、原生日志与事务投影链路；未调用真实模型。
- MySQL 集成测试使用 `MySqlTestDatabase` 创建的 `as_test_*` 隔离 schema，不触碰开发库。
- 一期长期记忆只接受用户显式写入，框架默认记忆钩子/工具按固定标签核对关闭；不引入向量库或语义检索。

## Skill 导入、版本与治理

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 导入规范化/A14 | 文本包导入校验文件数/单文件/总量，拒绝绝对路径/`..`/符号链接/重复路径/非文本；恶意包不能越权或自动安装 | SkillPackageValidatorTest |
| 版本固定与只读 | 发布固定 skillVersionId 与包摘要；在途执行绑定版本摘要，Git HEAD 或同名新版本不改变在途版本 | SkillHttpIntegrationTest.importsOnlyOwnedVersionsAndPinsActualSdkResources |
| Grant 隔离 | 按用户作用域可见、Grant 隔离；两用户同名技能不混用，停用阻止新回复和新任务选择 | SkillHttpIntegrationTest |
| 安全撤销 | 安全撤销持久化授权代际，阻止在途新读取/调用；重复读快照不隐藏新固定撤销 | SkillHttpIntegrationTest.repeatableReadDoesNotHideNewPinnedRevocation、repeatableReadDoesNotSelectGrantDisabledAfterSnapshot |
| 快照可见性 | 快照提交后新目录对后续读取可见；缺失工具不构成隐式授权 | SkillHttpIntegrationTest.repeatableReadSeesCatalogCommittedAfterSnapshot、missingToolsDoNotBecomeImplicitAuthorization |

## 显式记忆与删除屏障

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 受控写入/纠正 | write_explicit_memory 附持久 Message 用户原话来源；纠正/删除重建授权视图并保留 tombstone，不静默覆盖新值 | MemoryIntegrationTest.ownsRecordsAndUsesCasWithoutOverwritingNewValues |
| 删除屏障/A15 | 删除后回权威 Memory 校验归属/版本/有效期/tombstone；删除内容不回流到普通回复、任务或 Worker 路径 | MemoryIntegrationTest.correctionAndDeletePersistBarrierAcrossRestart、ExecutionKnowledgeIntegrationTest.deletedMemoryBlocksLateAnswerAndArtifactAndCannotReturnFromHistory |
| 过期失效 | 过期即失效，无需版本推进 | MemoryIntegrationTest.expiryInvalidatesEvenWithoutRevisionAdvance |
| 来源与去重 | 从回复派生来源；工具调用去重，回放使用当前回执 | MemoryIntegrationTest.derivesSourceFromResponseAndDeduplicatesToolCalls、replayUsesCurrentReceiptAfterAnOlderRepeatableReadSnapshot |
| 重建不复活 | 删除后以新身份重建同名键不复活旧引用 | MemoryIntegrationTest.recreatesDeletedKeyWithNewIdentityWithoutRevivingOldReferences |
| V17→V18 升级 | 升级迁移保留 tombstone 身份 | MemoryIntegrationTest.upgradesV17TombstonesWithoutChangingTheirIdentity |
| 治理边界 | 记忆治理的 MySQL 端到端（跨重启、跨快照）验证 | MySqlKnowledgeGovernanceTest |

## 自动发现与按需读取

| 验收 | 实验与断言 | 证据来源 |
| --- | --- | --- |
| 普通回复发现 | 普通回复自然语言选中即用，自动发现并加载 Skill，无需会话下拉绑定 | ExecutionKnowledgeIntegrationTest.ordinaryReplyDiscoversAndLoadsSkillThroughSdk |

## 页面

- Skill 导入预览/详情/启用页（B11）：`web/console/src/features/skills/views/SkillsView.vue`，前端 `npm run build` 通过。
- 记忆设置页（B13）：`web/console/src/features/memory/views/MemorySettingsView.vue`，前端 `npm run build` 通过。

## 汇总与复现

- D4 定向测试：22 项，0 失败，1 项跳过（MySqlKnowledgeGovernanceTest 默认跳过，见 README"专用 MySQL 集成测试默认跳过"）。
- 完整套件：131 项，0 失败，0 错误，19 项跳过（默认配置外的专用 MySQL 集成测试）。
- 前端 `npm run build` 通过；未调用真实模型、真实连接器或生产系统。
- Java 21 运行时复验仍未完成（见 D3 证据同项限制），不将本轮结果描述为 Java 21 运行验证。

复现运行：

```sh
SQUAD_TEST_MYSQL_PASSWORD=<mysql根密码> mvn -f server/pom.xml -B -ntp test \
  -Dtest='Memory*,Skill*,ExecutionKnowledge*,MySqlKnowledge*' -DfailIfNoTests=false
cd web/console && npm run build
```

MySQL 测试需显式设置 `SQUAD_TEST_MYSQL_PASSWORD`（可选 `SQUAD_TEST_MYSQL_USER` 默认 root、`SQUAD_TEST_MYSQL_ADMIN_URL` 默认 `jdbc:mysql://127.0.0.1:13306/mysql`）；未配置时跳过，不回退内存库。实际执行使用本机 `agent-squad-mysql` 容器（127.0.0.1:13306）的隔离 `as_test_*` schema。
