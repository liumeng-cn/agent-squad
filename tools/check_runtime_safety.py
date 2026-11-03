#!/usr/bin/env python3
"""检查正式代码的进程退出入口及直接 JDBC 访问。"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
violations = []
for path in (root / 'server').glob('*/src/main/java/**/*.java'):
    source = path.read_text()
    for pattern in (r'\.halt\s*\(', r'after-inflight-exit',
                    r'import\s+java\.sql\.', r'JdbcTemplate', r'NamedParameterJdbcTemplate',
                    r'DriverManager', r'\.(?:prepareStatement|createStatement)\s*\('):
        if re.search(pattern, source):
            violations.append(str(path.relative_to(root)))
            break
if violations:
    print('正式源码包含禁止的进程退出或直接JDBC入口：' + ', '.join(violations), file=sys.stderr)
    sys.exit(1)
print('运行安全检查通过：正式Java源码无进程退出故障入口和直接JDBC访问')
