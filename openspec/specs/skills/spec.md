# Skill 自动发现与版本治理（Skills）

## Purpose

Skill 能力负责执行知识与流程资料的自动发现、按需读取、版本固定与授权；Skill 是执行知识/流程资料，与 Worker 不是一对一关系。（M06）

## Requirements

### Requirement: 自动发现与按需读取

执行者自动识别、按需读取和使用 Skill，无需逐次绑定。（R05）

#### Scenario: 自然语言选中
- **WHEN** 任务需要相关技能
- **THEN** 模型通过框架目录描述自动选择并读取实际固定文本，不要求用户逐次绑定或下拉选择

### Requirement: 版本固定与运行隔离

GoalTask/WorkerJob 创建上下文时固定 skillVersionIds 和包摘要，使用只读发布视图；Git HEAD 或同名新版本不能改变在途任务。

#### Scenario: 升级不扰动
- **WHEN** Skill 发布新版本
- **THEN** 旧固定版本保留直到无运行引用，在途任务内容稳定

#### Scenario: 停用与撤销
- **WHEN** Skill 停用或安全撤销
- **THEN** 停用阻止新任务选择；安全撤销递增授权版本并阻止在途新读取/调用，相关 Binding 进入可诊断阻塞

### Requirement: 不可信输入与权限

Skill 内容属于不可信输入；脚本只经工具 wrapper 与框架 Sandbox 执行；依赖缺失明确 CAPABILITY_UNAVAILABLE，不自动安装依赖或授权工具。

#### Scenario: 恶意 Skill 拒绝（A14）
- **WHEN** Skill 含恶意路径、注入或“忽略权限”等指令
- **THEN** 不能越权、不能自动安装、不能在 Java 宿主 shell 执行

#### Scenario: 依赖缺失
- **WHEN** skill manifest 声明的工具依赖与平台 ToolDefinition 不符
- **THEN** 明确 CAPABILITY_UNAVAILABLE，不自动安装或授权

### Requirement: 导入与授权隔离

规范化文本包导入（限制文件数、单文件与总量大小，拒绝绝对路径/.. /符号链接/重复路径/非文本）；Grant 保存用户授权，两用户同名技能不混用。

#### Scenario: 同名隔离（A14）
- **WHEN** 两用户拥有同名技能
- **THEN** 各自版本与 Grant 隔离，不混用
