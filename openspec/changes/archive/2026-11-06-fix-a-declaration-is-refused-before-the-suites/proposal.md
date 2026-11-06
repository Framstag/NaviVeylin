# A declaration is refused before the suites, not at the end of the gate

## Why

`tools/loop/gate.sh:158` built the owed list with the compiled step first and the profile's `LOOP_GATE_SUITES` pairs
second, so a delta that writes a case's first-line declaration (`tools/loop-cases.sh`) owed the whole loop suite and
nothing cheaper. The one case that reads a declaration is `driver#everyCaseDeclaresTheFilesItCanReach`
(`tools/loop-cases.sh:10585`), and it is reachable only through that same suite, which runs 326 cases.

**Root cause, in one sentence:** the rule is asserted by the cheapest case in the suite and reachable only through the
most expensive command in the gate, so a declaration one path token short is found at the gate's twelfth minute instead
of in the seconds the case costs — and the attempt that paid it was recorded `blocked` with nothing archived (#790,
2026-11-06T05:16Z–06:04Z: `1 of 320 cases failed` from `tools/loop-selftest.sh --from-git 528d8e2a` after
`./gradlew test`, both in `tools/loop-selftest.sh`'s own gate round).

**The red case, on the base `560edf23`** (`bash tools/loop-selftest.sh --case 'gate#theDeclarationCaseIsOwedFirst'`,
2026-11-06T08:45:09Z):

```
gate#theDeclarationCaseIsOwedFirst|   a delta that writes a case owes the case that reads the declaration: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
gate#theDeclarationCaseIsOwedFirst| tools/loop-selftest.sh --from-git 5b7fbb8a9d31a5ff895cca03fe4e94ab799671dd
FAIL gate#theDeclarationCaseIsOwedFirst
loop-selftest: 1 of 1 cases failed
```

That output is the whole defect: the plan for a delta that writes a case named the build first and the suite second, and
the case that reads the declaration not at all. `evidence/red-on-HEAD.xml` is the tally of that run
(`tests=1 failures=1`), carried beside `evidence/red-on-HEAD.log` because the shell runner writes no JUnit XML; the
same case after the change is `evidence/green-on-change.log` (`PASS`, 2026-11-06T08:45:18Z), so the tree without the
engine change is the red run itself.

**Owning capability and requirement:** `agent-issue-loop`, requirement "A step that refuses a delta in seconds is owed
before the suites" — added: the capability's requirement at `openspec/specs/agent-issue-loop/spec.md:2018` states the
declaration and the case that checks it, and nothing stated where that case's cost is paid.

**Owning guideline section:** `guidelines/Backlog.recipes.md`, the section "The gate and its suite" (`:458`), the
paragraph at `:490`–`:502` that describes the declaration and the case that keeps it honest.

surface: 1 case added (`gate#theDeclarationCaseIsOwedFirst`), 1 profile key added (`LOOP_GATE_FIRST_STEPS`), 1 engine
function extracted (`owe_pairs`, both pair lists read through it), 1 prompt paragraph, 1 guideline paragraph, 1 mutation
record — counted with `bash tools/loop-selftest.sh --list | wc -l` (326 cases), `grep -c '^case_gate_' tools/loop-cases.sh`
(24), `grep -c '^LOOP_GATE' tools/loop/loop.conf` (8), `grep -c "^  '[0-9]" tools/loop-mutations.sh` (246) and
`grep -c '^#### Scenario' openspec/specs/agent-issue-loop/spec.md` (323 — the delta's own scenario is the 323rd).

## What Changes

1. **The gate owes the cheap step first.** A fourth profile key, `LOOP_GATE_FIRST_STEPS` (`tools/loop/loop.conf:535`),
   is the same `<glob>|<command>` pair list as the suite map, and `tools/loop/gate.sh:185` reads it before the compiled
   step and before every suite. Its one pair is
   `tools/loop-cases.sh|tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, so a delta that
   writes a declaration refuses in about ten seconds instead of at the end of the gate. The engine's scripts still name
   no project fact: which command is cheap, and which path owes it, are the profile's.
2. **The child is told to run the plan's first line before anything else.** `tools/loop/prompts/worker.md:215` sends the
   child to that line and names neither the case nor the command, because the prompt is an engine template and either
   value would arrive in another project's child and order work that project does not have. The gate's order saves the
   gate's tail and only the child's step saves the attempt — a `blocked` item's branch is set aside and the whole change
   is worked again.
3. **The one reader stays the one reader.** `driver#everyCaseDeclaresTheFilesItCanReach` and `loop_case_helper_files()`
   are unchanged: this change orders a command the suite already owns and writes no second derivation of the reach table.

## What this does not do

- It does not narrow the child's pre-gate scope beyond this one case: the scope block in `tools/loop/prompts/worker.md`
  is unchanged, and a test-only delta still owes the red case and the affected cases rather than the gate.
- It does not make the suite cheaper. `LOOP_GATE_SUITES` is unchanged, and the full set is still owed before a change
  that owns `tools/loop/**` is archived.
- It does not re-derive the reach table, so a case that calls a helper still declares that helper's files by hand.
