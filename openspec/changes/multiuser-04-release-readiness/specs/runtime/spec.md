# Spec Delta: Runtime / R08

## MODIFIED Requirements

### Requirement: 最终表结构与数据库管理边界

开发及测试环境 MUST 以完整 `V1__baseline.sql` 保持可重建最终结构；本地开发库可直接修改表结构，修改后 MUST 同步回 baseline。生产环境 MUST 使用显式版本化受控迁移作业，业务服务 `spring.sql.init.mode=never`，启动不得自动执行 DDL。两个路径的最终表/列/索引 MUST 通过 schema-parity 自动校验。现有真实数据库必须先备份/验证再升级，生产数据库变更仅在用户批准后执行。

#### Scenario: 应用启动
- **WHEN** 应用连接已配置的数据库启动
- **THEN** 开发/测试环境幂等重放 baseline，仅补齐缺失表，不清理已有数据；production 仅验证 schema 版本，不执行 DDL 或历史迁移

#### Scenario: 开发与测试重建
- **WHEN** 隔离的空开发/测试 schema 初始化
- **THEN** 由完整 baseline 建立表结构，与受控生产迁移链的最终 schema 一致；可在现有本地库上幂等补齐新增表，但不得清理用户真实库

#### Scenario: 生产应用启动
- **WHEN** 应用使用 production profile 连接已有生产数据库
- **THEN** 仅验证 schema 版本与必需表，不执行 DDL/历史数据回填或危险隐式 baseline；结构不兼容时启动失败并给出安全诊断

## ADDED Requirements

### Requirement: 可重现发布与可诊断恢复

系统 MUST 在 CI、独立 staging 与部署操作手册中提供构建、迁移、备份、故障恢复和对外开放前的验收证据；未经跨进程 SDK 恢复实测不得宣传多业务实例运行。

#### Scenario: CI 不得掩盖跳过项
- **WHEN** 关键集成测试依赖的 MySQL、Docker 或环境变量缺失
- **THEN** CI 明确失败或标记 BLOCKED，不能以测试 skipped 却输出 BUILD SUCCESS 作为上线依据

#### Scenario: 备份恢复
- **WHEN** 从真实 staging 备份在隔离数据库恢复
- **THEN** 关键用户/消息/任务/Operation/SDK 存储一致，恢复耗时与数据点有真实记录
