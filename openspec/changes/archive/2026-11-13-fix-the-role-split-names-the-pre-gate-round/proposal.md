# The recorded role split names the round before the gate

## Why

`child_usage_json` (`tools/loop/state.sh:992`) keys every session by the prompt file it was handed, and the role map
that folds those sessions into `metrics.roles` matches the word `reviewer` alone
(`tools/loop/state.sh:1152`, and again in the model pass at `tools/loop/state.sh:1166`):

```jq
| if test("worker-tail") then "tail"
  elif test("reviewer") then "reviewer"
  else "worker" end
```

`reviewer-pre-gate-prompt.md` carries the word `reviewer`, so the round that reads the diff **before** the gate is
charged to the reviewer, and the state's `metrics.roles` cannot answer the only question an item's reviewer figure
raises — how much of it the pre-gate review took, and how much the round did — which is exactly the split that says
whether the pre-gate phase earned its price.

**The measurement:** run `2026-11-11T20:36:10Z`, item #859 — `roles: {worker: 4132852, reviewer: 5576103, tail:
131490}`, so review is 56.7 % of the run's 9 840 445 tokens, more than the worker that wrote the change (42.0 %), with
two reviewers inside the one figure. `tools/loop-spend.py`'s own `byRole` already separates them (`reviewer-pre-gate`:
12 sessions, 17 557 461 tokens; `reviewer`: 13 sessions, 71 202 907 tokens, window-wide) — the engine measures the
distinction and the frozen state loses it.

**Root cause, in one sentence:** the state's role map matches the word `reviewer`, which a
`reviewer-pre-gate-prompt.md` session also carries, so the two reviewers a run can start are folded into one bucket at
the moment the item's record is written.

**Why this is not the split the spend report already fixed:** `loop-spend.py` was widened to the engine's six role
names by the archived change `2026-11-07-fix-the-spend-report-names-every-role`, whose own `## Impact` deferred this
one — "`tools/loop/state.sh:841-847` still records the item's `roles` as `worker` / `reviewer` / `tail` at write time,
which is `agent-issue-loop`'s *other* requirement … and a change of its own".

**The red case, before any production code:** `report#thePreGateRoundIsChargedToItsOwnRole`
(`tools/loop/tests/loop-cases.sh`) drives a stream of one worker, one reviewer and one pre-gate round at 1 + 2 + 4 Mtok
and reads the item's record: on the base's `state.sh` it failed
`the round before the gate is a bucket of its own, not part of the reviewer's: expected '4000000', got 'null'` —
`tests=1 failures=1` — taken 2026-11-13T15:13Z, kept as `red-on-HEAD.log` in the run's evidence directory
(`.pi/issue-loop/items/864/`, beside the change root and not inside it), and green once the arm is added
(`green-on-fix.log`, `PASS`, 2026-11-13T15:14:15Z).

Owning capability: `agent-issue-loop`. Owning guideline section: `guidelines/Backlog.recipes.md:277` "What an item
costs, and the bounds on it" — its paragraph on "where the spend went" is the text that names what `metrics.roles`
holds (`guidelines/Backlog.recipes.md:664`), so it is the paragraph that owes the name of the new bucket.

surface: 1 case added, 1 mutation added, 2 role arms in 1 engine file, 1 spec scenario, 2 guideline paragraphs and 1
header comment —
`tools/look.sh 'test\\("reviewer"\\)'` → **7 matches in 4 files**: the two arms this change edits
(`tools/loop/state.sh` 2), one archived probe left as the record of the reading it took, and the four quotations this
change's own `proposal.md` and `design.md` carry; and `tools/look.sh 'metrics\.roles'` → **61 matches in 22 files**,
the live ones `tools/loop/tests/loop-cases.sh` 18, `tools/loop/report.sh` 3 and `guidelines/Backlog.recipes.md` 2.

## What Changes

1. **The role map gains the pre-gate arm, in the order `child_role` applies it.** `test("pre-gate")` → the key
   `reviewer-pre-gate`, read before `test("reviewer")` and after `test("worker-tail")` — the arm order
   `tools/loop/run.sh:1551-1559` already states, so the record and the run cannot disagree about which role a session
   was.
2. **The model pass gains the same arm.** `roleModels` is keyed by the same reading; a pre-gate session's model tally
   left under `reviewer` would be a row the role total no longer holds, and the report's residual row would print a
   reviewer tally the state contradicts.
3. **Both objects declare the key beside the three**, `"reviewer-pre-gate": null`, so a state whose stream holds no
   pre-gate session keeps the shape (a `null` bucket, never a `0`).
4. **The report's code needs no edit:** its role line prints the trio and then every other bucket the record holds
   (`tools/loop/report.sh:478-480`), which is the rule issue #832 added. Its header — which is also what `--help`
   prints — names the fourth bucket, because it is the file's own statement of what the line says.

## Impact

- Production: `tools/loop/state.sh` — one arm in each of two passes, one key in each of two literals, and the comment
  above the field; `tools/loop/report.sh` — one header comment line, no code.
- Cases: `tools/loop/tests/loop-cases.sh` — 1 case added (its own `# files:` declaration), and
  `tools/loop/tests/loop-mutations.sh` — 1 record (`864.1`).
- Documents: `openspec/specs/agent-issue-loop/spec.md` through the change's delta, and
  `guidelines/Backlog.recipes.md:277` ("What an item costs, and the bounds on it") and
  `guidelines/Backlog.recipes.md:1287` ("Leftovers, the report and the queue") — the two paragraphs that state what the
  role split holds.
- No configuration key, no compiled file, no report code: `metrics.roles` gains one bucket and every existing figure —
  `tokens`, `phases`, `roleModels`, `afterGate` and the three roles a stream already named — is byte-identical.
