# Web 工作台（Web Console）

## Purpose

Web 工作台能力负责一个目标的统一正文、真实增量、快照与回放，以及追问/审批、连接、记忆、Skill 与管理员能力的页面入口；页面属于一期交付，不生成工具操作页面。视觉基线见 [视觉评审稿](assets/README.md)，静态稿见 assets/ 下 PNG/SVG。（R10）

## Requirements

### Requirement: 信息架构与权限

页面按登录、工作台、任务详情、技能、连接设置、记忆设置、Worker 管理七个路由组织；角色只控制导航与交互体验，服务端授权决定最终访问权。路由模板：`/login`、`/app/conversations/:conversationId?`、`/app/tasks/:taskId`、`/app/skills`、`/app/settings/connections`、`/app/settings/memory`、`/app/admin/workers`。

#### Scenario: 未登录转登录（B01）
- **WHEN** 进入页面先取 currentUser，未登录或登录失效
- **THEN** 转登录并返回原地址；旧内容和订阅清除，不反复创建新会话

#### Scenario: 管理员不默认越权（B14）
- **WHEN** 普通用户直达 Worker 管理页或越权调用其接口
- **THEN** 页面无操作入口，直接请求仍被后端拒绝；管理员也不隐式获得读取其他用户内容权限

### Requirement: 提交目标与恢复显示

提交固定幂等键、附件就绪后才引用；202 只表示平台持久接受；恢复显示只读快照与增量，刷新/重连不触发新执行。

#### Scenario: 发送超时与重复点击（B02）
- **WHEN** 发送超时或重复点击
- **THEN** 固定 Idempotency-Key，超时保留原 key/输入补查或重送，不生成第二个目标

#### Scenario: 附件未就绪（B03）
- **WHEN** 附件上传失败或未到 READY
- **THEN** 不把无效 artifactId 加入 TaskCreate，给出可理解重试入口

#### Scenario: 刷新/切页/断线（B04）
- **WHEN** 刷新、切换会话或断线重连
- **THEN** 恢复显示只读 snapshot + events，不 submit/resume，无双连接串消息

### Requirement: 状态与可用动作

页面按 QUEUED/RUNNING/WAITING_WORKERS/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING 与终态给出初始动作；所有控制请求携带 expectedVersion，409 后刷新快照。

#### Scenario: 状态不混同（B05）
- **WHEN** 任务处于等待输入/审批或部分成功
- **THEN** 每种状态有正确动作，不显示为完成，不用假百分比或定时文本模拟进度

#### Scenario: 取消与在途操作（B07）
- **WHEN** 取消时仍有在途操作待核对
- **THEN** 页面区分停止中与已取消，不点击后立即标为已取消

### Requirement: 追问、审批与核对

INPUT 仅支持受限 JSON Schema 表单；审批卡展示脱敏影响并要求 requestHash 匹配；核对抽屉按服务端计算的 canReconcile 展示。

#### Scenario: 旧审批与重复回复（B06）
- **WHEN** 审批已失效、版本冲突或重复回复
- **THEN** 刷新事实；重复回复用原幂等键；失败或失效保持真实状态，不显示未发生的成功

#### Scenario: 核对权限（B09）
- **WHEN** 打开任务详情与核对入口
- **THEN** 无权限无操作入口，直接请求仍被后端拒绝；不提供“忽略未知并继续”

### Requirement: Skill、连接与记忆页面

Skill 导入先预览再服务端规范化验证，启用只加入个人可用目录；连接不返回原 Secret；记忆由用户主动创建/纠正/删除并有版本冲突提示。

#### Scenario: Skill 导入与启用（B11）
- **WHEN** 导入或启用/停用 Skill
- **THEN** 可自动发现、不要求会话绑定；错误定位到具体文件，不笼统报异常

#### Scenario: 连接轮换与编辑（B12）
- **WHEN** 编辑或轮换连接凭据
- **THEN** Secret 不回显，留空不覆盖，错误不含凭据；输入新凭据才触发轮换

#### Scenario: 记忆纠正与删除冲突（B13）
- **WHEN** 纠正或删除记忆遇版本冲突或在途任务引用
- **THEN** 提示版本冲突并刷新，不静默覆盖新值；在途影响说明清楚

### Requirement: SSE 与技术边界

同源 Cookie、原生 EventSource；首次 after 参数取 snapshot.cursor，重连合法 Last-Event-ID 优先；事件名统一 message、按 seq 去重、按 revision 替换正文；受限 Markdown 渲染。

#### Scenario: 半答案替换与多轮折叠（B08）
- **WHEN** 新一轮 Master 或半答案重生成
- **THEN** 只显示一个用户目标，answer.replaced 替换而不拼接，内部 Turn 不显示为新用户消息

#### Scenario: 键盘/窄屏/恶意 Markdown（B15）
- **WHEN** 键盘操作、窄屏或 Markdown 含恶意内容
- **THEN** 焦点可见、主要动作不隐藏、无脚本执行；颜色不作为唯一状态提示

#### Scenario: 下载与鉴权（B10）
- **WHEN** 下载不存在或越权产物
- **THEN** 显示明确错误，无内部路径泄露，后端重验授权
