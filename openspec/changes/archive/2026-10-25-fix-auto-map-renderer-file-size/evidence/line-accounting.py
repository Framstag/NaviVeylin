#!/usr/bin/env python3
"""The two invariants a mechanical split must keep: the member roster and the code lines.

Usage: python3 line-accounting.py <HEAD copy of AutoMapRenderer.kt> [project root]
The HEAD copy is `git show <base>:auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`.
"""
import re
import sys
from collections import Counter

HEAD_COPY = sys.argv[1]
ROOT = sys.argv[2] if len(sys.argv) > 2 else '.'
BASE = ROOT + '/auto/src/main/java/com/naviveylin/auto/'
CORE = BASE + 'AutoMapRenderer.kt'
TOPICS = ['Loops', 'Motion', 'Follow', 'Render', 'Overlays', 'Markers']

orig = open(HEAD_COPY).read().split('\n')
new_all = open(CORE).read().split('\n')
headers = set()
for t in TOPICS:
    tl = open(BASE + 'AutoMapRenderer%s.kt' % t).read().split('\n')
    new_all += tl
    last_import = max(i for i, l in enumerate(tl) if l.startswith('import '))
    for l in tl[last_import + 1:]:
        if l.startswith('// '):
            headers.add(l.strip())


def roster(lines):
    names = []
    for l in lines:
        m = re.match(r'^    (?:private |internal |public )?fun ([A-Za-z0-9_]+)', l)
        if m and len(m.group(1)) > 1:
            names.append(m.group(1))
            continue
        m = re.match(r'^(?:internal |public )?fun AutoMapRenderer\.([A-Za-z0-9_]+)', l)
        if m:
            names.append(m.group(1))
    return sorted(names)


r_before = roster(orig)
r_after = roster(new_all)
print('# member functions: before %d, after %d' % (len(r_before), len(r_after)))
delta = Counter(r_before) - Counter(r_after)
extra = Counter(r_after) - Counter(r_before)
print('roster: identical (%d names)' % len(r_before) if not delta and not extra
      else 'roster DELTA missing=%s extra=%s' % (dict(delta), dict(extra)))


def code_lines(lines):
    out = []
    for l in lines:
        s = l.strip()
        if not s:
            continue
        if s.startswith('import ') or s == 'package com.naviveylin.auto':
            continue
        if s.startswith('//') or s.startswith('*') or s.startswith('/*'):
            continue
        s = re.sub(r'^(private|internal) ', 'VIS ', s)
        s = re.sub(r'^(.*\b)(private|internal) (var|val)\b', r'\1VIS \3', s)
        s = re.sub(r'\bfun AutoMapRenderer\.', 'fun ', s)
        s = re.sub(r'\bAutoMapRenderer\.ViewportState\b', 'ViewportState', s)
        s = re.sub(r'^@androidx\.annotation\.VisibleForTesting$', '', s)
        out.append(s)
    return [l for l in out if l]


l_before = code_lines(orig)
l_after = [l for l in code_lines(new_all)]
print('# code lines (non-blank, non-comment): before %d, after %d' % (len(l_before), len(l_after)))
missing = Counter(l_before) - Counter(l_after)
added = Counter(l_after) - Counter(l_before)
if not missing and not added:
    print('code lines: multiset identical')
else:
    print('code-line DELTA: %d missing, %d added' % (sum(missing.values()), sum(added.values())))
    for k, v in list(missing.items())[:8]:
        print('  missing x%d: %s' % (v, k))
    for k, v in list(added.items())[:8]:
        print('  added   x%d: %s' % (v, k))
