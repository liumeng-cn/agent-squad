#!/usr/bin/env python3
"""管理本地服务，支持显式重建开发库聊天表。"""
import argparse
import os
import re
from pathlib import Path
import shlex
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / os.environ.get('SQUAD_STATE_DIR', '.local/auth')
SERVICES = [('user-server', 18083, 'user/user-server'), ('v2-server', 18082, 'v2-server'), ('gateway', 18081, 'gateway'), ('web', 5173, None)]

def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)

def config():
    # 应用参数统一由各服务 application.yml 提供，此处只准备运行日志目录。
    STATE.mkdir(parents=True, exist_ok=True)
    return dict(os.environ)

def health_path(module):
    """Servlet 应用的健康检查跟随工程路径，网关健康检查仍由根路径暴露。"""
    if module not in ['user/user-server', 'v2-server']:
        return '/actuator/health'
    source = ROOT / 'server' / module / 'src/main/resources/application.yml'
    match = re.search(r'^    context-path:\s*([^#\n]+)', source.read_text(), re.MULTILINE)
    if not match:
        raise RuntimeError(f'{module} 缺少 server.servlet.context-path 配置')
    prefix = match.group(1).strip().strip("\"'").rstrip('/')
    return prefix + '/actuator/health'

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

def tail_backend_logs():
    """启动完成后在 iTerm2 新窗口持续查看后端日志。"""
    if sys.platform != 'darwin':
        return
    logs = [str((STATE / f'{name}.log').resolve()) for name, _, module in SERVICES if module]
    command = shlex.join(['tail', '-n', '100', '-F', *logs])
    script = '''on run argv
    tell application "iTerm2"
        activate
        set logWindow to (create window with default profile)
        tell current session of logWindow
            write text (item 1 of argv)
        end tell
    end tell
end run'''
    try:
        subprocess.run(['osascript', '-e', script, command], check=True, capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.SubprocessError):
        print(f'无法自动打开 iTerm2，后端已启动，可手动执行：{command}', file=sys.stderr)

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
            path = '/' if name == 'web' else health_path(module)
            url = f'http://127.0.0.1:{port}' + path
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
    tail_backend_logs()

def init_db():
    config()
    dependencies()
    # 用户和聊天各自初始化所属 schema。
    sql = ROOT / 'server/user/user-server/src/main/resources/db/schema.sql'
    with sql.open('rb') as data:
        run(['docker', 'exec', '-i', 'squad-postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'squad', '-d', 'agent_squad'], stdin=data)


def rebuild_chat_db():
    # 仅重建开发库的聊天表。使用默认 RESTRICT，外部依赖存在时整笔回滚。
    baseline = ROOT / 'server/v2-server/src/main/resources/db/migration/V1__baseline.sql'
    drops = '''DROP TABLE IF EXISTS
    squad_chat.sq_chat_session,
    squad_chat.sq_chat_conv,
    squad_chat.agentscope_store,
    squad_chat.agentscope_sessions,
    squad_chat.agentscope_snapshots;
'''
    run(['docker', 'exec', '-i', 'squad-postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
         '--single-transaction', '-U', 'squad', '-d', 'agent_squad'],
        input=drops + baseline.read_text(), text=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'restart', 'status', 'init-db', 'rebuild-chat-db'])
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    try:
        if args.action in ['stop', 'restart']: stop()
        if args.action in ['start', 'restart']: start(args.skip_build)
        if args.action == 'init-db': init_db()
        elif args.action == 'rebuild-chat-db': rebuild_chat_db()
        if args.action == 'status':
            for name, port, _ in SERVICES: print(f'{name}: PID={owned(name) or "未启动"}, 端口={"监听" if listening(port) else "关闭"}')
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr); sys.exit(1)
