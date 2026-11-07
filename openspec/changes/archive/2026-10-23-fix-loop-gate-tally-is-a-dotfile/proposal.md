# A tally's name is a file a reader finds, not a dotfile

## Why

**Verified in the tree at the base commit `677a7e6f`.** `tools/loop/gate.sh:173` built each tally's file name from
the suite's command with only two characters mapped — `/` and space — so the `.` of a command the profile runs from
the project root survived into the name:

```
name=$(printf '%s' "$cmd" | tr '/ ' '__' | cut -c1-80)
```

and the profile's own compiled command is such a command (`tools/loop/loop.conf:139`,
`LOOP_GATE_COMPILED_CMD="./gradlew test -PforceTests --no-build-cache"`), under the tally directory the profile
names at `tools/loop/loop.conf:138`. The one tally that records the build was therefore written as
`._gradlew_test_-PforceTests_--no-build-cache.xml` — a dotfile.

**Root cause, one sentence.** The file name is the command with `/` and space sanitised and the leading `.` of `./`
left in place, so the tally of a `./`-prefixed command is a hidden file that a plain `ls` and a `*.xml` glob do not
list.

**What it cost, as the item measures it.** The round-3 reviewer of #273 counted the archived change's
`evidence/gate-tallies-round3/` with `ls`, read five files where the change's artifacts said six, and refused the
round on a copy it read as short — a `BLOCKING:` finding about a tally that was present. The readers that resolve it
(`.pi/skills/fix-loop/scripts/evidence-check.sh` C2's `grep -r`, `tools/loop/measure.sh:93`'s `find -name '*.xml'`)
see dotfiles; every reader that lists or globs — a shell `*.xml`, a plain `ls`, a person, an agent — misses that one
file, and it is the only tally of the compiled step.

**The red test.** `gate#aCompiledStepsTallyIsNotHidden` (`tools/loop-cases.sh`, added by this change): a throwaway
project whose profile names the compiled command `./gradlew test` and a `gradlew` stub at the project root, and a
`.kt` path the profile's compiled patterns owe the build for. The case asks whether a plain `ls` prints the tally
(without `-A`) and whether the shell glob `tally/*.xml` finds it. Taken against the engine's `gate.sh` as HEAD
carries it (`git show HEAD:tools/loop/gate.sh`), 2026-10-23T13:42:30Z:

```
  a plain ls prints the compiled step's tally (the directory holds: ._gradlew_test.xml ): expected '1', got '0'
FAIL gate#aCompiledStepsTallyIsNotHidden
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-head.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-on-head.log` the transcript
and the engine the case was run against.

**Owning requirement.** `agent-issue-loop`. The spec owns every other property of the record the run's own check
reads — the numbers, the error count, the wall clock, the one observation the tally and the console share — and none
of them names the file the record is written to; this delta adds the one requirement that does.

**Owning guideline section.** `guidelines/Backlog.md`'s section "The loop" (`guidelines/Backlog.md:279`–`287`), the
paragraph that states what a suite's numbers are (a suite that fails without a summary contributes an error, never a
case) after the paragraph on the streamed gate. This change adds the sentence that says a tally's name is a file a
reader finds; no existing sentence asserts the naming and none is rewritten.

## What Changes

- **`tools/loop/gate.sh`** — the tally's name is built from the command with its `./` prefix dropped
  (`tools/loop/gate.sh:173` at the base, `tools/loop/gate.sh:178` as this change leaves it): `${cmd#./}` before the
  `/`/space mapping, so `./gradlew test -PforceTests
  --no-build-cache` becomes `gradlew_test_-PforceTests_--no-build-cache.xml`. The rest of the name is still the
  command, so a reader that matches a tally against the suite that wrote it still can, and the sanitising, the
  80-character cut and every other property of the tally are unchanged.
- **`tools/loop-cases.sh`** — one case, `gate#aCompiledStepsTallyIsNotHidden`, declared in the case list and named
  in the dispatcher, asserting that a plain `ls` prints exactly one file and that the `*.xml` glob is the file
  `gradlew_test.xml`.
- **`tools/loop-mutations.sh`** — record `51.1`, which puts the `./`-carrying name back, so the invariant is
  falsified by a mutation like every other: the named case must go red and its restore green.
- **the spec `agent-issue-loop`** — one ADDED requirement, "A tally's name is a file a reader finds", with its one
  scenario naming that case.
- **`guidelines/Backlog.md`**, its section "The loop" — one sentence in the paragraph on a suite's numbers: a tally's name is the
  command, never a dotfile.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `grep -rn "cut -c1-80\|tr '/ ' '__'" tools/loop-cases.sh
tools/loop/ tools/loop/fixtures tools/loop/prompts` (one site, `tools/loop/gate.sh`), and no case or fixture names a
tally file except through the `tally/*.xml` glob (`grep -rn 'tally/[^ "]*\.xml\|_gradlew' tools/loop-cases.sh
tools/loop/ tools/loop/prompts tools/loop/fixtures`).

Surface, before the plan was fixed: the rule that names a tally appears once in the engine
(`tools/loop/gate.sh:173`), no case asserts a tally's name (the six existing assertions read `"$d"/tally/*.xml` with
`cat`, so a hidden sibling would have been invisible to them too), no prompt names the tally's file name
(`tools/loop/prompts/worker.md:37` and `tools/loop/prompts/reviewer.md:44` speak of the tally, never of its path), and no fixture under
`tools/loop/fixtures/` carries a `./` command — **1 case added, 0 rewritten, 0 fixtures realigned**, plus one
mutation record added. The change therefore touches one engine line, one case, one mutation record, one spec delta
and one guideline paragraph: no compiled file, so the gate owes the shell suites and nothing of Gradle's
(`tools/loop/loop.conf:152`).
