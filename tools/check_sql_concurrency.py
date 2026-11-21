#!/usr/bin/env python3
"""检查项目 SQL 不得使用悲观查询锁，覆盖归档和研究夹具。"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'.git', 'node_modules', 'target', 'dist', '.local'}
PATTERN = re.compile(r'\bfor\s+(?:no\s+key\s+)?update\b', re.IGNORECASE)
failures = []
for path in ROOT.rglob('*'):
    if not path.is_file() or path.suffix not in {'.java', '.xml', '.sql', '.py', '.sh'}:
        continue
    if EXCLUDED.intersection(path.relative_to(ROOT).parts) or path.resolve() == Path(__file__).resolve():
        continue
    content = path.read_text(errors='replace')
    # 合并直接拼接的字符串片段，防止用拆分关键字绕过扫描。
    content = re.sub(r'''["']\s*\+\s*["']''', '', content)
    for match in PATTERN.finditer(content):
        line = content.count('\n', 0, match.start()) + 1
        failures.append(f'{path.relative_to(ROOT)}:{line}')
if failures:
    print('发现禁止的悲观查询锁语句：\n' + '\n'.join(failures))
    sys.exit(1)
print('SQL 并发约束检查通过')
