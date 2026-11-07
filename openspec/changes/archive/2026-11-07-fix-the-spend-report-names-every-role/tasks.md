# Tasks — the spend report names every role the engine starts

## 1. The red case first

- [x] 1.1 Write the fixtures and the cases into `tools/loop-spend-selftest.sh` **before** `tools/loop-spend.py` is
  touched: one item directory `$WORK/roles/items/200` with six sessions — `worker-prompt.md`,
  `worker-prompt-round2.md`, `worker-gate-prompt.md`, `worker-tail-prompt.md`, `reviewer-prompt-1.md`,
  `reviewer-pre-gate-prompt.md` — one turn each, carrying 1 000 / 2 000 / 4 000 / 8 000 / 16 000 / 32 000
  `cacheRead` tokens, and a second `$WORK/prompts/items/300` with the three first user messages that name no role
  path: prompt *text* carrying the reviewer's word, prompt *text* carrying the worker's, and a `.md` name no arm of
  `child_role` matches. The four cases
  are `spend#eachRoleTheEngineStartsIsChargedToItsOwnName`, `spend#theRoundBeforeTheGateIsChargedApart`,
  `spend#thePerRoleTableNamesWhatTheJsonCarries` and `spend#anUnmatchedPromptKeepsItsOwnReading`.
  - verified: red on the base, `bash tools/loop-spend-selftest.sh` → **4 of 27 cases failed**, 23 passed
    (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, 2026-11-07T18:54:56Z, with `tools/loop-spend.py` restored
    to the base by `git show 580c2106:tools/loop-spend.py` for the run and this change's file written back after it,
    byte-identical by `cmp`).
- [x] 1.2 Give every case of the suite its id from the shape a spec's `- **Case**:` line names (`spend#…`, the 23
  existing cases renamed and their assertions untouched), `--case <id>` to run that one, a refusal (exit 2) for an
  id no case carries, and the summary line in the shape `tools/loop/gate.sh:441-446` parses
  (`<name>: all N cases passed` / `<name>: M of N cases failed`).
  - verified: `bash tools/loop-spend-selftest.sh --case 'spend#eachRoleTheEngineStartsIsChargedToItsOwnName'` →
    `all 1 cases passed`; `--case 'spend#noSuchCase'` → `no case carries the id`, exit `2`; `--bogus` → exit `2`.

## 2. The role, read the engine's way

- [x] 2.1 `tools/loop-spend.py`: add `prompt_role(first_user)` carrying `child_role`'s arms in `child_role`'s order
  (`tools/loop/run.sh:1382-1392`), assign it in `read_sessions` where the two-value line stood, and widen
  `report()`'s per-role column from 10 to 17 — the width of `reviewer-pre-gate`.
  - verified: `bash tools/loop-spend-selftest.sh` → **all 27 cases passed** (`evidence/spend-suite-green.log`).

## 3. The measurement the item exists for, taken either side of the edit

- [x] 3.1 `python3 tools/loop-spend.py --json --items .pi/issue-loop/items` before and after, and the per-role table
  after, kept as `evidence/spend-before.json`, `evidence/spend-after.json` and `evidence/per-role-table-after.txt`;
  the "before" half is taken with the base's own tool (`git show 580c2106:tools/loop-spend.py` into
  `.pi/issue-loop/items/822/scratch/loop-spend-at-base.py`, machine-local and so not carried by the change) so both
  halves are one command over one archive.
  - verified: `jq -c '{items,sessions,turns,byRole}'` over the two JSON files — same 273 items / 730 sessions /
    41 304 turns (the JSON pair's figure; the per-role table, taken two minutes later over the live archive, reads
    41 305) and the same 3 056 665 795 tokens, charged to two roles before and six after.

## 4. The documents the rule is read from

- [x] 4.1 `.pi/skills/loop-spend/SKILL.md` — a section stating that the split is `child_role`'s own rule over the
  session's prompt `.md` path, with the archive's before/after numbers, and the `bash tools/loop-spend-selftest.sh` line
  carrying the new case count. `guidelines/Build.md` §1 — the `loop-spend` row says the split is the one the role's
  prompt names.
  - verified: `tools/check-doc-routes.sh` → all 329 section references and all 7 skill references resolve;
    `tools/check-openspec-hygiene.sh` → consistent.

## 5. The change's own records

- [x] 5.1 `proposal.md`, `design.md` and this file carry the Phase A evidence, the surface count, the root cause, the
  alternatives the evidence eliminated and the note this change deliberately does not repair
  (`tools/loop/state.sh:841-847`, the item record's three-name `roles`).
  - verified: `bash tools/check-change-readiness.sh openspec/changes/fix-the-spend-report-names-every-role` →
    `LOOP-CHANGE-READY: ok`.

- No compiled file is touched, so no build is owed: the shell suite, the case-declaration driver
  (`tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`) and the document checks are the gate's
  content, and this item's gate is deferred to the run's gate child — it runs
  `./tools/loop/gate.sh --base 580c2106c9a9294d63d2bd621fb8e265dbb7dca8`, not this session.
