# 双服务端到端验证

2026-10-30，使用 Spring Cloud Gateway 4.3.5 内置路由，本地独立 JAR 启动，隔离 MySQL 新增 schema（未删除已有库）。

- 无会话访问返回401。
- 缺少CSRF写入返回401。
- 登录后发消息返回202持久受理。
- 会话snapshot返回200。
- SSE收到首行事件数据，随后主动断开。
- 退出后读取返回401。
- 第二网关独立用户跨用户读写均返回404。

Delivery关闭；未调用真实模型。仅验证消息持久受理及已有事件流，不代表模型执行完成。

## 浏览器前端链路

使用 Chrome 原生UI、隔离测试账号，Vite 127.0.0.1:5179 经代理连接上述双服务JAR。登录后发送“浏览器隔离验证消息”，会话页面出现相同内容及“已收到，等待开始”。Chrome DevTools Network确认：

- POST `/api/v1/conversations/messages`：202 Accepted。
- GET 会话 snapshot：200 OK。
- GET 会话 events：200 OK，类型 eventsource，持续连接约15秒后已有传输数据。

未保存浏览器密码，未截图。Delivery关闭，因此不验证模型回答。测试服务、Vite及本次浏览器标签页验证结束后关闭。JAR复制至/tmp运行；此证据对应复制时构建，不代表后续用户正在调整中的源码已通过重新编译。
