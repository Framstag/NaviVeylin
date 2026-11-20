# Tasks — the classifier's two report-only states owe a versioned mutation set

Spec: `spec-feature-index` (delta `specs/spec-feature-index/spec.md`, one requirement ADDED, six scenarios).

Scope note: no compiled file changes — a bash suite, a record set, four `loop.conf` entries, one workflow step and one
guideline section — so no Gradle build is owed (`guidelines/Build.md` §2, the scope block) and the structural checks of
the files edited stand in for it: `bash tools/check-rule-routes.sh`, `bash tools/gen-feature-list.sh
--check-classification`, `bash tools/check-doc-values.sh`, `bash tools/check-todo-references.sh`. The change's gate is
the child this run starts after the diff (`tools/loop/prompts/worker-gate.md`).

## 1. The suite can name one case

- [x] 1.1 `tools/tests/gen-feature-list-selftest.sh` gains `--case <name>`, filtering in the `ok`/`bad` helpers the
  file's assertions already pass through, so the fixture commands a case shares still run and only the other cases'
  assertions are skipped. *Verification*: `bash tools/tests/gen-feature-list-selftest.sh --case "gate: a delta-only
  classification is reported as pending, not as stale"` prints `1 passed, 0 failed` (`evidence/selftest-green.log`
  holds the unfiltered run: `144 passed, 0 failed`).
- [x] 1.2 `--case` naming no case exits 2 after the run, never 0. *Verification*: `bash
  tools/tests/gen-feature-list-selftest.sh --case "no such case"` → exit 2, `no case is named '…'` on stderr.
  Satisfies `A filter that names no case runs no pass`.

## 2. The record set

- [x] 2.1 `tools/tests/gen-feature-list-mutations.json`: five records, one per expression — `tools/gen-feature-list.sh`
  `:204` (the not-shipped set both states subtract from), `:205`/`:206` (the two states), `:209`/`:210` (the two
  figures). Each names the case it reds and a `case-cmd` of `bash tools/tests/gen-feature-list-selftest.sh --case
  '{case}'`, so one mutation pays one case and not the whole suite. *Verification*: `bash tools/mutate.sh --mutations
  tools/tests/gen-feature-list-mutations.json --list` prints `5 mutation(s)` and exit 0.
  Satisfies `The set names the two states and their two figures`.
- [x] 2.2 The suite gains the `mutation:` case — one name, six assertions — that pins the set: five records, each
  naming the classifier, the `--case` command, a `case` this suite records, and a `search` that still quotes exactly
  one line of the tool, plus the harness's own `--list` exiting 0. *Verification*: `bash
  tools/tests/gen-feature-list-selftest.sh --case "mutation: the classification's two sets and their counts are held by
  a record set"` prints `6 passed, 0 failed`; on the base the same case fails with `expected [5], got [0]`
  (`evidence/red-on-base.log`). Satisfies `A record a moved expression orphaned fails the case that pins the set`.
- [x] 2.3 Revert-check the whole set: every record reds the case it names with the mutation in place and restores green.
  *Verification*: `bash tools/mutate.sh --mutations tools/tests/gen-feature-list-mutations.json --only <label>` for each
  of `926.1`–`926.5` → `1 mutation(s) run, 1 went red and restored green, 0 problem(s)`
  (`evidence/mutation-set-per-record.log`); the same probe
  against the base tree, run before the set existed, gives the item's own Consequence its measurement
  (`evidence/base-suite-mutation-probe.log`). Satisfies `A pending entry computed as the remainder …`, `A stale entry
  computed as the intersection …`, `The not-shipped set read as the intersection reds the stale-entry case`.

## 3. The declarations

- [x] 3.1 `tools/loop/loop.conf` gains four `LOOP_GATE_SUITES` entries: `tools/gen-feature-list*.sh`,
  `tools/gen-feature-list.sh` and `tools/tests/gen-feature-list-selftest.sh` each owe the set's `--list`, and the
  selftest owes itself. *Verification*: `tools/look.sh 'gen-feature-list-mutations' --files` names `tools/loop/loop.conf`
  among the eight files that hold the set's name.
- [x] 3.2 `.github/workflows/build.yml` prints the set's `--list` beside the suite in the existing feature-list tooling
  step, so a set that no longer parses fails the pipeline. *Verification*: the step's two commands are the suite and
  `bash tools/mutate.sh --mutations tools/tests/gen-feature-list-mutations.json --list`.

## 4. The rule's owner

- [x] 4.1 `guidelines/FeatureList.md` §4: the two-state paragraph names the set, the command that runs it, the
  `mutation:` case that keeps it honest, and `tools/loop/loop.conf`'s entry; the section's case list gains the
  `mutation:` case. *Verification*: `bash tools/check-rule-routes.sh` → `every glob matches the tree and all 652
  named rule(s) are reached`; `bash tools/check-doc-values.sh` green.
- [x] 4.2 The item's own Consequence sentence is measured rather than repeated: `evidence/base-suite-mutation-probe.log`
  records five of five mutations red on the base tree, so `proposal.md` and `design.md` state the absence as the
  *versioned* form and not as unguarded behaviour.
