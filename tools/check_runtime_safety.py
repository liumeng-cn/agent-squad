#!/usr/bin/env python3
"""防止进程退出故障注入进入正式 Java 执行路径。"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
violations = []
for path in (root / 'server').glob('*/src/main/java/**/*.java'):
    source = path.read_text()
    for pattern in (r'\.halt\s*\(', r'after-inflight-exit'):
        if re.search(pattern, source):
            violations.append(str(path.relative_to(root)))
            break
if violations:
    print('正式源码包含进程退出入口：' + ', '.join(violations), file=sys.stderr)
    sys.exit(1)
print('运行安全检查通过：正式 Java 源码无进程退出故障入口')
