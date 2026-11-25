# Tasks: fix-the-usage-case-owns-its-bounds

No compiled file is touched: the delta is one case's body, one `# files:` declaration, one guideline paragraph and one
requirement. The structural check of each file stands in for a build, and the case set the file declarations select
stands in for the suite until the gate runs it once.

## 1. The declaration the case now owes — the cheapest check, first

- [x] `tools/loop/tests/loop-cases.sh:12959`: `tools/loop/loop.conf` added to the case's `# files:` line, because the
      case now reads the profile it copies.
- **Verify**: `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` prints
  `PASS` (seconds, and it is the step that refuses a declaration narrower than the body).

## 2. The case records the two bounds that decide its child count

- [x] `tools/loop/tests/loop-cases.sh:12970-12974`: the case copies the profile into its own scratch, strips
      `LOOP_ITEM_CONTEXT_WARN` and `LOOP_ITEM_CONTEXT_MAX`, records `LOOP_ITEM_CONTEXT_WARN=0` and
      `LOOP_ITEM_CONTEXT_MAX=200000`, and exports `LOOP_CONF` — the seam `tools/loop/run.sh:90` exposes for exactly
      this, as `tools/loop/tests/loop-cases.sh:23133` already does for the context-bound cases.
- **Verify**: `bash tools/loop/tests/loop-selftest.sh --case run#childUsageIsRecordedPerItem` prints `PASS`, and
  `git diff -- tools/loop/tests/loop-cases.sh` shows the two keys written after the copy and nothing else in the conf
  block.

## 3. The count becomes the children's names

- [x] `tools/loop/tests/loop-cases.sh:13029`: the `grep -c '^invocation'` assertion is replaced by the list of
      `prompt:` names the stub recorded, asserted whole — `worker-prompt.md worker-tail-prompt.md` — so a third child
      is named in the failure (issue #1023).
- **Verify**: `bash tools/loop/tests/loop-selftest.sh --case run#childUsageIsRecordedPerItem` prints `PASS`. The harness that
  proves the case still refuses a one-child run is the mutation set's `76.1`
  (`tools/loop/tests/loop-mutations.sh:806`), and it is cited from the gate's mutation step rather than run here —
  that step is the gate's selection and running it now would pay for it twice.

## 4. The red the case could not name, and the green it names now

- [x] `tools/loop/tests/fixtures/pi` mutated with `sleep 3` after the fixture's events (the stub child held past one
      reading of the wait) → `run#childUsageIsRecordedPerItem` FAILs on HEAD with
      `the worker phase is two children, not one: expected '2', got '3'`; the stub restored byte for byte
      (`sha256 c06d45a1…`).
- [x] The same hold on the changed tree → `PASS`, so the third child was the checkpoint's continuation and not the
      phase's.
- **Verify**: `evidence/red-on-HEAD.log` and `evidence/green-with-the-hold.log`, copied into the change when they were
  produced; `sha256sum tools/loop/tests/fixtures/pi` prints `c06d45a1…` afterwards.

## 5. The rule the change lands

- [x] `guidelines/Backlog.recipes.md`, in the section that owns the suite's cases and their `# files:` declaration
      (*The gate and its suite*), a paragraph beside the case pool: a case whose
      count a bound decides records that bound in its own profile copy, and asserts the children by name.
- [x] `openspec/changes/fix-the-usage-case-owns-its-bounds/specs/agent-issue-loop/spec.md`: the requirement and its
      one scenario, naming `run#childUsageIsRecordedPerItem` as its case.
- **Verify**: `openspec validate fix-the-usage-case-owns-its-bounds --strict` prints valid, and
  `tools/look.sh 'whose count a bound can decide'` names the guideline paragraph beside the requirement.

## 6. The structural checks this delta owes instead of a build

- [x] `bash -n tools/loop/tests/loop-cases.sh` — the edited shell file parses.
- [x] `bash tools/check-doc-routes.sh` — the paragraph the guideline gained resolves.
- **Verify**: both print nothing to fix (`check-doc-routes` prints `all N section references resolve`), and
  `./tools/loop/measure.sh --change fix-the-usage-case-owns-its-bounds`'s `LOOP-MEASURE-CHECK` lines are the gate's
  own selection, quoted rather than run.

## 7. Complete the change, then run the gate once

- [x] The XMLs and logs the change cites are copied into `openspec/changes/fix-the-usage-case-owns-its-bounds/evidence/`.
- [x] `bash tools/loop/evidence-check.sh openspec/changes/fix-the-usage-case-owns-its-bounds --root .` from the change
      root, and what it reports is fixed.
- [x] `openspec archive fix-the-usage-case-owns-its-bounds --yes` — the delta merges into
      `openspec/specs/agent-issue-loop/spec.md`.
- **Verify**: `./tools/loop/gate.sh --base 31c1d9db5232ec14e7505c5f3aeb30676939da14` run once, started detached and
  polled; its last line is the `LOOP-GATE:` tally the report quotes.
