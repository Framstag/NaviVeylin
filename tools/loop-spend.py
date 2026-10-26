#!/usr/bin/env python3
"""loop-spend.py — what a loop run's children spent, and on what.

Why this exists: a run's report (`tools/loop/report.sh`) prints an item's tokens and its largest context, and
that is where the measurement stopped. It cannot answer *why* an item cost 16 M tokens, whether the price is
rising, or which lever would move it — the answer is in the children's own event streams
(`.pi/issue-loop/items/<issue>/events.jsonl`, kept whole by `tools/pi-child-events.sh` for exactly this
reason), and reading 1.8 GB of JSONL by hand is not a thing a person does twice.

What it measures, and the model behind it. A child's reported spend is almost entirely **cacheRead** — the
whole conversation re-read on every turn — so

    tokens ≈ T × B + Σ_k (T − 1 − k) × Δ_k

with `T` the turns of the session, `B` the fixed prompt baseline (system prompt + the phase prompt, read on
every turn), and `Δ_k` what turn `k` added to the context (its tool results and the assistant's own output).
Two consequences the report makes explicit: the baseline is charged once per turn, and content injected
*early* is re-read many more times than content injected late — so a 7 k-token file read at turn 5 of 200
costs about 1.3 M tokens, and the same file read at turn 190 costs 70 k.

The baseline is not estimated: it is the first turn's `input + cacheRead`, which by construction is the prompt
before anything was appended. Chars→tokens is calibrated on that same number (the prompt's own character
count against its measured token count), not guessed: `CHARS_PER_TOKEN`.

Usage:
  loop-spend.py [--items DIR] [--top N] [--since YYYY-MM-DD] [--json] [--quiet]

  --items DIR     the loop's item directory (default: `.pi/issue-loop/items` beside the project root)
  --top N         rows in the per-item table and the "heaviest" lists (default 20; 0 = all)
  --since DATE    only items whose first turn is on or after this UTC date
  --json          one JSON object on stdout instead of the tables
  --quiet         only the `LOOP-SPEND-*` lines, no tables (what a script or a prompt quotes)

Exit: 0 measured, 2 the item directory is missing or holds no event stream.

Reads text files only: no build, no device, no network, no token.
"""

import argparse
import datetime
import glob
import json
import os
import re
import statistics
import sys
from collections import Counter, defaultdict

# Calibrated, not assumed: the prompt baseline measures ~17.3 k tokens for ~48.6 k characters of system
# prompt plus phase prompt (median over the archive). Only the *attribution* leans on it — every token field a
# turn reports is exact, and the `LOOP-SPEND-*` lines never mix the two.
CHARS_PER_TOKEN = 2.8

READ_TOOLS = ('ctx_read', 'read')
GATE_RE = re.compile(r'gate\.sh|gradlew ')

SECTION_RULE = '-' * 100


# ─────────────────────────────────────────────────────────────── reading the streams

def result_chars(content):
    """Characters of a tool result's content, whatever shape the harness gave it."""
    if isinstance(content, list):
        return sum(len(x.get('text') or '') for x in content if isinstance(x, dict))
    return len(str(content or ''))


def read_sessions(path):
    """The sessions of one item's event stream, each a list of turns.

    A stream holds several sessions: the worker, then one per review round, then a worker round again — which
    is why a per-item number is a sum over sessions and not one conversation. Tool results are joined to their
    call by id, falling back to the call the turn reported (a result without an id still has its tool name).
    """
    sessions = []
    current = None
    with open(path, 'r', errors='replace') as fh:
        for line in fh:
            # The cheap filter names the three event kinds this reader wants, and then `type` decides — never
            # the spacing of the JSON: a stream written with `json.dumps` defaults (a fixture, another
            # harness) carries `"type": `, and a filter that assumed the compact form read such a stream as
            # empty and reported a calm zero (caught by the case `a repeated read is a duplicate`).
            if 'turn_end' not in line and 'message_start' not in line and 'session' not in line:
                continue
            try:
                event = json.loads(line)
            except ValueError:
                continue
            kind = event.get('type')
            if kind == 'session':
                current = dict(turns=[], first_user='', role='worker')
                sessions.append(current)
                continue
            if current is None:
                continue
            if kind == 'message_start':
                # The phase's role is read from its *prompt*: a worker is started with a prompt file path, a
                # reviewer with prompt text that names the job. Nothing in the stream carries the phase name.
                message = event.get('message') or {}
                if not current['first_user'] and message.get('role') == 'user':
                    content = message.get('content')
                    if isinstance(content, list):
                        current['first_user'] = ''.join(
                            x.get('text') or '' for x in content if isinstance(x, dict))
                    else:
                        current['first_user'] = str(content or '')
                continue
            if kind != 'turn_end':
                continue
            message = event.get('message') or {}
            usage = message.get('usage') or {}
            calls = {}
            turn_calls = []
            for block in (message.get('content') or []):
                if block.get('type') == 'toolCall':
                    name = block.get('name') or '?'
                    args = block.get('arguments') or {}
                    calls[block.get('id')] = (name, args)
                    turn_calls.append((name, args))
            results = []
            for tr in (event.get('toolResults') or []):
                name, args = calls.get(tr.get('toolCallId')) or (tr.get('toolName') or '?', {})
                results.append((name, args, result_chars(tr.get('content'))))
            current['turns'].append(dict(
                inp=usage.get('input') or 0,
                out=usage.get('output') or 0,
                cr=usage.get('cacheRead') or 0,
                cw=usage.get('cacheWrite') or 0,
                cost=(usage.get('cost') or {}).get('total') or 0,
                dur=message.get('durationMs') or 0,
                ts=message.get('timestamp'),
                calls=turn_calls,
                results=results,
            ))
    # A reviewer is told so in its prompt; a worker is handed a path. The role decides nothing but the split in
    # the report, so an unrecognised prompt is a worker rather than a third category nobody can act on.
    for s in sessions:
        text = (s['first_user'] or '')[:4000].lower()
        s['role'] = 'reviewer' if 'reviewer' in text else 'worker'
        s['turns'] = [t for t in s['turns'] if t['inp'] or t['cr'] or t['out']]
    return [s for s in sessions if s['turns']]


def read_items(items_dir, since=None):
    items = []
    for path in sorted(glob.glob(os.path.join(items_dir, '*', 'events.jsonl'))):
        issue = os.path.basename(os.path.dirname(path))
        sessions = read_sessions(path)
        if not sessions:
            continue
        first = next((t['ts'] for s in sessions for t in s['turns'] if t['ts']), None)
        day = ''
        if isinstance(first, (int, float)):
            day = datetime.datetime.fromtimestamp(first / 1000, datetime.timezone.utc).strftime('%Y-%m-%d')
        elif isinstance(first, str):
            day = first[:10]
        if since and day and day < since:
            continue
        items.append(dict(issue=issue, day=day, sessions=sessions, bytes=os.path.getsize(path)))
    return items


# ─────────────────────────────────────────────────────────────── measuring

def measure(items):
    """Every aggregate the report prints, computed once."""
    m = dict(
        items=len(items),
        sessions=0, turns=0, inp=0, out=0, cr=0, cw=0, cost=0.0,
        baseline_part=0.0, tool_part=0.0, out_part=0.0,
        injected_chars=0, max_ctx=0, wall_ms=0,
        calls=Counter(), tool_chars=defaultdict(int), tool_calls=defaultdict(int),
        reads=Counter(), dup_reads=0, read_calls=0,
        by_day=defaultdict(lambda: dict(items=0, turns=0, cr=0, tok=0, max_ctx=0, reads=0, dup=0)),
        by_role=defaultdict(lambda: dict(sessions=0, turns=0, tok=0)),
        by_day_role=defaultdict(lambda: defaultdict(int)),
        per_item=[], gates=[], biggest=[],
        baselines=[], cap_saved={}, turn_saved={},
        session_lengths=[],
    )
    turn_growth_k = []          # per session: list of (removed-able chars, turns-left) for the caps
    for item in items:
        it = dict(issue=item['issue'], day=item['day'], turns=0, tok=0, out=0,
                  max_ctx=0, reads=0, dup=0, injected=0)
        reads_here = Counter()
        for s in item['sessions']:
            m['sessions'] += 1
            T = len(s['turns'])
            m['session_lengths'].append(T)
            base = s['turns'][0]['inp'] + s['turns'][0]['cr']
            m['baselines'].append(base)
            role = s['role']
            r = m['by_role'][role]
            r['sessions'] += 1
            res_tokens = out_tokens = 0.0
            for k, t in enumerate(s['turns']):
                ctx = t['inp'] + t['cr']
                m['turns'] += 1
                m['inp'] += t['inp']
                m['out'] += t['out']
                m['cr'] += t['cr']
                m['cw'] += t['cw']
                m['wall_ms'] += t['dur']
                m['cost'] += t['cost']
                m['max_ctx'] = max(m['max_ctx'], ctx)
                it['turns'] += 1
                it['tok'] += ctx
                it['out'] += t['out']
                it['max_ctx'] = max(it['max_ctx'], ctx)
                r['turns'] += 1
                r['tok'] += ctx
                d = m['by_day'][item['day'] or '?']
                d['items'] = d['items'] or 0
                d['turns'] += 1
                d['cr'] += t['cr']
                d['tok'] += ctx
                d['max_ctx'] = max(d['max_ctx'], ctx)
                m['by_day_role'][item['day'] or '?'][role] += ctx
                m['baseline_part'] += base
                # Attribute the growth of the context to tool results vs the assistant's own output, by the
                # ratio the two hold in what has been appended so far.
                denom = res_tokens + out_tokens
                if denom > 0:
                    m['tool_part'] += (ctx - base) * res_tokens / denom
                    m['out_part'] += (ctx - base) * out_tokens / denom
                for name, args, size in t['results']:
                    m['tool_chars'][name] += size
                    m['tool_calls'][name] += 1
                    m['injected_chars'] += size
                    it['injected'] += size
                    m['biggest'].append((size, name, item['issue']))
                    res_tokens += size / CHARS_PER_TOKEN
                    if name in READ_TOOLS:
                        p = str(args.get('path') or args.get('file_path') or '')
                        if p:
                            m['reads'][p] += 1
                            reads_here[p] += 1
                            m['read_calls'] += 1
                            it['reads'] += 1
                for name, args in t['calls']:
                    m['calls'][name] += 1
                out_tokens += t['out']
                turn_growth_k.append((k, T, sum(sz for _, _, sz in t['results'])))
            # the gate split: the first turn of this session that runs the gate or a suite
            first_gate = next((k for k, t in enumerate(s['turns'])
                               if any(GATE_RE.search(str(a.get('command') or ''))
                                      for _, a in t['calls'])), None)
            ctxs = [t['inp'] + t['cr'] for t in s['turns']]
            m['gates'].append((first_gate, ctxs))
        extra = sum(v - 1 for v in reads_here.values() if v > 1)
        m['dup_reads'] += extra
        it['dup'] = extra
        d = m['by_day'][item['day'] or '?']
        d['items'] += 1
        d['reads'] += it['reads']
        d['dup'] += extra
        m['per_item'].append(it)
    # the caps, from the per-turn injection table
    for cap in (4000, 8000, 16000):
        saved = 0.0
        for k, T, size in turn_growth_k:
            removed = max(0, size - cap) / CHARS_PER_TOKEN
            saved += removed * (T - 1 - k)
        m['cap_saved'][cap] = saved
    return m


def total(m):
    return m['inp'] + m['cr'] + m['out']


def human(n):
    n = float(n)
    for unit in ('', 'k', 'M', 'B'):
        if abs(n) < 1000:
            return f'{n:.0f}{unit}' if not unit else f'{n:.1f}{unit}'
        n /= 1000.0
    return f'{n:.1f}T'


# ─────────────────────────────────────────────────────────────── the report

def display_path(path, root):
    """A read path without the absolute noise: the loop worktree is marked, the project root dropped."""
    if not root:
        return path
    path = path.replace(os.path.join(root, '.pi', 'worktrees', 'loop') + os.sep, 'worktree/…/')
    return path.replace(root + os.sep, '')


def report(m, top, quiet):
    out = []
    add = out.append
    grand = total(m)
    base_median = int(statistics.median(m['baselines'])) if m['baselines'] else 0
    growth = grand - m['baseline_part']
    tool_share = m['tool_part'] / growth if growth else 0
    injected_tokens = m['injected_chars'] / CHARS_PER_TOKEN
    amplification = (m['tool_part'] / injected_tokens) if injected_tokens else 0
    # The split is measured over the sessions that ran a gate, and says so: a session that never gated has no
    # position to split at, and folding its turns into "before" would make the two halves disagree.
    gated = [(i, c) for i, c in m['gates'] if i is not None]
    after_gate = sum(sum(c[i:]) for i, c in gated)
    pre_gate = sum(sum(c[:i]) for i, c in gated)
    pre_turns = sum(i for i, _ in gated)
    post_turns = sum(len(c) - i for i, c in gated)

    if not quiet:
        add('loop spend — what the loop\'s children were charged, and on what')
        add(SECTION_RULE)
        add(f'{m["items"]} items, {m["sessions"]} child sessions, {m["turns"]} turns '
            f'({m["wall_ms"]/3.6e6:.1f} h of model time)')
        add('')
        add(f'  cacheRead (context re-read)  {human(m["cr"]):>9}   {100*m["cr"]/grand:>4.0f}%')
        add(f'  input (uncached)             {human(m["inp"]):>9}   {100*m["inp"]/grand:>4.0f}%')
        add(f'  output (thinking + text)     {human(m["out"]):>9}   {100*m["out"]/grand:>4.0f}%')
        add(f'  total                        {human(grand):>9}')
        add('')
        add('Prompt baseline (first turn of a session = system prompt + phase prompt), median: '
            f'{base_median} tok/turn')
        add(f'  charged once per turn  {human(m["baseline_part"]):>8}  {100*m["baseline_part"]/grand:>4.0f}% of all tokens')
        add(f'  tool results           {human(m["tool_part"]):>8}  {100*m["tool_part"]/grand:>4.0f}%   '
            f'{human(m["injected_chars"])} chars injected once -> {human(injected_tokens)} tok, '
            f're-read x{amplification:.0f}')
        add(f'  model output           {human(m["out_part"]):>8}  {100*m["out_part"]/grand:>4.0f}%')
        add('')
        add('Reads: '
            f'{m["read_calls"]} file reads, {m["dup_reads"]} of them a second read of a path the session '
            f'already had ({100*m["dup_reads"]/max(m["read_calls"],1):.0f}%)')
        add(f'Spend by position, in the {len(gated)} sessions that ran a gate: '
            f'{human(after_gate)} ({100*after_gate/max(after_gate+pre_gate,1):.0f}% of them) after the first '
            f'gate/suite call — {post_turns} turns at {after_gate/max(post_turns,1)/1000:.0f}k/turn, against '
            f'{pre_turns} turns at {pre_gate/max(pre_turns,1)/1000:.0f}k/turn before it')
        add('')
        add('The baseline and the two growth parts are a model, not a ledger (chars->tokens is calibrated at '
            f'{CHARS_PER_TOKEN}/tok):')
        add('  tokens ~ T x B + sum_k (T-1-k) x delta_k — early content is re-read far more often than late')

    if not quiet:
        add('')
    add(f'LOOP-SPEND-ITEMS n={m["items"]}')
    add(f'LOOP-SPEND-SESSIONS n={m["sessions"]}')
    add(f'LOOP-SPEND-TURNS n={m["turns"]}')
    add(f'LOOP-SPEND-TOKENS input={m["inp"]} cacheRead={m["cr"]} output={m["out"]} total={grand}')
    add(f'LOOP-SPEND-COST total={"-" if m["cost"] == 0 else format(m["cost"], ".4f")}')
    add(f'LOOP-SPEND-BASELINE tokens={base_median} charged={int(m["baseline_part"])}')
    add(f'LOOP-SPEND-READS calls={m["read_calls"]} duplicate={m["dup_reads"]}')
    add(f'LOOP-SPEND-GATE after={int(after_gate)}')
    for cap, saved in sorted(m['cap_saved'].items()):
        add(f'LOOP-SPEND-CAP{cap//1000}K saved={int(saved)}')
    if quiet:
        return out

    add('')
    add('Per day (averages per item; dup% is the share of that day\'s file reads that repeated a path)')
    add(f'  {"day":10} {"items":>5} {"turns":>6} {"ctx/turn":>8} {"Mtok/item":>9} {"max ctx":>8} {"dup":>4}')
    for day in sorted(m['by_day']):
        d = m['by_day'][day]
        add(f'  {day:10} {d["items"]:>5} {d["turns"]/max(d["items"],1):>6.0f} '
            f'{d["cr"]/max(d["turns"],1)/1000:>7.0f}k {d["tok"]/max(d["items"],1)/1e6:>9.2f} '
            f'{human(d["max_ctx"]):>8} {100*d["dup"]/max(d["reads"],1):>3.0f}%')

    add('')
    add('Per session role')
    add(f'  {"role":10} {"sessions":>8} {"turns":>7} {"Mtok":>8} {"Mtok/session":>13} {"ctx/turn":>9}')
    for role in sorted(m['by_role'], key=lambda r: -m['by_role'][r]['tok']):
        r = m['by_role'][role]
        add(f'  {role:10} {r["sessions"]:>8} {r["turns"]:>7} {r["tok"]/1e6:>8.1f} '
            f'{r["tok"]/max(r["sessions"],1)/1e6:>13.2f} {r["tok"]/max(r["turns"],1)/1000:>8.0f}k')

    add('')
    add('Cost per item against its turn count (the multiplier, measured)')
    buckets = [(0, 20), (20, 50), (50, 100), (100, 150), (150, 250), (250, None)]
    for lo, hi in buckets:
        sel = [i for i in m['per_item'] if lo <= i['turns'] and (hi is None or i['turns'] < hi)]
        if not sel:
            continue
        label = f'{lo}-{hi}' if hi is not None else f'{lo}+'
        add(f'  turns {label:<8} items={len(sel):>3} '
            f'avg turns={sum(i["turns"] for i in sel)/len(sel):>6.0f} '
            f'avg Mtok/item={sum(i["tok"] for i in sel)/len(sel)/1e6:>7.2f} '
            f'avg ctx/turn={sum(i["tok"] for i in sel)/max(sum(i["turns"] for i in sel),1)/1000:>5.0f}k')

    add('')
    add('What the levers would save, on this archive (model, not a promise)')
    levers = [(f'tool result capped at {cap//1000}k chars', saved) for cap, saved in sorted(m['cap_saved'].items())]
    for frac in (0.15, 0.30, 0.50):
        saved = sum(sum(c[int(len(c) * (1 - frac)):]) for _, c in m['gates'])
        levers.append((f'{int(100*frac)}% fewer turns', saved))
    levers.append(('baseline trimmed by 30%', 0.30 * m['baseline_part']))
    for label, saved in levers:
        add(f'  {label:<34} -{human(saved):>7} ({100*saved/grand:>3.0f}%)')

    add('')
    add('Tool traffic')
    add(f'  {"tool":16} {"calls":>7} {"result chars":>13} {"per call":>9}')
    for name, cnt in m['calls'].most_common():
        chars = m['tool_chars'].get(name, 0)
        if not chars:
            continue
        add(f'  {name:16} {cnt:>7} {human(chars):>13} {human(chars/cnt):>9}')

    add('')
    add(f'The {min(top or 25, 25)} most-read paths (a path read by 40 items is a prompt or a doc, not an incident)')
    for path, cnt in m['reads'].most_common(min(top or 25, 25)):
        add(f'  {cnt:>4}x  {display_path(path, m["root"])}')

    add('')
    add(f'The {min(top or 10, 10)} heaviest single tool results (each is re-read by every later turn)')
    for size, name, issue in sorted(m['biggest'], reverse=True)[:min(top or 10, 10)]:
        add(f'  {human(size):>8} {name:14} #{issue}')

    add('')
    rows = sorted(m['per_item'], key=lambda i: -i['tok'])
    rows = rows[:top] if top else rows
    add(f'Per item, most expensive first ({"all" if not top else f"top {top}"}; res/item is the characters the '
        f'item\'s tool results injected, which the turns after each of them re-read)')
    add(f'  {"issue":>5} {"day":10} {"turns":>5} {"Mtok":>6} {"ctx/turn":>8} {"max ctx":>8} '
        f'{"reads":>5} {"dup":>4} {"res/item":>8}')
    for i in rows:
        add(f'  {i["issue"]:>5} {i["day"]:10} {i["turns"]:>5} {i["tok"]/1e6:>6.1f} '
            f'{i["tok"]/max(i["turns"],1)/1000:>7.0f}k {human(i["max_ctx"]):>8} {i["reads"]:>5} '
            f'{100*i["dup"]/max(i["reads"],1):>3.0f}% {human(i["injected"]):>8}')
    return out


def as_json(m):
    grand = total(m)
    return dict(
        items=m['items'], sessions=m['sessions'], turns=m['turns'],
        tokens=dict(input=m['inp'], cacheRead=m['cr'], output=m['out'], total=grand),
        baseline=dict(median=int(statistics.median(m['baselines'])) if m['baselines'] else 0,
                      charged=int(m['baseline_part'])),
        attribution=dict(baseline=int(m['baseline_part']), toolResults=int(m['tool_part']),
                         modelOutput=int(m['out_part']),
                         toolResultChars=m['injected_chars']),
        reads=dict(calls=m['read_calls'], duplicate=m['dup_reads']),
        afterFirstGate=int(sum(sum(c[i:]) for i, c in m['gates'] if i is not None)),
        cost=round(m['cost'], 4),
        capSaving={str(k): int(v) for k, v in m['cap_saved'].items()},
        maxContext=m['max_ctx'],
        modelMs=m['wall_ms'],
        byDay={d: dict(v) for d, v in m['by_day'].items()},
        byRole={r: dict(v) for r, v in m['by_role'].items()},
        tools=dict(m['calls']),
        toolResultChars=dict(m['tool_chars']),
        topReads=m['reads'].most_common(25),
        heaviest=[[s, n, i] for s, n, i in sorted(m['biggest'], reverse=True)[:25]],
        perItem=sorted(m['per_item'], key=lambda i: -i['tok']),
    )


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    ap = argparse.ArgumentParser(add_help=True, description='Measure what a loop run spent, and on what.')
    ap.add_argument('--items', default=os.path.join(root, '.pi', 'issue-loop', 'items'))
    ap.add_argument('--top', type=int, default=20)
    ap.add_argument('--since', default=None)
    ap.add_argument('--json', action='store_true')
    ap.add_argument('--quiet', action='store_true')
    args = ap.parse_args(argv)

    if not os.path.isdir(args.items):
        print(f'loop-spend: no item directory {args.items}', file=sys.stderr)
        return 2
    items = read_items(args.items, args.since)
    if not items:
        print(f'loop-spend: no event stream under {args.items}', file=sys.stderr)
        return 2
    m = measure(items)
    # The project root is derived from the item directory — `<root>/.pi/issue-loop/items`, or any `<dir>/items`
    # for a fixture — and used only to shorten a path in the report; the numbers never depend on it.
    items_abs = os.path.abspath(args.items)
    if os.path.basename(os.path.dirname(items_abs)) == 'issue-loop':
        m['root'] = os.path.dirname(os.path.dirname(os.path.dirname(items_abs)))
    else:
        m['root'] = os.path.dirname(os.path.dirname(items_abs))
    if args.json:
        print(json.dumps(as_json(m), sort_keys=True, default=int))
    else:
        print('\n'.join(report(m, args.top, args.quiet)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
