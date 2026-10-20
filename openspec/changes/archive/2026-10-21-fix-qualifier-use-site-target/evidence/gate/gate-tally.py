"""Sum the gate's JUnit XMLs per test task.

Run from the change root: `python3 gate-tally.py .` — the XMLs it reads are the ones the gate left in
the build directories, so the number in tasks.md can be re-derived from the same artefacts.
"""

import pathlib
import re
import sys

TASKS = [
    ("app mobile", "app/build/test-results/testMobileDebugUnitTest"),
    ("app automotive", "app/build/test-results/testAutomotiveDebugUnitTest"),
    ("auto", "auto/build/test-results/testDebugUnitTest"),
    ("core", "core/build/test-results/testDebugUnitTest"),
    ("osmscout-client-java", "osmscout-client-java/build/test-results/test"),
]

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
ATTR = re.compile(r'tests="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"')

total = [0, 0, 0]
for name, rel in TASKS:
    t = f = e = 0
    for xml in sorted((ROOT / rel).glob("TEST-*.xml")):
        m = ATTR.search(xml.read_text(errors="replace").splitlines()[1])
        t += int(m.group(1))
        f += int(m.group(2))
        e += int(m.group(3))
    print(f"{name}: tests={t} failures={f} errors={e}")
    total = [total[0] + t, total[1] + f, total[2] + e]
print(f"total: tests={total[0]} failures={total[1]} errors={total[2]}")
sys.exit(0 if total[1] == 0 and total[2] == 0 else 1)
