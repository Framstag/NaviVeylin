import glob, re, sys

root = sys.argv[1]
t = f = e = cls = 0
lo = hi = None
for p in glob.glob(root + "/TEST-*.xml"):
    s = open(p).read(3000)
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)" timestamp="([^"]+)"', s)
    if not m:
        print("NOPARSE", p)
        continue
    cls += 1
    t += int(m.group(1))
    f += int(m.group(3))
    e += int(m.group(4))
    ts = m.group(5)
    lo = ts if lo is None or ts < lo else lo
    hi = ts if hi is None or ts > hi else hi
print("classes=%d tests=%d failures=%d errors=%d timestamps %s .. %s" % (cls, t, f, e, lo, hi))
