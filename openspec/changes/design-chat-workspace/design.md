# Design

## Context

用户明确指出会话存储路线未确认，可能使用AgentScope原生表，也可能自建表。此前加入的Directory、SDK日志投影、导入、PG查询和定时核对实现撤销。当前目标是页面和接口形状，后端直接读取固定JSON示例。

## Goals / Non-Goals

**Goals:**
- 完成参考图的左侧会话列表、主区空态和消息、底部输入、用户菜单和几何头像。
- 保留创建、列表、改名、消息读取、消息发送与订阅接口及DTO/VO。
- 统一接口注释、异常与当前用户入口。

**Non-Goals:**
- 不确定正式存储方案；不读取文件或SDK历史，不写会话表，不做导入和定时核对。
- 正常聊天保留真实模型调用；历史示例JSON不持久化，不当作真实历史。
- 不执行数据库清理；按用户最新要求完成测试并重启本地服务。

## Decisions

### 页面

沿用assets/workspace-overview.svg布局。桌面左侧272px，可折叠为64px；手机使用抽屉。右侧新会话显示欢迎语和输入框，有消息后独立滚动，输入框固定底部。白色主区、浅中性色侧栏、深色正文、蓝色强调色，保持文字和边框清晰。

历史列表目前是演示会话列表，页面标注演示数据。支持点击切换、新建和重命名的页面交互；列表和消息直接取自固定JSON。写接口仅回显请求，不保存变化；刷新后恢复固定示例。SSE返回一次JSON快照并保持连接，不维护模拟状态或定时任务。

头像继续采用用户已确认的稳定几何图案：按用户业务ID摘要生成图案和配色，昵称变化不改头像。用户菜单展示昵称优先，否则用户名；副行用户名或脱敏手机号。个人信息弹窗显示完整本人手机号、只读用户名/ID、昵称编辑及复制动作。

### 后端接口

- POST /api/v2/chat/sessions 创建会话
- GET /api/v2/chat/sessions 获取会话列表
- PATCH /api/v2/chat/sessions/{sessionId} 修改会话名称
- GET /api/v2/chat/sessions/{sessionId} 获取会话消息
- POST /api/v2/chat/messages 发送消息
- GET /api/v2/chat/sessions/{sessionId}/events 订阅会话消息
- POST interruptions/resumptions 保留格式，当前只返回mock结果

没有导入接口。所有业务JSON使用ApiResult，SSE保持事件格式。SessionSummaryVO、SessionPageVO、ChatSessionVO和请求DTO继续保留，正式查询、分页和持久化逻辑等存储方案确定后再设计。当前JSON示例不实现分页查询，不作为正式查询约定。

### 命名和代码归属

前端为chat/api/sessionApi、chat/stores/sessions、chat/model/session和聊天组件。后端为ChatSessionController、ChatController与ChatSessionService；固定数据放在resources/preview，不创建专门的Mock类。没有Directory、Mapper实现、投影器或定时任务。ChatSessionPO及data-structure-draft.sql只表示曾讨论的数据结构草案，不参与运行。

### 公共异常和用户

异常定义放common.exceptions，ExceptionResolver统一响应映射。MVC接口和过滤器使用common.web.GlobalExceptionHandler，Gateway仅保留WebFlux协议适配并使用相同映射，业务模块不再各自定义异常出口或手写错误JSON。

MVC通过CurrentUserContext.getUser/getUserId获取当前验签用户，删除各Controller的重复提取方法。异步流程显式传递用户，响应式场景可从请求属性通过公共方法读取，不自建ThreadLocal。

HTTP及对外调用接口的方法注释首行为10字以内简短中文标题，不带标点或“服务”等空泛词；此要求已写入全局规范。

## Risks / Trade-offs

当前mock只用于页面审阅，数据在服务重启后恢复固定示例；示例不执行模型；正常聊天调用真实模型，历史适配待定。正式会话存储与运行逻辑另行确认，不从mock实现推导生产方案。

## Migration Plan

本轮按用户最新要求测试并重启本地应用。此前新增到本地的候选表未删除或清空；源码已不使用它，启动脚本也不再创建会话表。后续由用户确定存储方案后再实施。


正常聊天入口保留：ChatService处理发送停止继续，ChatRuntimeService打开SDK会话，ChatEventStreamService订阅当前会话执行快照。新对话连接真实模型；固定JSON仅供历史列表及三个示例会话展示，示例不允许发送。正式历史查询和存储适配后续实现，不增加导入或后台核对任务。
