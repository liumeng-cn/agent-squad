# 保留的 Langfuse Docker 环境

Langfuse 容器与数据卷按用户要求保留，地址为 http://127.0.0.1:13000 。Squad v2 当前观测目标已切回此实例。

现有独立 Compose 项目 `squad-langfuse` 的管理命令：

```bash
python3 deploy/langfuse/local.py status
python3 deploy/langfuse/local.py up
python3 deploy/langfuse/local.py stop
```

此脚本不再生成或覆盖 v2 的观测配置。官方部署样例仍保留，原本地源码目录已删除。初始化凭据保存在被 Git 忽略的 `.local/langfuse/.env`，不影响 Studio。

新建实例预置账号：`admin@local.agentsquad.test` / `Langfuse-local-2026!`。已有数据卷不重置账号或数据。

当前接入逻辑、配置和验证以本项目 v2 的 `application-local.yml` 为准：OTLP 地址为 `http://127.0.0.1:13000/api/public/otel/v1/traces`，认证使用 `.local/langfuse/.env` 中的项目公钥和密钥。管理端入口通过 `VITE_OBSERVATION_URL` 打开 Langfuse。
