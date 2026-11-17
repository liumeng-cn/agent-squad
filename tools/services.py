#!/usr/bin/env python3
"""本地服务启停；不清库、不覆盖本地配置、不终止未知进程。"""
import argparse
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / os.environ.get('SQUAD_STATE_DIR', '.local/auth')
SERVICES = [('user-server', int(os.environ.get('SQUAD_USER_PORT', '18083')), 'user/user-server'), ('v2-server', int(os.environ.get('SQUAD_V2_PORT', '18082')), 'v2-server'), ('gateway', int(os.environ.get('SQUAD_GATEWAY_PORT', '18081')), 'gateway'), ('web', int(os.environ.get('SQUAD_WEB_PORT', '5173')), None)]

def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)

def config():
    STATE.mkdir(parents=True, exist_ok=True)
    path = STATE / 'config.json'
    if not path.exists():
        path.write_text(json.dumps({'pgUser': 'squad', 'pgPassword': 'squad', 'redisPassword': ''}, indent=2))
        path.chmod(0o600)
    cfg = json.loads(path.read_text())
    for key in ['identityKey']:
        if key not in cfg:
            cfg[key] = secrets.token_hex(32)
    path.write_text(json.dumps(cfg, indent=2))
    path.chmod(0o600)
    private = STATE / 'jwt-private.pem'
    public = STATE / 'jwt-public.pem'
    if not private.exists():
        run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        private.chmod(0o600)
    if not public.exists():
        run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    env = dict(os.environ)
    env.update(SQUAD_PG_USER=cfg['pgUser'], SQUAD_PG_PASSWORD=cfg['pgPassword'], SQUAD_REDIS_PASSWORD=cfg.get('redisPassword', ''), SQUAD_IDENTITY_KEY=cfg['identityKey'], SQUAD_JWT_PRIVATE_KEY=str(private), SQUAD_JWT_PUBLIC_KEY=str(public))
    env['SQUAD_USER_URL'] = f"http://127.0.0.1:{SERVICES[0][1]}"
    env['SQUAD_V2_URL'] = f"http://127.0.0.1:{SERVICES[1][1]}"
    env['SQUAD_GATEWAY_URL'] = f"http://127.0.0.1:{SERVICES[2][1]}"
    return env

def listening(port):
    with socket.socket() as sock:
        return sock.connect_ex(('127.0.0.1', port)) == 0

def dependencies():
    for name, port in [('PostgreSQL', 15432), ('Redis', 16379)]:
        if not listening(port):
            raise RuntimeError(f'{name} 未就绪（127.0.0.1:{port}），请先启动现有依赖')

def owned(name):
    path = STATE / f'{name}.pid'
    if not path.exists(): return None
    pid = int(path.read_text())
    try:
        cmd = subprocess.check_output(['ps', '-p', str(pid), '-o', 'stat=,command='], text=True).strip()
    except subprocess.CalledProcessError:
        path.unlink(missing_ok=True)
        return None
    if not cmd or cmd.split()[0].startswith('Z') or '<defunct>' in cmd:
        path.unlink(missing_ok=True)
        return None
    module = next(item[2] for item in SERVICES if item[0] == name)
    marker = str(ROOT / 'web/workbench/node_modules/vite/bin/vite.js') if name == 'web' else str(ROOT / 'server' / module / 'target' / f'{name}-0.1.0-SNAPSHOT.jar')
    if marker not in cmd:
        raise RuntimeError(f'{name} PID 已被其他进程使用，不执行停止')
    return pid

def stop():
    for name, _, _ in reversed(SERVICES):
        pid = owned(name)
        if pid:
            os.killpg(pid, signal.SIGTERM)
            for _ in range(100):
                state = subprocess.run(['ps', '-p', str(pid), '-o', 'stat='], capture_output=True, text=True)
                if state.returncode != 0 or not state.stdout.strip() or state.stdout.strip().startswith('Z'):
                    (STATE / f'{name}.pid').unlink(missing_ok=True)
                    break
                time.sleep(.1)
            else: raise RuntimeError(f'{name} 未退出，请查看日志；未强制终止')
            print(f'已停止 {name}')

def start(skip_build):
    env = config()
    dependencies()
    for name, port, _ in SERVICES:
        if listening(port) and not owned(name):
            raise RuntimeError(f'端口 {port} 已由未知进程占用，未启动 {name}')
    if not skip_build:
        run(['mvn', '-f', 'server/pom.xml', '-B', '-ntp', '-Daether.syncContext.named.factory=noop', '-Dmaven.test.skip=true', 'package'], env=env)
    started = []
    try:
        for name, port, module in SERVICES:
            if owned(name):
                print(f'{name} 已启动'); continue
            if module:
                jar = ROOT / 'server' / module / 'target' / f'{name}-0.1.0-SNAPSHOT.jar'
                args = ['java', '-jar', str(jar)]
                cwd = ROOT
            else:
                if not (ROOT / 'web/workbench/node_modules').exists():
                    run(['npm', '--prefix', 'web/workbench', 'ci'])
                args = ['node', str(ROOT / 'web/workbench/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--strictPort', '--port', str(port)]
                cwd = ROOT / 'web/workbench'
            with open(STATE / f'{name}.log', 'ab') as log:
                proc = subprocess.Popen(args, cwd=cwd, env=env, stdout=log, stderr=log, start_new_session=True)
            (STATE / f'{name}.pid').write_text(str(proc.pid)); started.append((name, proc))
            url = f'http://127.0.0.1:{port}/' + ('' if name == 'web' else 'actuator/health')
            for _ in range(120):
                if proc.poll() is not None: raise RuntimeError(f'{name} 启动失败，日志：{STATE / (name + ".log")}')
                try:
                    with urllib.request.urlopen(url, timeout=1) as response:
                        if response.status == 200: break
                except Exception: time.sleep(.5)
            else: raise RuntimeError(f'{name} 健康检查超时，日志：{STATE / (name + ".log")}')
            print(f'{name} 就绪：{url}')
    except BaseException:
        for name, proc in reversed(started):
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                try: proc.wait(timeout=15)
                except subprocess.TimeoutExpired: continue
            (STATE / f'{name}.pid').unlink(missing_ok=True)
        raise
    print(f'访问 http://127.0.0.1:{SERVICES[3][1]}/v2/login；测试验证码 8888')

def init_db():
    env = config(); dependencies()
    # 只创建用户模块schema/table，不删除或重建现有对象。
    sql = ROOT / 'server/user/user-server/src/main/resources/db/schema.sql'
    with sql.open('rb') as data:
        run(['docker', 'exec', '-i', 'squad-postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', env['SQUAD_PG_USER'], '-d', 'agent_squad'], stdin=data)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'restart', 'status', 'init-db'])
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    try:
        if args.action in ['stop', 'restart']: stop()
        if args.action in ['start', 'restart']: start(args.skip_build)
        if args.action == 'init-db': init_db()
        if args.action == 'status':
            for name, port, _ in SERVICES: print(f'{name}: PID={owned(name) or "未启动"}, 端口={"监听" if listening(port) else "关闭"}')
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr); sys.exit(1)
