# Design — fix-parked-tombstone-keeps-its-settled-state

## Root cause

`reopen_item` (`tools/issues/resolve-stale.sh:157-180`) writes `--status "$open_status"` unconditionally
(`:170`, `:175`). The park it reverses had written `holdStatus` through `update.sh --status`, and that write
*replaces* the status namespace (`tools/issues/update.sh:14-18`, `:155-174`), so a tombstone's `status/retired`
was removed when the park landed. At reversal time the labels hold `holdStatus` alone, and the reversal writes
the queue state — a settled tombstone becomes indistinguishable from work (`tools/loop/prescreen.sh:258`:
only a status in `settledStatuses` is `settled`).

## The single fix

At reversal, the resolver asks the item's body whether it carries the profile's settled-record marker
(`roles.settledRecordMarker`, the migration's `- **Retired** ℹ: removed from the backlog by commit …` line,
written by `openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/tombstones.sh:70`):

- marker present → write `roles.retiredStatus`, count `restored`, and comment that the item's own record
  settles it rather than that the pre-screen decides it again;
- no marker → today's reversal, unchanged (`openStatus`, counted `reopened`).

A profile that declares the marker must declare `retiredStatus` inside `settledStatuses`; otherwise the run
stops before its first write, naming both roles. The refusal is a predicate with a case
(`resolver#tombstoneRestoreNeedsASettledState`), not a comment.

## Alternatives the evidence eliminates

- **Restore the label the reversal replaces** (the item's first candidate, read against the labels). Impossible:
  `update.sh` removes every other status value in the same edit that adds the park's (`tools/issues/update.sh:155-174`),
  so the reversal sees `holdStatus` and nothing else. The park's comment never named the replaced state either
  (`git show a378325c^:tools/issues/resolve-stale.sh` — the parking comment carries the citation line and the
  repairs, no status), and the pre-#281 run wrote no other record. The body is the only surviving evidence.
- **Refuse to reverse a parked tombstone** (the item's second candidate). A refusal writes nothing, so the item
  stays at `holdStatus` — not one of `settledStatuses` either, so the pre-screen still reads it as a candidate
  and the symptom (a settled tombstone indistinguishable from work) survives the fix. The new case asserts the
  written state, so a refusal is red.
- **Stop the park from erasing the state** (a fix at write time). The park is gone: #281 removed it and the
  script now writes none (`tools/issues/resolve-stale.sh:5-9`, `:124-125`, `:384-388`). Only the residue is
  reversible, and the change the item asks for is the reversal.
- **Name the record's text as a literal in the resolver.** A project value in an engine is the leak
  `tools/check-portability.sh` exists for: it reads `roles` values and forbids them in engines
  (`tools/check-portability.sh:170-176`). The marker therefore belongs in the profile.
- **Declare the marker in a profile section of its own.** `tooling-portability`'s requirement fixes the check's
  value set to the namespaces and `roles`; a new section would either go unchecked (a value the check's set does
  not carry, so a fresh leak path) or extend that requirement — a second capability delta for one defect, which
  the item is not. `roles` already means "which value an engine's decision means" and the check reads it, so the
  marker is declared there and is forbidden in engines the moment it is declared.

## Blast radius

- `tools/issues/resolve-stale.sh` — two role reads, one predicate, one branch inside `reopen_item`, one counter.
  No new writer: every write still goes through `update.sh` (`Backlog.md` §5), so the reserved-label refusal and
  the closed vocabulary apply unchanged.
- `tools/tooling.json` — one `roles` key and the comment that says which script reads which role.
- `tools/fixtures/foreign-project/tools/tooling.json` — the same key, declared empty (a project without
  tombstones), following that fixture's existing `deviceStatus: ""` convention.
- `tools/resolve-stale-cases.sh` — two cases added (`parkedTombstoneKeepsItsSettledState`,
  `tombstoneRestoreNeedsASettledState`); `cleanParkedItemIsReopened` stays the other half unchanged.
- `tools/resolve-stale-mutations.json` — one revert-check (5.17) on the new guard.
- `guidelines/Backlog.md`'s resolver paragraph and the shipped `stale-citation-resolution` spec (delta, then merged on archive).

Not reached: anything compiled, the app, the car path, the native boundary, the tracker. The reversal is a
maintenance verb run by a person or the sensors step; it has no runtime effect.

## Risk

- *A false positive on the marker* would settle an item nobody wanted settled. Bounded: the marker is the
  migration's provenance line, 21 items carry it, and the profile owns the text — a project that writes
  tombstones differently declares its own marker or none. A project with no marker keeps today's behaviour for
  every item.
- *A profile half-adapted* (marker without a settled `retiredStatus`) is refused before the first write rather
  than silently reversed to a wrong state.
- *Existing reverts*: an ordinary parked item still returns to `openStatus`, and the case that asserts it
  (`resolver#cleanParkedItemIsReopened`) is unchanged — a regression there is red, not silent.

## Verification

Case-to-scenario, one case each: `resolver#parkedTombstoneKeepsItsSettledState` (restore),
`resolver#cleanParkedItemIsReopened` (unchanged reversal), `resolver#tombstoneRestoreNeedsASettledState` (the
refusal). One revert-check (`5.17`) mutates the guard so every reversal counts as restored, and
`resolver#cleanParkedItemIsReopened` must go red. No compiled file is touched, so the change's structural check
stands in for a build (`guidelines/Build.md` §2 scope block); the gate is
`./tools/loop/gate.sh`, run once, and its result is quoted rather than retried.
