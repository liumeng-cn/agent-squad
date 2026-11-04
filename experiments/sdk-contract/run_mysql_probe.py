"""在专用临时 MySQL 容器执行 SDK 实验，不读取业务配置或输出临时密码。"""
import os
import secrets
import subprocess
import time
import uuid
from pathlib import Path


def run():
    root = Path(__file__).resolve().parents[2]
    name = "squad-sdk-probe-" + uuid.uuid4().hex[:12]
    env = dict(os.environ)
    password = secrets.token_hex(24)
    env["MYSQL_ROOT_PASSWORD"] = password
    env["MYSQL_PWD"] = password
    started = False
    try:
        subprocess.run(["docker", "run", "--rm", "-d", "--name", name,
                        "-p", "127.0.0.1::3306", "-e", "MYSQL_ROOT_PASSWORD",
                        "-e", "MYSQL_DATABASE=squad_sdk_probe", "mysql:8.4"],
                       env=env, check=True, stdout=subprocess.DEVNULL)
        started = True
        for _ in range(90):
            ready = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD", name,
                                    "mysql", "-uroot", "-Nse", "SELECT 1"],
                                   env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("临时MySQL启动超时")
        port = subprocess.check_output(["docker", "port", name, "3306/tcp"], text=True).strip().rsplit(":", 1)[1]
        env["SQUAD_PROBE_MYSQL_URL"] = "jdbc:mysql://127.0.0.1:" + port + "/squad_sdk_probe?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
        env["SQUAD_PROBE_MYSQL_PASSWORD"] = password
        env["JAVA_HOME"] = "/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home"
        return subprocess.run(["mvn", "-o", "-Daether.syncContext.named.factory=noop", "-f",
                               str(root / "experiments/sdk-contract/pom.xml"),
                               "-Dtest=SdkMySqlContractTest,RuntimeEventReceiptMySqlTest", "test", "-q"], cwd=root, env=env).returncode
    finally:
        if started:
            subprocess.run(["docker", "stop", "-t", "2", name], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, check=False)


if __name__ == "__main__":
    raise SystemExit(run())
