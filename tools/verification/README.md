# 隔离验证入口

从仓库根目录执行。这里的脚本只服务测试，不是正常开发或部署配置。

## 后端业务验证

```sh
python3 tools/verification/run_mysql_probe.py --server-tests PlanJoinPolicyTest,WorkerDependencyPolicyTest,WorkerDependencyFailureIntegrationTest,PlanPublishIntegrationTest,MultiWorkerExecutionIntegrationTest,GoalTaskNativeCreationIntegrationTest,WorkerGuardIntegrationTest
```

脚本创建独占临时 MySQL 容器，结束后回收；不会读取业务连接密码或清理开发库。`--with-redis` 可同时创建独占 Redis，运行相关测试。

不带 `--server-tests` 时复核归档的 SDK 数据库契约，源码位置为 `tools/research-archive/sdk-contract/`。

## 浏览器验证

`chat-browser/` 是本地隔离夹具，使用合成身份，只能验证页面与测试后端，不证明正常网关登录。操作步骤见该目录 README。不得将其中的身份模拟配置接入开发启动、构建或部署流程。
