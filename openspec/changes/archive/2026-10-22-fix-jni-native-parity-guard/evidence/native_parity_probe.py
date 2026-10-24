import os
import re

S = "app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client"
O = "osmscout-client-java/src/main/java/com/framstag/libosmscout/client"
files = ["OSMScoutClient.java", "OSMScoutClientBuilder.java", "BasemapManager.java",
         "MapDownloadManager.java", "AvailableMapEntry.java"]


def natives(path):
    t = open(path).read()
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    out = []
    for m in re.finditer(r'\bnative\s+([\w\[\].<>, ]+?)\s+(\w+)\s*\(([^)]*)\)', t, flags=re.S):
        params = [p.strip() for p in m.group(3).split(',') if p.strip()]
        types = []
        for p in params:
            tokens = [x for x in p.split(' ') if x and x != 'final']
            if len(tokens) > 1:
                tokens = tokens[:-1]
            types.append(''.join(tokens).split('.')[-1])
        out.append(m.group(2) + "(" + ",".join(types) + ")")
    return out


total = 0
for f in files:
    s = natives(os.path.join(S, f))
    o = natives(os.path.join(O, f))
    ss, oo = set(s), set(o)
    print("%s: submodule %d natives, override %d natives" % (f, len(s), len(o)))
    for x in sorted(ss - oo):
        print("   SUBMODULE-ONLY " + x)
        total += 1
    for x in sorted(oo - ss):
        print("   OVERRIDE-ONLY " + x)
        total += 1
print("total deviations: %d" % total)
