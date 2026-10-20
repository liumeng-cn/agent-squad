# M06 Skill 自动发现与版本治理

Skill 是执行知识/流程资料；Worker 是能力角色，二者不是一对一关系。模型通过框架目录描述自动选择并按需读取 Skill，不要求用户逐次绑定。

## 接口和包结构

GET /api/v1/skills 返回当前用户可见版本；GET /skills/{skillVersionId}返回SkillDetail（文本文件及个人Grant版本），供详情预览/状态更新；POST /skills 导入规范化文本包 SkillImport；PUT /skills/{skillVersionId}/grant 启用/停用个人可见性。Worker 固定目录由平台挂载，实际发现/读取用 AgentScope Skill 机制。

SkillImport 包含 files[{path,content}]，SKILL.md 必需且含 name/description frontmatter；允许 references/scripts 等 UTF-8 文本。最多 64 文件、单文件 256KiB、总量 1MiB；拒绝绝对路径、..、符号链接、重复路径、保留运行时命名空间和非文本。ZIP 可在后续上传适配中安全规范化，不让原始压缩包直接落入 Workspace。

SkillVersion 保存 owner、name、version/contentHash、规范化文本对象、依赖声明、status；版本内容不可变。同 owner/name/hash 导入去重。Grant 保存用户授权和 version，不把私有包置于所有用户共享目录。

## 运行固定与权限

GoalTask/WorkerJob 创建上下文时固定 skillVersionIds 和包摘要；使用只读 SnapshotSkillRepository/Workspace 发布视图，不能让 Git HEAD 或同名新版本改变在途任务。skill manifest 声明的工具依赖与平台实际 ToolDefinition 比对，缺失明确 CAPABILITY_UNAVAILABLE；不能自动安装依赖或授权工具。

Skill 内容属于不可信输入；“忽略权限”“上传凭据”等指令不获得执行权。脚本由平台工具 wrapper 和框架 Sandbox 执行，不能在 Java 宿主 shell 执行。禁用自学习自动发布/自动改写公共 Skill；以后可单独设计审核。

停用阻止新任务选择；安全撤销递增授权版本，阻止在途新读取/调用，并使相关 Binding 进入可诊断阻塞。仅普通新版本发布不使旧固定版本丢失，保留直到没有运行引用。

验收：自然语言选中相关 Skill 并读取实际固定文本；未知依赖拒绝；两用户同名技能不混用；升级过程中旧任务内容稳定；恶意路径和 Skill 注入不能绕过工具权限。
