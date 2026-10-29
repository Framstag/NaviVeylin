# Evidence — fix-ui-navigation-content-zero-distance

## Round 2 — the gate over the reviewed pathspec

Round 1's review refused this change on one blocking finding: `evidence/gate.log` recorded a real execution, but
not one over the reviewed pathspec — the diff carries the archive move, the profile maps `openspec/*` to
`bash tools/gen-feature-list.sh --check-classification`, `tools/check-openspec-hygiene.sh`,
`tools/check-openspec-hygiene-selftest.sh` and `tools/check-doc-routes.sh`, and only the last had run. Round 1's
gate measured the guideline alone because the archive move was still uncommitted and `openspec/changes/*/` is
gitignored (`tools/loop/loop.conf`, `LOOP_UNTRACKED_RECORD_DIR`).

This round re-ran the change's gate once, over the reviewed pathspec, and touched no production file: the diff's
one code hunk (`guidelines/UI.md`) and the red→green probe are exactly round 1's.

Round 1's block read `paths=1` — the guideline alone — because the tool compares against the base. Before this
round's gate the same reading was `paths=20` (`guidelines/UI.md` plus
the 19 archived paths the move, its evidence and round 1's review note carry); the block in
`evidence/measure.txt` is the end-of-round reading, taken after the gate had written its seven tallies and this
file had changed, and it reads `paths=24`. `insertions=` is not quoted here for that reason: it grows with every
line these artifacts gain, so the tool's own file is the place it is read from.

## Measurement block

The block's stable lines for this change (the whole block, verbatim as the tool printed it, is
`evidence/measure.txt`):

```
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
LOOP-MEASURE-SUITE cases=276 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=7 suites=7
LOOP-MEASURE-CHANGE 2026-10-29-fix-ui-navigation-content-zero-distance scenarios=0 cases=0 unnamed=0
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/bash_tools_gen-feature-list.sh_--check-classification.xml tests=0 failures=0 time=4s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-doc-routes-selftest.sh.xml tests=0 failures=0 time=1s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-doc-routes.sh.xml tests=0 failures=0 time=0s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-openspec-hygiene-selftest.sh.xml tests=6 failures=0 time=0s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-openspec-hygiene.sh.xml tests=0 failures=0 time=1s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-todo-references.sh.xml tests=0 failures=0 time=1s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_mutate.sh_--mutations_tools_check-doc-routes-mutations.json_--list.xml tests=0 failures=0 time=0s
LOOP-MEASURE-SURFACE surface: 1 guideline bullet edited, 1 evidence probe case added, 0 Gradle cases added, 0 cases rewritten, 0 fixtures realigned — …
```

## Red and green cases

`evidence/red-on-head.xml` — `tests=1 failures=1`, timestamp `2026-10-29T07:30:05Z`:

```
FAIL ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet does not state:
  [known zero] [`0 m`] [absent or `NaN`] [`--`] [stats line] [hint's secondary text]
  [navigation-ongoing-notification]
```

`evidence/green-after-fix.xml` — `tests=1 failures=0`, timestamp `2026-10-29T07:30:48Z`:

```
PASS ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet states the zero-versus-unknown distance rule on both surfaces
```

## Gate

`./tools/loop/gate.sh` ran once over the reviewed pathspec, from the change root, at 2026-10-29T07:36Z; its
transcript is `evidence/gate.log`, its seven tallies are `evidence/gate-tallies/`, and its summary line is:

```
LOOP-GATE: tests=6 failures=0 errors=0 (bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 1s, tools/check-openspec-hygiene-selftest.sh 0s)
```

The `tests=6` is `check-openspec-hygiene-selftest.sh`'s own count (`check-openspec-hygiene-selftest: all 6 cases
passed (throwaway OpenSpec trees, no Gradle)`, the only suite of the seven that reports per-case XML). The other
six print their counts in the transcript and their tallies are `tests=0` because `gate.sh` derives a tally from a
summary line of the form `all N cases passed`, which they do not emit: `check-doc-routes: all 155 section
references resolve, all 4 skill references resolve`, `check-doc-routes-selftest: 19 passed, 0 failed`,
`check-todo-references: 198 line(s) scanned, 62 distinct citation(s), 162 in the recorded table`.

`gen-feature-list.sh --check-classification` exits 0 and prints three pre-existing stale entries
(`search-name-matching`, `search-result-row-icon`, `start-map-selection`). Neither the entries nor the specs they
name are touched by this change: `git diff --name-only 42d3e00` lists `guidelines/UI.md` and this change's own
`openspec/**` paths, no `tools/feature-list/*` path and no `openspec/specs/**` path.

`gate.sh` prints no timestamp of its own (`gate.log` and the tallies carry none; the tallies carry the gate's
per-suite seconds, and `LOOP-MEASURE-GATE seconds=7 suites=7` re-reads them), so the gate's position in the
day's sequence rests on the transcript's copy time, 2026-10-29T07:37Z, which is after the probe's green run at
07:30:48Z.

## Validation

`openspec validate fix-ui-navigation-content-zero-distance --strict` → `Change
'fix-ui-navigation-content-zero-distance' is valid` and the INFO that `skip_specs` is set
(`evidence/openspec-validate.log`). No capability spec changes: this is a documentation-only change. The archive
move is confirmed by `evidence/evidence-check-archived.log` (`evidence-check: PASS`).
