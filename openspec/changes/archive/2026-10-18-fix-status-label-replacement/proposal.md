# Proposal

## Why

`tools/issues/update.sh` treats `--status <value>` as one more label to add, so the tool that every loop
transition and every repair path goes through cannot perform a transition at all — it accumulates states.
`labels+=("status${separator}${status_value}")` (`tools/issues/update.sh:70`) feeds a single
`--add-label` loop (`tools/issues/update.sh:88-90`); nothing reads the issue's current labels, so no other
`status/*` value is ever removed.

Evidence: after the 2026-10-18 escalations #114 and #115 carried both `status/on-hold` and `status/open`;
after their closures they carried both `status/fixed-by` and `status/open`. Each repair needed a second,
remembered `--remove-label status/open` call. The damage is still visible in the tracker: #109 and #110
carry `status/on-hold` and `status/open` today.

The invariant broken is the one the tooling exists to keep: an item carries exactly one `status/*` label, and
two statuses make every status query answer twice — a selection on `status/open` still returns an item that
moved to `on-hold`, and a selection on the old value still returns an item that moved away. The requirement
it contradicts is `issue-backlog` → "Labels carry the item's kind, area, state and provenance": "exactly one
value per namespace where the item has that fact" ("Each fact has its own namespace").

Reach: `tools/loop/labels.sh:108` is the writer of every loop transition and passes `--status on-hold` for
`stale`, `unspecifiable`, `too-wide`, `not-spec-shaped`, `needs-target`, `needs-device`, `needs-decision`,
`needs-diagnosis`, `escalate`, and `--status fixed-by` for `close`; `tools/issues/resolve-stale.sh:230` parks
an item with `--status on-hold`; `update.sh` is also the documented repair path of the `update-issue` and
`issue-metadata` skills. Every one of those paths leaves the old state behind.

The single fix follows from the root cause: make the write path replace the state. `tools/issues/reopen.sh`
already does exactly this and is the proven model — it reads `gh issue view --json labels`, collects every
label under the status prefix other than the target, and passes them as `--remove-label` in the same
`gh issue edit` (`tools/issues/reopen.sh:99-117`). The requirement the defect contradicts is `issue-backlog`
"Labels carry the item's kind, area, state and provenance"; there is one defensible fix, so the item is
loop-eligible.

## What Changes

- `tools/issues/update.sh`: when `--status <value>` is given, read the issue's current labels, compute every
  `status/*` label that is not the target, and remove them in the same `gh issue edit` invocation that adds
  the target — so one call cannot produce two states and a repair needs no second call.
- The same call refuses a self-contradicting request instead of writing it: `--status <v>` together with
  `--label status/<other>` (two states in one call) and `--label status/<v>` / `--remove-label status/<v>`
  that duplicate or cancel the `--status` value.
- Idempotence: a `--status <v>` on an item that already carries only `status/<v>` writes nothing beyond the
  requested label (no add+remove of the same label in one edit, which `gh` cannot serve).
- `--json` output's `removedLabels` reports the implied removals, so a caller sees the whole transition rather
  than only the flags it typed.
- The status-prefix scan lives once: `tools/issues/reopen.sh`'s loop and `lib.sh`'s vocabulary helpers are the
  two homes considered; one of them owns it for both writers.
- Falsification case in `tools/issue-tooling-cases.sh` (offline, `tools/issues/fixtures/gh` with
  `GH_STUB_RECORD`): `update.sh <n> --status fixed-by` on an item carrying `status/open` produces one
  `gh issue edit` carrying both `--add-label status/fixed-by` and `--remove-label status/open`; the case is red
  on HEAD.
- One-time repair of the items already carrying two states (#109, #110 and whatever `list.sh --json` finds),
  through the fixed `update.sh` — no new tool, no hand-edited labels.
- `guidelines/Backlog.md` §4 gains the invariant row this defect shows was only prose (the state label is
  replaced, not accumulated) with its measurement — the case above.

Not in scope: a periodic checker that reports dual-state items. The write path is where the defect lives; a
check would report damage after the fact and belongs to a later item if it is wanted.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `issue-backlog`: "Labels carry the item's kind, area, state and provenance" gains the requirement that a
  state transition *replaces* the namespace's value in one write — asserted by a scenario that a status write
  leaves exactly one `status/*` label, and one that a request naming two states is refused before the write.
  The delta is additive (an added requirement beside the existing one) rather than a MODIFIED rewrite, so no
  existing scenario is at risk of being dropped at archive.

## Impact

- `tools/issues/update.sh` — the fix.
- `tools/issues/lib.sh` or `tools/issues/reopen.sh` — where the status-prefix scan ends up shared.
- `tools/issue-tooling-cases.sh`, `tools/issues/fixtures/gh` — the red case and its recording fixture.
- `guidelines/Backlog.md` §4 — the invariant row and its measurement; §5 ("Who may write what") is unchanged,
  since no new writer appears.
- Behavioural change for callers, additive in intent: `tools/loop/labels.sh` needs no edit and stops leaving
  the old status; `tools/issues/resolve-stale.sh` stops parking an item beside its old state; the
  `update-issue` and `issue-metadata` skills' repair path becomes one call.
- Not breaking a data format: no persisted schema, no migration, no new dependency. Rollback is reverting the
  one write path and the delta — labels already repaired stay repaired, and nothing reads the tool's internals.
- No native, JNI, submodule or Android surface is touched; this is tracker tooling only.
