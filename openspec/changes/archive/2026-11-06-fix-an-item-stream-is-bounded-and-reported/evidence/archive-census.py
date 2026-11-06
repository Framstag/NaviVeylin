#!/usr/bin/env python3
"""Census of the loop's item directories (issue #802).

Read-only. Takes the numbers the change quotes: how big the item directories are, what a bound on
each kept event stream would leave, and how many directories each retention would release.

    python3 openspec/changes/fix-an-item-stream-is-bounded-and-reported/evidence/archive-census.py [<items dir>]

Default items dir: `.pi/issue-loop/items` beside the project root this file was run from.
"""
import os
import sys
import time

MIB = 2 ** 20


def census(root):
    events = []
    dirs = []
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if not os.path.isdir(path):
            continue
        newest = 0.0
        total = 0
        for cur, _subdirs, files in os.walk(path):
            for f in files:
                fp = os.path.join(cur, f)
                try:
                    st = os.stat(fp)
                except OSError:
                    continue
                total += st.st_size
                newest = max(newest, st.st_mtime)
                if f == "events.jsonl":
                    events.append(st.st_size)
        dirs.append((name, newest, total))
    return events, dirs


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else ".pi/issue-loop/items"
    events, dirs = census(root)
    events.sort()
    n = len(events)
    print(
        "items=%d events-files=%d total=%.0fMiB p50=%.2fMiB p90=%.2fMiB max=%.2fMiB"
        % (len(dirs), n, sum(events) / MIB, events[n // 2] / MIB, events[int(n * 0.9)] / MIB, events[-1] / MIB)
    )
    for bound in (4, 8, 16):
        print("bound=%dMiB kept-sum=%.0fMiB" % (bound, sum(min(s, bound * MIB) for s in events) / MIB))
    now = time.time()
    for days in (7, 14, 30):
        old = [d for d in dirs if now - d[1] > days * 86400]
        print("days=%d dirs=%d MiB=%.0f" % (days, len(old), sum(d[2] for d in old) / MIB))
    print("all item-dirs MiB=%.0f" % (sum(d[2] for d in dirs) / MIB))


if __name__ == "__main__":
    main()
