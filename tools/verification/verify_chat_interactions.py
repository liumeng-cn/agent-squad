"""通过正式聊天接口验证原生插话、下一轮回退和停止；不创建替代 Agent。"""
import argparse
import json
import time
import urllib.request
import uuid
from pathlib import Path


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://127.0.0.1:18082')
    parser.add_argument('--scene', choices=['all', 'master', 'tool', 'subagent', 'stop'], default='all')
    args = parser.parse_args()
    records = []

    def request(path, body=None, user='1'):
        headers = {'X-User-Id': user}
        if body is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(args.base_url + '/api/v2/chat/' + path,
                                     data=None if body is None else json.dumps(body).encode(), headers=headers)
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.load(response)
        if result['code'] != 0:
            raise AssertionError(result)
        return result['data']

    def state(session):
        return request('sessions/' + session)

    def wait(session, predicate, timeout=120):
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            snapshot = state(session)
            if predicate(snapshot):
                return snapshot
            time.sleep(0.25)
        raise AssertionError('等待状态超时：' + session)

    def send(session, text, turn=None, interaction=None):
        body = {'sessionId': session, 'interactionId': interaction or uuid.uuid4().hex, 'message': text}
        if turn:
            body['observedTurnId'] = turn
        return request('messages', body)

    def active_tool(snapshot, name):
        return any(a['title'] == name and a['status'] == 'RUNNING'
                   for m in snapshot['messages'] for a in m['activities'])

    scenes = [
        ('master', '只回复：收到。不要调用工具。',
         lambda s: s['status'] == 'RUNNING' and bool(s['runId'])),
        ('tool', '调用一次 test_event_tool，然后只回复：完成。',
         lambda s: active_tool(s, 'test_event_tool')),
        ('subagent', '调用 java-tester 子Agent，让它只回复：完成。你最后也只回复：完成。',
         lambda s: active_tool(s, 'agent_spawn')),
    ]
    for name, prompt, during in scenes:
        if args.scene not in ['all', name]:
            continue
        session = 'accept-' + name + '-' + uuid.uuid4().hex[:12]
        first = send(session, prompt)
        wait(session, during)
        extra = send(session, '补充要求：最后只回复“补充已应用”。', first['turnId'])
        assert extra['disposition'] == 'STEERING' and extra['turnId'] == first['turnId']
        final = wait(session, lambda s: s['status'] in ['COMPLETED', 'FAILED', 'INTERRUPTED', 'WAITING'])
        assert final['status'] == 'COMPLETED', final['status']
        assert any(i['id'] == extra['inputId'] and i['status'] == 'APPLIED' for i in final['inputs'])
        assert not request('sessions/' + session, user='2')['messages']
        record = {'scene': name, 'sessionId': session, 'first': first, 'supplement': extra,
                  'status': final['status'], 'inputs': final['inputs'], 'cursor': final['cursor'],
                  'reply': next(m['content'] for m in reversed(final['messages']) if m['role'] == 'ASSISTANT')[:300]}
        records.append(record)
        write_records(records)
        print(json.dumps(record, ensure_ascii=False), flush=True)
        if name == 'master':
            follow = send(session, '只回复：下一轮正常。', first['turnId'])
            assert follow['disposition'] == 'NEW_TURN' and follow['turnId'] != first['turnId']
            wait(session, lambda s: s['turnId'] == follow['turnId'] and s['status'] == 'COMPLETED')
            records.append({'scene': 'finished-turn-fallback', 'receipt': follow})

    if args.scene in ['all', 'stop']:
        session = 'accept-stop-' + uuid.uuid4().hex[:12]
        first = send(session, '只回复：完成。不要调用工具。')
        running = wait(session, lambda s: s['status'] == 'RUNNING' and bool(s['runId']))
        accepted = request('interruptions', {'sessionId': session, 'runId': running['runId']})
        assert accepted is True
        final = wait(session, lambda s: s['status'] in ['INTERRUPTED', 'COMPLETED', 'FAILED'])
        records.append({'scene': 'stop', 'sessionId': session, 'accepted': accepted, 'status': final['status'],
                        'turnId': first['turnId']})
        print(json.dumps(records[-1], ensure_ascii=False), flush=True)
    write_records(records)


def write_records(records):
    output = Path('.local/chat-implementation/real-model-results.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    run()
