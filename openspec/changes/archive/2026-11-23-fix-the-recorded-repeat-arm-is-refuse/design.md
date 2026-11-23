# Design — the recorded repeat-read arm is the refusal

## The decision this change makes

One recorded value moves: `LOOP_REPEAT_READ=measure` → `refuse`. Everything else in the rule already exists (#925's
arm and count, #989's count bound, the adapter's export, the extension's fallback), so this is not a design of a new
mechanism but the choice of which of two shipped arms the project records.

## What the arm is, and what it is not

The arm decides what a result is **answered with** — the earlier result's line range and one `[LOOP-REPEAT-READ: …]`
line, or the text again. It is not a verdict: no key gates a run, `tools/loop/state.sh` still counts
`metrics.repeatReads`, and no item's grading reads the arm. That is what allows the recorded value to move without a
gate, a case count or a stop condition changing.

`measure` stays the fallback inside `recordedRepeatArm` (`tools/pi-trim-result.mjs:118-124`) and inside the adapter's
own `case` (`tools/pi-child-events.sh:104`): a word this rule does not declare, and a project that records no key, are
both `measure`. A configuration nobody wrote must never withdraw a read from a child — a ported project that deletes
`loop.conf`'s key owes nothing by it.

## Why the fallback and the recorded value differ

The fallback's reason is unchanged: a value nobody declared cannot be known to be safe, and the arm that serves the
read is the one that cannot break a child. The recorded value's reason is a measurement: 25–76 % of an item's file
reads were a second read, median 66 %, so the arm that serves the read served text the item already had two times in
three. A fallback is a guess about a configuration; a recorded value is a decision about this project.

## What a needed re-read still gets

- The session's own edit: a repeat whose content changed is the verification `#886`/`#685` own, and
  `VERIFICATION_READ_BOUND` answers it with the changed region rather than the earlier range.
- A path the child was never handed: served under either arm, because a notice naming lines the child does not hold
  would send it to read what it cannot.
- A shell range read: served, so the read after `lines:1-200` still delivers the whole file.
- The tail: past `LOOP_REPEAT_READ_REFUSALS` reads of one path the read is refused **whatever arm is recorded** (#989),
  with the count and the turn the result it may quote instead came in at.

## The one way this change can lose

A withheld repeat a child re-asks is a turn, and a turn is what the arm spends to save characters. The item's own
`Measure after` names the reading that decides it (`.pi/issue-loop/finding-verification-reads.json`): if an item buys
an extra turn for a withheld repeat, `measure` was the cheaper arm and the value returns. This change takes no
measurement of that; it moves the default the item asks for and records the reading it must be judged by.

## Where the prose homes are, and why they move

- `tools/loop/loop.conf` — the recorded value and the paragraph that prices it (the loop's project block, D16; the
  engine reads no such key).
- `tools/pi-trim-result.mjs` — the header's "the recorded default" and `recordedRepeatArm`'s doc, which stated the arm
  the file records.
- `tools/pi-child-events.sh` — "`measure`, the value `tools/loop/loop.conf` records".
- `tools/loop/prompts/worker.md` — the repeat-rule paragraph a child reads.
- `guidelines/Backlog.recipes.md` §7 — the paragraph that states the rule for a reader of the loop.
- `tools/loop/tests/loop-cases.sh` — the neighbour case's comment, which called `measure` the recorded value.

## Not decided here

- Whether the arm's refusal should ever carry the changed region itself: that is `VERIFICATION_READ_BOUND`'s split and
  is answered before the arm is reached.
- The count bound's value: `3` is #989's, and this change reads it, never rewrites it.