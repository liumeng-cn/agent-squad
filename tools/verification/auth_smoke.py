#!/usr/bin/env python3
"""使用真实服务验证注册、登录、昵称版本、CSRF、JWT与退出，不输出令牌。"""
import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.request

BASE = os.environ.get('SQUAD_AUTH_TEST_URL', 'http://127.0.0.1:18181')
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
phone = '139' + ''.join(str(secrets.randbelow(10)) for _ in range(8))

def request(path, data=None, method=None, csrf=None, base=BASE, opener=client, headers=None):
    hs = {'Content-Type': 'application/json'}
    if csrf: hs['X-CSRF-Token'] = csrf
    hs.update(headers or {})
    req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=hs, method=method)
    try: response = opener.open(req, timeout=10)
    except urllib.error.HTTPError as error: response = error
    return response.status, json.loads(response.read())

status, result = request('/api/v2/auth/me')
assert status == 401, (status, result)
status, result = request('/api/v2/users/register', {'phone': phone, 'verificationCode': '0000'})
assert result['code'] == 400, (status, result)
status, result = request('/api/v2/users/register', {'phone': phone, 'verificationCode': '8888'})
assert status == 200 and result['code'] == 0, (status, result)
user = result['data']
assert user['userName'] == 'user_' + user['userId'] and user['effectiveDisplayName'] == user['userName']
_, result = request('/api/v2/users/register', {'phone': phone, 'verificationCode': '8888'})
assert result['code'] == 409, result
_, result = request('/api/v2/auth/login', {'phone': phone, 'verificationCode': '0000'})
assert result['code'] == 400, result
_, result = request('/api/v2/auth/login', {'phone': phone, 'verificationCode': '8888'})
assert result['code'] == 0, result
csrf = result['data']['csrfToken']
session_cookie = next(c for c in jar if c.name == 'SQUAD_SESSION')
saved_cookie = session_cookie.name + '=' + session_cookie.value
parts = session_cookie.value.split('.')
parts[2] = ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]
status, _ = request('/api/v2/auth/me', opener=urllib.request.build_opener(), headers={'Cookie': 'SQUAD_SESSION=' + '.'.join(parts)})
assert status == 401, status
assert any(c.value.count('.') == 2 and any(k.lower() == 'httponly' for k in c._rest) for c in jar)
status, result = request('/api/v2/users/me')
assert status == 200 and result['data']['userId'] == user['userId'], (status, result)
status, result = request('/api/v2/users/me', {'displayName': '测试昵称', 'version': 0}, method='PATCH')
assert status == 403, (status, result)
status, result = request('/api/v2/users/me', {'displayName': '测试昵称', 'version': 0}, method='PATCH', csrf=csrf)
assert result['code'] == 0 and result['data']['effectiveDisplayName'] == '测试昵称', (status, result)
_, result = request('/api/v2/users/me', {'displayName': '过期编辑', 'version': 0}, method='PATCH', csrf=csrf)
assert result['code'] == 409, result
_, result = request('/api/v2/users/me', {'displayName': '  ', 'version': 1}, method='PATCH', csrf=csrf)
assert result['data']['effectiveDisplayName'] == user['userName'], result
status, result = request('/api/v2/users/me', base=os.environ.get('SQUAD_USER_TEST_URL', 'http://127.0.0.1:18183'), headers={'X-User-Id': user['userId']})
assert status == 401, (status, result)
status, _ = request('/internal/users/' + user['userId'] + '/auth')
assert status == 404, status
# 另一Gateway实例使用相同JWT密钥和Redis，验证跨实例认证与撤销。
other = os.environ.get('SQUAD_SECOND_GATEWAY_URL')
if other:
    status, result = request('/api/v2/auth/me', base=other)
    assert status == 200 and result['data']['user']['userId'] == user['userId'], (status, result)
    status, result = request('/api/v2/auth/logout', {}, csrf=csrf, base=other)
    assert result['code'] == 0, (status, result)
else:
    status, result = request('/api/v2/auth/logout', {}, csrf=csrf)
    assert result['code'] == 0, (status, result)
status, result = request('/api/v2/auth/me', opener=urllib.request.build_opener(), headers={'Cookie': saved_cookie})
assert status == 401, (status, result)
print('PASS: 注册、重复注册、验证码、JWT Cookie、当前用户、CSRF、昵称回退、乐观锁、伪造身份、内部路由隔离、退出' + ('、跨Gateway实例' if other else ''))
