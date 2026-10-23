# Design — the run stops committing the tree of a child it ended

## Root cause

`end-item` is called at the point the run believes the child is over, and the run has not yet read the status
`run_child` returned: `tools/loop/run.sh:905` (the base commit `6683376e`) commits, `tools/loop/run.sh:919` is the
first line that looks at `worker_exit`. A child the cap ended is therefore committed as if it had finished, with
whatever its half-finished work left in the tree — the measured case being `tools/loop-mutations.sh` mutation `19.2`
applied to `tools/loop/run.sh` itself (issue #271, 2026-10-22). The same shape sits at `tools/loop/run.sh:1108`: the
rework worker a refusing round starts is committed there without its status being read at all.

## The single fix

The status is read before the commit, and a child the run ended gets no commit:

```sh
  if [ "$worker_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$timeout_claim" ]; then
    progress commit "end-item skipped — the run ended this child, so its tree is not committed; HEAD is … "
  else
    commit_out=$(bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" 2>&1)
  fi
```

Both halves of the condition are the same fact and either alone is enough: `run_child` sets the cap's status
(`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`) and appends the `LOOP-TIMEOUT` line to the transcript, so a status
that reached the run through a wrapper which lost the line still lands on the same verdict.

The same guard sits at the second commit site, one phase further on — the rework worker a refusing review round
starts (`run.sh:1114` onward at HEAD), whose status was not read at all before:

```sh
      run_child "$item_dir/worker-prompt-round2.md" "$item_dir/worker-report-round2.md"
      round2_exit=$?
      …
      round2_timeout=$(report_field "$item_dir/worker-report-round2.md" LOOP-TIMEOUT)
      if [ "$round2_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$round2_timeout" ]; then
        review_verdict="round2-ended"
        outcome="blocked"
        blocked_reason="the cap ended the rework worker of the refusing round, so its tree is not committed — …"
        break
      fi
      bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" >/dev/null 2>&1 || true
```

That guard exists because the requirement is universal and the first pass shipped it while leaving this call site as
it stood: the round-1 review of this change read the two against each other and refused it
(`evidence/review-round-1-…` in the branch's history). The rework worker's ended round blocks the item — the two
candidates for a capped round (block it, or run another round) are settled by the same rule as everywhere else: a
run that ended a child has no claim to make about it, so the item goes to its owner rather than to a fresh round.

`head_now` is read either way, because the outcome block reads it (at HEAD: `run.sh:915`, `934`, `976`, `983`) and a branch
that stayed at the base must not be mistaken for a child that could not start — that branch requires
`-z "$timeout_claim"` already, so a capped child still lands on "the worker process exited 124 — LOOP-TIMEOUT…".

## Blast radius

- **Two call sites, one per guard**: the item's worker (`run.sh:905` at the base) and the rework worker of a
  refusing round (`run.sh:1108` at the base). The outcome block, the escalation and the report are unchanged — a
  capped child was already `blocked`; only the commit that used to accompany it is gone.
- **The kept worktree**: the tree is now left dirty by design, which is the state `start-item` refuses
  (`worktree.sh:138`) and `run.sh`'s pre-flight attributes and escalates at the *next* run (`run.sh:371`–`404`).
  Within the same run the next `start-item` refuses and the run stops on `the item could not be started` instead of
  taking another item; that is a recorded stop, not a silent commit, and the repair remains a person's
  `--reset-worktree` — the rule `run.sh:55`/`:396` already states ("nothing is committed or discarded without that
  flag").
- **The cases**: none of the existing ones assert the commit of a capped child (the first installs no action, so its
  tree is clean; the second asserts only that the survivor is gone), so none is rewritten. Two cases are added, and
the second one cannot use `PI_STUB_WAIT` — that blocks *every* child, so the round's reviewer would block before the
rework worker started — so its stub action writes the file and then `sleep 30`s past the 2 s cap.
- **No path outside `tools/loop/**` and `guidelines/Backlog.md`** is touched: no compiled file, so the gate owes the
  shell suites and the two documentation checks, not a build.

## Not changed, and why

- **The tree is not cleaned.** Discarding it would make the next item startable again, and it is exactly what
  `--reset-worktree` is for; a run that discarded it on its own would delete the only evidence of what the child was
  doing, at the moment nobody has read it.

## Alternatives the evidence eliminated

- **"Commit only the paths the child's own report names"** (the item's second candidate). A child the cap ends never
  writes its report at all — it is killed mid-work — so the set of paths its report names is empty, and the
  candidate reduces to "commit nothing" for exactly the case this item is about. Where the two candidates do differ,
  the child finished by its own exit, which is not this item's premise (the run did not end that child), and
  committing a subset of a tree on the child's own claim would contradict the requirement the run already carries,
  "The run verifies rather than trusts" (`openspec/specs/agent-issue-loop/spec.md:437`). The fork therefore does not
  survive the evidence: one behaviour remains for a child the run ended.
- **"Discard the tree so the run can take the next item"** — refused by the recorded rule that nothing is discarded
  without `--reset-worktree` (`run.sh:391`), which exists because the tree is the only record of what a child was
  doing.

## Rollback

Revert the one hunk in `run.sh` and the case: the run returns to committing whatever the tree holds, which is the
state HEAD is in. No data, state file or branch shape changes for an item that was not ended by the cap.
