# Proposal — the queue takes a per-item override

## Why

The queue's order is decided by three keys: the profile's leading categories, the class order, and the issue
number. There is no way for a person to say "this item next", so an owner who wants two items worked first has to
stop the run and work them by hand, or wait until the class and issue keys reach them. Measured 2026-11-07 on this
project's own backlog: the two issues the owner wanted next (#821, #822) are `class/improvement` items in a leading
category, so the interval puts them at the **tail** of a fresh queue — behind the leading defects and behind the
unpinned items' own order — and the metadata pass's `prio/*` band, which looks like it should decide this, is not
read by the pre-screen at all.

## What changes

One label the profile declares (`roles.nextUpAgent`, `agent/next-up` here) and the pre-screen honours: an item
carrying it takes the first ranks, in the class order and issue-number order every other stream keeps, and every
other item keeps its own relative order behind it. The label is a **rank**, never a gate: a next-up item that is on
hold or whose author is not authorised is excluded exactly as it would be without the label. A project that declares
the key empty ranks exactly as it does today.

Nothing else moves: no verdict, no label write, no count, and the queue's JSON keeps the flag beside the rank so the
run's dry run prints it.

## Verification

The case `prescreen#aPinnedItemLeadsTheQueue` (fixture `tools/loop/fixtures/scenarios/pinned/`) and
`prescreen#aProfileWithNoPinPinsNothing`; the mutation `259.1` takes the lift out and the case goes red
(`evidence/mutation.log`), while `22.1` keeps the category lead it has always asserted.

surface: 9 files — `git status --porcelain | wc -l` at the end of the implementation (engine, case, fixture,
mutation, profile, two guidelines, the spec delta, this change).
