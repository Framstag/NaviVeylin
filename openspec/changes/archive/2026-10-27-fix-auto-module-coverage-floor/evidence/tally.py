import glob
import re
import sys

pattern = sys.argv[1] if len(sys.argv) > 1 else 'auto/build/test-results/testDebugUnitTest/TEST-*.xml'
files = sorted(glob.glob(pattern))
t = f = e = s = 0
for p in files:
    head = open(p, encoding='utf-8', errors='replace').read(4000)
    m = re.search(r'<testsuite\b[^>]*>', head)
    if not m:
        continue
    tag = m.group(0)

    def attr(name):
        mm = re.search(name + r'="(\d+)"', tag)
        return int(mm.group(1)) if mm else 0

    t += attr('tests')
    f += attr('failures')
    e += attr('errors')
    s += attr('skipped')
print('classes=%d tests=%d failures=%d errors=%d skipped=%d' % (len(files), t, f, e, s))
