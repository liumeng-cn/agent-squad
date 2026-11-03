#!/usr/bin/env python3
"""审计本次 Maven 报告，任何失败、错误或跳过均阻断发布。"""
import sys
from pathlib import Path
from xml.etree import ElementTree

reports = sorted(Path('server').glob('*/target/surefire-reports/TEST-*.xml'))
if not reports:
    sys.exit('FAIL: 没有 Maven 测试报告')
counts = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
for report in reports:
    root = ElementTree.parse(report).getroot()
    for key in counts:
        counts[key] += int(root.get(key, 0))
    if int(root.get('skipped', 0)):
        print(f'SKIPPED: {report.name}: {root.get("skipped")}')
print(counts)
if not counts['tests'] or any(counts[key] for key in ('failures', 'errors', 'skipped')):
    sys.exit('FAIL: 发布测试必须全部实际执行并通过')
