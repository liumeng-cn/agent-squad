# Proposal

## Why

D0–D5 交付各纵向切片后进入 D6，做多副本一致性、备份恢复、负载与安全回归及无障碍/窄屏验收，收敛运行证据与已知限制，使一期可发布。行为由主 spec `runtime`/`access`/`events-artifacts` 及配置基线规定。

## What Changes

- 多副本 guard/租约/SSE 一致性验证。
- 备份与恢复演练（平台表 + 原生日志/inbox/水位 + Skill 固定版本 + 对象引用）。
- 负载与安全回归（预算上限、越权、CSRF、日志脱敏）。
- 无障碍/窄屏/视觉走查与整条工作流验收。

## Capabilities

### New Capabilities

（无——已在 `.openspec.yaml` 声明 `skip_specs: true`。）

### Modified Capabilities

（无）

## Impact

- 全部模块的集成验证；不新增业务功能。
- 验收：运行证据与已知限制清单齐全，不以接口数量代替完成度。
