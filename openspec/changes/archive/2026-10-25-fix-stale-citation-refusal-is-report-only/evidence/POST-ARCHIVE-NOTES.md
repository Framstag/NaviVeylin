# Post-archive notes — `2026-10-25-fix-stale-citation-refusal-is-report-only`

This change was archived on 2026-10-25 with two tasks open, no `evidence/` directory and no
independent review. The owner then asked for the remainder to be completed. This file records what
was done **after** the archive, so the archive does not read as more complete than it was.

## Tasks that were open

| task | resolution |
|---|---|
| 5.1 full loop suite | **done** — `bash tools/loop-selftest.sh`: 230/230 (`loop-suite.txt`) |
| 5.2 fate of the residue commit `0b5fe01f` | **settled by fact, one change** — it landed in `main` as the base of the merge `46946e01`, so the residue reversal and the report-only rule shipped together. No split is outstanding. |

`tasks.md` is left with the boxes as the archive recorded them. Ticking them here would make the
archived artifact claim a state it did not have at archive time; this table is the correction.

## Gates run after the archive

| file | command | result |
|---|---|---|
| `loop-suite.txt` | `tools/loop-selftest.sh` | 230/230 |
| `resolve-stale-suite.txt` | `tools/resolve-stale-selftest.sh` | 33/33 |
| `revert-checks.md` | `tools/mutate.sh --mutations tools/resolve-stale-mutations.json` | 15/15 red-then-green |
| `checks.txt` | `issue-tooling-selftest` 121/121 · `loop-selftest --files` 101/101 · CI OpenSpec hygiene step · `gen-feature-list --check-classification` | all `exit=0` |
| `portability.txt` | `tools/check-portability.sh` | OK |
| `classification.txt` | `tools/gen-feature-list.sh --check-classification` | unclassified 0 (the `stale: 3` entries — `search-name-matching`, `search-result-row-icon`, `start-map-selection` — predate this change) |
| `declared-cases.txt` | `tools/declared-cases.sh <change>` | nothing selected: no compiled or test source in the diff |

`revert-checks.md` is the name `tools/resolve-stale-mutations.json` refers to; the file it asks for
did not exist when the change was archived.

## Independent review

`review-independent.md` is the verbatim output of a read-only child session that reviewed
`git diff 25887f33 46946e01` and `git diff d373edf1 b078d314` against the artifacts and the shipped
spec.

**Verdict on the rule the change exists for:** no defect. `absent` is reported and never refuses or
parks, all renamed cases assert report-only correctly, and the three re-aimed mutations each still
kill their named case.

**Seven findings were correct statements and were fixed** in commit
`081e9d14 fix: #281's change left references to the refusal it removed` — comments in
`tools/loop/prescreen.sh`, `tools/loop/labels.sh`, `tools/issues/create.sh`,
`.github/workflows/sensors.yml`, `tools/tooling.json`, `tools/loop/state.sh`, and a message plus the
header of `tools/resolve-stale-mutations.json`. The `prescreen.sh` one was the worst: it claimed the
symbol sensor "still refuses", and that loop assigns `evidence` only, never a verdict.

## Findings left open, ranked

1. **`openspec/specs/stale-citation-resolution/spec.md:205`** — the scenario heading still reads
   *"A stale citation in the title is parked where it stands"* while its THEN and its `Case:` say
   *reported*. It cannot be renamed inside a `MODIFIED` delta: `openspec validate --strict` refuses a
   renamed scenario, and re-adding the requirement is refused by the 500-character limit on an
   `ADDED` block. A direct spec edit, in a change of its own, is the way to it.
2. **`## Purpose` of the same spec** still describes *"a quarantine for the ones that cannot be
   repaired"* and *"an authorised item is excluded only when its citation really is gone"*. Both are
   removed behaviour. The archive sync contract forbids touching an established `## Purpose`, so it
   needs its own change.
3. **`absentCitation` never reaches the run's state.** `tools/loop/state.sh`'s queue projection keeps
   `{issue, class, category, verdict, reason, rank, gates}`, and `tools/loop/run.sh` calls the
   pre-screen without `--json`, so the token survives only in the console line. The scenario *"the
   token is named in the item's record"* is therefore true of the pre-screen's transient JSON, not of
   the run's record.
4. **`--revisit-parked` has no requirement or scenario** in the shipped spec, though it is
   user-visible behaviour with a bound cost.
5. **`--revisit-parked` removes a label it may not carry.** `reopen_item` always passes
   `--remove-label agent/excluded-stale`; `parked_tracker` in `tools/resolve-stale-cases.sh` always
   injects that label, so the **comment-only** park shape — the primary shape before #281 — is never
   exercised, and the behaviour rests on `gh` tolerating an absent label.
6. **The scheduled repair does not pass `--revisit-parked`** (`.github/workflows/sensors.yml`), so the
   historical residue has no automated writer. This contradicts the proposal's *"Open"* line, which
   says nothing calls the resolver on a schedule in-repo.
7. **`design` artifact never written** and **`evidence/` came after the archive** — recorded here
   rather than papered over.
