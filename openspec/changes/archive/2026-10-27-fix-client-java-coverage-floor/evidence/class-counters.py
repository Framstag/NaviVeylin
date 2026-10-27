import sys
import xml.etree.ElementTree as ET

p = sys.argv[1]
t = ET.parse(p).getroot()


def pct(c):
    m = int(c.get('missed'))
    cv = int(c.get('covered'))
    tot = m + cv
    return cv, tot, (100 * cv / tot if tot else 0)


print("== per package/class INSTRUCTION ==")
for pkg in t.findall('package'):
    print("package", pkg.get('name'))
    for cl in pkg.findall('class'):
        for c in cl.findall('counter'):
            if c.get('type') == 'INSTRUCTION':
                cv, tot, pc = pct(c)
                print(f"  {cl.get('name').split('/')[-1]}: {cv}/{tot} = {pc:.1f}%")
print("== module counters ==")
for c in t.findall('counter'):
    if c.get('type') in ('INSTRUCTION', 'LINE', 'BRANCH', 'METHOD', 'CLASS'):
        cv, tot, pc = pct(c)
        print(f"{c.get('type')}: {cv}/{tot} = {pc:.1f}%")
print("== per-sourcefile LINE ==")
for pkg in t.findall('package'):
    for sf in pkg.findall('sourcefile'):
        for c in sf.findall('counter'):
            if c.get('type') == 'LINE':
                cv, tot, pc = pct(c)
                print(f"  {sf.get('name')}: {cv}/{tot} = {pc:.1f}%")
