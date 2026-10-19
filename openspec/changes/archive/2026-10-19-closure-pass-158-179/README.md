# Closure pass over issues #158–#179 — the ledger

This directory is a **record**, not a change: it holds no proposal, no design and no tasks, because the work
it describes is the verification and the tracker writes owed by the eleven changes that fixed those items.
`run-suites.sh`, `post-comments.sh` and the two `finding-*.json` files are the exact scripts and payloads
that were run, kept so the ledger can be re-derived rather than trusted.

## What the pass found, and what it repaired

Six of the items were fixed but carried `status/open`, and three carried two values of the status namespace
(`status/fixed-by` *and* `status/on-hold`) — the very shape issue #179 (`fix-issue-filing-state-namespace`)
refuses at filing time. `list.sh --status open` therefore offered finished work to the next selection.

| repair | items |
|---|---|
| `status/fixed-by` set (fix committed, no status recorded) | #165, #166, #167, #169, #170, #171 |
| stale `status/on-hold` removed (a second status value) | #172, #175, #179 |
| `status/open` corrected to `status/fixed-by` on **closed** items | #161, #163, #164 |

Every item in the range now carries exactly one `status/*` value; measured with
`gh issue view <n> --json labels -q '[.labels[].name | select(startswith("status/"))] | join(",")'`.

## Two things the pass could not do, and why

**Closure.** `tools/issues/close.sh` refuses an item without `agent/eligible`
(`missing-eligibility-label`, exit 3), and `tools/issues/update.sh` refuses to add that label
(`label-reserved`, exit 3) — only a person authorises automated work, as `guidelines/Backlog.md` §5
requires. The eleven items therefore carry `status/fixed-by` and one comment each naming the fixing commit
and the command that verified it; the closure is the owner's.

**#158** is `§169`, a migrated tombstone (`status/retired`, a settled status). No work is owed and none was
attempted.

## The evidence

| file | what it proves |
|---|---|
| `evidence/suites.log` | `build-inputs-selftest` 11/11, `declared-cases-selftest` 12/12, `check-portability-selftest` 26/26, `issue-tooling-selftest` 77/77 — all exit 0, at `70b278d` |
| `evidence/suites-2.log` | `sensor-selftest` 43/43 exit 0, then the loop suite and the loop mutation suite |

All ran offline, token-free and without Gradle — the whole range is tooling (`guidelines/Build.md` §2 scope
block: no compiled file changed, so no build and no device run is owed).

## Findings filed rather than folded in

Two defects the pass met while doing its own work, filed so the pass stayed a verification:

- **#181** — `close.sh` closes the item and leaves its old `status/*` label, so a closed item is still
  selected by `list.sh --status open`. Repaired by hand in the tracker here; the tool still has the defect.
- **#182** — a comment-only `update.sh` call prints `already carries  — no label write` with an empty
  status. Cosmetic, but it describes a write that was never requested, in the tool every tracker write goes
  through.
