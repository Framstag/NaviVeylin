# Tasks — the change-readiness gate

## 1. The checker

- [x] 1.1 Read the input shape from the shipped changes and freeze it: `design.md`'s `## Open decisions` section,
      the `**Case**:` line of each `#### Scenario:` in `specs/**/spec.md`, the `surface:` line in `proposal.md`.
      Record in the script's header the six refusal names and the one verdict prefix, because C2 quotes them.
      Verification: `grep -c 'LOOP-CHANGE-READY' tools/check-change-readiness.sh` is ≥ 1 and the header lists six
      refusals.
- [x] 1.2 `tools/check-change-readiness.sh <change-dir>…` — argument parsing, `--all` over `openspec/changes/*/`,
      `--help`, and the usage exit 2. No tracker, no git, no network.
      Verification: `tools/check-change-readiness.sh --help` exits 0 and names both forms.
- [x] 1.3 The five findings and the one unreadable-path refusal, each emitted as `name=artifact:line` on the single
      `LOOP-CHANGE-READY:` line; exit 0 only for `ok`.
      Verification: `tools/check-change-readiness.sh openspec/changes/change-readiness-gate` prints exactly one
      verdict line (the change declares `none`, names its cases and quotes its surface).
- [x] 1.4 `--json`, one object: `{verdict, refusals: [{name, path, line, detail}], checked: [paths]}`.
      Verification: `… --json | jq -e '.verdict'` succeeds and the refusal count equals the count on the verdict
      line.

## 2. The cases

- [x] 2.1 `tools/check-change-readiness-selftest.sh` with a scratch root under the loop's recorded temporary root
      and a trap on every signal that removes it — the shape `tools/check-doc-routes-selftest.sh` uses.
      Verification: the self-test exits 0 on a tree whose only change is this one.
- [x] 2.2 One case per refusal, six in all, and three passes: `readiness#declaredNone`, `readiness#openDecision`,
      `readiness#missingDeclaration`, `readiness#deferralIsNotAFork`, `readiness#scenarioWithoutCase`,
      `readiness#caseNoneWithReason`, `readiness#scenarioWithCase`, `readiness#surfaceMissing`,
      `readiness#surfaceCommandMissing`, `readiness#surfacePresent`, `readiness#oneVerdictLine`,
      `readiness#wordingIsNotAVerdict`, `readiness#changeUnreadable`.
      Verification: `bash tools/check-change-readiness-selftest.sh --list` names every one of them, and the run
      prints one `PASS`/`FAIL` line each.
- [x] 2.3 The `readiness#deferralIsNotAFork` case builds its fixture from a copy of a real in-flight `design.md`
      section — a `## Open Questions` entry that defers — so the case is about the tree and not about a
      string invented for it.
      Verification: the case fails when the checker is mutated to read `## Open Questions` (see 3.1).

## 3. The mutations

- [x] 3.1 `tools/check-change-readiness-mutations.json` — one record per refusal, each naming the case that must go
      red, in the shape `tools/mutate.sh` already runs (`tools/mutate.sh --mutations <file> --list`).
      Verification: `bash tools/mutate.sh --mutations tools/check-change-readiness-mutations.json --list` prints one
      record per refusal, and `--check` applies and restores each.
- [x] 3.2 Run the list against the tree: every mutation makes its named case fail, and the harness leaves the tree
      exactly as it found it.
      Verification: the run's summary is `n` red of `n`, and `git status --porcelain` is unchanged afterwards.

## 4. The gate's pathspec

- [x] 4.1 Three `LOOP_GATE_SUITES` entries in `tools/loop/loop.conf`, in the shipped shape
      `tools/check-change-readiness*|<command>`: the checker itself, its self-test, and its mutation list.
      Verification: `bash -c 'source tools/loop/loop.conf; printf "%s\n" "$LOOP_GATE_SUITES"' | tr ';' '\n' | grep -c
      check-change-readiness` is 3.
- [x] 4.2 The declared case count moves with the three entries: whatever `tools/loop-selftest.sh --list` reports
      before and after is recorded in the change's `evidence/`, because the gate's case count is evidence and not a
      recollection.
      Verification: `evidence/suite-cases.txt` carries both numbers and the command that printed them.

## 5. The document

- [x] 5.1 `guidelines/Backlog.md` §6 "How an item becomes work": one paragraph naming the checker, the declaration
      it reads and the refusal vocabulary — no restatement of the rules, a reference to this capability.
      Verification: `grep -n 'check-change-readiness' guidelines/Backlog.md` finds the paragraph, and
      `tools/check-doc-routes.sh` passes.
- [x] 5.2 Run the checker over the tree's 15 open changes and record the result in `evidence/readiness-on-open-
      changes.txt`, with the command beside it.
      Verification: the file carries the command and one line per change; every refusal is named in
      `design.md`'s "Not in this change" or fixed.

## 6. Verification and archive

- [x] 6.1 No compiled file is touched, so the Gradle gate is not owed: state the structural checks that stand in
      for it — the self-test, the mutation list, `tools/check-doc-routes.sh` and
      `openspec validate change-readiness-gate --strict`.
      Verification: `openspec validate change-readiness-gate --strict` exits 0.
- [x] 6.2 `bash tools/loop/evidence-check.sh openspec/changes/change-readiness-gate --root .` resolves every pointer
      this change writes.
      Verification: exit 0, and the report is copied into `evidence/`.
- [x] 6.4 `tools/feature-list/specs.json` gains the classification entry for the new capability `change-readiness`,
      in the area `build-tooling`, because an unclassified capability stops the feature-list run
      (`guidelines/FeatureList.md` §4).
      Verification: `bash tools/gen-feature-list.sh --check-classification` reports no unclassified entry for
      `change-readiness`.
- [x] 6.5 Archive the change and confirm `openspec/specs/change-readiness/spec.md` exists with its `## Purpose`
      written by this change rather than by the archiver's placeholder.
      Verification: `openspec show change-readiness --type spec --json | jq -r .purpose` is not the `TBD` sentence.
