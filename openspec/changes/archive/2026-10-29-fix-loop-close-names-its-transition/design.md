# Design — fix-loop-close-names-its-transition

## Root cause

One mechanism. A state write replaces the namespace's value, so the write that sets the closure's state also
removes the one it displaces (`issues_status_displaced`, `tools/issues/lib.sh:332`, read into
`implied_removals` at `tools/issues/update.sh:187-190`). #684 taught that scan which values are *settled* and put a
guard between the read and the write: a write that would remove one of
`roles.settledStatuses` (`tools/tooling.json:53` → `status/fixed-by`, `status/retired`) is refused with exit 3
`status-settled` unless the call names the transition (`issues_status_settled_guard`,
`tools/issues/lib.sh:366`). The two writers whose purpose the transition is name it — `tools/issues/close.sh`
passes `--displace-settled` (`tools/issues/close.sh:182`), `tools/issues/reopen.sh` passes `named`
(`tools/issues/reopen.sh:109`) — and the loop's own closure, which reaches the same act one call later, names it
nowhere. `tools/loop/labels.sh` therefore writes the closure's status as a routine writer would, and an item
carrying the other settled value refuses the write before `close.sh` is ever started
(`tools/loop/labels.sh:156` vs `:165`).

## The single fix

The verdict's own write names the transition, on the same line that names its state:

```
if [ -n "$status_value" ]; then
  args+=(--status "$status_value")
  [ "$close_issue" -eq 0 ] || args+=(--displace-settled)
fi
```

`--displace-settled` is `update.sh`'s own flag, so nothing new is invented: the loop's writer joins the other
two verbs that mean the transition, and every routine writer keeps the refusal. `update.sh` reports the settled
value the write removed on stderr, which is where `labels.sh` already sends the tool's output, so an operator
reading `labels.sh close <n>` sees the claim that moved.

## Alternatives the evidence eliminated

- **`labels.sh` skips its own status write for `close` and lets `close.sh` make it.** Eliminated: the verdict's
  state write is not redundant. With `--no-close` (`may_close=0`) the call still records `status/fixed-by` and
  leaves the item open (`tools/loop/labels.sh:168`), and `close.sh` is then given `--comment-only`; removing the
  first write would lose the state that path exists to record. It would also move the in-progress label removal
  onto a call the loop cannot make when `close.sh` refuses — a smaller change with a wider blast radius.
- **Widening the guard's `named` argument to every verdict.** Eliminated: it reverses #684 for the writers the
  guard exists for — a hold, a park or an escalation would erase a settled claim again, which is the defect that
  issue measured on #146 (`guidelines/Backlog.md:137`, I18).
- **Relaxing the guard when the target is settled.** Eliminated: the guard is a predicate over the *displaced*
  values, and which value an item carries is not the caller's intent; the same shape would let a hold remove
  `status/retired` because its target happens to be `status/on-hold` on another item.

## Blast radius

One line and one comment in `tools/loop/labels.sh`; one case, one fixture scenario and one mutation record; one
guideline row; one spec requirement. No profile key, no CLI change, no output format. The only calls whose
behaviour changes are `close` verdicts over an item that carries a settled value — which today refuse.

## Rollback

Revert the commit. The flag stores nothing and writes nothing of its own; the tree before it is the tree after
it, apart from the documents and the case.
