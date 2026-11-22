#!/usr/bin/env python3
"""split-model.py — the item's own spend under the checkpoint, at several readings (issue #1003).

The model is `tools/loop-spend.py`'s reader and its own arithmetic, applied to one question: what does
an item's first worker cost when the run splits it at `LOOP_ITEM_CONTEXT_WARN` and starts a fresh child
from the turn after the crossing? A split is modelled as what it is — the baseline paid again, nothing
carried over: the continuation's prefix starts at zero and its turns inject only their own results.

Cost of one session's turns t_1..t_n, with B the session's first `input + cacheRead` (the prompt read on
every turn) and D_i what turn i injected (its tool results at CHARS_PER_TOKEN plus its output):

    cost = sum_i (B + sum_{j<i} D_j) + sum_i out_i

which is loop-spend.py's `tokens ≈ T*B + sum_k (T-1-k) D_k` with the output charged at its own weight.
The largest context the model reports for an item is `B + sum_i D_i` over its longest session.

Usage: python3 split-model.py [--items DIR] [--at N ...]
"""

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def load_spend():
    for cand in (
        os.path.join(HERE, '..', '..', '..', '..', 'tools', 'loop-spend.py'),
        os.path.join(os.getcwd(), 'tools', 'loop-spend.py'),
    ):
        path = os.path.abspath(cand)
        if os.path.exists(path):
            sys.path.insert(0, os.path.dirname(path))
            import importlib
            return importlib.import_module('loop-spend')
    raise SystemExit('split-model: tools/loop-spend.py not found')


spend = load_spend()
C = spend.CHARS_PER_TOKEN


def delta(turn):
    return sum(r[2] for r in turn['results']) / C + turn['out']


def session_cost(turns, baseline):
    cost = 0.0
    prefix = 0.0
    for t in turns:
        cost += baseline + prefix + t['out']
        prefix += delta(t)
    return cost


def context_top(turns, baseline):
    prefix = 0.0
    for t in turns:
        prefix += delta(t)
    return baseline + prefix


def item_rows(items):
    rows = []
    for it in items:
        worker = next((s for s in it['sessions'] if s['role'] == 'worker'), None)
        if not worker or not worker['turns']:
            continue
        first = worker['turns'][0]
        baseline = first['inp'] + first['cr']
        if baseline <= 0:
            continue
        turns = worker['turns']
        rest = sum(session_cost(s['turns'], s['turns'][0]['inp'] + s['turns'][0]['cr'])
                   for s in it['sessions'] if s is not worker)
        rows.append(dict(issue=it['issue'], baseline=baseline, turns=turns, rest=rest))
    return rows


def split_at(row, at):
    """One item's cost with its first worker split at `at`, and the largest context the split leaves."""
    turns, baseline = row['turns'], row['baseline']
    prefix = 0.0
    cut = None
    for i, t in enumerate(turns):
        if baseline + prefix >= at:
            cut = i
            break
        prefix += delta(t)
    if cut is None:
        return session_cost(turns, baseline) + row['rest'], context_top(turns, baseline)
    head = session_cost(turns[:cut], baseline)
    tail = session_cost(turns[cut:], baseline)
    return head + tail + row['rest'], max(context_top(turns[:cut], baseline),
                                          context_top(turns[cut:], baseline))


def pct(values, p):
    values = sorted(values)
    if not values:
        return 0
    return values[int((len(values) - 1) * p / 100 + 0.5)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--items', default=os.path.join(os.getcwd(), '.pi', 'issue-loop', 'items'))
    ap.add_argument('--at', type=int, action='append', default=None)
    args = ap.parse_args()
    items = spend.read_items(args.items)
    rows = item_rows(items)
    ats = args.at or [0, 143124, 90000, 70000, 50000]
    print('SPLIT-MODEL items=%d sessions=%d turns=%d chars_per_token=%s' % (
        len(rows), sum(len(r['turns']) for r in rows), sum(len(r['turns']) for r in rows), C))
    for at in ats:
        total = 0.0
        tops = []
        splits = 0
        for r in rows:
            if at <= 0:
                cost, top = session_cost(r['turns'], r['baseline']) + r['rest'], context_top(r['turns'], r['baseline'])
            else:
                cost, top = split_at(r, at)
                prefix = 0.0
                for t in r['turns']:
                    if r['baseline'] + prefix >= at:
                        splits += 1
                        break
                    prefix += delta(t)
            total += cost
            tops.append(top)
        print('SPLIT-MODEL at=%s p50=%d p90=%d max=%d total=%dMtok splits=%d' % (
            'as-is' if at <= 0 else at, pct(tops, 50), pct(tops, 90), max(tops), round(total / 1e6), splits))


if __name__ == '__main__':
    main()
