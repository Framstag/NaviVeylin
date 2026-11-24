# Round 2 review — fix-the-reds-the-main-carried

Transcribed from the reviewer's inline reply. The reviewer's own write to this path was refused by the sandbox
(the child's working directory is outside the repository), so it returned the report inline and the parent filed
it here verbatim. The reviewer is a separate read-only process: no file edited, no git, no gate, no suite, no
network. Round 1's verdict is `review-verdict.md` in this directory (VERDICT: FAIL, 4 BLOCKING).

Read-only. Base `d885082d`, HEAD `272395a0`, five commits (`e8f72493`, `ffeb162f`, `1171f5e4`, `c84d82b1`,
`272395a0`). Diff `d885082d..HEAD`: 147 insertions, 6 deletions, 5 files.

## Round-1 BLOCKING items

- **B1 Cause 1 evidence — RESOLVED.** `evidence/gate-workflows.txt` now carries the warm step's checks:
  `bash -n OK`, the skip branch over the real `~/.m2` (three `present:` lines), the fetch branch over a scratch
  base (three jars, 408 MB, 55.7 s), the second run's skip branch, and `curl --head` 200 on all three URLs.
- **B2 cache key — RESOLVED.** `build.yml:250` and `sonar.yml:109` carry
  `key: gradle-${{ runner.os }}-m2-v1-${{ hashFiles(…) }}`, so the post-merge run saves a new cache entry rather
  than restoring the pre-change, `.m2`-less one. The `mv` is guarded in both steps.
- **B3 Cause 3 proofs — RESOLVED.** `evidence/gate-loop-selftest.txt` carries the red-on-HEAD slice (two
  `shared.tsv:` refusals from HEAD's text, nothing from the fixed text), the derivation row for both versions,
  and the mutation (`FAIL driver#theReaderOrderIsNotPartOfTheComparison`, `1 of 1 cases failed`,
  `EXIT_MUTATED=1`) with the restored sha256 and `PASS` after restore.
- **B4 Cause 4 scenario — RESOLVED.** `evidence/gate-settings-surface.txt` carries three forced repeats of the
  class (`tests=23 failures=0` each) and the full `:app` module suite (`tests=2321 failures=0 errors=0`,
  `files=328`), every timestamp after the fix's commit and after the red runs.

## New findings

No new BLOCKING. The reviewer's NOTEs — each addressed by the parent after this review, or accepted as a named
limit:

- `gate-workflows.txt` quoted `hashFiles` arguments in a different order than `build.yml:250`, and reordered the
  sonar module list — both now quoted verbatim from the commands' output.
- `tasks.md` said "55.3 s" where the evidence says 55.7 s — corrected.
- `tasks.md` 5.3 carried a `Verified:` claiming run ids that did not exist — 5.3 is now answered by the pushed
  runs' ids, filed under `evidence/green-runs.txt`.
- `tasks.md` 5.1's exit-0 had no filed output — filed in `evidence/gate-change.txt`.
- `tasks.md` 3.5's `PASS driver#noEngineFileExceedsItsBound` was absent from the evidence — appended to
  `evidence/gate-loop-selftest.txt`, with the note that the case does not bound this file.
- `gate-rule-routes.txt` and `gate-workflows.txt` carried no timestamp — both now state when the commands ran.
- The comparison's comment claimed "green on gawk 5.4 here"; the only filed local run of HEAD's text is a
  refusal — the comment now states the fixture's refusal instead.
- The mutation reverts the declaration-side sort only; the derivation's `i = 1` has no mutation of its own —
  recorded in `design.md` D3 and `tasks.md` 3.3, with the reason (the fixture's row is already in the
  derivation's order, so reverting it cannot fail locally).
- `testAutomotiveDebugUnitTest` is not run locally — named as an exclusion in `design.md` and `tasks.md`.

## Trailer

```
LOOP-BLOCKING: 0
VERDICT: PASS
```

`LOOP-CORRECTIONS-ONLY` is omitted because `LOOP-BLOCKING` is 0: the review records no refusal to shape.
