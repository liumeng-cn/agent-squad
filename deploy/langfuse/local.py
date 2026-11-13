#!/usr/bin/env python3
"""准备并管理官方 Langfuse 本地样例；只操作独立 Compose 项目，不清空卷。"""
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / ".local/langfuse"
STATE.mkdir(parents=True, exist_ok=True)
ENV = STATE / ".env"
if not ENV.exists():
    password = secrets.token_hex(24)
    storage_password = secrets.token_hex(24)
    values = {
        "LANGFUSE_IMAGE_TAG": "4.79.0", "NEXTAUTH_URL": "http://127.0.0.1:13000",
        "POSTGRES_PASSWORD": password, "DATABASE_URL": f"postgresql://postgres:{password}@postgres:5432/postgres",
        "SALT": secrets.token_hex(32), "ENCRYPTION_KEY": secrets.token_hex(32),
        "NEXTAUTH_SECRET": secrets.token_hex(32), "CLICKHOUSE_PASSWORD": secrets.token_hex(24),
        "REDIS_AUTH": secrets.token_hex(24), "MINIO_ROOT_PASSWORD": storage_password,
        "LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY": storage_password,
        "LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY": storage_password,
        "LANGFUSE_S3_BATCH_EXPORT_SECRET_ACCESS_KEY": storage_password,
        "LANGFUSE_S3_MEDIA_UPLOAD_ENDPOINT": "http://127.0.0.1:19090",
        "LANGFUSE_INIT_ORG_ID": "squad-local-org", "LANGFUSE_INIT_ORG_NAME": "Agent Squad",
        "LANGFUSE_INIT_PROJECT_ID": "squad-v2", "LANGFUSE_INIT_PROJECT_NAME": "Squad v2",
        "LANGFUSE_INIT_PROJECT_PUBLIC_KEY": "pk-lf-" + secrets.token_hex(16),
        "LANGFUSE_INIT_PROJECT_SECRET_KEY": "sk-lf-" + secrets.token_hex(24),
        "LANGFUSE_INIT_USER_EMAIL": "admin@local.agentsquad.test",
        "LANGFUSE_INIT_USER_NAME": "Squad Admin",
        "LANGFUSE_INIT_USER_PASSWORD": "Langfuse-local-2026!",
    }
    ENV.write_text("".join(f"{key}={value}\n" for key, value in values.items()))
    ENV.chmod(0o600)
values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if "=" in line)
command = sys.argv[1] if len(sys.argv) > 1 else "status"
commands = {"up": ["up", "-d", "langfuse-web", "langfuse-worker"],
            "stop": ["stop"], "status": ["ps"], "logs": ["logs", "--tail", "60", "langfuse-web", "langfuse-worker"],
            "prepare": []}
if command not in commands:
    raise SystemExit("用法: python3 deploy/langfuse/local.py [prepare|up|stop|status|logs]")
if commands[command]:
    raise SystemExit(subprocess.call(["docker", "compose", "--project-name", "squad-langfuse", "--env-file", str(ENV),
                     "-f", str(ROOT / "deploy/langfuse/docker-compose.official.yml"),
                     "-f", str(ROOT / "deploy/langfuse/compose.local.yml"), *commands[command]], cwd=ROOT))
print("本地配置已准备，接入密钥仅保存在忽略的本地文件中。")
