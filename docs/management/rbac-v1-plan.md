# 用户角色权限一期

## 范围与验收

复用 sq_user、现有网关登录与可信身份签名。管理只包含用户、角色、权限三个页面，采用紧凑列表与居中弹窗。普通聊天账号不默认授予管理权限，不实现 Agent、Tool、MCP、SubAgent 管理，不实现动态菜单或资源数据权限。

用户页管理已有账号的管理角色与管理启停；新增成员通过手机号查找已有账号，不绕过普通注册流程，不修改账号登录状态。管理禁用只停用其角色关联，普通聊天不受影响。用户撤权及角色禁用在提交后的新请求生效，不中断已经获准的请求。

权限目录由版本化代码与 SQL 维护，本期仅查询。权限码包括 management:access、management:user:read、management:user:manage、management:role:read、management:role:manage、management:permission:read；不允许通过管理随意创建没有后端执行点的权限码。

## 表结构

用户库 squad_user 新增 sq_role、sq_permission、sq_user_role、sq_role_permission、sq_rbac_log。sq_user 新增 rbac_version（管理授权乐观锁版本，与 auth_version 分离）。角色以独立 role_id 为业务标识，角色编码唯一；关联表使用业务 ID。所有新增表有审计字段，角色和权限有中文名称说明。

sq_role 保存角色名称、编码、说明、状态、编辑版本、应用版本及内置标志。sq_role_permission 通过 stage=DRAFT/APPLIED 区分保存草稿和应用权限，唯一键为 role_id+permission_code+stage。sq_user_role 的状态表示该用户的管理关联是否启用，唯一键为 user_id+role_id。sq_rbac_log 与授权修改同事务追加，记录操作者、目标、操作及关系摘要，只增不改，不记录手机号、令牌或密码。

新增角色默认禁用且未应用。保存修改只更新角色草稿及 DRAFT 权限；应用时在事务内复制 DRAFT 为 APPLIED 并启用角色。禁用即时撤销角色授权；重新启用要求已有应用版本，不自动应用未保存或未应用的权限。内置平台管理员角色的定义和权限不可通过页面修改或禁用。

角色权限和用户角色修改先条件推进全局授权版本，并发失败返回冲突；再读取目标角色或用户，核对 version。最后一个启用且账号正常的平台管理员不得被撤权或管理禁用，违反约束整笔事务回滚。用户关联修改更新 rbac_version，角色修改更新 version；接口冲突返回 ApiCode.CONFLICT。

## 接口

业务响应统一 ApiResult，分页统一 PageVO（records、total、current、size），分页从 1 开始，size 为 10/20/50，后端上限 100。

- GET /user-server/api/v2/users/me/management-access：当前用户 canAccess 与 permissionCodes；普通用户返回空权限，不影响正常登录。
- POST /user-server/api/v2/users/management/users/page：keyword、status、current、size；返回 userId、username、displayName、phone、status、roleIds、roleNames、version、lastUpdateTime。status 为 UNASSIGNED/ACTIVE/DISABLED。
- GET /user-server/api/v2/users/management/users/by-phone?phone=...：新增平台成员时精确查找已有账号，不输出额外认证信息。
- PUT /user-server/api/v2/users/management/users/{userId}/roles：roleIds、version；替换管理角色关联，新成员默认启用，已禁用成员继续保持禁用。
- PATCH /user-server/api/v2/users/management/users/{userId}/status：status、version；仅启停管理角色关联。
- POST /user-server/api/v2/users/management/roles/page：keyword、status、current、size；返回角色列表、关联人数、应用版本与草稿版本。
- GET /user-server/api/v2/users/management/roles/{roleId}：角色信息、draftPermissionCodes、appliedPermissionCodes。
- POST /user-server/api/v2/users/management/roles：code、name、description、permissionCodes；创建禁用草稿。
- PUT /user-server/api/v2/users/management/roles/{roleId}：name、description、permissionCodes、version；保存草稿。
- POST /user-server/api/v2/users/management/roles/{roleId}/apply：version；原子应用并启用。
- PATCH /user-server/api/v2/users/management/roles/{roleId}/status：status、version。
- POST /user-server/api/v2/users/management/permissions/page：keyword、current、size；查询代码维护的权限目录。

用户服务和运行服务直接配置 server.servlet.context-path，网关本地认证接口使用 /gateway 前缀。Gateway 为 WebFlux 路由入口，不配置 Servlet context-path。网关按工程路径前缀路由：/user-server/** 归属 user-server，/v2-server/** 归属 v2-server；注册接口为显式匿名特例，网关本身处理登录接口。管理接口集中在用户服务路径下的 /management/**。

Gateway 在登录认证和 CSRF 校验之后，仅对管理路由挂载 Permission 过滤器。过滤器通过 Feign 调用用户服务内部鉴权接口，用户服务按请求方法和路径匹配功能权限。内部鉴权依赖网络隔离，不额外签名。Gateway 的 OpenFeign 原生 Spring Cache 缓存鉴权结果，授权事务提交后更新共享版本失效；未知接口或查询失败不放行。授权事务使用全局版本条件更新裁决并发，保留越权授予和最后管理员保护，不使用悲观查询锁。

## 页面交互

/management/users、/management/roles、/management/permissions 三个路由，独立管理布局，复用登录。无 management:access 时禁止进入；菜单和按钮按权限显示，后端独立强制校验。个人菜单仅对有管理准入的账号显示管理入口。

查询栏：名称/编码/手机号关键字与状态；查询或重置回到第一页；页码和每页数量驱动真实分页。列表有加载、空数据、失败重试状态。

角色新增/编辑弹窗包含名称、唯一编码、说明、权限勾选；编辑时编码不可修改。保存草稿后回读列表。应用与禁用需确认，并携带当前 version。失败保留弹窗内容，409 提示刷新后重新确认，避免静默覆盖。取消未保存内容需确认。

管理用户新增弹窗：输入手机号精确查找已有账号，选择可授予角色后确认授权。编辑已有成员只修改角色。禁止没有角色时启用平台成员；移除全部角色表示撤销管理资格。权限页只查询与查看详情，不展示无效的新增或修改按钮。

## 初始化与执行顺序

1. 同步最终 V1__baseline.sql 与 schema.sql，提供非破坏性 V2 增量与管理员初始化 SQL。
2. 实现 Mapper XML、事务服务、网关权限验证及服务路径路由，补并发版本、最后管理员、撤权边界测试。
3. 实现三页列表、弹窗、分页、确认及管理入口，补路由和权限交互测试。
4. 同步 OpenAPI，完成 Java 格式化、编译、后端与前端测试、构建和本地联调。

首个管理员使用用户指定的手机号，通过显式 SQL 查询对应现有 user_id 后授权；不在应用启动时提权，不自动创建普通账号。仅执行增量建表/加列及该次明确授权，不删除、重建或清空实际数据库。


## 实施与验证

- 页面入口为 /management，沿用现有普通账号登录，只有获得管理权限的账号显示入口。
- 直接配置 server.servlet.context-path：user-server 为 /user-server，v2-server 为 /v2-server；Gateway 本地认证接口使用 /gateway 前缀。下游请求保留工程前缀，不裁剪路径；签名、前端请求、SSE 和跨服务用户查询使用相同路径。调整后需要一起重启三个应用和前端。
- Gateway 只转发服务前缀下的公开 /api/**，内部账号查询与健康检查不向浏览器开放；注册为匿名 POST 特例，登录接口由网关本地处理。
- 事务内校验授权版本和操作者权限，创建、应用、分配和重新启用均不能授予自身不具备的权限；草稿保存不改变生效快照。
- V2__rbac.sql 已在本地增量执行，指定现有账号已完成显式初始化授权。没有清空或重建已有库。
- Java 检查命令：JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn -f server/pom.xml -o -Daether.syncContext.named.factory=rwlock-local -Drbac.integration=true -pl user/user-server,gateway,v2-server -am test。集成测试仅在显式参数启用时连接已迁移的本地库，测试记录和业务变更回滚。
- 前端验证：npm --prefix web/workbench test；npm --prefix web/workbench run build。
- HTTP 联调已验证登录、签名权限查询、三个分页接口、未登录拒绝、未配置管理接口拒绝、内部接口拒绝。临时联调应用结束后已关闭。
- 最后管理员保护已有业务拒绝测试和事务锁实现；多实例并发撤权尚未做真实压力验证。浏览器真实点击和视觉验收仍需在服务重启后确认。
