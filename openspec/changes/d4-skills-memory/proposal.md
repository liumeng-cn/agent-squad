# Proposal

## Why

D3 多 Worker 协作完成后进入 D4，把 Skill 自动发现与版本治理、显式长期记忆落到实现，使执行者无需逐次绑定即可按需读取固定版本 Skill，且用户记忆可写、可纠正、可删除并具备删除屏障。行为已由主 spec `skills`(M06)、`context-memory`(M05) 规定。

## What Changes

- 实现 Skill 规范化文本包导入、版本固定、按用户作用域可见与停用/撤销。
- 实现执行者自动发现并按需读取固定版本 Skill，不要求逐次绑定。
- 实现显式记忆写入/纠正/删除与删除屏障，旧检查点/摘要不复活已删内容。
- 工作台交付 Skill 导入/详情/启用与记忆管理页。

## Capabilities

### New Capabilities

（无——已在 `.openspec.yaml` 声明 `skip_specs: true`，行为由主 spec skills/context-memory 覆盖。）

### Modified Capabilities

（无）

## Impact

- `server`：M06/M05 的 Service/Repository/DTO、Skill 包校验与显式记忆治理。
- `web/console`：技能页与记忆设置页。
- 数据：`sq_skill_version`/`sq_skill_grant`/`sq_memory` 表。
- 验收：A14、A15、B11、B13。
