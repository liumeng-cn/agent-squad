# OpenSpec

`specs/` 定义项目现行能力与行为，`changes/` 保存未完成变更及其任务和验证证据，`config.yaml` 定义跨模块约束。

已归档变更已从工作区删除；需要追溯时使用 Git 历史。不要根据旧阶段名称推断当前实现或验收结果。

## 能力规格

| 目录 | 能力 | 需求 |
|---|---|---|
| access | 账号、会话与身份信任 | R01 |
| runtime | 运行状态、命令、恢复与持久事实 | R02/R08/R11 |
| master | 目标、计划和协调 | R03/R06 |
| worker | Worker 定义、委派与执行 | R06/R12 |
| context-memory | 上下文与显式记忆 | R04/R09 |
| skills | Skill 版本与授权 | R05 |
| tools-interactions | 工具、操作与交互 | R07/R08/R11 |
| events-artifacts | 公共事件与产物 | R10 |
| web-console | Web 工作台 | R10 |

## 在途变更

- [重复最终交付修复](changes/fix-duplicate-final-delivery/tasks.md)
- [C2 资源治理与并发恢复](changes/multiuser-02-admission-isolation/tasks.md)
- [C3 对话与 Agent 体验](changes/multiuser-03-chat-agent-experience/tasks.md)
- [C4 发布准备](changes/multiuser-04-release-readiness/tasks.md)

任务只有在指定行为和验收完成后才勾选。代码实现、定向测试、全量测试、真实 SDK/模型及生产验收分别记录；未完成的门槛不得用历史通过结果代替。

## 配套权威

- [OpenAPI](../api/openapi.json)：传输结构。
- [UI manifest](../api/ui-manifest.json)：路由与接口映射。
- [最终 baseline](../server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql)：开发及测试最终存储结构。
- [受控生产迁移](../deploy/db/migrations/README.md)：生产迁移链和数据管理边界。

在项目根目录执行 `openspec list --json` 查看进度，执行 `openspec validate --all --strict` 校验规格。设计与接口静态检查使用 `python3 tools/check_design.py`，静态通过不能代替数据库和运行时验证。
