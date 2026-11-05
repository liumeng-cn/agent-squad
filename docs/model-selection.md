# 会话模型选择

输入框旁点击模型名，选择已配置模型并调整其实际支持的思考模式，立即影响下一条消息。正在生成的回答不换模型；显式重新回答沿用原回答选择。首发创建会话后保留选择，同一浏览器按用户和会话保存偏好；模型凭据只在服务端。

## 当前实现

- 统一目录复用 `agentsquad.worker.model-profiles`；Master回复与Worker定义引用同一批Profile。
- `GET /api/v1/models` 返回模型名称、能力标签和可选模式，不返回地址、密钥或价格内部字段。
- 消息提交增加 `modelId` / `reasoningMode`。受理事务将模型引用、非敏感配置指纹与模式写入 `sq_response_execution.model_selection_json`；选择同时计入幂等摘要。SDK路由与预算都使用该选择，不能只改变页面名称。
- Spark支持快速OFF、自动AUTO、深入ON；实际分别发送thinking.type的disabled/auto/enabled。OpenAI Effort适配仅开放声明的LOW/MEDIUM/HIGH；无此能力的模型不显示强度滑块。不会展示或保存用户可见的模型内部推理。
- 配置指纹排除密钥，允许凭据轮换；模型、地址、定价或额度改变时阻止旧执行隐式换模，不自动回退另一个模型。
- 本地配置直接位于既有 `server/agent-squad-server/src/main/resources/application-local.yml`，注册新Profile后重启加载；已注册模型在页面切换不需重启。只有启用且必要配置齐全的模型进入目录。

新增模型时配置provider、model-name、base-url、api-key、pricing-version、输入输出计价单位及Token上限；可选display-name、enabled、capabilities、reasoning-adapter、reasoning-modes、default-reasoning-mode。能力枚举REASONING/TEXT_GENERATION/CODING/TOOL_USE/VISION是管理员声明，不是质量评分。

当前真实可用且已验证为Spark-X2.6。同一密钥请求Spark-X2.6-4b得到403，未将其启用或修改供应商授权。无需为了演示多模型制造两个实际相同模型的假选项。

## 后续Worker策略

Worker目前仍按已发布Definition的modelProfileKey选择，不会自动按“推理强/生成强”改模型。后续可基于共享能力目录为计划节点声明所需能力、价格/延迟限制及用户允许范围，在创建WorkerJob时固定模型决定与原因；失败恢复沿用该决定，显式重规划才重新选择。自动选模须以真实测评或明确配置排序为依据。此自动策略及用户自建供应商管理页面不属于本轮交付。

## 本地运行与结构

本次V8新增可空选择列，旧回复NULL保留原路径；baseline/schema已同步。业务结构门禁期望版本8；完整历史升级链仍有V1至V4文件缺失，不能把专项通过当作整条链已通过。

JAR不包含含凭据的application-local.yml。本地以源配置启动：

```bash
java -jar server/agent-squad-server/target/agent-squad-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.config.additional-location=file:./server/agent-squad-server/src/main/resources/application-local.yml
```

Java服务运行JDK27。Maven开发运行与IDE仍可按原有方式加载local配置；不要将本地凭据提交仓库。
