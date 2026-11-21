# Review — commit `55589fc1` "fix: an-empty-tree-names-the-outside-write (#983)"

Read-only review of the commit against `git show 55589fc1` and `git show 55589fc1:tools/loop/run.sh`.
Working-tree changes of #938 (`prescreen.sh`, `loop-cases.sh`, `loop-mutations.sh`,
`guidelines/Backlog.md`, `openspec/specs/agent-issue-loop/spec.md`) are not part of this change and are
not reported below. Nothing was edited; the suite was not run.

## Verdict

**PASS with findings — merge verdict: OK with notes.**

The change does what #983 asks: an item whose child wrote the caller's tree and committed nothing now
carries a sentence naming the difference and the paths, and the arm's own sentence stays verbatim.
Six findings follow; findings 1–3 should land before the change is archived, because finding 1 is the
sentence the item's owner will read and it contradicts a requirement the shipped spec already holds.

## The six judgements asked for

**1. Root cause and scope — holds.** #983 is not "the child wrote the wrong tree" (that is #892/#895);
it is "the work stands in the caller's tree, unnamed". The fix is message-only: no verdict changes, no
new measurement, no new reading, and the three antecedent sentences are byte-identical before and
after the append (`run.sh:3354`, `:3758`, `:3878`). The issue's explicit exclusion — "place the
difference on the item's branch" — is honoured: nothing is cherry-picked, merged or committed from the
caller's tree.

**2. Honesty of the sentence — does not hold (finding 1).** The clause reads "the run nevertheless
recorded a difference in the project's own tree while that child ran: the work is in the caller's tree
and not on the item's branch — <reading>".

- First half is honest. Both note sources are a window around that child: the in-time sampler
  (`run.sh:2293-2297`, `outside_write_seen`) and the fallback `before`/`after` comparison
  (`run.sh:2541-2555`). The fallback *is* a reading about that child's lifetime, not a stale one — the
  variables are reset per child at `run.sh:2544-2545`.
- Second half is not. The reading the run actually holds for the real instance is
  `attributed=none`: `.pi/issue-loop/items/894/*.outside-write` carries
  `LOOP-OUTSIDE-WRITE-ALIVE: … · attributed=none`, and `run.sh:1648-1649` (`outside_write_decides`)
  makes such a reading non-deciding precisely because `--strict-outside-write` defaults to off
  (`run.sh:112-113`, `:131-133`). The run therefore records "I cannot place this on that child" and
  the new sentence then places it.
- Reachable, not theoretical: a peer edit in the caller's tree plus an item that committed nothing
  takes `run.sh:3878` and the record asserts the peer's work as the item's.
  `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` already stages exactly that peer write, and
  `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` stages the `attributed=none` shape.
- The new case stages the same shape and says so itself: "the difference is recorded
  (`attributed=none`, #704), decides nothing". So the test proves the sentence on a reading the run
  declines to attribute.

**3. Shell correctness — holds.** All of the following are correct as written:

- `printf " — … %s" "$note"` (`run.sh:1666`): the note is an *argument*, so `%`, backslashes, single
  and double quotes, `;` and `$(…)` inside it are printed literally. The format string itself carries
  no `%`. No `%b`, so no escape processing.
- `set -u`: `run.sh:33` is `set -uo pipefail`; every variable read in the function uses `${x:-}`
  (`:1662-1664`), so an unset `gate_write_note` or `outside_write_note` cannot kill the run.
- `local note` is inside a function, and the function is defined (`:1661`) before all three uses
  (`:3354`, `:3758`, `:3878`), so no use-before-definition and no top-level `local`.
- Command substitution inside a double-quoted string: content preserved, no word splitting, no
  trailing newlines to strip (the function ends with `printf`, no newline in the format).
- Interaction with the `${var:+…}` neighbours in `stop_run` (`:3758`): the clause sits between
  `${refused_by:+ — refused by …}` and `${retry_log_child_start:+; retried …}`, so the `; ` separator
  the retry half owns still reads as `… — <paths>; retried 1 of 2 (…) — attempt 1 after 5 s: …`. No
  doubled or missing separator.
- Note length: the append is bounded by `outside_write_clause_of` (`:1513-1517`) at 10 paths × 120
  chars, i.e. ≤ ~1.3 kB, and `loop_state step --note` / `progress note` cap nothing. The same class of
  text is already recorded raw at `:3142`/`:3150`, so no new length class reaches run.json.
- Exit status: the function returns 0 on every path (empty via `return 0`, otherwise `printf`), so it
  cannot perturb a `set -e`-free caller's control flow.
- Antecedent present at all three sites: `:3354` ("nothing was committed"), `:3878` ("nothing was
  committed"), `:3758` ("leaving the tree at the base"). No site appends the clause to a sentence that
  does not already conclude an empty tree.

**4. The three call sites — two clearly right, the third has a wrong-child risk (finding 5).**
`:3354` (pre-gate diff-ready reading) and `:3878` (the `uncommitted` block) are the two arms that write
"nothing was committed" and are the right pair. `:3758` (the run's own "could not start" stop) is
defensible: it also concludes an empty tree and the clause belongs there.

Arms deliberately left alone, all correct:

- `:3879` `block uncommitted "reported success but no archived change appears in the run's diff"` — the
  tree is *not* at the base there, the work is on the branch; the caller's tree is not the missing
  thing.
- `:4078`, `:4226`, `:4232` review-round and round-2 `block outside-write` — these fire only when
  `outside_write_decides` is true and already name the difference; not the empty-tree shape.
- `:3869` `outcome="falsified"` — concludes an empty tree ("a falsification that changed files is a
  change") but writes *no sentence*, so there is no antecedent to append to. Worth knowing that an
  outside-writing child which answers `falsified` still leaves its work unnamed; see finding 6.

`empty_tree_reading` readability: still readable, and nothing parses it — it is read only by `-z`
tests (`:3539`, `:3734`) and passed verbatim to `loop_state step` / `progress note`. No format
dependency was broken by the append.

**5. Test quality — sound, with a coverage gap (finding 4).**

- `run#anEmptyTreeWithAnOutsideWriteNamesThePaths` (`loop-cases.sh:9951`) stages what it claims: the
  action writes through `$LOOP_CHILD_ROOT`, exported as `$PWD` by `tools/loop/tests/fixtures/pi:82-83`,
  which is the caller's project root. The action runs after the stub's own `cd "$LOOP_WORKTREE"`, so
  the narrow placement rule (`loop_tests/fixtures/pi` + `pid_left_the_worktree`) places nothing —
  `attributed=none` is structural, not timing-dependent.
- Order is correct: `stub_write` (`:1629`) writes its own `$d/worker-action.sh`, and the case
  overwrites it *after* the call, so the stray-write body wins and the worktree stays at the base.
- Not flaky: whether the in-time sampler fires or only the fallback does, both note sources name
  `demo-owned.txt` (`outside_write_clause_of` prints status lines, truncated to 120 chars, capped at
  10). The child is ~50 ms, so in practice the fallback fires; the assertion holds either way.
- Asserts the right things: `result=blocked`, `stopReason=uncommitted` (which pins the arm), the arm's
  own sentence as a substring, the clause, and the path.
- Negative half at `:7358` in `run#childClaimWithoutCommitIsBlocked` is sufficient for "no reading →
  no clause", which is the invariant that keeps every other case green.
- Mutation `983.1` is real: `mutation-983.1.log` shows RED on `run#anEmptyTreeWithAnOutsideWriteNamesThePaths`
  with the exact missing substring, and restore green. It mutates two sites
  (`:3354` and `:3878`) while the case reads one, which is safe (a mutation broader than its case), but
  it leaves `:3354` and `:3758` unmutated and unread. The red-on-HEAD evidence
  (`red-on-HEAD-983.log`, base `8e3861c4`) is also genuine, and the arm it shows failing is the `:3878`
  one — confirming `:3758` has no coverage at all.

**6. Repo rules.** `# files:` is a selection over-approximation, not a checked claim (its own header says
so, `loop-cases.sh:23-30`), so it cannot be "wrong" in the sense of a failing check — but see finding 4.
The behaviour change *should* have moved one guideline (finding 3) and the spec delta *does* state the
rule the code implements, one scenario per case, but it contradicts a shipped requirement (finding 2).
The change directory is correctly untracked: `.gitignore:82-83` ignores `openspec/changes/*/` and
un-ignores only `archive/`, so the delta belongs to the archive commit, not this one.

## Findings

### 1. should-fix — `tools/loop/run.sh:1666`, with
`openspec/changes/fix-an-empty-tree-names-the-outside-write/specs/agent-issue-loop/spec.md` (requirement
"An item whose child wrote the caller's tree says so")

**What is wrong.** The clause asserts "the work is in the caller's tree and not on the item's branch" on
a reading the run marks `attributed=none`. The shipped spec says the opposite of that inference twice:
`openspec/specs/agent-issue-loop/spec.md:482-489` — "the comparison measures a tree the run, a peer
session and every landing also write, and two readings of it are not evidence about one child" — and
`:492-498` — "a difference nobody can place is not evidence about the child". The code agrees with the
spec (`run.sh:1645-1649`: a difference decides only when attributed or `--strict-outside-write yes`,
default `no` at `:112-113`). So the run records "I cannot place this" and the new sentence places it.

**Evidence.** `.pi/issue-loop/items/894/*.outside-write`:
`LOOP-OUTSIDE-WRITE-ALIVE: 10598 -zsh; … · attributed=none` — the live item's own record. Reachable in a
normal configuration: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` (`loop-cases.sh:9841`)
already stages a peer writing `demo-owned.txt` into the project tree; add an item that commits nothing
and `run.sh:3878` records the peer's work as the item's.

**Smallest acceptable fix.** Reword `run.sh:1666` so the second half is a hypothesis or names the
attribution, and mirror the wording in the spec requirement:

    printf " — the run nevertheless recorded a difference in the project's own tree while that child ran: if that difference is this item's work it is in the caller's tree and not on the item's branch — %s" "$note"

(or keep the assertion only when `${outside_write_attributed}` is non-empty, and say "the run could not
place it on that child" otherwise). The paths, which are the part a reader acts on, are unchanged. Then
amend the requirement text from "SHALL say the work is in the caller's tree" to the conditional, so the
spec no longer mandates the false statement.

### 2. should-fix — `openspec/changes/fix-an-empty-tree-names-the-outside-write/specs/agent-issue-loop/spec.md`

**What is wrong.** The delta is a pure `ADDED` block whose requirement contradicts a shipped
requirement ("A difference the run cannot attribute does not decide the item",
`openspec/specs/agent-issue-loop/spec.md:481-489`) without a `MODIFIED` block naming the exception. After
archive both stand, and a reader of the spec is told that an unplaceable difference is not evidence
about one child *and* that it SHALL be reported as that child's work. Separately, the requirement is
written for "a reason the run writes for a tree at its base" — all three arms — while only one arm has a
scenario and a case.

**Smallest acceptable fix.** Take the reworded requirement from finding 1, and either (a) add a third
scenario for the "child could not start" arm (`run.sh:3758`) with a case, or (b) narrow the requirement
to the `uncommitted` block and state that the stop arm is out of scope. (b) is the cheaper and is honest
about the coverage. If (a), the `## Purpose`-style body stays under 500 characters either way (the
current body measures ~415).

### 3. should-fix — `guidelines/Backlog.recipes.md:1325-1332`

**What is wrong.** That paragraph is the guideline that owns this behaviour, and this commit makes two of
its statements stale: it quotes the reason as the arm's whole sentence ("the item is recorded `blocked` on
'reported success but the tree is still at the base commit — nothing was committed'"), and it says the
sentence is "kept there as the invariant rather than written twice". After this change the reason carries
an appended clause, and the antecedent sentence now appears at two sites (`run.sh:3354` and `:3878`). The
repo's own rule — a rule lives in the guideline that owns it, next to the measurement that proved it —
makes a doc delta for a behaviour change part of the change, and a delta under `guidelines/**` runs no
build and no suite, so it costs nothing.

**Smallest acceptable fix.** Extend that paragraph with one sentence: the reason now also names the
outside-write reading the item's record carries, the clause is empty when the record carries none, and
the reading is a qualification and never a placement (naming finding 1's wording). Mention the second
site so "written twice" is no longer claimed. Optionally name the cases
`run#anEmptyTreeWithAnOutsideWriteNamesThePaths` and `run#childClaimWithoutCommitIsBlocked` and mutation
`983.1` beside them, as the neighbouring paragraphs do.

### 4. nit — `tools/loop/tests/loop-cases.sh:9952`

**What is wrong.** The `# files:` declaration names `tools/loop/tests/fixtures/scenarios/`, but the case
depends on `LOOP_CHILD_ROOT`, exported at `tools/loop/tests/fixtures/pi:82-83` — a path the token does
not cover. `loop_case_helper_files` (`loop-cases.sh:562-575`) maps no helper to `fixtures/pi` and
`loop_case_body_paths` does not substitute `$LOOP_CHILD_ROOT`, so
`driver#everyCaseDeclaresTheFilesItCanReach` will not notice. A delta to `fixtures/pi` — for instance
removing that export — would not select this case, and the case would go red in the gate instead of in
the selection. The nearest structural sibling, `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`
(`:9882`), declares `tools/loop/tests/fixtures/` for exactly this reason.

**Smallest acceptable fix.** Widen the first token from `tools/loop/tests/fixtures/scenarios/` to
`tools/loop/tests/fixtures/`.

### 5. nit — `tools/loop/run.sh:3758` (function at `:1661-1667`)

**What is wrong.** The clause's preference order is `worker_write_note` → `gate_write_note` →
`outside_write_note`, which is right at `:3354` (the change child) and at `:3878` (no child is named).
At `:3758` the sentence names a child explicitly — "the $last_child exited …" — while
`worker_write_note` holds the *change* child's reading (`:3130`) and `gate_write_note` the gate child's
(`:3233`, re-set at `:3520`). If a later child (gate or tail) wrote into the caller's tree and the
change child did not, the clause names a reading taken around a different child than the one the
sentence speaks of, under the words "while that child ran". Narrow — it needs `head_now == base`, no
verdict claim and an exhausted retry budget — but it is the one site where "that child" can be wrong.
No case exercises this arm (see judgement 5), so nothing would catch it.

**Smallest acceptable fix.** Give the function an optional argument —
`item_outside_write_clause() { local note="${1:-${worker_write_note:-}}" … }` — and pass
`"${outside_write_note:-}"` at `:3758`, since `outside_write_note` is set by the last `run_child` and is
therefore the last child's own reading. Leave `:3354` and `:3878` calling it with no argument.

### 6. nit — `tools/loop/run.sh:3869` (the `falsified` arm)

**What is wrong.** This arm concludes an empty tree and treats it as evidence — "The empty tree is part
of the evidence — a falsification that changed files is a change" — so a child that wrote six paths into
the caller's tree and then answered `LOOP-VERDICT: skipped` / `LOOP-SKIP-VERDICT: falsified` is recorded
`falsified` on an empty worktree, with the work standing in the caller's tree and unnamed. That is #983's
shape one arm over. It is *not* a defect this change introduced, and the arm writes no sentence, so
there is nothing to append a clause to — which is why it is a nit and not part of findings 1–3.

**Smallest acceptable fix.** None required for this commit. If wanted, the arm's recorded `skip` note
(or a `loop_state` step beside it) could carry the same reading; that is a second change with its own
case, not an edit here.

### 7. nit — `tools/loop/run.sh:3354` with `:3878`

**What is wrong.** For the pre-gate diff-ready shape (`#797`) both sites fire: `empty_tree_reading` gets
the clause and becomes the `empty-tree` step note, and the outcome block then blocks on `:3878` with the
same clause again. Two records in the item's directory now carry the same long sentence. Harmless — the
records differ in their antecedent — but it doubles the appended text and the duplication is not
mentioned anywhere a reader would look.

**Smallest acceptable fix.** Either leave it and say so in the guideline paragraph of finding 3, or pass
an argument that suppresses the clause in `empty_tree_reading` (`item_outside_write_clause` with an
empty first argument → empty string) and let the item's reason carry it once. Prefer the first: the step
note is where a reader looks for the reading, and suppressing it hides the fact at the earlier point.

## What is already good (evidence)

- Scope: no verdict changes, no new measurement, no new reading — the three antecedent sentences are
  unchanged at `run.sh:3354`, `:3758`, `:3878`; the diff touches three files and adds 64 lines.
- The empty-clause path is real and pinned: `run.sh:1665` (`[ -n "$note" ] || return 0`) and
  `loop-cases.sh:7358` asserts "caller's tree" is absent for a child that wrote nothing outside.
- Red-then-green discipline honoured: `evidence/red-on-HEAD-983.log` (base `8e3861c4`, HEAD's `run.sh`
  without the fix, case red on the exact missing substring) and `evidence/mutation-983.1.log`
  (`RED 983.1`, then `restore 983.1 … green again`, `not-noticed: 0`, `restore-failures: 0`).
- Gate evidence present: `evidence/gate-cases-run.sh.log` — "selected 260 of 510 cases (files:
  tools/loop/run.sh)", which is the scope a `run.sh` delta owes.
- `printf` argument handling, `set -u` guards and the `${var:+…}` neighbours all correct — this is the
  rare append where the shell mechanics are the easy part.

## Commands a supervisor must run (not run here)

    tools/loop/tests/loop-selftest.sh --case 'run#anEmptyTreeWithAnOutsideWriteNamesThePaths' \
        'run#childClaimWithoutCommitIsBlocked' 'run#anEmptyTreeIsRefusedBeforeTheGateChild' \
        'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem' \
        'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'
    tools/loop/tests/loop-mutations.sh --labels 983.1
