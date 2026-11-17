# Proposal

## Why
新服务拆分后需要独立注册与认证，替代旧版用户和登录链路，使多 Gateway 实例共享会话。

## What Changes
- user-server 管理 PostgreSQL 用户，注册匿名经 Gateway 转发。
- Gateway 使用 JWT、Redis 和账号事实完成认证、CSRF 与接口准入。
- user-api 仅包含跨服务接口及模型；提供可信身份传递和 v2 页面。
- 增加无破坏的一键启停脚本，不自动清库。

## Capabilities
### New Capabilities
- `user-auth`: 手机号注册登录、用户资料、分布式登录态与可信身份。
### Modified Capabilities
无；v2 聊天用户归属要求保持不变，补齐身份来源实现。

## Impact
影响 common、gateway、user-api、user-server、v2-server、web、API 契约和本地脚本。PostgreSQL 保存用户，Redis 保存登录会话；不复用 archive 实现。
