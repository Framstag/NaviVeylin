# Report — `issue-metadata-loop`

Applied 2026-10-17 on `main` (base `3eec054`, the archive commit of `agent-issue-loop`).

## What landed

| artifact | what it is |
|---|---|
| `tools/issues/prioritise.sh` | the priority half: reads every issue once, recomputes each **basis**, partitions into skipped/due, bounds the run (`--max-rescore`, default 20), and writes the band label + the body marker in one `update.sh` operation, with a comment only on a band transition |
| `tools/issues/classify.sh` | the classification half: the queue of issues with no kind, proposals validated against the closed vocabulary before the first write, an out-of-vocabulary category reported and never relabelled |
| `tools/issues/lib.sh` | the shared half: the basis (five inputs, one digest), the body record (`occurrence`), the freshness bucket in pure shell arithmetic, the fenced model handoff, the write log, the report line |
| `tools/issues/core/Bands.java` | the band mapping and its totality, read from the profile; `Bands meta` serves the pass's scalars; `Profile.problems` now validates the `bands` section |
| `tools/tooling.json` `.bands` | the scoring table, the four edges, `ttlDays`, `basisNamespaces`, the status caps — the profile owns every one of them |
| `tools/issues/check-metadata.sh` + `-selftest.sh` | the invariant (label/marker agreement, vocabulary, the pass never writes the automation opt-in) and its falsification |
| `tools/issues/metadata-mutations.sh` | the seven mutations of task 8, each applied, noticed and restored |
| `tools/issues/list.sh` | `--prio <p0,…>` (band-ordered) and `--unranked` |
| `.pi/skills/issue-metadata/SKILL.md` | the judgement half: the three tables, the four edges, `--max-rescore`, the untrusted-input rule, the classification contract |
| `.pi/skills/triage-todo/SKILL.md` | ranks by the persisted band, reports an unranked item as unranked, keeps the tables, names this change as the owner of a rescore |
| `.github/workflows/build.yml` | one token-free step (`Check issue metadata`), plus the mutation list |
| `guidelines/Backlog.md` | §1 row 5 marked applied; §12 gained the label+marker and classification-rides-the-same-pass decisions |

## Evidence

| file | what it records |
|---|---|
| `evidence/dry-run-live.md` | 7.1 — the read-only run over the live tracker: 159 read in 7.9 s, 159 due, 20 selected, 139 waiting, nothing written, and the tracker's own state before and after |
| `evidence/run-live.md` | 7.2/7.3 and 6.2 — the real run (5 scored, 5 written, 0 transitions, check green, band order verified) and the run after it (0 scored, 0 written, 5 skipped), with the timings and where they went |
| `evidence/revert-checks.md` | 8.1–8.7 — every mutation, the probe that must notice it, the exit codes, and the two mutations whose named case is a fixture-level one and therefore needed the end-to-end form |
| `evidence/traceability.md` | 7.6 — one row per scenario (22 rows = `grep -c '^#### Scenario:' specs/issue-priority/spec.md`) |

## Gate results (all green on the applied tree, 2026-10-17)

| gate | result |
|---|---|
| `bash tools/issue-tooling-selftest.sh` | **all 69 cases passed** (JDK 27, offline, no token) |
| `bash tools/issues/check-metadata-selftest.sh` | **7 of 7 cases passed**, each in both directions |
| `bash tools/issues/metadata-mutations.sh` | **7 of 7 mutations noticed, and every probe quiet again** (exit 0) |
| `bash tools/issues/check-metadata.sh` | 0 violations (token-free; also green under `env -i PATH="$PATH"`) |
| `bash tools/gen-feature-list.sh --check-classification` | exit 0 with the shipped spec present, exit 1 naming `issue-priority` without its line |
| `bash tools/check-doc-routes.sh` + `-selftest.sh` | 87 references resolve; selftest exit 0 |
| `bash tools/check-todo-references.sh` | exit 0 |
| `openspec validate issue-metadata-loop --type change` | valid |

## Exclusions, named (task 7.4)

- **No Gradle build and no Gradle suite was run** — this change touches shell, JSON and markdown only, and
  `tools/**` and `.pi/**` are outside every Gradle source set (`guidelines/Build.md` §2, the scope block).
  The gate is the tooling self-test, the check's self-test and the mutation harness, all offline.
- **No device and no pixel measurement** — nothing in this change draws anything, so no screen was measured
  and no capture was read (`guidelines/Build.md` §10, §11 do not apply).
- **CI gained one token-free step** (`Check issue metadata`), plus the mutation list. No model provider key
  was added anywhere: the pass runs where an agent runs (design D10).
- **`git diff --name-only` lists no file under `app/`, `auto/`, `core/`, `osmscout-client-java/`,
  `buildSrc/`** — the app, the native boundary and the release path are untouched. The only compiled file
  in the diff is the tooling's own Java core (`tools/issues/core/`), which is compiled by `javac` in the
  self-test and belongs to no Gradle module.

## Deviations and scope additions, with their reasons

1. **`P3` starts at 2, not at 3** (task 1.1 and design D2 said `3..6`). The model's minimum is
   severity 1 × reach 1 + confidence 1 = 2, and the spec's "The range is fixed and total" requires every
   producible value to fall in exactly one band. The design was corrected; the profile's `bands.comment`
   records it.
2. **`marks`/band spelling is lowercase** (`band=p2`), while design D1's example showed `band=P2`. The
   label is `prio/p2`, and a marker that spells the same vocabulary value differently is a marker that can
   disagree with its label. The design was corrected.
3. **`update.sh` gained `--comment` / `--comment-file`.** Design D4 assumes a comment can be posted by the
   pass, and the only comment path in the tooling was `close.sh --comment-only`, which is gated on
   eligibility while a priority comment belongs on any item. The addition is inside the existing
   reserved-label and vocabulary gate, and two cases cover the new path.
4. **`list.sh` gained `--prio` and `--unranked`** (tasks 5.1) and, with them, the band namespace and the
   band order are read from the profile rather than written into the script.
5. **`check-metadata.sh`'s marker-grammar rule is positive, not negative.** The first form asserted "no
   script repeats the delimiter", which the checker itself cannot satisfy while naming the delimiter to
   search for; the shipped rule requires the *writer* to use `lib.sh`'s helpers and refuses a second
   spelling of the delimiter in any engine script's code. Found by its own self-test (the green half went
   red), which is the falsification working as intended.
6. **Two case names beyond the task list** carry invariants the tasks state in prose: the freshness bucket
   (`basis#anAgedScoreIsRevisited`) and priority-is-not-authorisation at the query level
   (`list#eligibleOnlyIgnoresTheBand`). Both are recorded in the traceability table.
7. **8.6 and 8.7 mutate a writer and assert the *check* over what that writer produced**, because their
   named cases read fixtures rather than the pass's output. The reasoning is in
   `evidence/revert-checks.md`; the recorded finding is that the *file-level* reserved-label rule is a
   convenience while the write-log rule is the one that holds against a pass resolving the label at
   runtime.

## Open items, stated rather than hidden

- **The bound is small on purpose.** The first real run scored 5 of 159; 154 are reported as `waiting`.
  Re-running with a larger `--max-rescore` is the intended way to work the queue down, and each run costs
  one script pass (~7 s) and one model batch.
- **The first five bands are a judgement.** They were scored against the tables in the `issue-metadata`
  skill and recorded with their components and their rationale, so a disagreement is visible and a rescore
  is a bounded, cheap operation — that is the property this change was built for, not a claim that the
  first scores are right.
- **`status/fixed-by` items carry a residue, not a defect.** Two of the five scored items (#1, #3) are in
  that state; their rationale says what remains (a diagnosis, a fallback figure), which is what the band
  ranks.
- **A stale classification entry exists until this change is archived.** `tools/feature-list/specs.json`
  now carries `issue-priority`, and the spec is not shipped until `openspec archive` moves it, so the gate
  reports `stale: 1` (reported, not a failure — the same rule a stale shortlist entry follows). The
  archive clears it.
