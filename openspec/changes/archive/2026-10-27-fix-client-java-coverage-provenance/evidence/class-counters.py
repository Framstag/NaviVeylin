import sys, xml.etree.ElementTree as ET

path = sys.argv[1]
own = {
    "com/framstag/libosmscout/client/AvailableMapEntry",
    "com/framstag/libosmscout/client/BasemapManager",
    "com/framstag/libosmscout/client/InstalledMaps",
    "com/framstag/libosmscout/client/MapDownloadManager",
    "com/framstag/libosmscout/client/OSMScoutClientBuilder",
    "com/framstag/libosmscout/client/OSMScoutClient",
}
root = ET.parse(path).getroot()


def pct(covered, total):
    return 100.0 * covered / total if total else 0.0


own_covered = own_total = 0
vendored_covered = vendored_total = 0
rows = []
for pkg in root.iter("package"):
    for cls in pkg.iter("class"):
        name = cls.get("name")
        counter = None
        for c in cls.findall("counter"):
            if c.get("type") == "INSTRUCTION":
                counter = c
        if counter is None:
            continue
        covered = int(counter.get("covered"))
        missed = int(counter.get("missed"))
        total = covered + missed
        is_own = any(name == o or name.startswith(o + "$") for o in own)
        rows.append((name, covered, total, is_own))
        if is_own:
            own_covered += covered
            own_total += total
        else:
            vendored_covered += covered
            vendored_total += total

for name, covered, total, is_own in sorted(rows):
    print("%-70s %4d/%4d = %5.1f %% %s" % (name, covered, total, pct(covered, total), "own" if is_own else "vendored"))
print()
if own_total:
    print("own      %d/%d = %.1f %%" % (own_covered, own_total, pct(own_covered, own_total)))
if vendored_total:
    print("vendored %d/%d = %.1f %%" % (vendored_covered, vendored_total, pct(vendored_covered, vendored_total)))
