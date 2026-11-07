# Evidence — the `./` before a tally is named

The item's block, quoted rather than re-derived (`tools/loop/measure.sh --change fix-loop-gate-tally-is-a-dotfile`,
taken before the gate ran):

```
LOOP-MEASURE base=c03e7e0b66022218d797954099f1957aca34cb55 paths=91 insertions=5024 deletions=42
LOOP-MEASURE-CHECK ./gradlew test -PforceTests --no-build-cache
LOOP-MEASURE-CHECK tools/loop-selftest.sh
LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
LOOP-MEASURE-CHECK tools/loop-mutations-selftest.sh
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHANGE fix-loop-gate-tally-is-a-dotfile scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-TALLY openspec/changes/fix-loop-gate-tally-is-a-dotfile/evidence/green-gate-group.xml tests=9 failures=0
LOOP-MEASURE-TALLY openspec/changes/fix-loop-gate-tally-is-a-dotfile/evidence/red-on-head.xml tests=1 failures=1
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 0 fixtures realigned — `grep -rn "cut -c1-80\|tr '/ ' '__'" tools/loop-cases.sh
LOOP-MEASURE-END
```

That block is measured against `c03e7e0`, the merge base with `origin/main`, so its pathspec carries two other open
changes' committed Kotlin and owes `./gradlew test`. This change's own delta is the four files below `tools/` and
`guidelines/`, and it is what the gate below was measured on. The gate's own line (`evidence/gate.log`) names four
suites and their numbers, each read from its own summary and kept as its own tally beside this file
(`evidence/gate-tallies/`): `tools/loop-selftest.sh` (`tests="188" failures="0"`), `tools/loop-mutations-selftest.sh`
(`tests="34" failures="0"`), `bash tools/loop-mutations.sh --check` (`tests="108" failures="0"`) and
`tools/check-doc-routes.sh` (`tests="0" failures="0"`).

## The files this change leaves

| file | what it carries |
|---|---|
| `tools/loop/gate.sh:173` | `${cmd#./}` — the `./` dropped before the tally is named |
| `tools/loop-cases.sh` | `gate#aCompiledStepsTallyIsNotHidden`, its declaration and its dispatcher line |
| `tools/loop-mutations.sh` | record `51.1` |
| `guidelines/Backlog.md`, section "The loop" | the sentence that a tally's name is never a dotfile |
| `openspec/changes/fix-loop-gate-tally-is-a-dotfile/**` | this delta |

## The runs, with what each one read

- `red-on-head.log`, `red-on-head.xml` — `tests="1" failures="1"`, 2026-10-23T13:42:30Z, the case run against
  `git show HEAD:tools/loop/gate.sh`; the failure message reads the directory it counted and names
  `._gradlew_test.xml` as the only file in it.
- `green-gate-group.log`, `green-gate-group.xml` — `tests="9" failures="0"`, `--group gate` on the fixed tree.
- `gate-first-red.log` — the first gate run, red on `run#engineNamesNoProjectFact`: a comment in `gate.sh` named the
  project's build command. Rewritten to the `./`-form; both affected cases then passed on their own.
- `gate.log`, `gate-tallies/` — the gate over the four suites this delta owes, each green
  (`tests="188" failures="0"`, `tests="34" failures="0"`, `tests="108" failures="0"`, `tests="0" failures="0"`), with
  the item's base (`677a7e6f`) as `--base` so the two other open changes' committed Kotlin is not graded as this
  delta's.
- `revert-check-51.1.log` — `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.
- `mutation-check.log` — `all 5 cases passed — every selected record applies to the file it names` (5 of 108,
  filtered to `tools/loop/gate.sh`).
- `structural-checks.log` — `openspec validate … --strict` valid; `check-doc-routes: all 152 section references
  resolve, all 4 skill references resolve`.

## After the archive

`openspec validate agent-issue-loop --type spec --strict` exits 1 on eight requirements whose bodies exceed 500
characters (the validator's indices 3, 6, 19, 27, 28, 36, 37, 38), and all eight are pre-existing: the spec as HEAD
carries it already has bodies of 788, 522, 521, 889, 516, 1099, 557 and 559 characters
(`git show HEAD:openspec/specs/agent-issue-loop/spec.md`), and the requirement this change added is 306 characters —
it is the file's last requirement and the validator did not warn about it. The delta-only validation this change
passed before the archive is `evidence/structural-checks.log`; the two structural checks the archived pathspec
owes (`tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh`) both exit 0 in
`evidence/structural-checks-post-archive.log`.
