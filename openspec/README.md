# OpenSpec 规划与设计（权威）

本目录是 agent-squad 的规划与设计权威来源。后续设计、计划与变更从这里开始：`openspec-propose` 把讨论变成 change，`openspec-apply-change` 按 change 的 tasks 实现，归档后回到 `specs/`。

## 结构

- `specs/`：系统能力的稳定行为（R01–R12），对应 M01–M08 与一期 Web 工作台。
- `changes/`：在途变更（proposal/design/tasks）。D0–D2 已完成，D3–D6 以 change 承载。
- `config.yaml`：跨模块稳定约束与各 artifact 的撰写规则。

## 能力清单

| spec | 模块 | 对应需求 |
| --- | --- | --- |
| access | M01 | R01 |
| runtime | M02 | R02/R08/R11 |
| master | M03 | R03/R06 |
| worker | M04 | R06/R12 |
| context-memory | M05 | R04/R09 |
| skills | M06 | R05 |
| tools-interactions | M07 | R07/R08/R11 |
| events-artifacts | M08 | R10 |
| web-console | 页面 | R10（B01–B15） |

## 在途变更

| change | 内容 | 状态 |
| --- | --- | --- |
| d3-multi-worker | 多 Worker 协作 | 完成（本地验收，证据见 evidence/） |
| d4-skills-memory | Skill 与显式记忆 | 完成（本地验收，证据见 evidence/） |
| d5-sandbox-delivery | 沙箱与产物交付 | 完成（本地验收，证据见 evidence/） |
| d6-integration-release | 集成与发布准备 | 完成（本地验收，证据见 evidence/） |

## 旧设计资料的迁移关系

以下历史文件已删除，其设计/计划内容已迁入 OpenSpec（历史版本见 git 提交记录）：

| 旧文件 | 迁移去向 |
| --- | --- |
| docs/modules/01–08 | openspec/specs/ 对应 8 个 spec |
| docs/web-console.md + docs/ui/ | openspec/specs/web-console/（含 assets 视觉稿） |
| docs/architecture.md、project-structure.md | openspec/config.yaml context |
| docs/runtime-contract.md | specs runtime/master/worker/tools-interactions + config.yaml context |
| decisions/001–003 | config.yaml context + specs（ADR 结论） |
| docs/predevelopment.md（D0–D6） | openspec/changes/d3–d6 |
| contracts/（现 api/） | 保留为接口契约（OpenAPI + JSON Schema） |

`api/` 是接口契约（OpenAPI 与 JSON Schema），被 `tools/check_design.py` 和前端 DTO 镜像引用；SQL 以 `server/src/main/resources/db/migration/` 的 Flyway 迁移为唯一权威，`data/` 已删除；历史设计、计划、证据与研究文件已随 `docs/`、`decisions/` 一并删除。
