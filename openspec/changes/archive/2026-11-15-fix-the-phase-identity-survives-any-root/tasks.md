# Tasks — the phase answer keeps its identity on any root

The implementation of this change landed before the change was written, as commit `072ca76c` (merged `31750871`).
These tasks record what that commit does, where each part of it is, and what the change cannot show.

## 1. The relative form

- [x] 1.1 `tools/loop/phase.sh:87-92`: `phase_path_from_root` prints a path relative to `LOOP_ROOT` when it lies inside
  it, and unchanged when it does not — the `ROOT:` line is what makes the relative form resolvable, so a path outside
  the root keeps the absolute form instead of becoming a name nobody can follow. — *spec: "A bounded phase answer keeps
  the state and the package's head", paragraph 2; verified by `phase#theIdentitySurvivesTheBoundOnAnyRoot` asserting
  the package path, the gate's stream and the evidence directory in their relative form (`evidence/readings.md`, R5).*
- [x] 1.2 `tools/loop/phase.sh:129`: the one `ROOT:` line, printed second, after the phase line and before the item. The
  nine paths the answer carries follow it through the helper (`:145`, `:148`, `:150`, `:174`, `:189`, `:191`, `:196`,
  `:207`, `:211`). — *verified by the case's `grep -cF -- "$root"` assertion that exactly one line of the answer carries
  the root (`tools/loop/tests/loop-cases.sh:18488-18489`).*
- [x] 1.3 `tools/loop/shared.tsv:79`: the `LOOP_ROOT` row gains `phase.sh`, because the script reads the variable and the
  portability check reads that table as the declaration. — *verified by `tools/check-portability.sh` in
  `evidence/structural-checks.txt`.*

## 2. The order a cut leaves whole

- [x] 2.1 `tools/loop/phase.sh:137-151`: the package's identity — `PACKAGE:` with its fingerprint and provenance, and
  `ACCEPTANCE:` with the title it is accepted against — is printed before the tree, the gate, the paths and the
  checklist, and the item's own text stays last (`:219-223`), so the bound takes the tail and not the identity. —
  *verified by mutation `927.1`, which prints the checklist in the identity's place and reddens the case.*
- [x] 2.2 `tools/loop/phase.sh:226-235`: the cut notice names the directory the whole package is in, in the same
  relative form, so a reader whose answer was cut can still read the package whole. — *verified by the case's
  `PHASE-BOUND` assertion and by `phase#aResultOverTheBoundIsCutAndNamed`, which carries the notice's own text.*
- [x] 2.3 No configuration value moved: `LOOP_TOOL_RESULT_MAX_CHARS` stays `4000` (`tools/loop/loop.conf:463`), because
  the repair is the order and the relative form, not a wider window. — *verified by the recorded value in
  `evidence/readings.md`, R1.*

## 3. The cases

- [x] 3.1 `tools/loop/tests/loop-cases.sh:18461`: new case `phase#theIdentitySurvivesTheBoundOnAnyRoot`. It pins its own
  bound (`pinned=2000`, `:18466`), asserts the answer is at or under it, that it says it was cut, and that the
  fingerprint, the provenance, the acceptance title, the package path, the gate verdict, the evidence directory and the
  one `ROOT:` line are all there — on the suite's fixture root (`:18493-18494`), on a root over 120 characters long
  (`:18498-18502`), and at the recorded bound on that long root (`:18506-18512`). — *spec: scenario "The identity
  survives the bound on a root of any depth".*
- [x] 3.2 `tools/loop/tests/loop-cases.sh:18306-18310`: `phase#oneCallPrintsThePhaseState` runs with
  `LOOP_TOOL_RESULT_MAX_CHARS=0` exported, because it asserts the shape of the whole answer and a cut it did not ask
  for would leave its verdict to the length of the fixture's root — the defect 3.1 owns. Its assertions are otherwise
  unchanged. — *spec: scenario "One call prints the phase state".*
- [x] 3.3 Registry entries: `tools/loop/tests/loop-cases.sh:452` and `:454` (the case set) and `:1193` and `:1195` (the
  dispatcher arms), so both names run by name and in the pool. — *verified by
  `driver#everyCaseIsNamedInTheRegistry`, part of the loop self-test the gate runs.*

## 4. The falsification

- [x] 4.1 `tools/loop/tests/loop-mutations.sh:1342`: record `927.1` — the checklist printed where the identity was
  printed, so a cut that lands on the checklist takes the fingerprint, the provenance and the path that names the
  package. — *verified: it names `phase#theIdentitySurvivesTheBoundOnAnyRoot` as the case that must red, and the
  commit's own report records it red then green.*
- [x] 4.2 `tools/loop/tests/loop-mutations.sh:1343`: record `927.2` — every path under the root printed with the root
  again, so the bound is spent on a prefix the reader already holds. — *verified by the same case, whose `ROOT:`-once
  assertion and identity assertions fail together under it.*

## 5. The requirement

- [x] 5.1 `openspec/changes/fix-the-phase-identity-survives-any-root/specs/agent-issue-loop/spec.md`: a `MODIFIED` block
  that replaces "A bounded phase answer keeps the state and the package's head"
  (`openspec/specs/agent-issue-loop/spec.md:4964`) whole — its shipped paragraph and its one scenario verbatim, in
  order, plus a paragraph on the order and the relative form, a paragraph on what a case that asks the cut must pin,
  and two scenarios. — *verified by `openspec validate fix-the-phase-identity-survives-any-root --strict`
  (`evidence/openspec-validate.txt`).*

## 6. The gate, and what it does and does not say

- [x] 6.1 The gate that landed `072ca76c` is the loop's own run, and its per-suite tally is **not retained** in this
  checkout: the run record is gitignored and machine-local, and this change was written after the run. No figure is
  quoted here, because quoting a tally no artifact carries would be the claim `tools/loop/evidence-check.sh`'s C2
  exists to refuse. A reader who needs it re-runs `bash tools/loop/tests/loop-selftest.sh`.
- [x] 6.2 What this change could take instead, and took: the recorded bound and each printed line's place
  (`evidence/readings.md`), the two case bodies and their assertions by line, the two mutation records verbatim from
  the suite's own set, and the structural checks of 7.1.

## 7. The checks this change owes

- [x] 7.1 `openspec validate fix-the-phase-identity-survives-any-root --strict`, `openspec doctor`,
  `bash tools/check-doc-routes.sh`, `bash tools/check-openspec-hygiene.sh`, `tools/check-portability.sh` and
  `bash tools/loop/evidence-check.sh <this change>` — each re-runnable and recorded in
  `evidence/structural-checks.txt` with its verdict.
- [x] 7.2 The delta touches no compiled file — one shell script, its tables and its suite — so this change runs no
  build, no Android test and no native step. The suite it owes is the loop self-test, which the loop's own gate ran on
  the commit; this change does not re-run it.

## 8. What this change does not cover

- [x] 8.1 Not covered: the phase answer's *content* beyond the order and the relative form. What each line reports —
  the phase the state records, the gate arithmetic, the write surface per phase — is the subject of other requirements
  and is unchanged here.
- [x] 8.2 Not covered: a reader that resolves a relative path against a root other than the `ROOT:` line. The notice and
  every path are printed relative to that line and to nothing else, so a caller that discards it has no second way to
  resolve them; that a caller keeps it is not asserted by any case, and this change adds none.
- [x] 8.3 Not covered: `tools/pi-child-events.sh`'s own bound. A child's tool result is bounded by the hook the tail
  carries (`LOOP_TOOL_RESULT_MAX_CHARS` read through it), and this change only shapes what `phase.sh` prints inside
  that bound.
