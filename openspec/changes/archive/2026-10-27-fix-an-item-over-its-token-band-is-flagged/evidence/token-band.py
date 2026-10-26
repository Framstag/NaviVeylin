#!/usr/bin/env python3
"""token-band.py — the per-item token band the report warns against, measured from the loop's own record.

`tools/loop/report.sh` prints an item's spend from `metrics.tokens` in the run's state, so the band it warns
against is read from the same field and nowhere else: every `.pi/issue-loop/run.json` and every state
`state.sh init` kept under `.pi/issue-loop/runs/` that recorded one. An `archived change` and a skip are
counted alike — the band describes what an item costs, not how it ended.

Usage: token-band.py [root]        (default `.pi/issue-loop`, relative to the working directory)

Prints the count, the median, the 75th and 90th percentile, the maximum, and how many items sit above each,
then the `LOOP-BAND-*` lines a change quotes.

Exit: 0 measured, 2 the directory holds no state with a recorded spend.
"""

import glob
import json
import os
import statistics
import sys

root = sys.argv[1] if len(sys.argv) > 1 else '.pi/issue-loop'

tokens = []
contexts = []
for path in sorted(glob.glob(os.path.join(root, 'run.json')) + glob.glob(os.path.join(root, 'runs', '*.json'))):
    try:
        with open(path) as fh:
            state = json.load(fh)
    except (OSError, ValueError):
        continue
    for item in state.get('items') or []:
        t = (item.get('metrics') or {}).get('tokens')
        if not t:
            continue
        tokens.append(sum(v for v in (t.get('input'), t.get('output'),
                                      t.get('cacheRead'), t.get('cacheWrite'))
                          if isinstance(v, (int, float))))
        c = (item.get('metrics') or {}).get('context')
        if isinstance(c, (int, float)) and c > 0:
            contexts.append(c)

if not tokens:
    print('token-band: no state under %s records a spend' % root, file=sys.stderr)
    sys.exit(2)

tokens.sort()
contexts.sort()


def pct(q):
    return tokens[min(len(tokens) - 1, int(q * len(tokens)))]


median = statistics.median(tokens)
print('states: %s' % ' '.join(sorted({os.path.basename(p) for p in
                                      glob.glob(os.path.join(root, 'run.json'))})))
print('items with a recorded spend: %d' % len(tokens))
print('median   %d (%.2f M)' % (median, median / 1e6))
print('p75      %d (%.2f M)' % (pct(0.75), pct(0.75) / 1e6))
print('p90      %d (%.2f M)' % (pct(0.90), pct(0.90) / 1e6))
print('max      %d (%.2f M)' % (tokens[-1], tokens[-1] / 1e6))
for candidate in (pct(0.75), pct(0.90)):
    above = [t for t in tokens if t > candidate]
    print('above %d: %d items (%.0f%%)' % (candidate, len(above), 100 * len(above) / len(tokens)))
print('LOOP-BAND-ITEMS n=%d' % len(tokens))
print('LOOP-BAND-MEDIAN tokens=%d ratio_p75_to_median=%.2f' % (median, pct(0.75) / median))
print('LOOP-BAND-P75 tokens=%d' % pct(0.75))
print('LOOP-BAND-P90 tokens=%d' % pct(0.90))
print('LOOP-BAND-MAX tokens=%d' % tokens[-1])
if contexts:
    print('context (the largest window a child held) over %d items: median %d, p90 %d, max %d'
          % (len(contexts), statistics.median(contexts),
             contexts[min(len(contexts) - 1, int(0.9 * len(contexts)))], contexts[-1]))
    print('LOOP-BAND-CONTEXT n=%d p90=%d max=%d'
          % (len(contexts), contexts[min(len(contexts) - 1, int(0.9 * len(contexts)))], contexts[-1]))
