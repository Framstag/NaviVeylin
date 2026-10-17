---
name: close-issue
description: Close a tracker issue as the only terminal state — with a reason and the evidence for it, posted as a comment before the closure. Use when a fix landed or a person decided not to act. There is no delete path, and this skill adds none.
---

# close-issue

One terminal state, reached in two steps: the reason is posted as a comment, then the issue is closed with a
`state_reason`. Nothing here deletes anything.

**Prerequisites.** `gh` authenticated as an allowlisted author, `jq`, a JDK ≥ 17 on `PATH`
(`tools/issue-tooling-selftest.sh` runs the same surface offline).

## When to use it, and when not to

Use it when **the work is done and provable** (the fix landed, the case is green, the measurement is on the
issue), or when **a person decides not to act** — the second case is marked explicitly with `--not-planned`.

Do not use it to:

- **close your own unauthorised work.** The gate runs first: an issue whose author is not allowlisted, or
  which does not carry the opt-in label, is refused with exit 3 and **nothing is posted**.
- **shelve work you cannot finish.** That is an escalation — keep the branch, keep the change, comment the
  root cause and the failing case, and leave the item open.
- **remove an item.** There is no delete path in this tooling: `gh issue delete` appears nowhere under
  `tools/issues/`, and the self-test asserts it (`surface#noDeletePath`). A closure is reversible; a deletion
  is not.

## The call

```bash
tools/issues/close.sh 42 \
  --reason "the router's overall distance is taken from the description total" \
  --evidence "RouteInstructionPositionDeviceTest is green on HEAD; the numbers agree on the AAOS AVD"

tools/issues/close.sh 42 --reason "not worth the churn" --evidence "discussion on #17" --not-planned
```

`--reason` and `--evidence` are both required; a call without either exits 2 and writes nothing.

## The order, and why

```
1. gate        author allowlisted AND the opt-in label present   (exit 3, nothing posted, if not)
2. comment     the reason, then the evidence
3. close       --reason completed  (default)  |  --reason not_planned  (--not-planned)
```

The comment comes first so a closure can be audited and reversed — the issue carries *why* it was closed
even after the state changed.

`state_reason` is not decoration. A declared check may reopen an item it owns, and it may only reopen a
closure recorded as a **completed fix**; reopening a person's `not_planned` decision would be the tooling
overruling its owner.

## Exit codes

| code | meaning |
|---|---|
| 0 | commented and closed |
| 2 | usage — the reason or the evidence is missing, or the issue could not be read; nothing was written |
| 3 | policy refusal — the gate did not pass, nothing was written |
| 1 | `gh` itself refused the comment or the closure — read its message |
