# The spend report names every role the engine starts

## Why

`tools/loop-spend.py` reads a session's role from the words of its first user message and recognises two values:

```python
# tools/loop-spend.py:136-141, on the base 580c2106
    # A reviewer is told so in its prompt; a worker is handed a path. The role decides nothing but the split in
    # the report, so an unrecognised prompt is a worker rather than a third category nobody can act on.
    for s in sessions:
        text = (s['first_user'] or '')[:4000].lower()
        s['role'] = 'reviewer' if 'reviewer' in text else 'worker'
```

The engine starts six roles, read the way `child_role` reads them — from the prompt file's name, the only thing in a
stream that names the phase (`tools/loop/run.sh:1382-1392`): `worker`, `worker-rework`, `worker-gate`, `worker-tail`,
`reviewer`, `reviewer-pre-gate`. A stream's first user message **is** that path: over item #821's own stream the five
sessions begin `/…/items/821/worker-gate-prompt.md`, `…/worker-tail-prompt.md`, `…/reviewer-prompt-1.md`,
`…/worker-prompt-round2.md`, `…/reviewer-prompt-2.md`. So the two-value split is wrong in both directions:
`reviewer-pre-gate` contains the word `reviewer` and is charged to the reviewer, while the gate, the tail and the
rework round contain it not and are charged to the worker — four of the engine's six roles on somebody else's account.

**The measurement the item asks for cannot be taken on that split** (`python3 tools/loop-spend.py --json --items
.pi/issue-loop/items`, 2026-11-07, the project's archive, kept as `evidence/spend-before.json`): **273 items, 730
sessions, 41 304 turns** (the JSON pair's own figure; `evidence/per-role-table-after.txt`, taken two minutes later over
the same live archive, reads 41 305 — one child turn landed between the two), charged to `reviewer` 286 sessions /
10 263 turns / 550.0 Mtok and to `worker` 444 sessions
/ 31 041 turns / 2 481.6 Mtok. The same command over the same archive after this change (`evidence/spend-after.json`)
charges those 730 sessions to `worker` 256 · `worker-rework` 110 · `worker-tail` 46 · `worker-gate` 32 · `reviewer`
264 · `reviewer-pre-gate` 22 and prints the same total to the token — 3 056 665 795, the `tokens.total` both JSON files
carry — so a per-role model decision
reads six rows instead of two, and no role is priced on another's turns.

**Root cause, in one sentence:** the spend report derives a session's role from the words of its prompt instead of
from the prompt file's name, so the six roles the engine resolves through `child_role` collapse into the two words
that happen to appear in a prompt body.

**The item's third acceptance clause already holds in this tree**, and this change verifies it rather than rebuilding
it: the run's header carries `agent-roles=` computed from each role's resolved command (`tools/loop/run.sh:268-273`),
with `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo` asserting `agent-roles=reviewer,reviewer-pre-gate`
(`tools/loop-cases.sh:1763`) and `agent-roles=-` for a project that records no per-role key (`tools/loop-cases.sh:1726`).

**Why it is a defect rather than a preference:** the case `loop-spend-selftest.sh` "reviewer session recognised" pins
the two-role split as it stands, so the tool is *held* at two roles by its own suite, and the sibling reading of the
same fact already disagrees with it in a third direction: `tools/loop/state.sh:841-847` splits the item's recorded
`roles` into `worker` / `reviewer` / `tail`, which is what `openspec/specs/agent-issue-loop/spec.md:3235` states
("the tokens by the role that spent them (worker, reviewer, tail)"). Three spellings of one fact, none of them the
engine's six names.

**The red case, before any production code:** the role fixtures were written first and run against the base's
`tools/loop-spend.py` — 27 cases, 4 failed, 23 passed, and every failure is the split this change replaces
(`spend#eachRoleTheEngineStartsIsChargedToItsOwnName` read `reviewer=2/2/48000,worker=4/4/15000` for the six sessions;
`spend#theRoundBeforeTheGateIsChargedApart` read `0/0 2/48000 4/15000`; `spend#thePerRoleTableNamesWhatTheJsonCarries`
read `reviewer-worker` twice; `spend#anUnmatchedPromptKeepsItsOwnReading` read `reviewer=1/1/64000,worker=2/2/160000`).
The log and its transcription are `evidence/red-on-HEAD.log` and `evidence/red-on-HEAD.xml`, taken 2026-11-07T18:54:56Z
with the base's own tool restored for the run (`git show 580c2106:tools/loop-spend.py`) and this change's file written
back after it, byte-identical.

Owning capability: `agent-issue-loop`. Owning guideline section: `guidelines/Build.md` §1 "Skills" — its `loop-spend`
row is the row by which a reader is sent to this tool ("spend per role and per day"), so it is the row that owes the
name of the split.

surface: 4 cases added, 23 renamed to the suite's case ids, 1 production file, 1 suite summary realigned, 2 documents —
`tools/look.sh --files "byRole|by_role|'reviewer' in text|child_role"` → **75 matches in 24 files** (the count this
change leaves behind, its own change directory included), the live ones `tools/loop-spend.py` 8,
`tools/loop-spend-selftest.sh` 8, `tools/loop/run.sh` 5, `tools/loop/loop.conf` 2, `tools/loop-cases.sh` 1,
`guidelines/Build.md` 1; and `tools/look.sh --files 'worker-tail|reviewer-pre-gate'` → **266 matches in 111 files**,
the live split beside this one at `tools/loop/state.sh` 3.

## What Changes

1. **The role is the prompt's name, read the engine's way.** `prompt_role(first_user)` takes the first line of the
   session's first user message, and when that is a prompt path, applies `child_role`'s arms in the engine's order —
   `*-tail*` → `worker-tail`, `*pre-gate*` → `reviewer-pre-gate`, `*gate*` → `worker-gate`, `*round2*` →
   `worker-rework`, `reviewer-*` → `reviewer`, `worker-*` → `worker`, else the name without `.md` — so the report and
   the run cannot disagree about which role paid for a turn. A first user message that is not a prompt path (a
   fixture's prompt text, another harness) keeps the words' reading the tool had.
2. **The per-role table widens to the longest name.** `reviewer-pre-gate` is 17 characters against the table's 10,
   and a table that clips a role name into its neighbour's column reports a role as a suffix.
3. **The suite gains a case per role and an id per case** (`--case <id>`, the shape every other suite in this tree
   uses and the shape a spec's `- **Case**:` line names), so the red case is one runnable case and not the whole
   suite.
4. **The suite's summary line takes the shape the gate's tally reads.** `gate.sh:441-446` derives a step's
   `tests=`/`failures=` from `<name>: all N cases passed` or `<name>: M of N cases failed`; this suite printed
   `loop-spend-selftest: 23 cases passed`, so its tally recorded no case at all. A change that adds four cases to a
   suite tallied `tests="0"` would leave evidence nobody can check.

## Impact

- Production: `tools/loop-spend.py` only (one role function, one assignment, one column width).
- Cases: `tools/loop-spend-selftest.sh` — 4 cases added, 23 given case ids, `--case` support, the summary line.
- Documents: `.pi/skills/loop-spend/SKILL.md` (the role paragraph and the case count) and `guidelines/Build.md` §1's
  `loop-spend` row.
- No engine, no configuration key, no compiled file: the run's own header, its per-role commands and the report's
  three-role trend are unchanged.
- Not in this change, and named as a note rather than repaired here: `tools/loop/state.sh:841-847` still records the
  item's `roles` as `worker` / `reviewer` / `tail` at write time, which is `agent-issue-loop`'s *other* requirement
  (`spec.md:3232`, "The report says which phase and which role spent the run's clock") and a change of its own — the
  item's acceptance names the spend report and the run's header, and rewriting the recorded vocabulary would move
  `report#theSpendIsSplitByPhaseRoleAndGate` and every state written by a run that has already closed.
