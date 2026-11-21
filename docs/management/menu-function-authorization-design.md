# 菜单与功能级联授权方案

状态：已实施菜单功能级联授权；Gateway 按管理路由挂载权限过滤器，规则由用户服务数据库提供。

## 目标与范围

在已实现的用户、角色、权限管理基础上，增加菜单与功能的级联授权。角色显式关联选中的菜单和功能，用户通过多个启用角色获得授权并集。保持普通账号与管理资格分离，保留草稿保存、应用生效和最后管理员保护。

本期只覆盖用户管理、角色管理、权限管理三个页面。菜单及功能目录由代码与版本化 SQL 维护；不做菜单配置页面，不预置没有实现的删除、导入、导出功能。后续功能实际落地时再新增权限项。

## 数据模型

不另建功能表，现有 sq_permission 即功能权限目录。新增 sq_menu 和 sq_role_menu；扩展 sq_permission，复用 sq_role_permission、sq_user_role 和 sq_role。授权变更同事务写入 sq_rbac_log 操作日志。

| 表 | 字段与约束 |
| --- | --- |
| sq_menu | id 存储主键；menu_id 业务标识唯一；parent_menu_id 父菜单业务标识，根节点为空；code 唯一；name；type 为 DIRECTORY 或 PAGE；route_name 和 route_path 仅页面节点配置；icon；sort_order；status ACTIVE 或 DISABLED；version；公共审计字段 |
| sq_permission | 保留唯一 code、name、description；新增 menu_id 关联所属 PAGE、sort_order、status；每项代表一个可执行功能，不能绑定目录节点 |
| sq_role_menu | id；role_id；menu_id；stage DRAFT 或 APPLIED；公共审计字段；唯一键 role_id、menu_id、stage |
| sq_role_permission | 沿用角色、权限码和 DRAFT/APPLIED 快照关联 |
| sq_role | 沿用 version、applied_version；菜单和功能共用同一个角色编辑版本 |

新增表使用业务标识关联、中文表字段注释、统一创建人和更新人审计字段。为菜单父节点、权限所属菜单、角色菜单反向查询建立索引。route_name 与前端静态路由注册表对应，数据库不保存可执行组件代码或任意外部跳转地址。

父菜单必须存在，禁止循环引用；DIRECTORY 可挂目录或页面，PAGE 只能通过权限目录挂功能，不能再挂菜单。第一期预置一个管理目录和三个页面菜单。

## 预置授权树

```text
管理平台
├── 用户管理
│   ├── 查询用户      management:user:query
│   ├── 分配角色      management:user:assign-role
│   ├── 启用管理资格  management:user:enable
│   └── 禁用管理资格  management:user:disable
├── 角色管理
│   ├── 查询角色      management:role:query
│   ├── 新增角色      management:role:create
│   ├── 修改角色      management:role:update
│   ├── 应用权限      management:role:apply
│   ├── 启用角色      management:role:enable
│   └── 禁用角色      management:role:disable
└── 权限管理
    └── 查询权限      management:permission:query
```

“重置查询条件”“展开”“收起”“关闭弹窗”是本地交互，不独立建权限。查询与列表、详情接口关联。一个功能可以保护多个接口，一个接口必须明确对应所需功能；选项查询按其所属业务操作授权，不要求额外开放权限管理页面。

## 级联与勾选规则

1. 展示为目录 → 页面菜单 → 功能树，支持全选、全不选、半选和搜索。
2. 用户直接勾选目录或页面时，选中当前可授权范围内的全部后代。取消时移除相应后代；超出操作者权限范围的节点禁选，不暗示拥有全部后代。
3. 勾选功能时自动选择所属页面及祖先目录。第一期各页面进入时会查询数据，因此选择写功能时同时补齐该页查询功能；取消查询功能时明确提示会一起移除依赖它的写功能。
4. 取消某个功能，不清除同级其他功能。取消最后一个功能时可保留页面菜单，表示允许看到页面但无数据和操作；页面必须展示无查询权限状态，不能自动请求列表。入口与默认路由可选择该页面，但不能伪装成已有查询权限。
5. 父节点半选只是前端汇总状态，不作为额外授权保存。祖先目录作为结构闭包保存在 menuIds 中，不因目录存在授予其未勾选的子页面。
6. 保存提交明确的 menuIds、permissionCodes 和 version。服务端验证归属、依赖和可授予范围，并生成祖先闭包，不能只相信前端的勾选结果。
7. 停用目录或页面时，其后代菜单与功能均不生效，保留角色关联供后续恢复。停用功能也保留关联，但不能执行。目录将来新增节点时，已有角色不自动获得新菜单或功能；必须重新授权并应用。

## 角色授权交互

采用用户截图中的双栏级联树，放在约 1040px 宽的居中“分配权限”弹窗内，自适应小屏；角色基础信息仍使用现有居中表单。角色列表增加“分配权限”操作，修改角色名称不要求重新选择权限。

左栏“全部菜单与功能”：显示操作者可见的完整结构，提供搜索、展开全部、收起全部。勾选只表示本次待分配项目，点击“分配”后加入右栏草稿。右栏“已分配菜单与功能”：按同样层级显示当前角色草稿，支持勾选后“移除”；移除目录或菜单时一并移除其后代，并提示影响数量。

两栏各自维护待操作勾选状态，不把左侧临时勾选直接当成已授予。右栏才是待保存的完整集合。右侧显示菜单数、功能数和禁用项目标记；搜索只过滤展示，不能丢失隐藏选项。支持按钮操作和键盘操作，不依赖拖拽。

底部“取消”“保存草稿”。取消有修改时使用现有页面内确认框；保存后仍需在角色列表点击“应用”。详情模式只读，展示草稿与已应用差异（新增、移除），帮助确认实际影响。请求失败保留选择，版本冲突提示刷新后重新确认，不覆盖他人的修改。

## 接口设计

公开路径前缀沿用 /user-server/api/v2/users/management，业务响应沿用 ApiResult。

| 接口 | 入参 | 返回及权限 |
| --- | --- | --- |
| GET /authorization-tree | 无 | 目录、页面、功能树及 selectable、disabledReason；具备角色新增或修改权限的用户可查询 |
| GET /roles/{roleId}/authorization | 无 | roleId、version、appliedVersion、draftMenuIds、draftPermissionCodes、appliedMenuIds、appliedPermissionCodes；查询角色权限 |
| PUT /roles/{roleId}/authorization | menuIds、permissionCodes、version | 保存草稿并返回新版本；修改角色权限 |
| POST /roles/{roleId}/apply | version | 同事务应用菜单和功能快照；应用角色权限 |
| POST /roles/{roleId}/enable | version | 只恢复已有 APPLIED 快照，不隐式应用草稿；启用角色权限 |
| POST /roles/{roleId}/disable | version | 禁用角色；禁用角色权限 |
| POST /users/{userId}/enable | version | 启用管理资格；启用管理资格权限 |
| POST /users/{userId}/disable | version | 禁用管理资格；禁用管理资格权限 |

当前用户 GET /user-server/api/v2/users/me/management-access 扩展返回 menus 与 permissionCodes，供个人菜单入口、管理平台菜单、默认页面和按钮使用；menus 仅包含已授权且整条祖先链启用的节点。canAccess 根据是否至少有一个有效页面菜单计算，不要求创建“平台准入”按钮权限。

树节点建议使用明确的菜单节点和功能节点模型：菜单包含 menuId、parentMenuId、code、name、type、routeName、routePath、icon、sortOrder、status、selectable、disabledReason、children、functions；功能包含 code、name、status、selectable、disabledReason。用明确 DTO/VO 表达，不用任意 Map 混合所有节点字段。

查询角色详情和授权详情需要查询角色功能；创建角色时可一起提交初始授权草稿，也可先创建空角色再分配。角色基础信息修改接口不再隐式覆盖菜单和功能。

## Gateway 与事务边界

Gateway 的管理路由过滤器直接向用户服务询问是否允许；用户服务按 HTTP 方法和完整路径匹配功能权限。启用和禁用拆成独立接口，避免仅按同一个 status 接口无法区分两项功能。未知管理接口默认拒绝；权限查询超时或失败时不放行。

服务端生效权限查询必须同时满足账号正常、用户角色关联启用、角色启用且已应用、功能启用、功能所属页面已授予、页面和祖先目录均启用。多角色取并集，同一角色的功能必须与它自己的已应用菜单快照一致，不能用另一个角色的菜单补全非法关联。Gateway 从登录会话取用户编号，内部鉴权依赖网络隔离，不信任浏览器上传的用户编号、权限或菜单。

保存与应用流程：读取授权作用域版本 → 校验操作者并条件推进授权版本 → 读取角色并核对编辑版本 → 校验目录、依赖、菜单功能归属和操作者可授予范围 → 写入 DRAFT 或整体替换 APPLIED → 更新版本和审计 → 提交。角色的菜单快照、功能快照和 applied_version 必须同事务切换，任何一步失败整体回滚。

不能授予自己不具备的菜单或功能；启用角色和恢复用户资格也重新校验可授予范围。内置平台管理员仍禁止页面修改定义与禁用；保留最后一个正常平台管理员的保护。菜单展示和按钮隐藏不替代接口鉴权，撤权在提交后的新请求生效。

## 迁移与回滚

1. 新增增量脚本，同时同步 schema.sql 和最终 V1__baseline.sql；本地已执行增量迁移，保留原授权快照用于核对。
2. 预置菜单和细分功能，先生成迁移前后按角色和 DRAFT/APPLIED 统计的权限对照，校验不存在未知旧码。
3. 分阶段分别映射：user:read → user:query；user:manage → assign-role、enable、disable；role:read → role:query；role:manage → create、update、apply、enable、disable；permission:read → permission:query。以上均保留 management: 前缀。
4. 根据各阶段原有功能生成相应页面与祖先菜单；仅有 management:access 的角色映射到三个页面菜单但不授予任何查询功能，避免把准入迁移成数据读取权。无平台准入的旧角色所含功能先作为异常报告人工确认，不能静默扩大授权。
5. 同步发布 Gateway、用户服务和前端，切换完成后移除旧粗粒度权限与旧 status 接口。迁移期间暂停授权写入，避免新旧版本互相覆盖。
6. 迁移前备份本期授权关联及角色版本；回滚必须同时回退服务、关联快照和目录版本。不能把细分授权直接合并成旧 manage，否则会扩大权限；回滚后新增细分授权需单独记录并重放或人工处理。

## 验收清单

- 目录全选、页面半选、单项勾选、批量分配移除、搜索保留选择、禁用节点展示正确。
- 查询依赖、父菜单闭包、取消页面后清理功能、菜单单独授权的空权限页面行为明确。
- 保存草稿不改变生效授权；应用后菜单与功能同时切换；失败整笔回滚。
- 默认页面选择首个有权页面，普通用户无入口，直接输入无权页面地址也不能访问。
- 用户仅查询时不显示写按钮；伪造请求、旧 status 请求、未知管理接口均被 Gateway 拒绝。
- 多角色并集、目录禁用、功能禁用、角色禁用与恢复、越权授予及并发版本冲突均覆盖测试。
- 迁移前后逐角色逐阶段比较，原有用户不凭空增加读取或写入能力。


## Gateway 落地方式

- Path 谓词区分匿名注册、管理接口、用户普通 API 和运行服务 API；只有管理路由挂载 Permission。
- 全局登录过滤器负责认证、CSRF 和当前用户身份；管理路由过滤器从认证结果取 userId，传入目标请求方法和完整路径。
- Gateway 直接通过 UserClient 调用内部 POST /user-server/internal/users/permission-check，返回 ApiResult<Boolean>；不加载规则，不匹配功能权限，不为此次内部调用额外签名。
- 用户服务在 PermissionService 内匹配 sq_permission_route，再查询该用户的有效菜单功能授权；未知接口返回 false，异常由统一响应处理。
- 内部接口依赖部署网络隔离，仅允许可信调用方访问，Gateway 的公开路由不匹配 /internal/**。userId 由 Gateway 会话取得，不从浏览器请求参数复制。
- Feign 同步请求在 boundedElastic 上执行，三秒超时；只有成功且 data=true 才转发，拒绝、空响应与故障均不放行。
- 本地静态服务路由关闭 RouteRefreshListener 的自动刷新，避免 Feign 子上下文事件提前初始化认证链；数据库规则仍由用户服务每次读取。


## 缓存与紧凑布局

鉴权结果缓存在 Gateway 的 OpenFeign 原生 Spring Cache 中，用户服务只查询数据库并返回结果。默认有效期为 10 秒，可通过 agentsquad.auth.permission-cache-ttl 调整。Redis 键按共享授权版本、用户编号、请求方法和路径隔离，同时缓存允许和拒绝结果。授权变更事件仅在事务提交后增加共享版本，多个实例同时切换缓存命名空间；旧键自动过期，不扫描全库。事务内检查绕过缓存，防止读到旧授权。Redis 读写故障时回退数据库结果；版本失效写入失败会记录告警，旧缓存最多保留当前配置的短时有效期。手工维护目录或接口规则后应主动更新共享版本或等待该有效期。

角色授权双栏间距调整为 14px，树节点约 28px 高，收紧弹窗内边距和工具栏；功能编码保留在悬停提示中，不再逐项占用一行。


## 禁止悲观查询锁

项目生产代码、脚本、研究夹具和归档 SQL 均移除悲观查询锁。授权事务通过 sq_rbac_revision 读取版本并执行带预期版本的条件更新；并发失败返回冲突，整个事务回滚。角色和用户原有版本校验继续保留。新增 V6__authorization_revision.sql，同步最终 baseline 和 schema。执行 python3 tools/check_sql_concurrency.py 检查项目禁用语句。


Feign 缓存使用标准 @EnableCaching、UserClient.checkPermission 上的 @Cacheable 和 RedisCacheManager，不自建 Capability 或 InvocationHandler。PermissionCacheKeyGenerator 仅生成带授权版本的键，不执行查询或代理远程调用。用户服务仅保留事务提交后的共享版本失效通知。
