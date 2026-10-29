# Tasks — fix-settled-state-displacement-guard

## 1. The red case (first, before any production code)

- [x] 1.1 `update#refusesToDisplaceASettledState` in `tools/issue-tooling-cases.sh`: a stub tracker item
      carrying `status/retired`, written through `update.sh --status on-hold`; the same write with
      `--displace-settled`; and the flag without a state. *Verify*:
      `bash tools/issue-tooling-selftest.sh --case update#refusesToDisplaceASettledState` FAILs on HEAD
      ("a hold over a settled state is refused: … expected '3', got '0'"), recorded as
      `evidence/settled-displacement-red.xml` (`tests="1" failures="1" errors="0"`, 2026-10-28T19:45:44Z).
- [x] 1.2 `close#namesTheSettledStateItDisplaces` in the same file: a not-planned closure of an item carrying
      `status/fixed-by`. It is the regression case for the closure's half of the rule — it turns red if the
      closure stops being able to replace one settled state with the other, and its recorded green is the
      gate's. *Verify*: `bash tools/issue-tooling-selftest.sh --case close#namesTheSettledStateItDisplaces`
      green.

## 2. The guard

- [x] 2.1 `tools/issues/lib.sh` (the "state namespace" section): `issues_settled_statuses` — the profile's
      `roles.settledStatuses`, qualified with the status prefix, refusing a profile that does not declare the
      role — and `issues_status_settled_guard <number> <named> <displaced-label>…`, which fills
      `ISSUES_SETTLED_DISPLACED` and refuses with `status-settled` when the call names no transition.
      *Verify*: `bash tools/issue-tooling-selftest.sh` green, 127 cases.
- [x] 2.2 `tools/issues/update.sh`: `--displace-settled` (usage error without a state), the guard between the
      read and the write, the note naming the settled value it removed, the header comment and usage line.
      *Verify*: task 1.1's case passes, and the suite's other state-write cases stay green
      (`bash tools/issue-tooling-selftest.sh`, 127 cases).
- [x] 2.3 `tools/issues/close.sh` passes `--displace-settled`; `tools/issues/reopen.sh` passes `named` to the
      same guard with the sentence that says why its verb is the transition.
      *Verify*: `bash tools/issue-tooling-selftest.sh` green; `bash tools/resolve-stale-selftest.sh` and
      `bash tools/sensor-selftest.sh` green (the resolver's restore and the sensor reopen both write through
      these paths).

## 3. The rule where a reader looks for it

- [x] 3.1 `guidelines/Backlog.md` §4 gains invariant I18 (the claim, what holds it, the observation that
      falsifies it). *Verify*: `bash tools/check-doc-routes.sh` and `bash tools/check-todo-references.sh` green.

## 4. The revert-check

- [x] 4.1 `tools/status-mutations.json` mutation `8.1`: the guard's `named` branch forced open, so every call
      passes; `update#refusesToDisplaceASettledState` must go red. *Verify*: the whole set,
      `bash tools/mutate.sh --mutations tools/status-mutations.json`, reports
      `mutate: 6 mutation(s) run, 6 went red and restored green, 0 problem(s)` (2026-10-28T19:59:13Z–19:59:31Z);
      the red's own message and the restored tree's `PASS` are quoted in `evidence/revert-checks.md`.

## 5. The gate, once

- [x] 5.1 `bash tools/check-portability.sh` and `bash tools/check-portability-selftest.sh` green before the
      gate, so a project value leaking into the guard is found here rather than by the pipeline.
- [x] 5.2 `./tools/loop/gate.sh`, run once from the change root; its line quoted into `evidence/gate.md`
      beside the per-suite tallies. No compiled file is touched (`tools/**`, `guidelines/**`, `openspec/**` —
      `guidelines/Build.md` §2, the scope block), so the structural and documentation checks stand in for a
      build and the gate's own path map decides which suites are owed.

## 6. Evidence and archive

- [x] 6.1 Copy every cited XML into `<change>/evidence/` and into the item's evidence directory; write
      `evidence/measure.txt` from the one measurement of this change's own delta.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-settled-state-displacement-guard --root .`
      reports no dangling pointer.
- [x] 6.3 Archive the change; confirm the directory exists under `openspec/changes/archive/` and the delta
      merged into `openspec/specs/issue-backlog/spec.md`.
- [x] 6.4 The delta's every scenario names one case, and each of those cases is green in the recorded run.

## 7. The review's corrections (round 2)

- [x] 7.1 The gate did not cover the reviewed tree: it ran before the archive moved and before the delta merged
      into `openspec/specs/issue-backlog/spec.md`, so the map's `openspec/*` rows were never selected. *Verify*:
      `./tools/loop/gate.sh` re-run once on the archived tree — its `LOOP-GATE:` line and every per-suite tally
      in `evidence/gate.md`, whose step list carries `tools/check-openspec-hygiene.sh`,
      `tools/check-openspec-hygiene-selftest.sh` and `bash tools/gen-feature-list.sh --check-classification`.
- [x] 7.2 The merged requirement stated the naming obligation for every call that names the transition, and a
      reopen names it without reporting the value it removed. *Verify*: the item's decision (2026-10-29, comment
      on #684) took the **report** and not a narrower requirement — `tools/issues/reopen.sh` reports the settled
      value the guard found, as a clause on the line a person reads (`reopened #N (occurrence X, status/open,
      removed status/fixed-by)`) and as a `removed` list in the `--json` document; both branches are asserted by
      the portability suite's foreign reopen case, whose fixture item 11 carries `status/fixed-by`, with a second
      closed fixture for the text line. Green: `tools/check-portability-selftest.sh` (29 cases) and the gate
      quoted in `evidence/gate.md`.
- [x] 7.3 The revert-check quoted a summary line this tree's runner does not print, and its "other entries still
      pass" named a run that did not happen. *Verify*: the whole set run and its own counts quoted, the red's
      assertion message and timestamps recorded, the restore's byte-identity recorded, in
      `evidence/revert-checks.md`.
- [x] 7.4 The pointers: `proposal.md` names `issues_status_settled_guard` and the re-measured file count (10,
      not 8); `measure.txt` is re-taken on the final tree (`base=a86ff487`, `paths=34 insertions=829 deletions=7`).
- [x] 7.5 `git rebase main` before the last gate: `main` had moved five commits (#680 and the module-pin repair),
      the tail of `openspec/specs/issue-backlog/spec.md` conflicted with #680's requirement there and **both**
      were kept, and the stub `tools/issues/fixtures/gh` #680 changed is the one the suite ran against — which is
      why the issue tooling's suite counts 128 cases in this gate and 127 in the pre-rebase one.
