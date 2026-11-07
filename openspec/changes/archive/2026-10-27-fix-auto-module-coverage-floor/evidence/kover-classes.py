import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else 'auto/build/reports/kover/report.xml'
d = open(path).read()
rows = []
for m in re.finditer(r'<class name="([^"]+)"[^>]*>(.*?)</class>', d, re.S):
    name, body = m.group(1), m.group(2)
    c = re.findall(r'<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"/>', body)
    if c:
        missed, cov = map(int, c[-1])
        rows.append((missed, cov, name))
rows.sort(reverse=True)
for r in rows[:40]:
    print(r)
print('classes', len(rows), 'sum missed', sum(r[0] for r in rows), 'sum covered', sum(r[1] for r in rows))
