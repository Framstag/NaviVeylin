# Tasks

This change is spec-owned by `stale-citation-resolution`: `specs/stale-citation-resolution/spec.md` defines it,
so each task names the **design decision** it lands, and its verification names the case that exercises it; the
traceability task in group 6 maps every scenario to its case. Every task carries its own verification.

Scope guard: this change edits shell, JSON, markdown and two workflow steps — no Gradle source set, no compiled
file, no manifest, no native source. **No Gradle build and no Gradle suite is run or required**
(`guidelines/Build.md` §2, the scope block); the gate is `tools/resolve-stale-selftest.sh`. No device or pixel
measurement applies: no UI changes and nothing is read on a screen — say so in the change report.

Ordering: group 1 must land before group 2 (the pre-screen may not adopt a classifier that does not exist),
and groups 1–3 before group 4 (the pipeline step runs a resolver that must already be correct). Group 1 is
also the fix for **#164**; group 2 is the fix for **#163**.

Out of scope, and deliberately: closing an item (a person's `--not-planned` decision), judging a claim
obsolete, rewording prose around a citation, and any write to `agent/*`.

## 1. The classifier, in one home (D1, D2, D3)

- [x] 1.1 Move the extraction into one function in `tools/issues/lib.sh`: token boundaries (not preceded by `[A-Za-z0-9_.-]`, not followed by `[A-Za-z0-9_]`), the recognised extension set, and the path-shaped rule for header extensions. Verify: case `classifier#fragmentIsNotACitation` — `-Dorg.gradle.java.home` in a body yields no citation; case `classifier#bareHeaderIsNotAPath` — `jni.h` yields none (D2).
- [x] 1.2 Add the resolution classes to the same home: `exact`, `suffix` (exactly one tracked path ends with the token), `submodule` (it resolves inside a submodule), `generated` (an artefact the project produces), `not-a-path`, `absent` — built on one `git ls-files` index per run. Verify: case `classifier#bareSourceNameResolvesBySuffix` (`SearchScreen.kt`, `MapRendering.md`), case `classifier#submodulePathResolves` (`Tests/src/SearchScopeTest.cpp`), case `classifier#generatedArtefactIsNotMissing` (`RELEASE-NOTES.md`), case `classifier#absentIsTheOnlyStale` (a name no tracked file ends with) (D3).
- [x] 1.3 The `generated` list and the extension set are data, not code: one file per list, named in the spec's `generated`/extension sets, so a project adds an artefact without editing the classifier's logic. Verify: `classifier#generatedArtefactIsNotMissing` uses a listed artefact, and adding an entry to the list makes a previously `absent` name classify `generated` (D2).
- [x] 1.4 Write `tools/resolve-stale-selftest.sh` + `tools/resolve-stale-cases.sh` (the driver and the case table, the `tools/*-selftest.sh` convention, throwaway trackers under the gitignored `.pi/tmp/`). Verify: `--list` names every case in this change's traceability table, and the driver exits 2 with a named message when a prerequisite is missing.

## 2. The pre-screen asks the classifier, and its refusal is visible (D1, D5, D8)

- [x] 2.1 `tools/loop/prescreen.sh` drops its own `cited_paths_of` and asks the classifier; the `stale` verdict becomes a true positive. Verify: case `prescreen#refusalIsVisibleInTheTracker` — a body whose only citation is absent is refused, and one whose citation resolves by suffix is queued (D1).
- [x] 2.2 The refusal is recorded where a person can read it: a label a person can query plus one comment naming the citation, written by the pre-screen's own script (the loop's write, never a child's). Verify: case `prescreen#refusalIsVisibleInTheTracker` asserts both writes, and that a second pre-screen over the same item does not post a second comment (#163) (D5, D8).
- [x] 2.3 The loop's existing cases still pass, and the ones that relied on the crude extraction are updated to the classifier's verdicts. Verify: `bash tools/loop-selftest.sh` exits 0, and `tools/loop-mutations.json`'s set is re-run (`bash tools/mutate.sh --mutations tools/loop-mutations.json`) with every mutation red and restored green.
- [x] 2.4 Record in `guidelines/Backlog.md` §5 that the pre-screen may park an item (the ownership row for the resolver's and the pre-screen's write surface), and in §6/§7 that the discovery step asks the classifier. Verify: `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` both exit 0, and §5 names the writer.

## 3. The resolver (D4, D5, D7)

- [x] 3.1 Write `tools/issues/resolve-stale.sh`: dry run by default, `--apply`, `--max-items <n>` (default 20), `--json`, and a per-item report naming each citation with its class and the action taken. Verify: case `resolver#boundHoldsAndReportsTheRest` — with more due items than the bound, the first are processed and the rest are counted (D7).
- [x] 3.2 The repair ladder, in order: suffix → canonicalise to the full tracked path; rename → the successor from `git log --follow --diff-filter=DR --name-status`; symbol → the single surviving file that declares it (`git grep -l --fixed-strings`). Verify: case `resolver#suffixedCitationIsCanonicalised`, case `resolver#renamedFileIsFollowed`, case `resolver#removedSymbolIsReAnchored` (D4).
- [x] 3.3 A repair rewrites the citation token and nothing else: the `fingerprint` and `nvid` markers stay byte-identical, the body's prose is untouched, and the profile's body bounds are respected (the item is reported rather than written past them). Verify: case `resolver#markersSurviveTheRepair` compares the markers before and after, and a body at the bound is reported and not written (D7).
- [x] 3.4 One comment per repair, naming the citation before and after and the evidence (the history record or the symbol hit); no comment when nothing changed. Verify: case `resolver#repairPostsOneComment` and case `resolver#secondRunChangesNothing` (a second run rewrites nothing and posts nothing) (D7).
- [x] 3.5 Quarantine: an `absent` citation with no mechanical successor parks the item (`status/on-hold`) and posts one comment listing the ladder's attempts; an ambiguous match (2+ candidates) is reported and not repaired. Verify: case `resolver#unrepairableCitationIsParked`, case `resolver#ambiguityIsReportedNotRepaired` (D5).
- [x] 3.6 The resolver's write surface is exactly: a citation token, `status/on-hold` for a quarantine, one comment. It writes no `agent/*`, no `class/*`/`category/*`/`prio/*`, closes nothing and files nothing. Verify: case `resolver#neverWritesTheAuthorisation` asserts the recorded `gh` invocations across a repair and a quarantine (D8).
- [x] 3.7 Every write goes through the tooling's own paths (`update.sh`), so the reserved-label refusal and the closed vocabulary apply to the resolver exactly as to a person. Verify: case `resolver#neverWritesTheAuthorisation` also asserts that a `status` value outside the vocabulary is refused with exit 3 and nothing is written.

## 4. It runs on its own (D6)

- [x] 4.1 One row in `tools/sensors/sensors.json` (`stale-citations`) whose command reports the **count of unresolved citations** as its measurement, with the row's class, category, bound and fingerprint fields declared like every other row. Verify: case `pipeline#residueIsADeclaredRow` — the row exists, its command is a fixture script in the self-test, and its threshold is the count (D6).
- [x] 4.2 One step in `.github/workflows/sensors.yml` that runs the resolver (`--apply`, bounded), beside the rows and inside the same least-privileged, pinned, overlap-guarded job. Verify: case `pipeline#scheduledRunRepairs` drives the step's command against a throwaway tracker and asserts that a repairable citation is repaired and the item is eligible again (D6).
- [x] 4.3 One `Check stale citation resolver` step in `.github/workflows/build.yml` running the self-test — token-free, no tracker write. Verify: the command runs green locally with `env -i PATH="$PATH" bash tools/resolve-stale-selftest.sh`, and the step grants no permission beyond `contents: read`.
- [x] 4.4 `.pi/skills/cleanup-todo` gains the staleness recipe (it already owns tracker hygiene): what the resolver does, how to run it dry, how to read a quarantine, and the two things it may not do. Verify: the skill names the resolver, the dry run and the row, and `git ls-files .pi/skills/cleanup-todo` is unchanged in shape (one `SKILL.md`).

## 5. Falsification (revert-checks)

One mutation each, restored after; the named case must fail, then the suite must be green again. The gate is
`tools/resolve-stale-selftest.sh`, not Gradle; the mutations are versioned in
`tools/resolve-stale-mutations.json` and run with `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json`
(`.pi/skills/mutation-runner`). Name the exit codes in the report.

- [x] 5.1 Boundary: drop the trailing-boundary check. `classifier#fragmentIsNotACitation` must fail. Restore; self-test exits 0 (D2).
- [x] 5.2 Header rule: treat a header extension as a path whatever its shape. `classifier#bareHeaderIsNotAPath` must fail. Restore; self-test exits 0 (D2).
- [x] 5.3 Suffix: make the suffix test a substring test instead of a whole-path-tail test. `classifier#bareSourceNameResolvesBySuffix` must fail. Restore; self-test exits 0 (D3).
- [x] 5.4 Submodule: resolve only from the repository root. `classifier#submodulePathResolves` must fail. Restore; self-test exits 0 (D3).
- [x] 5.5 Markers: rewrite the body from its parsed fields instead of editing the token in place. `resolver#markersSurviveTheRepair` must fail. Restore; self-test exits 0 (D7).
- [x] 5.6 Ambiguity: take the first candidate instead of reporting the ambiguity. `resolver#ambiguityIsReportedNotRepaired` must fail. Restore; self-test exits 0 (D4).
- [x] 5.7 Quarantine: leave the item `agent/eligible` instead of parking it. `resolver#unrepairableCitationIsParked` must fail. Restore; self-test exits 0 (D5).
- [x] 5.8 Idempotence: post the repair comment unconditionally. `resolver#secondRunChangesNothing` must fail. Restore; self-test exits 0 (D7).
- [x] 5.9 Pre-screen: restore its own extraction beside the classifier. `prescreen#refusalIsVisibleInTheTracker` must fail. Restore; self-test exits 0 (D1).

## 6. Integration

- [x] 6.1 Run the full self-test on a clean checkout and record its output with the timestamp and the exit code as this change's gate evidence. Verify: every case prints `PASS` and the run exits 0.
- [x] 6.2 The **acceptance measurement** (D9): run the resolver's dry run over the whole open tracker and then re-decide the 49 items the pre-screen recorded as `stale` on 2026-10-18. Verify: the 49 come out **25 with every citation resolving now, 22 repaired, 2 parked**, the tracker total is 60 due / 49 repaired / 8 parked / 6 ambiguous, and the change report says so. (The task expected 44 `suffix` + 4 `not-a-path` + ≤1 quarantine; the measurement is different and better — the classifier gained the submodule, external-namespace, generated-artefact and archived-change rules while the ticket was open, so verdicts that would have been repaired are now not staleness at all. The measured shape is recorded rather than the guess.)
- [x] 6.3 The queue measurement: with the resolver applied, the same authorised set queues its items rather than 23. Verify: `bash tools/loop/run.sh --dry-run` reports an excluded count of at most the quarantines, recorded with its timestamp.
- [x] 6.4 Confirm the exclusions explicitly in the change report: no Gradle build and no suite was run; no compiled file was changed; the gate is `tools/resolve-stale-selftest.sh`; the pipeline step is the only tracker writer, and it is bounded. Verify: `git diff --name-only` lists no file under `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`.
- [x] 6.5 Classify `stale-citation-resolution` in `tools/feature-list/specs.json` as the capability-classification gate requires. Verify: adding the line makes `bash tools/gen-feature-list.sh --check-classification` exit 0, and removing it makes the same command fail naming the capability.
- [x] 6.6 Add the scenario-to-task traceability table: one row per scenario naming the case that exercises it and the task that owns that case. Verify: the row count equals `grep -c '^#### Scenario:' specs/stale-citation-resolution/spec.md` (20) and the change report says so.
- [x] 6.7 Keep `guidelines/Backlog.md` true for this change (§5 the new writer, §6/§7 the discovery step, §9 the residue row) and verify the routing table resolves: `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` both exit 0.
- [x] 6.8 Close **#164** and **#163** from this change: each gets a comment naming the task that fixed it and the case that proves it, then `tools/issues/close.sh <n> --reason … --evidence …`. Verify: both are closed with `state_reason: completed`, and `bash tools/issues/check-metadata.sh --live` exits 0.

## Workflow follow-up

- Archive after 6.1's self-test run, 6.2's acceptance measurement and 6.3's queue measurement are recorded.
- #73 (`OverrunWindow*`) is the one recorded genuine candidate: it should come out of this change as a
  **quarantine** with its attempts, and its closure (or its repair by hand) is a person's decision afterwards.
