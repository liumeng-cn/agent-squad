# Spec Delta

## MODIFIED Requirements

### Requirement: 自动发现与按需读取

执行者 MUST 自动识别、按需读取和使用 Skill，无需逐次绑定；普通消息回复和目标任务都适用，不以创建任务作为发现技能的前提。（R05）

#### Scenario: 自然语言选中
- **WHEN** 回复或任务需要相关技能
- **THEN** 模型通过框架目录描述自动选择并读取实际固定文本，不要求用户逐次绑定或下拉选择

### Requirement: 版本固定与运行隔离

回复执行、GoalTask/WorkerJob 创建上下文时 MUST 固定 skillVersionIds 和包摘要，使用只读发布视图；Git HEAD 或同名新版本不能改变在途执行。

#### Scenario: 升级不扰动
- **WHEN** Skill 发布新版本
- **THEN** 旧固定版本保留直到无运行引用，在途普通回复与任务内容稳定

#### Scenario: 停用与撤销
- **WHEN** Skill 停用或安全撤销
- **THEN** 停用阻止新执行选择；安全撤销递增授权版本并阻止在途新读取/调用，相关 Binding 进入可诊断阻塞
