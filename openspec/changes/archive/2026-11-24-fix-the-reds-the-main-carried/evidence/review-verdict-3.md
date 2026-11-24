# Review round 3 — verdict (FAIL, 3 BLOCKING)

Filed by the parent session from the reviewer's inline verdict. The reviewer's own write to this directory was
denied by its sandbox (`external_directory`), so the verdict was returned in the resume message and is recorded
here without paraphrase of its findings; the reviewer's own words are quoted where they carry a judgement.

- Reviewer session: `534ca03e-c895-4d16-9113-55cf99ecab58` (a revival of `bec829ae-fc7c-4b6c-b192-fead60ad82ff`).
- Role: read-only. It read the diff and the `evidence/` files; it ran no gate, no suite, no network.
- Scope: all seven causes, with the fifth and sixth causes' deltas read for the first time.
- Verdict: `LOOP-BLOCKING: 3`, `VERDICT: FAIL`.

## Per-cause result

| Cause | Verdict |
|---|---|
| 1 — Robolectric Maven 429 (cache + warm step) | CORRECT |
| 2 — `check-rule-routes` orphans (3 route rows) | CORRECT |
| 3 — `driver#theSharedNamesAreDeclared` (reader-order sort) | CORRECT |
| 4 — `SettingsSurfaceTest` settings-surface flake | CORRECT |
| 5 — `hotspot#theBoundIsAboveOneChangedFile` (row bound) | CORRECT, with NOTE N1 |
| 6 — Gradle dependency-resolver 429 (retry wrapper) | BLOCKING B3, NOTE N5 |
| 7 — diagnostics line that became five rows | not in this round's scope |

## BLOCKING

**B1 — `tasks.md:120–124` §7.2 names two verdict files that do not exist.**
The section's parenthetical names `review-verdict-3.md` and `review-verdict-4.md`; neither file was in
`evidence/` when the reviewer read it. A change's record may not cite evidence the tree does not hold.

**B2 — `tasks.md:95–97` §5.2 claims a message "is shown" in `evidence/gate-sensors.txt`; it is not.**
The sentence says the mutated run's `the row's bound (1000000) is not above the largest file it watches
(1685967 bytes)` message "is shown" in `evidence/gate-sensors.txt`. The file carried the case's source lines and
the selftest's PASS line, but not that message. The underlying claim was already supported by the file's prose
and by `tools/sensors/tests/sensor-cases.sh:2061–2062`; the citation was what was wrong.

**B3 — `design.md:174` states the retry backoff as "1 s doubling".**
The wrapper's default is `GRADLEW_RETRY_DELAY=90`, so the design's own statement of the bound contradicts the
code it describes. A design that misstates its own invariant cannot be the record the change lands on.

## NOTE

**N1 — `evidence/gate-sensors.txt:127` stated the wrong from-value.**
The line read "rises from `1500000` to `2000000`"; the bound it rises from is `1800000` (HEAD's row, and the
commit `2858c524` diff). The registry's own diff lines in the same file show `1800000`, so the prose contradicted
its evidence.

**N5 — the retry pattern missed the second 429 shape.**
The wrapper matched only Gradle's resolver wording (`status code 429`). The base run's 844 `:auto` failures are
Robolectric's in-JVM fetcher, which words it `HTTP response code: 429 for URL: …` (run 45271114385), so the
wrapper would not have retried the failure this change exists to answer. Note, not BLOCKING, because the
wrapper's own header and the workflow step it guards are correct for the shape it does match; the gap is a
missing second shape, not a wrong one.

## Corrections applied after this verdict

| Finding | Correction |
|---|---|
| B1 | `evidence/review-verdict-3.md` (this file) is filed and round 4's verdict is filed as `evidence/review-verdict-4.md`; `tasks.md` §8.2 names only files the tree holds. |
| B2 | The red run's own message line is appended to `evidence/gate-sensors.txt` ("The red run's own message line"), and §5.2's sentence now cites it. |
| B3 | `design.md:174` now reads "3 attempts, 90 s doubling", matching `GRADLEW_RETRY_DELAY`'s default. |
| N1 | `evidence/gate-sensors.txt:127` now reads "from `1800000` to `2000000`". |
| N5 | `tools/gradlew-retry.sh` recognises both shapes — `grep -q -e 'status code 429' -e 'HTTP response code: 429'` — and its header documents both runs. |
