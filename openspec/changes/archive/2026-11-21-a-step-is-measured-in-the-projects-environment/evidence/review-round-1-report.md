# Review round 1 — a-step-is-measured-in-the-projects-environment (issue #980)

Reviewed read-only on top of `64bc7419`: `git diff` of `tools/loop/gate.sh`, `tools/loop/tests/loop-cases.sh`,
`tools/loop/tests/loop-mutations.sh`; the change's four markdown files; `evidence/`; and the sources the diff leans on
(`tools/loop/loop.conf`, `tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/worktree.sh`,
`tools/pi-child-events.sh`, `tools/loop/tests/loop-selftest.sh`, `.pi/issue-loop/base-red.log`).

VERDICT: FAIL

The code is sound — the invariant is real, the removal is in the right place, the case is not vacuous and the mutation
reaches it. What fails is the *claim layer* around it: two of the change's own statements about what the suite
guarantees are false, two citations/numbers do not match the log they cite, one task claims a red run no evidence
records, and one task under-reports the full-suite log it names. All are small corrections; none needs a new design.

## Findings

### 1. **should-fix** — the case does not catch a new session fact, and two documents say it does
`openspec/changes/.../design.md:21-22` — "The list is the declaration, so the next session fact added to
`tools/pi-child-events.sh` is one line from being covered, and `gate#aStepIsNotHandedTheItemsSession` fails until it
is." The same claim is in `tools/loop/tests/loop-cases.sh:6926` ("the next session fact added to the loop is caught
here in seconds").

Evidence that it is false: the case's names are a **second hardcoded copy** of the list (`loop-cases.sh:6937`), and
nothing in the tree reads `item_session_vars` except `gate.sh:668` and `gate.sh:696` (`grep -rn item_session_vars .`
→ four hits, none in the suite). Add `LOOP_SOMETHING_NEW` to `tools/pi-child-events.sh` and both the case and the
suite stay green while the fact leaks into every step — exactly the defect this change exists for.
Smallest correction: have the case derive its names from the declaration, e.g.
`vars=$(sed -n 's/^item_session_vars=(//; s/)\\$//p' "$LOOP_ENGINE/gate.sh" | tr -s ' \t' ' ')`, or add one
assertion that the `LOOP_*` names `tools/pi-child-events.sh` exports (`:90,:103,:116,:140,:145`) are a subset of the
array — otherwise delete the claim from `design.md:21-22` and `loop-cases.sh:6926`.

### 2. **should-fix** — the two `base-red.log` citations are swapped
`proposal.md:16` cites `.pi/issue-loop/base-red.log:9525` for the adapter failure and `proposal.md:21` cites
`:9041` for the gate failure. The log has them the other way round: `:9040-9041` is
`a plain result read from a gate log path is held as gate evidence: expected '6000', got '211'` /
`FAIL adapter#theResultBoundExemptsGateEvidence`, and `:9524-9525` is
`— failing: ./suite-one: expected '0', got '1'` / `FAIL gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`.
Smallest correction: swap the two line numbers.

### 3. **should-fix** — "865–944 s per base, five base shas" is not what the log holds
Repeated at `proposal.md:12`, `design.md:28`, `tools/loop/gate.sh:665`, `tools/loop/tests/loop-cases.sh:6922` and
`tools/loop/tests/loop-mutations.sh:1532`. `.pi/issue-loop/base-red.log` records **six** measurements of the leg
`tools/loop/tests/loop-selftest.sh --from-git {base}`, each red with the same two cases plus `notes#aCaseThatWasRed`:
`7ecab8e6` 907 s (`:4095`), `0b62e579` 869 s (`:5436`), `20d4bbaf` 944 s (`:6833`), `49171fae` **859 s** (`:8160`),
`b63de90b` 865 s (`:9790`), `8e3861c4` **509 s** (`:11837`). So the floor is 859, not 865, and the count is six, not
five. Smallest correction: "859–944 s per base, the six bases the log records (the newest 509 s)", once, and quote it
from one place.

Related, same paragraph: `proposal.md:31-32` says "every item's verdict carries a `— base-red:` subtraction for it".
Only a gate that runs the step subtracts, and only a delta under `tools/loop/**` owes it (`loop.conf:920`; the
subtraction itself at `gate.sh:849-878`). "Every item's gate that owes the leg" is the accurate scope.

### 4. **should-fix** — task 3.2 claims a red run that no evidence records
`tasks.md:26-28`: "`63.1` … `--only 63.1` red on `the step ran on after its command exited`, then green. — done: the
full check below covers it." The full check does not cover it: `--check` applies each record's expressions to a
**copy** of the file and runs no case (`tools/loop/tests/loop-mutations.sh:26-31`, `:1699` — "every selected record
applies to the file it names"); `evidence/mutation-check.log` and `mutation-check-full.log` are both `--check` runs.
Applicability *is* evidenced (`evidence/full-suite.log:562-563`, `MUTATION-PATTERN-STALE 63.1` from the first run, gone in
the second), redness is not. By my reading of the new expression the red would come from the right place — the
leftover `( sleep 20 ) &` of `gate#aStepEndsWithItsCommandNotWithItsWatchers` (`loop-cases.sh:6195-6198`) keeps `cat`'s
stdin open, so the step outlives its command and the case's 8 s poll fails — but "by my reading" is not evidence.
Smallest correction: run `tools/loop/tests/loop-mutations.sh --only 63.1` and land the log as
`evidence/mutation-63.1.log`; keep 3.2 open until it exists.

### 5. **should-fix** — task 4.1 under-reports the run it cites
`tasks.md:32-36`: "519 of 519 green … The first run recorded **one** red, `run#childUsageIsRecordedPerItem` …".
`evidence/full-suite.log` records **two**: `:569 FAIL mutation#everyDeclaredRecordApplies` (the stale `63.1`, which is
the change's own doing and its best applicability evidence) and `:978 FAIL run#childUsageIsRecordedPerItem`, with
`:1304 loop-selftest: 2 of 519 cases failed — 568.05 s`. A reader who trusts 4.1 reads a flake where the log shows the
rewritten record being caught. Smallest correction: name both reds, keep the flake attribution on
`run#childUsageIsRecordedPerItem` only, and state that the second run's 519/519 also covers the rewritten `63.1`.

### 6. **nit** — the canary assertion doubles as the session assertion
`loop-cases.sh:6980-6982` asserts `LOOP_CHILD_SESSION=<unset>` under the message "the step ran and reported what it was
handed". On HEAD that assertion fails because the session name *is* there, and the report therefore prints as if the
fixture never ran (`evidence/red-on-HEAD-980-case.log:2-3`). Smallest correction: have the step print a `RAN=1` line
(`printf 'RAN=1\n' >> "$d/canary"` first) and assert that with the non-vacuity message, leaving the seven assertions
to the invariant.

### 7. **nit** — scenario 1's THEN has two halves and names one case
`specs/agent-issue-loop/spec.md:17-18` ends "… while the gate's own reading of the facts it keeps is unchanged —
**Case**: `gate#aStepIsNotHandedTheItemsSession`". The new case proves only the absence half; the "gate keeps reading
them" half is proven by `gate#aThirdFullRunInOneSessionIsRefused` (`loop-cases.sh:17756-17800`, which hands the gate
`LOOP_CHILD_EVENTS` and asserts `<events>.gate-runs` is written from it). Smallest correction: add that name to the
**Case** line.

### 8. **nit** (prose) / **deferred** (behaviour) — "the facts the loop hands one child" is wider than the seven
`gate.sh:652-654` ("the facts the loop hands a *child*"), `design.md:12-14` and `loop-cases.sh:6919-6921` define the
class by what the loop hands a child, but `run.sh:84-85` hands every child two more: `export LOOP_STATE_FILE=…
LOOP_WORKTREE=…` ("The resolved paths go to every child"). A step still inherits both.
Smallest correction *in this change*: state the class as closed — "the session facts `tools/pi-child-events.sh`
exports for one child, plus the switch" — so the requirement and the prose agree with the code (the requirement body
`spec.md:7-10` and the scenario `:14-16` already enumerate, so they are honest).
**Deferred:** adding `LOOP_STATE_FILE`/`LOOP_WORKTREE` to the list is a behaviour change of its own and needs its own
measurement — with `LOOP_BASE_RED` no longer reaching a step, a nested gate a step starts can now measure, and an
inherited `LOOP_STATE_FILE` would let it write `run.baseRed` into the *item's* record (`gate.sh:596-625`). Not this
change's to take silently.

### 9. **nit** — typo
`tools/loop/tests/loop-mutations.sh:1530`: "a step that runs the loop own suite" → "the loop's own suite".

### 10. **nit** — the `--check` file set names two of the three changed files
`evidence/mutation-check.log:1` selects `tools/loop/gate.sh tools/loop/tests/loop-cases.sh`, while the change also
edits `tools/loop/tests/loop-mutations.sh` (one record names that file). Harmless — `mutation-check-full.log` checks
all 440 — but `tasks.md:37-38` calls 28/28 "over the changed files".

## Checked, no problem found

- **`unset "${item_session_vars[@]}"` inside the subshell (`gate.sh:696`).** The array is a literal of seven names
  (`:668-669`), so it expands to seven words under `set -u`; it runs after the group's redirections and before `$cmd`,
  in the subshell only, so the gate's own environment is untouched; `printf … "$?"` still follows `$cmd` directly, so
  the status record is unchanged; no `set -e`, no readonly name, no `set -m`/job-control interaction (the group is
  already its own process group, and `unset` neither reads stdin nor writes).
- **Nothing a step legitimately needs is removed.** The worktree seam carries only `VCPKG_ROOT`
  (`worktree.sh:176-180`) and the gate sources it *itself* before starting anything (`gate.sh:106-108`), so it still
  reaches a step; the two env-first readers fall back to the profile (`look.sh:94-96`, `phase.sh:39,81`,
  `state.sh:145`); the profile in force is read from `loop.conf`, which the step's own tree carries;
  `loop-selftest.sh:166` already `unset`s `LOOP_CHILD_EVENTS` for itself. The only unguarded reads of the seven are
  the gate's own (`gate.sh:408,413`) and the child-side `child.sh:687`, neither of which is a step.
- **Clearing `LOOP_CHILD_EVENTS` is right.** The gate reads it before any step starts (`gate.sh:407-413`), which
  `gate#aThirdFullRunInOneSessionIsRefused` pins, and a nested `run.sh` sets its own (`run.sh:1187`) — so the step
  loses only the ability to write into the item's stream.
- **Recursion is bounded.** `measure_step_base` returns unless `base_red_mode` is 0 *and* `base_red_on` (`gate.sh:598-600`),
  and is reached only for a step that is red (`:855-861`), where it spawns `gate.sh --base-red` (`:604-605`) whose own
  steps cannot measure — at most one extra level, over fixture suites of seconds. No case can owe the real suites: all
  51 `gate.sh` invocations in the suite pin a copied `LOOP_CONF` (no `LOOP_CONF=$LOOP_CONF` anywhere in
  `loop-cases.sh`), so a measured suite cannot owe the 500–900 s leg.
- **The cost claim holds.** `design.md:41-42` — a step the base is green at records nothing and is measured only when
  it is red (`gate.sh:855-861`), so a green leg is never re-measured. And the condition the change creates inside a
  measured suite (a gate that may measure) is already exercised green: the second full-suite run started its gates
  with no `LOOP_BASE_RED` set, and `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt` passed in it.
- **The new case fails on HEAD for the invariant.** `evidence/red-on-HEAD-980-case.log:2` shows the step reporting
  `LOOP_CHILD_SESSION=item-worker-gate … LOOP_BASE_RED=off`, and the case is red on that assertion, not on a missing
  fixture; `mutation-980.1.log` shows the same assertion red again once the removal is dropped.
- **`980.1` mutates the invariant and nothing else** (`loop-mutations.sh:1535`): it replaces the `unset` line with
  `:` + comment, leaving the launch, the status record and the redirections intact; it and `63.1` name different
  lines, so they do not collide.
- **`63.1` still pins its own invariant** (`loop-mutations.sh:641`): the rewrite keeps the status record the old
  expression deleted, and changes only "the step ends with its command" to "the step ends with a pipe its command
  writes into", which is what the case's leftover writer detects.
- **The requirement body is 443 characters** (bound 500) and `openspec validate a-step-is-measured-in-the-projects-environment
  --strict` reports valid; the case's `# files:` declaration is accepted by
  `driver#everyCaseDeclaresTheFilesItCanReach` (in the green 519/519), and a wider declaration is explicitly allowed
  (`loop-cases.sh:18335-18336`).
- **The two registration points are the two the suite needs** (`loop-cases.sh:206`, `:1019`) — the driver derives the
  row/arm/body sets from the cases file, and the case has all three.

## Deferred (not this change's)

- `LOOP_STATE_FILE` and `LOOP_WORKTREE` remain in a step's environment (finding 8). A second change, with its own
  measurement of what a nested gate a step starts may write.
- The two cases that read the caller's session facts still read them when the suite is started from a shell that
  carries them (that is the condition `evidence/red-on-HEAD-980.log` reproduces); `design.md:26-28` rejects pinning
  them, and the leg this change fixes is the gate-started one.
