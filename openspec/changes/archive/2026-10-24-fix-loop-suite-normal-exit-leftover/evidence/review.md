# Independent read-only review (2026-10-24)

A fresh child (`reviewer`, read-only) reviewed the delta against the change's artifacts and the two full-suite
logs, with no access to this session's reasoning. It was asked seven questions; its verdicts:

| # | point | verdict |
|---|---|---|
| 1 | the delta's `MODIFIED` requirement repeats the shipped scenario verbatim, adds exactly one, body < 500 chars | PASS — byte-identical scenario (`diff` empty); body 490 bytes / 489 chars; `openspec validate --strict` re-run by the reviewer → valid |
| 2 | the case exists and is registered in the case list **and** the dispatcher; its `# files:` covers every path it names | PASS — `tools/loop-cases.sh:6115`, list `:229`, dispatcher `:490`, declaration `:6116`; corroborated by `driver#everyCaseDeclaresTheFilesItCanReach` green |
| 3 | the case drives the normal exit with no signal, leaves a process the case's own group signal cannot reach, and asserts positively | PASS — nested driver awaited (`:6141`), `set -m` + `sleep 300 &` in the suite's own scratch root (`:6130-6132`), `kill -0` poll expecting `alive=0` (`:6146-6151`); record `53.11` turns it red |
| 4 | record `53.11` names the exact sweep line and the new case, and applies | PASS — `path="$engine/${file#tools/loop/}"` resolves `../loop-selftest.sh`; sed names `loop-selftest.sh:112`; `--check --only 53.11` → `all 1 cases passed` |
| 5 | the attribution is honest: the only common failure is #288's documented red, the rest are genuinely different | PASS — intersection is exactly `run#aWriterTheRunCanPlaceBlocksTheItem`; the other two sets are disjoint (`prompt#` vs `run#`); corroborated by `fix-loop-attribution-is-read-before-the-sweep/proposal.md` and `9281534d` |
| 6 | the change touches nothing it claims not to | PASS — `git diff --stat 49dfd7db` = `tools/loop-cases.sh` (48 +) and `tools/loop-mutations.sh` (1 +), 0 deletions; engine files clean |
| 7 | no unexercised scenario, no unsupported claim | PASS |

**Merge verdict: OK with notes.** The reviewer's four P2 notes — all documentation precision, none in the diff —
and their repairs:

1. `design.md` quoted `on_exit`/`run_case` line numbers from another tree (the unlanded #288 hunk). **Fixed**: the
   numbers are now this base's (`on_exit` 98/104/105/112/113/116, `run_case` 224/226–230/232).
2. `design.md` alternative 1 claimed the revert-check log shows the whole suite green. **Fixed**: the log shows the
   record's own case red then green (the harness builds each trial with `--case "$case_name"`), and it says so.
3. `tasks.md` marked the archive and the closure done before they were. **Fixed**: 4.2 and 4.3 are unchecked until
   they are.
4. `attribution.md` quoted an isolation tally (11 of 11) that its log did not carry. **Fixed**: the sentence now
   cites the 6-of-6 the log holds.

The reviewer's re-check commands, quoted from its report:

```
bash tools/loop-selftest.sh --case clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft
bash tools/loop-mutations.sh --only 53.11
```
