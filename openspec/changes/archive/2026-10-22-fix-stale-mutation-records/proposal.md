# Fix the two mutation records that name a line the engine no longer has, and give the set a check

## Why

Issue #264. The loop's mutation harness (`tools/loop-mutations.sh`) declares 87 records, and a record quotes one
line of one engine file as a `sed` pattern. Two of them quoted `run_child`'s child-start line as it stood before
issue #227 gave that line the child's own completion record:

- the line today, `tools/loop/run.sh:668`:
  `( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1"; printf '%s\n' "$?" > "$2.status" ) > "$2" 2>&1 < /dev/null &`
- the line records `9.9` and `12.2` quote:
  `( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1 < /dev/null &`

**Root cause.** A record quotes a literal line, and nothing compared a record with the file it names, so an ordinary
edit to the file — #227's, to the tail of exactly this line — orphaned two records silently. Both then reported
`MUTATION-DID-NOT-APPLY`, so neither `$LOOP_AGENT_CMD`'s single indirection nor the child's working directory was
falsified by anything: their cases (`state#agentCommandIsTheOnlyIndirection`,
`run#childStartsInTheProjectRoot`) still passed, because a record that mutates nothing fails no case.

**Red on HEAD**, base `9f10749` (`.pi/issue-loop/items/264/red-on-head.log`, `mutations-baseline.log`):

```
$ bash tools/loop-mutations.sh --only '9.9 12.2'
MUTATION-DID-NOT-APPLY 9.9  …/tools/loop/run.sh (the pattern no longer matches — fix the mutation, not the case)
MUTATION-DID-NOT-APPLY 12.2  …/tools/loop/run.sh (the pattern no longer matches — fix the mutation, not the case)
red-when-mutated: 0 · not-noticed: 2 · restore-failures: 0        exit=1
```

and the three cases this change adds, all `FAIL` on HEAD:

```
FAIL mutation#everyDeclaredRecordApplies
  every declared record applies to the file it names — the check printed: loop-mutations: unknown argument --check
FAIL mutation#aStaleRecordIsNamed
  a record whose expression names no line fails the check: …: expected '1', got '2'
FAIL gate#anEnginePathOwesTheRecordCheck
  tools/loop-selftest.sh: 'loop-mutations.sh --check' not found in: tools/loop-selftest.sh
```

**The check finds this very tree** (`check-names-the-stale-records.log`): with `--check` implemented and the two
records *not* yet repaired, it named them and nothing else —

```
MUTATION-PATTERN-STALE 9.9  tools/loop/run.sh (an expression of this record names no line of this file)
MUTATION-PATTERN-STALE 12.2  tools/loop/run.sh (…)
loop-mutations: 2 of 84 checked records no longer apply — the set is stale        exit=1
```

**After** (`mutations-9.9-12.2.log`, the item's own evidence line):

```
RED    9.9  state#agentCommandIsTheOnlyIndirection     restore 9.9 … green again
RED    12.2  run#childStartsInTheProjectRoot           restore 12.2 … green again
red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0        exit=0
```

**Why it survived, and what the fix decides.** The declared set was in no gate. `tools/loop/gate.sh` ran
`tools/loop-mutations-selftest.sh` for `tools/loop-mutations.sh` (the harness's own guards) and
`tools/loop-selftest.sh` for `tools/loop/*`, and neither applies a record to the file it names. Running the whole
set in the gate is the wrong half of the pair: 87 records × two case runs is the 21-minute pass that issue #235's
`--files`/`--from-git` bound exists to avoid, against a loop whose child cap is `LOOP_CHILD_MAX_SECONDS=2700` and
whose item floor is 45 minutes. The harness's own header records the policy the tree already lives by — the file set
while iterating, the full set before a change that owns `tools/loop/**` is archived. What was missing is the cheap
half: whether each record still *applies*, which is 87 `sed` calls and no case run.

- Owning capability: `agent-issue-loop` (the requirement "A mutation run is scoped by the files the change
  touches" is its neighbour; this change adds "A declared mutation record is checked against the file it names").
- Owning guideline: `guidelines/Build.md` §2, "Verification follows the changed files" —
  `LOOP_GATE_SUITES` in `tools/loop/loop.conf` is this project's mapping of that rule.
- `surface: 3 cases added, 0 rewritten, 3 records added, 2 records rewritten (9.9, 12.2), 2 profile pairs, 1 skill
  doc — bash tools/loop-mutations.sh --list | wc -l → 87 records; grep -c "^  '" tools/loop-cases.sh → 166 cases;
  grep -rl "loop-mutations" tools/ .pi/skills/ openspec/specs/ | grep -v '.pi/tmp' → 12 files, of which 5 are the
  loop's own (tools/loop-mutations.sh, tools/loop-cases.sh, tools/loop/loop.conf,
  tools/loop-mutations-selftest.sh, .pi/skills/revert-check/SKILL.md)`.

## What changes

1. `tools/loop-mutations.sh`: records `9.9` and `12.2` are anchored on the **head** of the child-start line (the
   directory and the command) instead of the whole line, so the tail — the child's completion record, and whatever
   the next edit gives it — is no longer part of the quote.
2. `tools/loop-mutations.sh --check`: every selected record's expressions applied to a copy of the file the record
   names; each record in which one expression changes nothing is reported as `MUTATION-PATTERN-STALE <label>
   <file>`, exit 1; exit 0 only when every selected record applies. No case runs, nothing in the tree is mutated.
3. `tools/loop/loop.conf`: `LOOP_GATE_SUITES` owes `bash tools/loop-mutations.sh --check` for `tools/loop/*` and for
   `tools/loop-mutations.sh`.
4. Three cases (`tools/loop-cases.sh`) and three records (`41.1`–`41.3`) that falsify them; `.pi/skills/revert-check`
   names the check.

## Non-goals

- The full set is still not run by the gate; nothing here changes the 21-minute pass, the bound, or the hand-run
  before archive.
- A record that *applies* but that its case no longer notices (`GREEN`) remains the hand-run's to find: this check
  answers "is the quote still a quote", not "is the case still adjacent to the invariant".
