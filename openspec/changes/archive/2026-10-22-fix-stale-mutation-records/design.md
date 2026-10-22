# Design — the record check, and the two records it names

## Root cause

`tools/loop-mutations.sh` declares each invariant as a record whose last field is a `sed` expression that quotes one
line of one engine file. `one_mutation` backs the file up, applies the expression, and compares the result with the
backup: identical means `MUTATION-DID-NOT-APPLY`. That guard is exact, and it fires only when the record is *run*.
Nothing ran it: `tools/loop/gate.sh` owes `tools/loop-mutations-selftest.sh` for `tools/loop-mutations.sh` and
`tools/loop-selftest.sh` for `tools/loop/*`, and neither applies a record to the file it names. So issue #227 edited
the tail of `run_child`'s child-start line (`tools/loop/run.sh:668`) to add the child's own completion record, and
records `9.9` and `12.2` — which quoted the whole line, tail included — became quotes of a line that no longer
existed. Their two invariants, `state#agentCommandIsTheOnlyIndirection` and `run#childStartsInTheProjectRoot`, went
on passing because a record that mutates nothing fails no case.

## The single fix

Two halves, one mechanism — a record is a quote, and a quote needs comparing against its original:

1. **The quote stops at the unstable part.** Both records now anchor on the head of the line, up to the `printf`
   that begins the completion record, so the tail can change again without orphaning them:
   `s|^  ( cd "$LOOP_ROOT" \&\& $LOOP_AGENT_CMD "$1"; printf|  ( cd "$LOOP_ROOT" \&\& pi --print "$1"; printf|`
   (12.2: the same, with `$worktree` in place of the directory). Measured after the rewrite —
   `.pi/issue-loop/items/264/mutations-9.9-12.2.log`:

   ```
   RED    9.9  state#agentCommandIsTheOnlyIndirection     restore 9.9 … green again
   RED    12.2  run#childStartsInTheProjectRoot           restore 12.2 … green again
   red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0
   ```

2. **`tools/loop-mutations.sh --check`** applies each selected record's expressions to a copy of the file the record
   names, one expression at a time, and reports the record when one of them changes nothing:

   ```
   MUTATION-PATTERN-STALE <label>  <file> (an expression of this record names no line of this file …)
   loop-mutations: N of M checked records no longer apply — the set is stale
   ```

   Exit 1 when any record is stale, 0 otherwise, and its summary line is the suite vocabulary the gate parses
   (`all N cases passed`), so the check contributes a real tally rather than a green exit with no number. It runs no
   case and mutates nothing: the copies live in the harness's own scratch directory, and the two prerequisites that
   only *running a case* needs (a sibling `issues/` tree and `tools/loop-selftest.sh`) are skipped when `--check` is
   set, so a bare copy of the set can be checked.

   Per **expression**, not per record: a record with one stale expression applies only in part, and a partial
   mutation that still turns its case red is the same silent lie in its quieter form. Measured — every one of the 87
   declared records applies expression-by-expression, so the stricter rule needs no exception.

3. **The gate owes it.** `tools/loop/loop.conf`'s `LOOP_GATE_SUITES` gains two pairs —
   `tools/loop/*|bash tools/loop-mutations.sh --check` and `tools/loop-mutations.sh|bash tools/loop-mutations.sh
   --check`. The first is the one the defect needs: #227's edit was to `tools/loop/run.sh`, which owed
   `tools/loop-selftest.sh` and nothing that read the records. This is the project's own mapping of the rule
   `guidelines/Build.md` §2 states (verification follows the changed files), and it is recorded in the profile
   because a portable engine names no project fact of its own.

## Blast radius

| file | change |
|---|---|
| `tools/loop-mutations.sh` | 2 records rewritten (9.9, 12.2), 3 records added (41.1–41.3), `--check` mode, header |
| `tools/loop/loop.conf` | 2 `;`-pairs appended to `LOOP_GATE_SUITES` |
| `tools/loop-cases.sh` | 3 cases, in the list, the dispatcher and the file's end |
| `.pi/skills/revert-check/SKILL.md` | `--check` in the usage block and the paragraph that owns the hand-run rule |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, three scenarios |

Nothing in the engine's phases, run loop, prompts or fixtures changes; no product code is touched. The gate gets
slower only for a delta that touches the loop's tooling, and by 87 `sed` calls over copies of engine files
(sub-second, against `tools/loop-selftest.sh`'s minutes).

## Rollback

Revert the commit: the two records go back to quoting the stale line (they then report `MUTATION-DID-NOT-APPLY`,
which is the defect, not a new one), the two profile pairs disappear with the check they name, and the three cases
and records with them. No state, no build output and no machine-local file is written by any of it.

## Alternatives the evidence eliminated

- **Run the declared set in the gate.** Eliminated by a recorded cost, not by taste: 87 records × two case runs is
  the 21-minute pass that issue #235's `--files`/`--from-git` bound exists to avoid, and the harness's header states
  the policy the tree lives by — the file set while iterating, the whole set before a change that owns
  `tools/loop/**` is archived. Against `LOOP_CHILD_MAX_SECONDS=2700` and a 45-minute item floor, a 21-minute gate is
  most of an item's budget, paid by every change that touches the engine. The check answers the defect's question
  for 87 `sed` calls, and the whole-set pass keeps the question it alone answers (a record that applies but whose
  case no longer notices it).
- **Put the check only in `tools/loop-mutations-selftest.sh`.** Eliminated by what the gate maps: that suite is owed
  for `tools/loop-mutations.sh`, and the edit that orphaned the two records was to `tools/loop/run.sh`, whose suite
  is `tools/loop-selftest.sh`. A check the orphan's own path does not run reproduces the defect with a green gate.
- **Re-quote the whole line.** Eliminated by the measurement: the tail is the part that changed (#227 gave it the
  child's completion record), and re-quoting the whole line makes the next edit to the tail repeat this item. The
  head — the directory and the command — is what the two invariants are about.
- **A second script, `tools/loop/check-mutations.sh`.** Eliminated because the records live inside
  `tools/loop-mutations.sh`'s `MUTATIONS` array: a second reader either parses another file's shell array (the
  fragile thing `declared` already does once in the selftest) or duplicates the set.
