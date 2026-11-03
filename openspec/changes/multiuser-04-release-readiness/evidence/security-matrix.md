# 安全回归矩阵（2026-11-03）

本表区分自动化范围与未执行的真实部署验收，不代表完整安全验收通过。

| 范围 | 当前证据 | 边界 |
|---|---|---|
| 登录、CSRF、退出、可信身份覆盖 | GatewayProxyHttpTest 已通过 | 本地HTTP，不证明真实TLS/CORS拓扑 |
| 管理员权限、最后管理员、角色变更撤权 | AdminUserHttpTest 已通过 | 真实MySQL/HTTP；尚非完整双网关并发压力矩阵 |
| 跨实例会话与撤销 | CrossInstanceSessionTest 已通过 | 同JVM多上下文，不是独立进程灾备 |
| Secure Cookie、过期及停用 | SessionPolicyHttpTest 已通过 | 测试HTTP检查响应头，不是浏览器HTTPS使用证明 |
| Server身份、跨用户资源和版本 | RuntimeHttpSecurityTest / ConversationApiSecurityTest 纳入全量 | 未覆盖全部Skill/Memory/Artifact/Operation组合 |
| Markdown脚本与深层引用、代码复制 | C3前端组件测试已通过 | 实际多用户浏览器仍NOT_RUN |
| 生产配置降级 | 新增Gateway/Server负向断言，待最终定向回归 | 不替代私网和防火墙检查 |
| SDK并发上下文、工具授权全矩阵 | NOT_RUN | 不因投递线程池测试宣称完整用户隔离 |
| 真实HTTPS、CORS、反代、文件全链路 | NOT_RUN | staging未启动 |

所有测试使用隔离测试数据；没有调用真实付费模型、发送第三方消息或执行外部写副作用。
