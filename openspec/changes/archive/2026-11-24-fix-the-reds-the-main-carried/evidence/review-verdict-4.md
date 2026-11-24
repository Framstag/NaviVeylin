# Review round 4 — verdict (FAIL, 3 BLOCKING)

Filed by the parent session from the reviewer's inline verdict, returned in three parts because the supervisor's
notification truncated each reply. The reviewer's own write to this directory was denied by its sandbox, so the
verdict is recorded here without paraphrase of its findings; its words are quoted where they carry a judgement.

- Reviewer run: `a25d0d00-f378-45a8-99b1-e3397ac1cf7d`, revived twice for the two follow-up prints (its first
  reply was truncated by the supervisor's notification, so the run was resumed to reprint the blocking items).
- Role: read-only. It read the diff and the `evidence/` files; it ran no gate, no suite, no network.
- Scope: all seven causes, plus round 3's five findings.
- Verdict: `VERDICT: FAIL`, `LOOP-BLOCKING: 3`.

## Per-cause result

| Cause | Verdict |
|---|---|
| 1 — Robolectric Maven 429 (cache + warm step) | CORRECT |
| 2 — `check-rule-routes` orphans (3 route rows) | CORRECT |
| 3 — `driver#theSharedNamesAreDeclared` (reader-order sort) | CORRECT, with BLOCKING B5 |
| 4 — `SettingsSurfaceTest` settings-surface flake | CORRECT |
| 5 — `hotspot#theBoundIsAboveOneChangedFile` (row bound) | CORRECT |
| 6 — Gradle dependency-resolver 429 (retry wrapper) | CORRECT |
| 7 — diagnostics line that became five rows | CORRECT |

Cause 1's reading: "`build.yml:250` (`~/.m2` … key `…-m2-v1…`), step `build.yml:294`, mirrored `sonar.yml:109`;
`gate-workflows.txt:1-10` (both `yq` OK, `EXIT_SONAR=0`, `EXIT_RUNTIMES=0`), the 27-line extraction + `bash -n`,
scratch jars all three 408 MB in 55.7 s, `present:` on re-run, 3 URLs HTTP 200. Flaky download cannot redden the
run: the `mv` is guarded and a failure prints `::warning::`."

Cause 3's reading: "`gate-loop-selftest.txt` 548/548 in 543.37 s, the red proof's old-awk refusals, the mutation
red, `RESTORED: identical`."

Cause 4's reading: "`gate-settings-surface.txt` three forced repeats of the class (23 cases, 0 failures) and the
full 2 321-case module suite; fixture commit `c84d82b1` (+16/−1)."

Cause 5's reading: "`sensors.json:50` bound 2 000 000; the comment's arithmetic reconciles — 1 419 357 + 266 610 =
1 685 967, 314 033 bytes of headroom, below the fixture's 2 800 378; `gate-sensors.txt` quotes the case's own
`FAIL` line and the mutation's `1 of 94`."

Cause 6's reading: "`tools/gradlew-retry.sh` retries only a logged 429, bounded; `build.yml:357` and
`sonar.yml:155` wrap the step; `gate-gradle-retry.txt` holds T1–T5 (`EXIT_T1=0`, `EXIT_T3=1`, `EXIT_T5=2`) and the
`82305af6` red run `45361986255`."

Cause 7's reading: "the fold at the single write seam (`DiagnosticsLog.kt:423`); every writer reaches it;
`gate-diagnostics-line.txt` gives the red-first proof (`expected:<1> but was:<3>`) and the green run, plus the
CI-only red's title and `979 tests completed, 1 failed`."

## BLOCKING

**B4 — `tasks.md:132–136` §8.2 names `review-verdict-4.md`, which does not exist.**
The task is marked `[x]` and its sentence says "Round 4 read causes 5, 6 and 7 after those corrections and its
verdict is `review-verdict-4.md`". The change directory held `review-verdict.md`, `-2.md`, `-3.md` and no `-4.md`
when the reviewer read it. A checked task may not cite an artifact the tree does not hold. This is round 3's B1
recurring one round later.

**B5 — `tasks.md:58` §3.3 quotes a `sha256sum` its own citation contradicts.**
The task says the restored file's hash is `591325326ec015deb1fc7117cd58b6249fe496501f625ff64579e7e73c8ce9be`.
`evidence/gate-loop-selftest.txt:43–44` prints `880af467a8672086e6135e74894084fd758558cdf8ad5588a22eb84e2c79e6cf`
before and after the mutation, and its own note says that value is the file after the follow-up commit's comment
edit. The quoted `59132532…` is round 1's pre-comment value. The claim and its citation disagree.

**B6 — the change's own count is stale in three places.**
`proposal.md:4` says "five edits in seven files"; `proposal.md:199` says "Each of the five checks" and enumerates
only causes 1–5; `design.md:3` says "Five causes, five decisions" while carrying D1–D7. The proposal's own
`surface:` line says 10 files. The change's opening count contradicts its own later count.

## NOTE

**N6 — `evidence/review-diff.txt` is stale.**
It stopped inside `tools/loop/tests/loop-cases.sh`; it held no `sensors.json`, `sensor-mutations.sh`,
`gradlew-retry.sh`, `DiagnosticsLog.kt` or `DiagnosticsLogTest.kt` hunks, so `tasks.md` §8.2's "confirm each cause
against the diff" could not be discharged for causes 5–7 from that file.

**N7 — `build.yml:283` (mirror `sonar.yml:128`) left `mkdir -p` unguarded.**
The step's contract is that a failed warm warns and the run continues; the download and the `mv` are guarded, the
`mkdir` was not, and `run:` steps execute under `bash -e`. A read-only or full cache volume aborts the step there,
before the guarded download is reached. The smallest fix is the same guard and `continue` the step already uses.

## Round 3's findings

| Finding | Result |
|---|---|
| B1 — `review-verdict-4.md` named but absent | **NOT RESOLVED** — recurs as B4 |
| B2 — the mutated run's message not in `gate-sensors.txt` | **RESOLVED** — `gate-sensors.txt:132–139` |
| B3 — `design.md:174` said "1 s doubling" | **RESOLVED** — now "90 s doubling" |
| N1 — `gate-sensors.txt:127` wrong from-value | **RESOLVED** — now "from `1800000` to `2000000`" |
| N5 — the retry pattern missed the Robolectric 429 shape | **RESOLVED** — both shapes matched |

## Corrections applied after this verdict

| Finding | Correction |
|---|---|
| B4 | This file is filed as `evidence/review-verdict-4.md`; round 5's verdict is `evidence/review-verdict-5.md`; `tasks.md` §8.2 names both. |
| B5 | `tasks.md` §3.3 now quotes the re-measured `880af467…` and names the earlier value as the pre-comment one. |
| B6 | `proposal.md`'s opening now says seven causes and ten files; its Capabilities paragraph enumerates causes 6 and 7; `design.md:3` now reads "Seven causes, seven decisions". |
| N6 | `evidence/review-diff.txt` regenerated from `git diff d885082d..HEAD` — 10 file hunks, 493 lines. |
| N7 | Both workflows guard the `mkdir` and `continue` with the step's own `::warning::`; `yq -e .`, `check-sonar-workflow.sh` and `check-action-runtimes.sh` all exit 0 after the edit. |
