# Backlog tooling — why (rationale)

The rationale layer of `guidelines/BacklogTooling.md`: the history, the derivation and the alternatives
behind the rules that document states, read against the numbered section it carries. The backlog's own
rationale is `guidelines/Backlog.why.md`.

## §9 Checks — why

The rules of §9 and the rows it names were each a failure the repository had already had:

- A failed run writes nothing because a broken check reading as health is the classic monitoring failure (§12,
  change 4).
- The first exercise is reported rather than spent because 337 items from one `hotspot` row were filed before any
  count of them had been printed anywhere, and that row's report line read `last=never` throughout (issue #667).
- The filing bound exists because one row filed 337 items in 36 minutes (issue #666).
- A cleared condition with no open item is recorded on the row's own record issue so a green check reaches the
  tracker instead of reading as one that never ran (issue #257).
- `metadata-invariant` reports and repairs nothing because which half of a drifted pair is the wrong one is a
  judgement; the drift a citation park left behind made a tombstone read as work (issue #683), the row is the
  answer recorded on #292 (2026-10-25, option 1 of three), and the paired-write refusal landed as the other half of
  the same root cause (issue #671).
- `hotspot`'s bound sits above the largest file it watches because at the old 5 000 bound 358 paths were past it and
  one tick filed 337 items at one every six seconds (issue #665); the exclusion of the change tree exists because a
  copy a change kept of the file it brought under the bound is not a condition (issue #670).
- The read-cost rows exist because nothing measured the start-of-session cost (issue #197), and the routing-table
  figure is a comparison rather than a presence: a figure after a lettered reference must equal that section's cost
  (issue #712).
