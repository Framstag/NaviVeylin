# Round 5 — independent read-only review of HEAD `5e8ebbd6`

Reviewer run `d714ba24-1c5e-4abf-93e6-7e303a2e8925` (round 5), revived as `dc875b9e-9e5d-4b28-b2cb-882d4f175cf6`
for the truncated reprint. Read-only: no gate, no suite, no network; every green figure quoted from an evidence
file by path. Filed by the parent because the reviewer's sandbox denies writes.

## Verdict

```
VERDICT: FAIL — LOOP-BLOCKING: 4
```

All seven causes read CORRECT in the code and in the honest evidence; the four blocking items are stale
quotations in the change's own record — each an evidence file or a checked task contradicting the file it quotes.

## Cause lines

- **Cause 1 — CORRECT.** `.github/workflows/build.yml:242-252` (paths incl. `~/.m2/repository`, key
  `…-m2-v1-${{ hashFiles(…) }}`, live `grep -n` → build.yml:250 / sonar.yml:109, matching `gate-workflows.txt`),
  step 273-307, guard-with-warning contract intact.
- **Cause 2 — CORRECT.** `tools/doc-routes.json` diff adds exactly 5 rows (UI.md §8d ×2, MapFrames.md §13 ×2,
  BuildGate.md §2b ×1); `gate-rule-routes.txt` quotes `658 named rule(s)` + 13 selftest cases.
- **Cause 3 — CORRECT.** `loop-cases.sh` `i = 2` → `i = 1` (line ~19516) and the declaration-side
  normalisation; case registered in `LOOP_SHELL_CASES` and the dispatch; `gate-loop-selftest.txt` red-on-HEAD
  refusals, `1 of 1 cases failed` under mutation, `RESTORED: identical`, 548 cases / 543.37 s.
- **Cause 4 — CORRECT.** `SettingsSurfaceTest.kt` gives `SettingsStorage` the rule dispatcher (1029-1053 area)
  and drains per poll; `gate-settings-surface.txt` three repeats `tests=23 failures=0` plus
  `tests=2321 failures=0 errors=0`.
- **Cause 5 — CORRECT.** `sensors.json:50` `"threshold": 2000000`; 1 685 967 + 314 033 = 2 000 000,
  1 685 967 − 1 419 357 = 266 610; fixture score 2 800 378 anchored at `tools/sensors/tests/sensor-cases.sh:2028`
  (400 000 × 7); mutation 8.31 `2000000`→`1000000`, CI message `(1000000) is not above … (1685967 bytes)`.
- **Cause 6 — CORRECT.** `tools/gradlew-retry.sh` retries only a 429 log (both shapes), bounded, returns the
  command's status; both steps wrapped; `EXIT_T1=0 / T2=0 / T3=1 / T4=1 / T5=2`.
- **Cause 7 — CORRECT.** Fold in `timestamped` (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:709-713`:
  `"[…] ${oneLine(line)}"` + `NEWLINE_RUN` regex); every writer reaches the seam (call sites 425, 458, 501, 629);
  `evidence/gate-diagnostics-line.txt` gives red-first `expected:<1> but was:<3>`, `8 tests completed, 1 failed`,
  green `tests="8" … failures="0"`, CI red run 45366310469 + `979 tests completed, 1 failed`.

## LOOP-BLOCKING (4)

- **B7.** `proposal.md:237` — "`tools/gradlew-retry.sh` — new, 53 lines"; `evidence/gate-gradle-retry.txt`
  ("The wrapper" block) quotes `wc -l` → 53. Live file = 58 lines (grew in `21c0fd24`, +7/−1). Blocks: a claim
  and its evidence quote a count the file contradicts. Fix: state 58 in both places.
- **B8.** `tasks.md:19` §1.2 — "the extracted step is 27 lines"; `evidence/gate-workflows.txt` quotes
  `wc -l < /tmp/warm-step.sh` → 27. Live run block is `.github/workflows/build.yml:275-307` = 33 lines (round 4's
  `mkdir` guard + its 4-line comment). Blocks: checked task's verified figure contradicts the step it counts.
  Fix: re-extract, state 33.
- **B9.** `evidence/gate-gradle-retry.txt` — quotes `grep -n 'gradlew-retry'` → build.yml:351/357, sonar.yml:150/155.
  Live = build.yml:358/364, sonar.yml:155/160 (N5/N7 edits shifted both files). Blocks: evidence contradicts the
  line numbers it quotes; a reader re-running it gets different lines. Fix: re-run the grep, quote the new numbers.
- **B10.** `evidence/gate-change.txt` ("the change's metadata") — quotes `.openspec.yaml` as "each of the five
  checks … a test fixture's dispatcher and a sensor row's bound". Live `.openspec.yaml` says "the seven checks …
  a retry wrapper two workflow steps call and a fold in the diagnostics log's write seam". Blocks: evidence quote
  contradicts the file quoted (the B6 correction updated the file, not the quote). Fix: re-quote the `cat`, or
  drop the block.

## Resolution applied by the parent

- **B7 RESOLVED.** `proposal.md:237` → "new, 58 lines"; `evidence/gate-gradle-retry.txt` "The wrapper" block now
  quotes `58 tools/gradlew-retry.sh` (live `wc -l` = 58).
- **B8 RESOLVED.** `tasks.md:19` §1.2 → "34 lines as `yq -r` emits it — 33 lines of step text at
  `build.yml:275–307`, plus the block scalar's trailing blank line"; `evidence/gate-workflows.txt` quotes
  `34` and its fetch-branch heading says "the same 34 lines". Re-extracted with `yq -r` and counted.
- **B9 RESOLVED.** `evidence/gate-gradle-retry.txt` "The two steps the wrapper runs" block re-quotes the live
  `grep -n 'gradlew-retry'` output: `build.yml:358`/`364`, `sonar.yml:155`/`160`.
- **B10 RESOLVED.** `evidence/gate-change.txt` "the change's metadata" block re-quotes the live `.openspec.yaml`
  (seven checks, the retry wrapper, the fold in the diagnostics log's write seam).
