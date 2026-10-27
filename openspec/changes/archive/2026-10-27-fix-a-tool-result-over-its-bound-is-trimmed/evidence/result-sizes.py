#!/usr/bin/env python3
"""result-sizes.py — the size distribution of the tool results a loop child was handed.

Why this exists: issue #660 asks for a bound on one tool result, and the bound has to be a number the
archive measured rather than one a worker guessed. `tools/loop-spend.py` prints what a cap would *save*
(`LOOP-SPEND-CAP4K/8K/16K`); this probe prints the distribution those savings come from — the percentiles
of one result's characters over every tool result the children of the archive were handed — so the bound can
be placed above an ordinary result and under the tail that carries the spend.

Usage: result-sizes.py [--items DIR]
       The default is `.pi/issue-loop/items` under the project root (the run's own archive).

Output: one line per figure, `LOOP-RESULT-SIZES ...`, plus the count of results at or above each candidate
bound. Read the archive's event streams (`items/*/events*.jsonl`), the same wire `tools/loop-spend.py` reads.

2026-10-27, the run of issue #660: 59 179 014 characters over 22 901 tool results, p50 825, p75 2 917, p90 7
194, p95 13 684, p99 16 149, max 116 628. A bound at 8 000 cuts 9.0 % of the results; at 4 000, 18.9 %.
"""
import argparse
import glob
import json
import os
import sys
import time


def sizes(items_dir):
    out = []
    for path in glob.glob(os.path.join(items_dir, '*', 'events*.jsonl')):
        with open(path, 'r', encoding='utf-8', errors='replace') as fh:
            for line in fh:
                line = line.strip()
                if not line.startswith('{'):
                    continue
                try:
                    event = json.loads(line)
                except ValueError:
                    continue
                if event.get('type') != 'turn_end':
                    continue
                for tr in (event.get('toolResults') or []):
                    n = 0
                    content = tr.get('content')
                    if isinstance(content, str):
                        n = len(content)
                    elif isinstance(content, list):
                        for block in content:
                            if isinstance(block, dict):
                                n += len(block.get('text') or '')
                    out.append(n)
    return out


def pct(values, p):
    if not values:
        return 0
    k = max(0, min(len(values) - 1, int(round((p / 100.0) * (len(values) - 1)))))
    return sorted(values)[k]


def main(argv):
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument('--items', default=os.path.join('.pi', 'issue-loop', 'items'))
    args = ap.parse_args(argv)

    values = sizes(args.items)
    if not values:
        print(f'LOOP-RESULT-SIZES none — no event stream under {args.items}', file=sys.stderr)
        return 2
    total = sum(values)
    # The archive grows while a run is in flight — the running item's own session is written into it — so the
    # reading carries its own moment and the numbers quoted anywhere are this log's, not a later re-run's.
    print(f'LOOP-RESULT-SIZES at={time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())} n={len(values)} chars={total}')
    for p in (50, 75, 90, 95, 99):
        print(f'LOOP-RESULT-SIZES p{p}={pct(values, p)}')
    print(f'LOOP-RESULT-SIZES max={max(values)}')
    for bound in (4000, 8000, 16000):
        above = [v for v in values if v > bound]
        share = 100.0 * len(above) / len(values)
        print(f'LOOP-RESULT-SIZES bound={bound} above={len(above)} share={share:.1f}%')
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1:]))
