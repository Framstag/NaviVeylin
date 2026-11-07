# Design — the spend report names every role the engine starts

## The root cause

`tools/loop-spend.py:136-141` decides a session's role from the words of its first user message:

```python
text = (s['first_user'] or '')[:4000].lower()
s['role'] = 'reviewer' if 'reviewer' in text else 'worker'
```

The comment above it states the assumption it rests on — "a reviewer is told so in its prompt; a worker is handed a
path" — and the archive contradicts it: the run hands **every** child its prompt path (`run_child`,
`tools/loop/run.sh:1476`: `$child_cmd "$1"`), and of the project's 730 sessions every one begins with a path to a
`.md` file (`.pi/issue-loop/items/822/scratch/role-census.py`, machine-local, `evidence/role-census.log`:
`sessions=730 prompt-path=730 not-a-path=0`), so the
two words that survive the lowercase are `worker` (from `worker-prompt.md`,
`worker-prompt-round2.md`, `worker-gate-prompt.md`, `worker-tail-prompt.md`) and `reviewer` (from
`reviewer-prompt-N.md` and `reviewer-pre-gate-prompt.md`). The split is therefore a reading of the file name that
stops at its first word.

The engine's own rule is one function away and reads the same string: `child_role`, `tools/loop/run.sh:1382-1392`,
six arms in a deliberate order (`*-tail*` and `*pre-gate*` before the `gate*` and plain shapes they also match,
`*round2*` before the worker shape it is rendered from). The report cannot call it — one is Python, the other a shell
function inside a 3 044-line engine — so the fix is to repeat the arms, in the engine's order, and to say beside them
that they are the engine's.

## The single fix

1. `prompt_role(first_user)` — the first line of the session's first user message; when that line is a path to a
   `.md` file, `child_role`'s arms in `child_role`'s order over its basename, with the engine's own fallback (the name
   without its extension) for a name no arm matches; when it is not a path, the words' reading the tool already had.
2. `read_sessions` assigns `s['role'] = prompt_role(s['first_user'])` where the two-value line was, and the comment
   that justified the two values moves into the function, where the assumption now lives beside its refutation.
3. `report()`'s per-role table widens its role column from 10 to 17 — the width of `reviewer-pre-gate` — in the header
   and in the rows.

Nothing else in the tool changes: `byRole` is keyed by whatever `prompt_role` returns, so the JSON needs no edit, and
the table, the `LOOP-SPEND-*` lines and every other figure are computed from the same aggregates as before.

## Blast radius

- `tools/loop-spend.py` — one new function, one assignment, two column widths.
- `tools/loop-spend-selftest.sh` — the fixture gains two item directories: `$WORK/roles/items/200` holding one
  session per role the engine starts, each with one turn of its own `cacheRead` (1 000, 2 000, 4 000, 8 000, 16 000,
  32 000), so every role's total is arithmetic rather than a recording, and `$WORK/prompts/items/300` holding the
  three first user messages that name no role path (prompt text carrying a reviewer's word, prompt text carrying a
  worker's, and a `.md` name no arm matches); the 23 existing cases take the suite's case ids and keep their assertions
  unchanged; `--case <id>` runs one of the 27 and refuses an id no case carries; the summary line takes the shape
  `gate.sh:441-446` parses.
- `.pi/skills/loop-spend/SKILL.md` — the role paragraph, and the case count on the `bash tools/loop-spend-selftest.sh`
  line.
- `guidelines/Build.md` §1 — the `loop-spend` row's "spend per role" names what the split is now read from.
- Not touched, by intent: `tools/loop/state.sh:841-847` (the item's recorded `roles`, three names, written by the run
  and read by `report.sh`) and `openspec/specs/agent-issue-loop/spec.md:3232-3248` (its requirement), which is a
  second reading of the same fact with its own cases and its own recorded vocabulary. This item's acceptance names the
  spend report's per-role table, its JSON and the run's `agent-roles` header; moving the recorded vocabulary would
  rewrite a state field every closed run already carries. It is reported as a note on the item rather than repaired
  here.

## Rollback

Revert the change: `git revert` of the single commit restores the two-value split, and no file outside the set above
was edited, so no configuration, no state field and no case in another suite changes meaning. The one asymmetry a
revert leaves is the summary line: the suite would print its old shape again and its gate tally would read
`tests="0"` — which is how it read before this change.

## The alternatives the evidence eliminated

- **The role from the prompt's words, with five keyword arms.** `reviewer-pre-gate` and `reviewer` both contain
  `reviewer`; `worker-prompt-round2.md` contains neither `gate` nor `tail`. The archive's own prompt names are what
  `child_role` was written to read, and a keyword rule over the prompt body cannot tell the pre-gate round from the
  review round — eliminated by the two roles the item names first.
- **A role field written into the stream by the run** (`run_child` knows the role before it forks the child). It is the
  better long-run shape and it is what `state.sh` already does for the item record, but it changes the *stream*, which
  is the record every closed item already carries: sessions started by a run before the field existed would have to be
  read by the old rule anyway, so the fix would still owe the arms above. Eliminated as a second capability, not as a
  wrong one.
- **Reading `LOOP_AGENT_CMD_<ROLE>` from `tools/loop/loop.conf` instead of the stream.** A role's *command* is what the
  item prices, but the report charges turns and tokens, and a command may be recorded after the run that spent them
  (and a class-qualified key differs per item). The stream is the only record of what a child actually ran on.
- **A third table row kind (`gate`, `tail`, `pre-gate` as three columns).** The item asks for one row per role, and the
  JSON is read by `docs/2026-11-07-reviewer-model-analysis.md`'s follow-up measurement, which is per role name.

## Open decisions

none

The item's one fork — which roles the split carries — is settled by the engine's own `child_role` (six arms), and
the item's "five roles" is that rule as it stood when the item was filed; #821 added `worker-rework` as a role of its
own after it, so the report carries six and the delta says six.
