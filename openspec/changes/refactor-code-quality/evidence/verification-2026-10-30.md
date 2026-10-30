# 网关与 MyBatis 重构验证

## 已完成验证

- Spring Cloud Gateway 4.3.5 内置路由，无手写 HTTP 代理；4 项真实 HTTP 测试通过，覆盖会话、CSRF、身份覆盖、状态/头、multipart 原始字节和 SSE 首帧。
- 最新定向回归：网关 4 项、业务 25 项，0 失败/错误。
- 2026-10-30 17:15:44 完整回归：网关 4 项、业务 139 项，0 失败/错误；业务 24 项专项条件未开启而跳过。使用隔离 MySQL 8.4，只新增 as_test_ 前缀 schema，未删除开发库。
- 33 个 Mapper 共 337 条 SQL 与原注解逐条标准化对比无差异；79 组显式构造映射保留，接口无 SQL 注解。
- ConnectionOptimisticLockTest 验证成功更新后旧版本写入返回 0；BudgetReservationConcurrencyTest 验证真实数据库预算锁边界。
- 前端 TypeScript/Vite 构建、设计静态检查、XML namespace/解析、git diff --check 通过。
- 保留 JDK 27 构建，编译目标固定 Java 21，避免 Spring 扫描器无法解析 major 71。

## 资源目录与当前边界

Mapper XML 已去掉 repository 中间目录，手动 MyBatis 工厂测试的资源路径已同步。完整回归后执行 `clean package`，清除旧 XML 副本再编译并运行网关 4 项、业务 5 项针对性测试，全部通过；两个应用独立 JAR 打包成功。新目录下 33 个 XML namespace 校验通过。

浏览器实际登录、发送消息、页面受理状态、snapshot 与 SSE eventsource 连接已验证，详见 gateway-e2e.md。同步编辑中的 Mapper 声明恢复完整后才执行最终 clean 验证，未覆盖用户编辑。

SQL 合并仅完成 44 张表的只读结构清单；未替换 Flyway 历史或重建实际开发库。测试使用本地协议桩，未调用真实模型。
