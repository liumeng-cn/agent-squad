# 用户认证验证记录

日期：2026-11-17。本次验证使用本地现有PostgreSQL/Redis，在独立squad_user schema新增用户表；不删除或重建旧数据。

- Maven全量package通过：common 5、Gateway 3、user-server 1、v2-server 17个测试，其中Studio真实外部探针1项跳过；合计25通过、1跳过。
- 前端vue-tsc与Vite构建通过，Vitest 17个文件、87个测试通过。
- 真实HTTP冒烟通过：无登录401、错误验证码、注册、重复手机号409、JWT Cookie、用户查询、CSRF403、昵称编辑与空值回退、旧版本409、直连伪造身份401、外部内部路由404。
- 第二Gateway实例（18184）使用相同RSA密钥和Redis完成认证与退出；退出后在首实例重放原JWT仍返回401，验证服务端会话撤销，不仅是清理Cookie。
- 篡改JWT签名返回401。账号状态及auth_version变更拒绝由认证单元测试覆盖。
- 一键启动、重启及健康检查通过。默认18081被原进程占用时明确拒绝，未停止原进程；本次服务使用18183/18182/18181/5174，日志及PID在.local/auth-test。
- 新建Java文件经IntelliJ IDEA格式化；schema.sql与V1__baseline.sql逐字一致，OpenAPI JSON解析和OpenSpec严格校验通过，脚本语法和git diff --check通过。

页面浏览器视觉检查未完成：浏览器自动审批超时，未获得本地页面访问授权。构建、状态测试与HTTP链路已验证；不据此声称页面视觉验收完成。

当前测试验证码固定8888；未调用真实短信或真实模型。认证多实例验证不代表Agent执行多副本已验收。测试创建随机手机号账号保留在本地用户表中。
