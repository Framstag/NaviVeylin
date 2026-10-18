# Design — the pre-screen's refusal is a refusal label

## D1. The two shapes the issue names

**(1) A verdict label per exclusion reason, authorisation label intact.** The item keeps `agent/eligible`
and gains `agent/excluded-<verdict>`; the queue's own queries are unaffected; the label states *why*.

**(2) Withdraw the authorisation label** for a verdict the item cannot escape (`stale`, `class-feature`,
`settled`), so the item stops looking authorised.

## D2. Shape (2) is not available to the loop, and would be the same defect mirrored

- Every write path refuses every reserved label in its input — `Policy.refuseReserved` returns
  `label-reserved`, exit 3 — and `agent/eligible` is the profile's only reserved label
  (`tools/tooling.json` → `reservedLabels`). A withdrawal is a write naming that label, so it is refused
  like an addition. Shape (2) is not a small change; it is a widening of the reservation, which is the
  property `Backlog.md` §5 calls "the only key to unattended work" and change 1 landed deliberately.
- Even if permitted, it conflates two states the owner must be able to separate: *nobody authorised this*
  and *the loop withdrew the authorisation*. That is the defect the issue reports, mirrored — the item would
  become indistinguishable from an unauthorised one, which is exactly what the gap is about.
- The spec forbids it in words: the run "SHALL never write the authorisation label itself".

**Chosen: shape (1)**, and it is also the shape that is a *refusal* rather than a silent tracker edit —
`status/on-hold` would have been the third candidate (the value `labels.sh` already uses when it parks an
item), and it is rejected for that reason: it moves the item's state without saying why, and it collides
with the parking `resolve-stale.sh` performs for a different purpose.

## D3. Which verdicts are written

Only the three an item cannot escape by itself — `stale`, `class-feature`, `settled`:

| verdict | why it is not written by the pre-screen |
|---|---|
| `in-flight` | clears when the other change closes; a label would need a second writer to remove it |
| `needs-device` | `labels.sh` already writes `agent/needs-device` for it |
| `needs-decision` | `run.sh`'s `escalate_prescreen_decisions` already writes `agent/needs-decision` + a comment |
| `missing-authorisation` | the item is already visibly unauthorised — the label would add nothing |
| `author-not-allowlisted` | the same, and the branch is unreachable behind `list.sh --eligible-only` |

## D4. Why a rerun does not re-decide forever

Two properties, both asserted:

- the write happens only when the item does not already carry the label, so a second run of the same verdict
  is one no-op `gh` call and no comment stream;
- a run whose verdict is no longer a refusal **removes** the label (`clear-stale`, `clear-feature`,
  `clear-settled`), so the refusal is a *re-stated decision* rather than a permanent mark — the item returns
  to work after a repaired citation without a person having to hunt for a stale label.

## D5. Where the write lives

`prescreen.sh` collects the writes and applies them once, after the queue is written (so a pre-screen that
dies mid-selection changes no item), and every write goes through `labels.sh` — the loop's single label
writer (`Backlog.md` §5, D7). The pre-screen runs in the run's script, never in a child process, so the
tracker credential never reaches an agent.

`run.sh` gains one line: it passes `--dry-run` to the pre-screen, because a dry run must write no label and
`prescreen.sh` has no other way to know it is one.

Failures are reported and never fatal: a tracker write that fails leaves the verdict, its reason and its
evidence in the run's own state and report, which is what the run already produces.

## D6. Alternatives the root cause eliminated

- *Leave the record in `run.json` and let the report print it* — the state is replaced by the next `init`;
  the defect is the lifetime, not the content.
- *Post a comment per refusal* — the reason survives but a rerun posts it again; a label is idempotent by
  construction and selectable, which the owner needs ("one query").
- *One shared `agent/excluded` label* — one query for the owner, but the verdict (the only part worth
  querying on) would live in a comment, i.e. back in text nobody selects on.
- *Withdraw the opt-in (shape 2)* — refused by the reservation; see D2.
