# Proposal

## Why
聊天页面需要参考已确认布局完成前端展示，但会话存储方案尚未确定。先保留接口和数据模型，以后端JSON示例支撑页面，不提前决定原生表或自建表。

## What Changes
- 完成左侧会话列表、聊天主区、个人信息与几何头像展示。
- 以“会话”命名替换Directory，不引入文件夹业务概念。
- 聊天接口暂时返回固定JSON数据，不读取SDK历史，不处理历史导入，不添加定时任务。
- 异常定义与映射集中common，当前用户通过全局方法获取；接口增加10字以内中文标题。
- 自建表结构仅保留为候选草案，不作为当前运行实现。

## Capabilities
### New Capabilities
- `chat-sessions`: 用稳定接口和JSON示例数据支撑会话页面，真实存储路线待确认。
- `user-avatar`: 稳定几何头像与个人信息展示。
### Modified Capabilities
- `v2-chat`: 页面布局及当前JSON示例响应边界。

## Impact
影响前端chat/user功能、v2会话接口和common公共支持。正常聊天保留SDK执行与当前会话读取，历史列表采用固定JSON；注册与登录保持现有实现。不恢复旧历史，不执行数据库清理，按用户最新要求测试和重启。


正常聊天入口保留：ChatService处理发送停止继续，ChatRuntimeService打开SDK会话，ChatEventStreamService订阅当前会话执行快照。新对话连接真实模型；固定JSON仅供历史列表及三个示例会话展示，示例不允许发送。正式历史查询和存储适配后续实现，不增加导入或后台核对任务。
