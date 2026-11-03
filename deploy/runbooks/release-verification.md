# 发布验证与回退

当前状态：production not yet approved。正式运行环境以 JDK 27 为已验证基线；Java 21 运行兼容性尚未验证，本机未安装 JDK 21。

## 隔离验证

1. 使用新建、专属测试 MySQL 8.4，显式传入测试变量，不加载 `.env.local`。
2. 执行 Maven `clean verify`、前端 `npm ci/test/build`、`tools/check_design.py`、`tools/check_runtime_safety.py`。
3. 执行 `tools/audit_test_reports.py`；任何 skipped 都阻断发布，不能以 Maven 成功代替专项验收。
4. `SchemaParityTest` 在两个隔离 schema 比较 baseline 与 V1→V5 的表、字段、默认值、注释和索引；这不代表真实生产迁移已执行。
5. CI 工作流需实际运行通过；仅存在 YAML 不能证明远程 CI 已通过。

## 迁移与备份

生产由独立迁移账号执行已审查 SQL，应用账号只授予运行所需 DML/SELECT。迁移前确认备份可解密、可还原、覆盖 MySQL 和 SDK workspace/journal，并保留密钥版本及会话撤销事实。禁止把 dump、密钥或会话令牌提交仓库。

在隔离恢复库实际还原后，核对完整表集合、用户/消息/任务数量、Operation UNKNOWN 账本、SDK journal 与产物内容；记录备份时间点、恢复起止时间及真实 RPO/RTO。未经演练保持 NOT_RUN。

V5 增加最终交付权，既有在途请求缺交付位时将拒绝发布终稿。升级前必须停止新受理、核对在途执行并制定受控回填方案；不能从可能已重复的历史消息自动猜测权威交付者。旧失败回复的显式重试和旧已完成 Task 的答案修订也需要交付位，未经核对回填将明确返回冲突；排空在途不等于历史操作已兼容。

回滚优先恢复旧应用版本，保留新增表及数据。不得自动 DROP 表/列或用旧备份覆盖当前数据库。出现结构不兼容时停流并由用户决定恢复策略。

## 内部身份密钥轮换

先在 Server 配置旧 `previous-key-id/previous-hmac-key` 与新 `key-id/hmac-key`，确认接受两代断言；再切换 Gateway 新签名密钥。等待最长断言有效期及部署时钟余量后移除旧密钥。密钥从 Secret 注入，不写入镜像、命令日志或 Git。轮换失败时先恢复 Gateway 旧签名，保持 Server 双密钥验证直至全部实例核对完成。

Gateway Secure Cookie 需要实际 HTTPS 同源入口；SSE 反向代理必须关闭缓冲。Server、DB 和管理端口只允许私网访问。单业务执行实例是当前部署上限，跨进程 SDK 接管未通过前不能扩容业务副本。

## 验证输出与清理

本地测试结束后可删除各 Java 模块 `target/` 与前端 `dist/`；需要复核时先将测试报告复制到项目外的临时验证目录。`node_modules` 是开发依赖，`.agentscope` 可能包含恢复所需 journal；它们不按普通构建垃圾自动清理。删除历史 OpenSpec 不改变现行 `specs/`、未完成 `changes/` 或 Git 历史。
