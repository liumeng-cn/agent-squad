"""隔离 Redis 协议实验；不连接业务库，不调用模型，不改运行中的 Redis。"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid
from concurrent.futures import ThreadPoolExecutor


IMAGE = 'redis@sha256:489a769e0a54dc245896132a15c72d2bc7296719954da99398f43a3877e5f8eb'
ROOT = Path(__file__).resolve().parent
ENQUEUE = (ROOT / 'enqueue.lua').read_text()
FINISH = (ROOT / 'finish.lua').read_text()


def run():
    name = 'squad-redis-probe-' + uuid.uuid4().hex[:12]
    env = dict(os.environ, REDISCLI_AUTH=secrets.token_hex(24))
    results = []

    def redis(*args):
        proc = subprocess.run(['docker', 'exec', '-e', 'REDISCLI_AUTH', name,
                               'redis-cli', '--json', *map(str, args)],
                              env=env, capture_output=True, text=True, check=True)
        # redis-cli 的 Redis 错误可能仍返回退出码0，不能误记为成功。
        try:
            return json.loads(proc.stdout)
        except json.JSONDecodeError as error:
            raise RuntimeError(proc.stdout.strip()) from error

    def keys(kind='PROJECTION', binding='binding-1', user='user-1'):
        prefix = 'squad:probe:{local:p0}:' + user + ':' + kind
        return prefix + ':binding:' + binding, prefix + ':ready'

    def enqueue(kind='PROJECTION', binding='binding-1', user='user-1', capacity=100):
        state, stream = keys(kind, binding, user)
        return redis('EVAL', ENQUEUE, 2, state, stream, capacity, 1,
                     kind, user, binding, '', '')

    def group(kind='PROJECTION', user='user-1'):
        redis('XGROUP', 'CREATE', keys(kind, user=user)[1], 'workers', '0', 'MKSTREAM')

    def take(kind='PROJECTION', user='user-1', consumer='consumer-1'):
        stream = keys(kind, user=user)[1]
        response = redis('XREADGROUP', 'GROUP', 'workers', consumer, 'COUNT', 1,
                         'STREAMS', stream, '>')
        return response[stream] if isinstance(response, dict) else response[0][1]

    def finish(message_id, observed, more=False, kind='PROJECTION', user='user-1'):
        state, stream = keys(kind, user=user)
        return redis('EVAL', FINISH, 2, state, stream, 'workers', message_id,
                     observed, int(more), 1, kind, user, 'binding-1', '', '')

    def passed(title):
        results.append(title)
        print('PASS ' + title, flush=True)

    def ready():
        for _ in range(100):
            try:
                if redis('PING') == 'PONG':
                    return
            except (subprocess.SubprocessError, RuntimeError):
                pass
            time.sleep(.1)
        raise RuntimeError('隔离Redis启动超时')

    started = False
    with tempfile.TemporaryDirectory(prefix='squad-redis-') as directory:
        config = Path(directory) / 'redis.conf'
        config.write_text('bind 127.0.0.1\nprotected-mode yes\n'
                          'requirepass ' + env['REDISCLI_AUTH'] + '\n'
                          'appendonly yes\nappendfsync always\ndir /data\n'
                          'maxmemory 64mb\nmaxmemory-policy noeviction\nsave ""\n')
        config.chmod(0o600)
        try:
            subprocess.run(['docker', 'run', '--rm', '-d', '--network', 'none',
                            '--name', name, '--mount',
                            'type=bind,source=' + str(config) + ',target=/usr/local/etc/redis/redis.conf,readonly',
                            IMAGE, 'redis-server', '/usr/local/etc/redis/redis.conf'],
                           check=True, stdout=subprocess.DEVNULL)
            started = True
            ready()
            group()
            with ThreadPoolExecutor(max_workers=8) as pool:
                refs = list(pool.map(lambda _: enqueue(), range(32)))
            assert len({item[1] for item in refs}) == 1
            assert redis('XLEN', keys()[1]) == 1
            assert redis('HGET', keys()[0], 'dirtyVersion') == '32'
            passed('32个并发通知合并成一个引用，版本不丢失')

            message_id = refs[0][1]
            take()
            enqueue()  # 业务处理期间又有提交。
            successor = finish(message_id, 32)
            assert successor not in ('CLEARED', 'STALE')
            assert redis('XLEN', keys()[1]) == 1
            take()
            assert finish(successor, 33) == 'CLEARED'
            # 清理后新提交仍须产生引用；迟到旧ACK不得抹掉它。
            fresh = enqueue()[1]
            assert finish(message_id, 32) == 'STALE'
            assert redis('HGET', keys()[0], 'queued') == fresh
            passed('处理中新到达、清理后新到达和迟到确认均不丢唤醒')

            take()
            bounded = finish(fresh, 1, more=True)
            assert bounded not in ('CLEARED', 'STALE')
            take()
            assert finish(bounded, 1) == 'CLEARED'
            passed('批次耗尽时即使版本不变也续排未消费尾部')

            for _ in range(8):
                racing = enqueue()[1]
                take()
                with ThreadPoolExecutor(max_workers=2) as pool:
                    confirmed = pool.submit(finish, racing, 1)
                    arrived = pool.submit(enqueue)
                    confirmed.result()
                    arrived.result()
                assert redis('XLEN', keys()[1]) == 1
                queued = redis('HGET', keys()[0], 'queued')
                observed = redis('HGET', keys()[0], 'dirtyVersion')
                assert take()[0][0] == queued
                assert finish(queued, observed) == 'CLEARED'
            passed('生产与确认并发竞态连续8轮均保留新提交引用')

            crashed = enqueue()[1]
            take()
            claimed = redis('XAUTOCLAIM', keys()[1], 'workers', 'consumer-2', 0, '0-0', 'COUNT', 1)
            assert claimed[1][0][0] == crashed
            assert finish(crashed, 1) == 'CLEARED'
            assert redis('XPENDING', keys()[1], 'workers')[0] == 0
            passed('消费后未ACK可由其他消费者接管并清理pending')

            persisted = enqueue()[1]
            take()
            subprocess.run(['docker', 'restart', name], check=True, stdout=subprocess.DEVNULL)
            ready()
            assert redis('HGET', keys()[0], 'queued') == persisted
            assert redis('XPENDING', keys()[1], 'workers')[0] == 1
            assert finish(persisted, 1) == 'CLEARED'
            passed('AOF重启后引用、合并状态及pending保留')

            group('COMMAND')
            group('CONTROL')
            enqueue('COMMAND')
            take('COMMAND')  # 模拟长模型任务占用其独立通道。
            control = enqueue('CONTROL')[1]
            assert take('CONTROL')[0][0] == control
            assert finish(control, 1, kind='CONTROL') == 'CLEARED'
            group(user='user-2')
            assert enqueue(user='user-2')[0] == '1'
            assert redis('XLEN', keys(user='user-2')[1]) == 1
            passed('CONTROL独立就绪通道及用户命名空间隔离')

            try:
                enqueue('COMMAND', binding='binding-2', capacity=1)
                raise AssertionError('达到容量后仍接受新引用')
            except RuntimeError as error:
                assert 'WAKEUP_CAPACITY' in str(error)
            assert redis('EXISTS', keys('COMMAND', 'binding-2')[0]) == 0
            passed('达到容量时显式拒绝，不裁掉未确认引用或留下排队标记')
            print(json.dumps({'passed': len(results), 'scope': 'redis-protocol-only',
                              'image': IMAGE}, ensure_ascii=False))
        finally:
            if started:
                subprocess.run(['docker', 'stop', '-t', '2', name],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


if __name__ == '__main__':
    run()
