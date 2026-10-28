# Proposal

## Why

优先变更 `conversation-first-experience` 与 D4 完成后进入 D5，把不可信脚本隔离执行与产物交付/保留/完整预算落到实现，使外部脚本在受限沙箱运行、产物可归属下载且预算不被绕过。行为由主 spec `tools-interactions`(M07)、`events-artifacts`(M08) 及优先变更的会话/预算 delta 规定。

## What Changes

- 实现固定隔离驱动与资源/网络/挂载策略，脚本仅经受控工具与 Sandbox 执行。
- 实现 Artifact 上传校验、stage/commit 交付、归属下载鉴权与保留清理。
- 在优先变更统一的 ExecutionScope/BudgetAccount 与共享 Reservation 上接入摘要与沙箱重试，复核普通回复/任务、供应商切换和完整预算 gate。
- 在对话消息中完善附件与交付卡片、下载和分级故障说明；不新增普通用户上传页、下载页或失败诊断页。

## Capabilities

### New Capabilities

（无——已在 `.openspec.yaml` 声明 `skip_specs: true`。）

### Modified Capabilities

（无）

## Impact

- `server`：M07/M08 的 Sandbox 适配、Artifact/预算 Service。
- `web/console`：对话内附件、产物卡、分级故障与服务端允许动作。
- 验收：A16、A18、B03、B10。
