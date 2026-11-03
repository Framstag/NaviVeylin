# Review round 1 — fix-calls-per-turn-is-a-reported-number (issue #757)

Read-only review of the uncommitted working tree, 2026-11-03 21:44–22:02 CET. No file was edited except
this one.

**The review target moved under the review, twice, and then vanished.** That is the first fact, because
every other finding is dated relative to it.

| time | event |
|---|---|
| 21:35:45 | the gate's report cases PASS (`evidence/gate.log:797,801,834`) |
| 21:43:22 | `evidence/gate.log` written — the gate finished |
| 21:43:39 | `tools/loop/state.sh` written (hash `8940a3bdc57bc34d619d6c4264757eac`) |
| 21:43:42 | `evidence/mutations.log` written (records 78.1–78.3 red, restored) |
| 21:44:21 / 21:44:23 | `report.sh` / `loop-cases.sh` edited again (`376a0a2c…`, `0e66e539…`) |
| 21:44:58 | `state.sh` edited again (`221d375d…`) — **and it did not parse** |
| 21:50:32 | every changed file reverted to HEAD in one bulk write; `git status` clean, `git diff` empty |

So the gate evidence covers a revision that was already superseded before the change was declared ready,
and the content reviewed below no longer exists: the nine files (`tools/loop/state.sh`,
`tools/loop/report.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/loop.conf`,
`tools/loop/prompts/worker.md`, `guidelines/Backlog.recipes.md`, `PORTING.md`,
`.pi/skills/loop-spend/SKILL.md`) carry HEAD's content at 22:02, and no branch, patch or stash of the
change was found (`.pi/worktrees/loop` is pristine; no `*757*` branch).

## Scenarios

`SCENARIO report#anItemBelowTheCallsPerTurnFloorIsNamed — BROKEN` (as of the 21:44:58 content; it cannot
run now, because the engine it drives was reverted)

- trend's run ratio with both counts — **OK**: `tools/loop-cases.sh:8621` asserts
  `calls:  1.33 calls per turn (8 calls / 6 turns)`; produced by `tools/loop/report.sh:269-274` from
  `$calls[].c`/`$calls[].t`. 6+2 calls over 2+4 turns is 1.3333, printed at two decimals.
- the named item below the floor with its own ratio — **OK**: `tools/loop-cases.sh:8623` asserts
  `below the recorded floor of 2 per turn: #22 0.5`, and mutation 78.1's RED message in
  `evidence/mutations.log:4-5` proves the assertion bites (`… not found in:   calls:  1.33 calls per turn
  (8 calls / 6 turns) · none below the recorded floor of 2 per turn`).
- the table's last column reads `3` and `0.5` — **OK**: `tools/loop-cases.sh:8628-8629` via
  `table_cell … 17`; the column is `tools/loop/state.sh:765-768`, appended after `over`.
- a floor of `0` prints as off **and flags nothing** — **BROKEN**: `tools/loop-cases.sh:8636` asserts only
  the substring `floor off — LOOP_ITEM_CALLS_PER_TURN_MIN records 0, so no item is flagged`; the branch
  prints that string and then ends the line (`report.sh:278`), so an appended name would not be detected
  by `assert_contains`. The token band's mirror case does assert the negative half
  (`tools/loop-cases.sh:8566-8569`: "no row is flagged by a bound that is off").
- an item that recorded no turn prints `-` with its reason — **OK**: `tools/loop-cases.sh:8641` asserts
  `calls:  - · no item recorded a turn` and `:8648` asserts the same item's column is `-`.
- both items keep the outcome their work earned — **OK**: `tools/loop-cases.sh:8630-8631` asserts
  `closed 2 · mis-diagnosed 0 · skipped 0 · blocked 0`.

`SCENARIO report#wallTimeAndReasonInTheTable (the pipe count) — OK`. `tools/loop-cases.sh:8420-8425` reads
18 pipes = 17 columns; the header (`tools/loop/state.sh:726`), the rule (`:727`), the empty-run row
(`:728`) and the data row (`:770`) all carry 17 fields, and `over` stays at column 16
(`tools/loop-cases.sh:8557-8569`), so every reader that quoted the earlier shape keeps its index.

`SCENARIO run#childUsageIsRecordedPerItem (the two counts) — OK in principle, unverified now`:
`tools/loop/state.sh:672-679` counts `tool_execution_end` and `turn_end` in the one pass that already
reads the stream, records `calls`/`turns` as `null` when the item reported no turn (`:699` fallback gains
both keys), and `cmd_done` stores `calls:$usage.calls, turns:$usage.turns` (`:543-544`). The case asserts
`5` and `2` over the item's two children (`tools/loop-cases.sh:7367-7371`), which is the item-level sum of
one appended stream.

## Findings

- **P0 — `tools/loop/state.sh:763-764` broke the shell's quoting, and with it the whole report.**
  The jq program opens at `state.sh:723` (`jq -r --argjson over "$tokens_warn '`) and the two new comment
  lines inside it contain unescaped apostrophes: `# item's own figure …` and `# is the comparison the
  report's trend makes …`. Bash closes the string at `:763`; `bash -n tools/loop/state.sh` reports
  `Zeile 765: Syntaxfehler beim unerwarteten Symbol »|«`, `state.sh report` emits nothing, and
  `report.sh` prints **no table at all** while still exiting 0 (it runs `set -uo pipefail`, no `-e`):
  `bash tools/loop/report.sh --state .pi/issue-loop/runs/2026-11-03T12:18:52Z.json` → 0 rows, 0 headers,
  the syntax error on stderr, `exit=0`. Every case that reads a table row fails with it. Smallest fix:
  drop the apostrophes (`the item` / `the trend the report makes`); the pre-existing jq comments in this
  file keep to that convention. The same edit had to be made in `report.sh:264` (`rate's` → `rate`)
  during the session, which is the same hazard one file over.
- **P0 — the change is not in the working tree.** All nine files were reverted to HEAD at 21:50:32
  (single-second mtimes, `git status --short` empty, `git diff` empty, HEAD `47ec83e5`). Whatever is
  archived from this tree carries no engine, no case, no mutation and no doc change. The change's
  artifacts under `openspec/changes/fix-calls-per-turn-is-a-reported-number/` survive because the
  directory is gitignored.
- **P1 — the gate evidence does not cover the content it is cited for.** `evidence/gate.log` mtime
  21:43:22, its report cases PASSed at 21:35:45; `state.sh`, `report.sh` and `loop-cases.sh` were written
  at 21:43:39, 21:44:21, 21:44:23 and 21:44:58. `tasks.md` task 10 therefore claims a green gate for a
  revision that no longer exists — and for the last one it claims it for, the gate would be red (P0
  above). The gate must be re-run on exactly the content that lands, and `evidence/` regenerated.
- **P1 — a case asserts a behaviour no scenario states and no mutation covers.** At 21:44:23
  `tools/loop-cases.sh:8650-8663` gained a fourth phase asserting
  `calls:  - · this state records no calls or turns — it was written before the report read them`
  against `report.sh:262-268`. The delta's only scenario (`specs/agent-issue-loop/spec.md`, the THEN
  clause) says "an item that recorded no turn prints `-` with its reason" and nothing about a state
  written before the field existed; `design.md` and `tasks.md` do not mention the branch either, and
  records 78.1–78.3 do not reach it. Either the scenario gains the clause or the branch loses the
  distinction; as it stands the requirement and its case disagree.
- **P2 — "33 184 `turn_end` lines" does not reproduce.** The exact count over the archive is 33 183:
  `LC_ALL=C grep -F -h -o '"type":"turn_end"' .pi/issue-loop/items/*/events.jsonl | wc -l` → `33183`
  (227 streams). 33 184 is the count `state.sh`'s own `turns` def yields, because it also counts a
  trailing turn that never closed — an awk pass mirroring that reduce over the same 227 streams returns
  `… 33184`. The number is right as "turns" and wrong as "`turn_end` lines"; the wording appears at
  `tools/loop/loop.conf:128`, `guidelines/Backlog.recipes.md:317`, `tools/loop/report.sh:229`,
  `tools/loop/prompts/worker.md:85`, `tools/loop-cases.sh:8573`, `tools/loop-mutations.sh:742` and
  `proposal.md:14`. Everything else in the measurement reproduces exactly: 36 825 calls
  (same grep), 2 591 315 029 tokens over 33 184 turns = 78 091 per turn ("78 k"), cache read
  2 502 388 090 = 96.6 % ("97 %"), and the per-item ratio p10 0.9853, **p25 1.0459**, p50 1.1053,
  p75 1.2143, p90 1.3171, max 1.6111 against the recorded 0.99 / **1.05** / 1.11 / 1.22 / 1.32 / 1.61.
  The floor `LOOP_ITEM_CALLS_PER_TURN_MIN=1.05` is therefore honest and is the archive's p25.
- **P2 — the floor's rejection of p50 cites the wrong precedent.** `tools/loop/loop.conf:132-134` says
  "p50 (1.11) was rejected for the reason p90 was rejected for tokens — a floor at the median flags half
  the archive". That same file rejects p90 for tokens because it "sits above most of the items of the
  run that was missed" (`loop.conf:100-103`) — because it flags too *few*, not too many. The mirror
  argument is the share, not the direction: the band took p75 and not p90 so it flags a quarter rather
  than a tenth; this floor takes p25 and not p10 for the same quarter.
- **P2 — the floor-off path cannot fail on the half of the clause that matters.** See the scenario
  above: `tools/loop-cases.sh:8636`. Smallest fix: extract `^  calls:` and `assert_not_contains` `#2`
  beside the existing positive assertion.
- **P2 — the sanitizer lets a value through that `jq` refuses.** `tools/loop/report.sh:236` accepts
  `''|*[!0-9.]*|*.*.*` as "not a floor"; `05`, `1.` and `.` pass it and are then rejected by
  `--argjson`, and because `report.sh` runs `set -uo pipefail` without `-e` the whole `calls:` line
  disappears silently instead of falling back to the floor-off text. Smallest fix: one `case` arm
  per accepted shape, e.g. reject anything not matching `^[0-9]+(\.[0-9]+)?$`. (The token band's
  integer sanitizer has the milder version of the same hole at `state.sh:722`; it predates this change.)
- **P2 — cosmetic:** the mutation record `tools/loop-mutations.sh:745` reads "the table column that
  prints an item calls per turn" (missing possessive); mutation descriptions are quoted verbatim into
  reports.

## What I checked and did not verify

- The report still reads the state file, git, and `loop.conf` — no new class of read: `report.sh:140`
  already read `LOOP_ITEM_TOKENS_WARN` before this change, so `:235` adds a second key from the same file.
- No division by zero is reachable: both the trend (`report.sh:259`, `select(… (.metrics.turns // 0) > 0`)
  and the column (`state.sh:767`, `$turns == 0 → "-"`) guard the denominator; `.r` is rounded before the
  floor comparison, so an item cannot be named at 1.0499 for a floor of 1.05.
- Null handling is right on both paths: a missing `metrics`, a missing key, `calls` recorded as `0`
  (a real measurement) and a pre-change state all print `-` rather than a zero.
- The mutations' search strings still matched the last content (`78.1` and `78.2` one occurrence each;
  `78.3`'s nine-space-anchored line at `state.sh:678`, as `evidence/mutations.log` records for the
  21:43 content).
- Doc ownership: the new paragraphs sit in `Backlog.recipes.md` §7b ("What an item costs, and the bounds
  on it") and §7e ("…the report…"), the two sections that own them, and the key's home is `loop.conf`;
  `check-doc-routes.sh` was green in `gate.log:964`. No AGENTS.md routing row needs changing: no new
  document, and the routed documents' own sections were edited.
- Not run, by design (read-only): the self-test, the gate, the mutation set, `openspec validate
  --strict`, `tools/loop/evidence-check.sh`. For the record, the delta's requirement body is 383
  characters (limit 500), its `## Purpose` is not owed (the capability exists), and `tasks.md` uses
  `- [x]` markers. `tasks.md` has no `openspec validate --strict` step, which `AGENTS.md` ("OpenSpec
  Workflow") asks for before an artifact is finished rather than at archive time.
- Unverified claims I could not falsify: the "halving the calls per turn is worth about a third of the
  spend" estimate (`report.sh:229-230`, `proposal.md`) has no derivation in the change.

## To re-review

`bash -n tools/loop/state.sh tools/loop/report.sh tools/loop-cases.sh tools/loop-mutations.sh`, then
`bash tools/loop-selftest.sh --case 'report#anItemBelowTheCallsPerTurnFloorIsNamed' --case
'report#wallTimeAndReasonInTheTable' --case 'run#childUsageIsRecordedPerItem'`, then the whole gate
(`tools/loop/gate.sh`) and the mutation run (`bash tools/loop-mutations.sh --only '78.1 78.2 78.3'`),
against the tree that is actually going to be archived.

VERDICT: FAIL
