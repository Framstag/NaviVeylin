# Design — the classification reads the answer, not the status

## The root cause

`tools/loop/run.sh:1655` (base `412ec81d`) decides whether a child read the item with four facts, one of which is
the child's exit status:

```bash
if [ "$worker_exit" -ne 0 ] && [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ "$head_now" = "$base" ]; then
```

The other three facts are already the item's own evidence — no `LOOP-VERDICT:` line in the answer
(`report_field`, `run.sh:796`), no `LOOP-TIMEOUT:`/`LOOP-DEADLINE:` line (the run's own clock did not end it,
`run.sh:1637`), and a worktree still at the base commit (`run.sh:1636`). The status contributes nothing: it is the
child's own report about itself, and the shape measured on 2026-11-01T23:30:01Z has a *successful* status while the
answer is 0 bytes (`evidence/refused-provider-shape.log`). The `-ne 0` clause therefore sends the exit-0 shape past
the guard into the outcome dispatch, whose `[ "$head_now" = "$base" ]` arm (`run.sh:1718`) records it `blocked` with
"reported success but the tree is still at the base commit — nothing was committed" and escalates it — the exact
question-nobody-asked the guard exists to prevent, spent once per item and again on the run's stop.

## The single fix

Remove the status clause. The condition becomes `no verdict && not ended by the run && tree at the base`, and the
stop reason quotes the status and the answer's size instead of *testing* the status:

```bash
  if [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ "$head_now" = "$base" ]; then
    child_words=$(last_line "$item_dir/worker-report.md")
    answer_bytes=$(wc -c < "$item_dir/worker-report.md" 2>/dev/null || printf 0)
    loop_labels unpick "$issue" >&2 || true
    in_progress=""
    stop_run "the child could not start — it exited $worker_exit with an answer of $answer_bytes B and no verdict line, leaving the tree at the base${child_words:+: $child_words}"
    break
  fi
```

The size is in the message because it is the fact that distinguishes the two shapes a reader will meet: exit 1 with a
35-byte complaint (an adapter that refused the argv) and exit 0 with `0 B` (a provider that refused every call).
Everything the arm does afterwards is unchanged — the unpick, the cleared `in_progress`, `stop_run`, `break`.

## Blast radius

One condition, one message, one comment block in `tools/loop/run.sh`. The three facts that decide the arm are the
same three the requirement already names, so no other arm changes shape:

- A child the run ended still carries `LOOP-TIMEOUT:` and leaves through `run.sh:1637`'s own branch; `timeout_claim`
  is non-empty, so the guard is not entered (issue #203, #686).
- A child that wrote a verdict is never in this arm, whatever its status and tree — `verdict_claim` is non-empty.
  That covers every stub report in `tools/loop-cases.sh` (`STUB_WORKER_REPORT` begins `LOOP-VERDICT: closed`), which
  is why the exit-0 cases of the suite (`run#aWriterTheRunCanPlaceBlocksTheItem`, the peer-write and strict-write
  cases that never commit) are unaffected: they answer with a verdict.
- A child that left work behind has a branch, so `head_now != base` and it stays a blocked item (issue #176).
- A child that exits 0, writes no verdict and wrote outside the worktree is now the run's stop rather than a
  `blocked` item: it is the same "nothing of the item was read", and the outside-write note is still written to the
  item's transcript (`run_child`) — it just no longer decides an item the run never got an answer from. No case
  stages that combination; the two facts cannot both be true of a child that answered, because a report is written
  to the item's own directory inside the project.

## Alternatives the evidence eliminated

- **Keep the status test and add a second arm for exit 0.** Two conditions asserting one rule is the defect this
  change removes; the arm would have to be kept in step with the first, and the mutation set would need a record per
  arm (the `27.1` record already asserts the first arm is reachable).
- **Treat "0-byte report" as the signal, with the status.** Same defect at one remove: the item's evidence is *no
  verdict line*, which is also what a 12-byte refusal and a 4 kB answer that ends mid-sentence have in common
  (`answer_complete`, `run.sh:812`, is the reviewer's shape of the same fact). The bytes are quoted, not tested.
- **Re-queue the item instead of stopping the run.** A reversal of what the requirement already decides for the
  exit-1 shape ("the run records that once as its own stop reason … and stops"), and a design fork the item does not
  ask for. Not taken here; the item's own words ask for the sibling's treatment.
- **Wait for the provider / retry the child.** The run has no provider of its own (`LOOP_AGENT_CMD` is the project's
  adapter) and a retry inside one decision slot is a budget question the run's deadline already answers.

## Rollback

Restore `[ "$worker_exit" -ne 0 ] && ` at the head of the condition and the previous message
(`the child could not start — it exited $worker_exit without a verdict and left the tree at the base…`), delete
`27.2` and re-point `27.1` at the restored line. `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` then fails
exactly as `evidence/red-on-HEAD.xml` records (`tests="1" failures="1"`), and the sibling case is green again.

## Not measured, and named

- The reviewer half: `run.sh:1772-1790` reads the reviewer's `LOOP-DEADLINE`/`LOOP-TIMEOUT` and its closing `VERDICT:`
  separately, and a reviewer whose provider refuses every call is a different arm with its own case
  (`run#reviewerEndedAtTheRunsDeadlineIsNotADecision`). This change does not touch it, and no measurement here says
  what that shape costs.
- How often the exit-0 shape occurs across the archive: the run that produced it left two items in one run, and no
  count over the archived runs was taken.
- `gen-feature-list --check-classification` is red in the worktree this change was written in, for two delta specs of
  *other* open changes (`auto/off-route-indicator` from `car-reroute-without-losing-the-map`, `tooling-test-layout`
  from `separate-tooling-test-code`). Their classification is owed by their own changes
  (`guidelines/FeatureList.md`, the rule that a capability is classified in the change that writes its delta), the check
  is owed for no path this change touches, and it is green on the base
  commit (`git archive 412ec81d` into a scratch tree, then the check — exit 0). Named here because it is what the
  gate's default base would have made this item pay for.
