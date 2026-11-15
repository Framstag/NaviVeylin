# The gate's one red step is attributed to work this change does not touch

Phase F, item #867, round 2. The gate was run once, by the gate child, and never re-run here. Its
verdict line, verbatim from `evidence/gate-output-round2.log` (also mirrored at
`items/867/worker-tail-report-round2-gate-output.txt` and `app/build/test-results/loop-gate/gate.log`,
tally run=3928050):

    LOOP-GATE: tests=46 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 407s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s) — failing: bash tools/gen-feature-list.sh --check-classification

Every other step is green, the build included (`BUILD SUCCESSFUL in 6m 45s`,
`evidence/gate-output-round2.log:234`).

## What the failing step printed

`evidence/gate-output-round2.log:238-256` (the step's whole console), verbatim:

    gate: bash tools/gen-feature-list.sh --check-classification
    gen-feature-list: unclassified spec id(s):
      settings-surface (introduced by the open change settings-taxonomy-and-surface — classify it in that change, before it archives)
      settings-taxonomy (introduced by the open change settings-taxonomy-and-surface — classify it in that change, before it archives)
    gen-feature-list: stale classification entry for a spec that no longer exists:
      architecture-test-suite
      search-name-matching
      search-result-row-icon
      start-map-selection
      store-assets
    specs read: 190
    classified: 195
    unclassified: 2
    delta spec ids: 30
    stale: 5
    car-only areas: 1
    highlights: 19
    gen-feature-list: classification gate failed

The step's own JUnit record is `evidence/gate-tallies/bash_tools_gen-feature-list.sh_--check-classification.xml`
(`tests="0" failures="0" errors="1"`), mirrored at `items/867/gate-tallies/`.

**Only the unclassified half fails the step.** `tools/gen-feature-list.sh:220-221` sets
`gate_errors=1` when `unclassified_n > 0`; the stale half at `:252-255` only echoes — the comment at
`:198-199` states the rule ("a delta's line reads as stale until the capability ships, which is
reported and does not fail"). No other `gate_errors` branch fired: none of the messages at `:233-251`
or `:266-270` is on the console, and `car-only areas: 1` satisfies `:248`.

## The two failing ids belong to another change's open delta

`settings-surface` and `settings-taxonomy` are unclassified rows of
`openspec/changes/settings-taxonomy-and-surface/specs/` — the check's own message says so, and the
script derives it from `delta_origins` (`tools/gen-feature-list.sh:191-192, 225-227`). This change
answers for neither:

- that change's spec files carry mtime **2026-11-14 09:37:58** — before this run's base commit
  `4c4e6819` (19:58:58), before this item's first process started (22:03:04) and before this item's
  first commit `bf839e9c` (22:08:24); it is inherited tree state;
- it is gitignored (`.gitignore:76: openspec/changes/*/`, `git check-ignore -v` confirms), so it is
  absent from every diff of this item by construction;
- `git diff --name-only 4c4e681967c30eedc0cdd874a900562ab7f8b32b` (change root) intersects the check's
  inputs — `openspec/specs/`, the open changes' deltas, `tools/feature-list/` — only in
  `openspec/specs/package-structure/spec.md` and `openspec/specs/architecture-test-suite/spec.md`
  (added, then narrowed/deleted by the round-1 corrections); neither id is `settings-surface` or
  `settings-taxonomy`, and `git diff 4c4e6819 -- tools/feature-list` is empty, so the classification
  file is byte-identical to the base. Nothing this change writes can add, remove or classify the two ids.

## Red before this work

- **The failing input predates this item entirely** — the delta existed at 09:37 (above), so the same
  verdict follows from the same bytes at the base commit; the script is deterministic (reads text
  files only, `tools/gen-feature-list.sh:16`).
- **Measured, not inferred:** item #861's gate printed the identical block — same step, same two
  unclassified ids, same `classification gate failed` — at its own HEAD (`0a7b66cae1`), logged at
  18:41:01 today, hours before this item's work began. Copied verbatim into this directory as
  `classification-red-before-867.log` (source: item #861's
  `openspec/changes/archive/2026-11-14-fix-a-flaked-case-is-off-the-blocked-path/evidence/gate-861.log:1393-1410`,
  attributed there in `evidence/gate-red-attribution-861.md` "Red 2").

## Stays red after this work

This gate ran at 23:05:11 (XML mtime), after the last write this change made (corrections and
`proposal.md`, ≤ 22:57:07), and printed the block above. The verdict is a pure function of files this
change never touches, so it stays red whether the corrections stand or not — this measurement is the
"after".

## Disclosure: the stale half names one id of this change

`architecture-test-suite` is stale because the round-1 correction deleted
`openspec/specs/architecture-test-suite/spec.md` (the reviewer's subtractive remedy, recorded in
`evidence/corrections-round-1.md`) while the inherited row
`tools/feature-list/specs.json:12` stays. Two facts keep this off the verdict: the stale half does not
fail the step (`:252-255`, above), and the row was already stale at the base commit — the base ships
neither `architecture-test-suite` nor `package-structure` (`git diff 4c4e6819..HEAD` shows both added
by this item's commits) while carrying both rows (`git show 4c4e6819:tools/feature-list/specs.json`
lines 12 and 143). The correction returns that id to its base state; it did not create the row.

## The clause this close is waived with

    LOOP-GATE-WAIVED: the step's only failing condition is two unclassified ids, settings-surface and settings-taxonomy, introduced by the open change settings-taxonomy-and-surface — another change's open delta, gitignored, created at 09:37 today before this item's base commit (19:58) and first commit (22:08) — and it was red before this work (item #861's gate printed the identical block hours earlier, evidence/classification-red-before-867.log) and stays red after it (this gate at 23:05, after every write this change made, evidence/gate-output-round2.log:240), while nothing this change writes touches those ids: tools/feature-list is byte-identical to the base commit and the delta is outside this diff by .gitignore:76; the step's stale half (5 ids) only prints and does not fail (tools/gen-feature-list.sh:252-255)
    LOOP-GATE-WAIVED-SUITE: bash tools/gen-feature-list.sh --check-classification
