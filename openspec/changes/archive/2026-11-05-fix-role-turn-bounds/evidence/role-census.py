#!/usr/bin/env python3
"""The per-role turn census behind `fix-role-turn-bounds` (issue #784).

Reads the loop archive's event streams and prints, per child role, the number of turns (`turn_end` lines) each
session took — then the percentiles the five `LOOP_CHILD_TURNS_MAX_<ROLE>` values were read from.

The role is not in the stream: `run.sh` starts a child with its prompt file, so the first user message of a session
is that path and its basename names the role (`child_role`'s mapping is repeated here, in the same order).

    python3 evidence/role-census.py [items-dir]     # default: .pi/issue-loop/items
    python3 evidence/role-census.py --post-758      # only the items that ran a round before the gate

"post-#758" is the population the three new phases exist in: an item whose stream holds a
`reviewer-pre-gate-prompt.md` session. The worker and the review round are also read over the whole archive, where
their samples are large.
"""
import glob
import json
import os
import sys


def role_of(first):
    base = os.path.basename(first.strip())
    if base.endswith('-tail-prompt.md') or '-tail' in base:
        return 'worker-tail'
    if 'pre-gate' in base:
        return 'reviewer-pre-gate'
    if 'gate' in base:
        return 'worker-gate'
    if base.startswith('reviewer'):
        return 'reviewer'
    if base.startswith('worker'):
        return 'worker'
    return 'other'


def sessions(path):
    """One per `session` event: its turn count and the first user message it was started with."""
    out, cur = [], None
    with open(path, errors='replace') as fh:
        for line in fh:
            if 'session' not in line and 'turn_end' not in line and 'message_start' not in line:
                continue
            try:
                event = json.loads(line)
            except ValueError:
                continue
            kind = event.get('type')
            if kind == 'session':
                cur = {'turns': 0, 'first': ''}
                out.append(cur)
                continue
            if cur is None:
                continue
            if kind == 'message_start':
                message = event.get('message') or {}
                if not cur['first'] and message.get('role') == 'user':
                    content = message.get('content')
                    if isinstance(content, list):
                        cur['first'] = ''.join(x.get('text') or '' for x in content if isinstance(x, dict))
                    else:
                        cur['first'] = str(content or '')
                continue
            if kind == 'turn_end':
                cur['turns'] += 1
    return out


def pct(values, p):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * p / 100.0
    lo, hi = int(k), min(int(k) + 1, len(values) - 1)
    return values[lo] + (values[hi] - values[lo]) * (k - lo)


def main(argv):
    post_only = '--post-758' in argv
    dirs = [a for a in argv if not a.startswith('--')]
    items_dir = dirs[0] if dirs else '.pi/issue-loop/items'

    rows = []
    for path in sorted(glob.glob(os.path.join(items_dir, '*', 'events.jsonl'))):
        sessions_here = sessions(path)
        if not sessions_here:
            continue
        per = [(role_of(s['first']), s['turns']) for s in sessions_here]
        rows.append((os.path.basename(os.path.dirname(path)),
                     any(role == 'reviewer-pre-gate' for role, _ in per), per))

    population = [r for r in rows if r[1]] if post_only else rows
    label = 'post-#758' if post_only else 'archive'
    print(f'== {label}: {len(population)} of {len(rows)} items')

    by_role = {}
    for _, _, per in population:
        for role, turns in per:
            by_role.setdefault(role, []).append(turns)
    print(f'{"role":20} {"n":>4} {"p50":>7} {"p75":>7} {"p90":>7} {"max":>4}')
    for role in sorted(by_role):
        values = by_role[role]
        print(f'{role:20} {len(values):>4} {pct(values, 50):>7.1f} {pct(values, 75):>7.1f} '
              f'{pct(values, 90):>7.1f} {max(values):>4}')

    totals = [sum(turns for _, turns in per) for _, _, per in population]
    print(f'{"item total":20} {len(totals):>4} {pct(totals, 50):>7.1f} {pct(totals, 75):>7.1f} '
          f'{pct(totals, 90):>7.1f} {max(totals):>4}')


if __name__ == '__main__':
    main(sys.argv[1:])
