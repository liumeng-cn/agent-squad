# 前端目录与职责

当前仅对接Gateway、user-server、v2-server；旧server已归档，其旧工作台、任务/管理/配置/模型页面、旧登录和实验页面已移除。删除的是代码及路由，不触碰浏览器或数据库中的用户数据。

```text
src/
  app/                       应用壳、路由与路由守卫
  main.ts                    Vue/Pinia初始化、全局未登录回调
  features/
    auth/
      api/                   Gateway登录、会话查询、退出
      model/                 登录结果模型
      stores/                当前登录身份和CSRF生命周期
      views/                 注册/登录页面
    user/
      api/                   user-server注册、资料编辑
      model/                 用户资料类型
      components/            个人信息弹窗（后续头像属于这里）
      composables/           本人资料编辑与当前展示同步
    chat/
      api/                   会话JSON示例接口、消息和SSE订阅
      model/                 聊天公开模型、协议投影、键盘规则
      composables/           当前会话状态及订阅生命周期
      components/            消息、思考、过程、输入框
      views/                 聊天页面（后续目录与布局属于这里）
  shared/
    http/                    HTTP解包、错误和CSRF持有器、ApiResult
    components/              不依赖业务的安全Markdown渲染
    styles/                  全局基础样式、Markdown样式
```

测试就近存放；独立键盘测试在tests/。api文件维护传输，model定义固定结构，stores/composables维护状态与流程，views组合页面，components处理展示和交互；不把特定业务接口放进shared。不创建只转发导出的index层。

当前登录态统一由auth/stores/auth维护。用户API无认证Store依赖；资料编辑由user/composables维护，成功后刷新认证Store中的本人展示；注册请求也直接调用用户API。聊天不持有完整账号认证实现。HTTP只通过回调通知身份失效，不直接跳转窗口或依赖Router。

路由为`/login`、`/register`、`/chat`；已发布的`/v2/login`、`/v2/register`、`/app/squad-v2`作为当前功能别名保留。已归档功能路由不保留组件与守卫，未知路径转当前聊天页。后端传输路径仍为`/api/v2/**`。

```bash
npm run build
npm test
```

新布局及会话目录方案见项目根目录`openspec/changes/design-chat-workspace/design.md`。目录整理保留前一轮未提交的对比度调整。当前历史列表通过后端JSON示例展示，正常聊天保留模型执行和SSE，正式会话存储未定，不包含历史导入或定时同步。本轮按最新要求执行构建、测试和本地服务重启。


正常聊天入口保留：ChatService处理发送停止继续，ChatRuntimeService打开SDK会话，ChatEventStreamService订阅当前会话执行快照。新对话连接真实模型；固定JSON仅供历史列表及三个示例会话展示，示例不允许发送。正式历史查询和存储适配后续实现，不增加导入或后台核对任务。
