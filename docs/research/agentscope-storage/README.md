# AgentScope Java 原生存储源码研究

研究日期：2026-11-17。对象为 AgentScope Java **2.1.0-BETA1**，不使用主分支或本机 v2.0.4 的实现代替。本文仅研究机制与当前接入，不提出产品存储方案。

## 版本与证据

- Agent Squad：`feat/20261111_squad_v2`，Commit `7dc3c5fecb42f4d3240605581607bec8ceb83dee`。
- 【源码确认】`server/pom.xml:31` 固定版本；Maven 实际依赖树确认 `agentscope-core`、`agentscope-harness`、`agentscope-extensions-agui`、`agentscope-extensions-model-openai` 均为 `2.1.0-BETA1`。
- 官方 [v2.1.0-BETA1](https://github.com/agentscope-ai/agentscope-java/tree/5451c73abe47053340683a1cc6d67b290b056648) 的 annotated Tag 对象为 `887c88196018ff5ad09ee0e850c74013bd939e3f`，指向 Commit `5451c73abe47053340683a1cc6d67b290b056648`，经 GitHub Git API 核实。
- 下载目标 Tag 完整源码至 `/tmp/agentscope-beta1-official`，本地目标 Source Jar 解包至 `/tmp/agentscope-storage-2.1.0-BETA1`。首次直接模块比较642个Java文件，随后从用户提供仓库导出Tag并覆盖嵌套扩展路径复核：全部803个Source Jar Java文件均有对应、逐字节差异为零。
- [source-manifest.json](source-manifest.json) 保存已取得 Source Jar 的 SHA-256。临时源码目录可能被系统清理，复核时应重新获取同一 Tag 或这些制品。

实际依赖核对命令（本次成功，不代表编译或测试）：

```sh
mvn -o -Daether.syncContext.named.factory=noop -f server/pom.xml -pl v2-server -am dependency:tree -Dincludes=io.agentscope
```

首次默认锁配置因本地 Maven 锁文件无写权限失败；上面的只读离线解析避免写锁，不修改项目配置或依赖。

文档内 `agentscope-core/io/...` 等路径相对 Source Jar 解包根；对应官方仓库是 `agentscope-core/src/main/java/io/...`。扩展模块在官方仓库 `agentscope-extensions/<artifact>/src/main/java/` 下。所有行号以本次固定版本为准。标记含义：**源码确认**是可定位实现，**文档确认**仅表示已有文档记录，**源码推导**是逻辑推论，**待验证**不能作为已经通过的能力。

## 文档导航

| 文档 | 内容 |
|---|---|
| [storage-architecture.md](storage-architecture.md) | 组件关系、默认 Builder 装配、职责边界 |
| [storage-data-structures.md](storage-data-structures.md) | Session、Commit、Head、Inbox、Blob 格式及路径 |
| [storage-lifecycle.md](storage-lifecycle.md) | 新建、输入、宕机、恢复、并发调用链及时序图 |
| [storage-extension.md](storage-extension.md) | State、Workspace、Snapshot、JDBC、Redis、扩展接口 |
| [squad-v2-storage-assessment.md](squad-v2-storage-assessment.md) | 当前 V2 接入矩阵与历史记录差异 |
| [storage-version-compatibility.md](storage-version-compatibility.md) | 格式、并发版本、运行语义与迁移边界 |
| [runtime-samples.json](runtime-samples.json) | 本次无模型实验实际序列化结果 |

## 主要发现

【源码确认】原生会话事实以 SessionLog 为核心；JournalSessionLog 把不可变提交对象与 Head CAS 组合成已提交前缀。Inbox 是独立序号与 writer 的输入子日志。独立 AgentStateStore 不等于 Journal 的 state/checkpoint，Workspace 普通文件也不是 Journal 对象。

【源码确认】本地 Journal 文件有 8 字节大端版本前缀，后接 JSON，不能把 `.json` 当作纯 JSON 解析。远程模式通过 StoreSessionStorage 将日志对象适配到 BaseStore；JDBC、State、Snapshot 使用各自协议与实现，并非一个 JDBC Store 自动替换全部数据族。

【源码确认】当前 V2 使用本地默认 Journal，HTTP 已接入 submit/steer；列表与标题管理仍存在 fixture。旧研究关于 HTTP 不写 Inbox 的结论不适用于当前代码。详见接入矩阵。

## 问题逐项核对

原请求的编号问题已分组逐项回答，不能只凭专题文件数量判断完成度：

| 原请求范围 | 逐项证据 |
|---|---|
| Session 15问、Inbox 8问、场景A–E全部35个子问 | [Session逐题核对](storage-session-question-audit.md) |
| AgentState 9问、Snapshot 10问、Workspace 14问、JDBC/Redis 10问 | [扩展逐题核对](storage-extension-question-audit.md) |
| V2接入11问、默认装配各项、升级兼容10问 | [接入与兼容逐项核对](storage-integration-compatibility-audit.md) |
| 全量实现盘点与不同后端的数据协议 | [后端实现清单](storage-backend-inventory.md) |
| 物理位置、namespace、路径、字段、读写及并发 | [数据结构](storage-data-structures.md)、[扩展](storage-extension.md)及上述逐题附件 |

## 本次实际验证

[运行证据](validation-evidence.md) 记录复核方法、测试边界和实验源代码。关键结果：

- 从用户提供的 `/Users/liumeng/work/pWork/agentscope-java` 以git archive导出准确Tag至 `/tmp/agentscope-beta1-local-git`；保留原仓库v2.0.4检出状态，不修改远程关联。
- 官方目标Tag测试 **215项通过，0失败、0跳过**；包括Session、Inbox、State、JDBC/H2、Workspace与Filesystem隔离及CAS。完整类名见 [官方测试结果](official-test-results.json)。使用未修改的官方测试源码与缓存目标SDK Jar，不是全仓Maven构建。
- 三个独立JVM验证有效租约拒绝竞争、SIGKILL后已提交事件可读、到期后epoch提升接管及旧writer拒写，见 [进程实验](process-probe-results.json)。仅同机POSIX存储，不是跨主机故障转移。
- 本地SandboxSnapshot写入合成tar、descriptor序列化/反序列化、restore字节一致，见 [快照样例](snapshot-probe-results.json)。
- 默认codec忽略State未知属性、拒绝未知ContentBlock类型、拒绝schemaVersion=2事件，见 [兼容边界实验](compatibility-probe-results.json)。这不是跨版本混写通过。
- [runtime-samples.json](runtime-samples.json) 保留真实AgentState与Journal数据，固定合成身份，无用户上下文或模型内部推理。

## 结论的验证边界

本次源码研究逐题解释目标版本的实现与能力边界；不把源码存在或同机测试说成所有部署环境已经验收。未运行真实Redis、MySQL/PostgreSQL服务、对象存储服务、跨主机共享盘、断电损坏、滚动升级和任意未来SDK版本的混写。JDBC/H2结果不能替代其他数据库方言与运维配置的集成验收。未来版本未指定，不能给出通用跨版本兼容承诺。

当前HTTP与真实模型存储全链路未重跑；现有代码接入结论为源码确认，历史实验另标日期。模型短问成功不计作存储恢复验证。

本次仅新增研究文档及JSON证据，没有修改业务Java、Maven、配置、DDL或既有文档，没有切换项目分支或提交。原有两处本地修改保留。
