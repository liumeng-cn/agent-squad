# 实现前交叉评审记录

2026-10-20，v0.2.1。这里记录实际发现及修订，不把“文档齐全”当作行为验证。

| 编号 | 发现 | 处置与状态 |
| --- | --- | --- |
| F01 | 页面只有技术选型，没有完整路由、交互与错误态 | 已补Web设计、7路由、B01–B15、API映射及纵向交付；视觉稿与浏览器实现待D0/后续切片 |
| F02 | 首次SSE只有Last-Event-ID，原生EventSource无法设置该头 | 已增after参数、Header优先级、断线/失效处理，schema检查覆盖；实际浏览器验证待做 |
| F03 | APPROVAL无法展示操作影响，Skill页无法读取文件与Grant版本 | 已补ApprovalContext、OperationDetail、SkillDetail与查询接口；无详情审批作为反例拒绝 |
| F04 | 任务命令受理后无法单独查回执 | 已增授权getCommandReceipt，UI不能把202当已执行 |
| F05 | 平台审批和原生ASK可能各问一次或互相绕过 | 已规定单一Operation/Interaction决定和绑定；Hook接入点、挂起/重启需D0/D2验证 |
| F06 | 原生resume无应用幂等键，只查Run可能遗漏已排队的resume | 已明确读SDK inbox与Turn.started，无法辨明则阻塞；未运行故障实验 |
| F07 | 框架日志key只有255字符，直接用长业务名字会超限 | 已固定短ID编码，静态路径样例最长252/255；SDK渲染及实际MySQL验证待做 |
| F08 | 只记录依赖标签，未证实仓库POM可获取 | 本轮4个核心POM GET 200；仅证明元数据存在，JAR/传递依赖/编译仍待D0 |
| F09 | 把生产验证都当成编码前置会导致只写文档 | 已区分开工评审、D0兼容探针、逐切片验收和D6生产验证 |
| F10 | Account需返回roles但业务表缺少角色来源 | 已补roles_json，自服务端维护；角色变化递增authVersion，前端导航不作为授权 |

入口：[开发准备](predevelopment.md)、[SDK适配](sdk-adapter-map.md)、[页面设计](web-console.md)。
