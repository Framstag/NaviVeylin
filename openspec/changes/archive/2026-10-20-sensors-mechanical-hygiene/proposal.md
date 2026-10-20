# Proposal

## Why

The tracker's hygiene pass exists as a **skill**, not as a step: `cleanup-todo` repairs labels, finds
duplicates and closes what is provably done — and nothing calls it. Measured 2026-10-20: it was named in a
session as the thing that should have run, and the answer was "a person has to remember it"; the same day
the tracker reached 10 open `agent/needs-decision` items and its decision ceiling had to be raised so runs
could still start.

Part of that work is mechanical, and a scheduled writer for exactly that class already exists:
`.github/workflows/sensors.yml` runs every 30 minutes (`cron: '0,30 * * * *'`), holds `issues: write` for
its job only, serialises itself (`concurrency: sensors-main`) and already applies two repairs today —
`tools/issues/labels.sh --apply` and `tools/issues/resolve-stale.sh --apply --max-items 25`. What is missing
is the third, purely mechanical sweep:

- **Duplicates by fingerprint.** `tools/issues/create.sh` refuses a duplicate *at filing time* by hashing
  `class | category | claim | anchor` into the body's `<!-- fingerprint: … -->` marker. Nothing ever looks at
  the markers that already exist, so a duplicate filed before the other was found, or by an import, stays.
- **`nvid` collisions.** Two items claiming the same `<!-- nvid: N -->` make `tools/issues/list.sh --id N`
  ambiguous, which is how a `§N` citation is resolved. Nothing checks for it.

Both are `jq` over fields the tooling itself wrote; neither needs a judgement, a model or a token. They
belong in the workflow that already owns cadence, dry-run/apply and the write scope.

Not in scope, deliberately: closing items whose work is provably done, and closing duplicates that are only
*judgement*-duplicates (#64/#65 were merged by a person because their fingerprints differ — that stays a
person's call), and anything that needs a model. The judgement half of `cleanup-todo` stays what it is — a
skill a person runs — and this change removes the part that never needed a person.

## What Changes

- A new script `tools/issues/duplicates.sh` reads every open item's fingerprint marker and `nvid` marker and
  reports two lists: **duplicates** (one fingerprint, several items) and **collisions** (one `nvid`, several
  items). It is read-only by default and `--apply` closes the younger duplicate of each group with a comment
  naming the surviving item — the rule the tracker's own skill states, now executed. An item that does not
  carry the authorisation label cannot be closed by any part of this tooling (`close.sh` refuses with
  `missing-eligibility-label`), so for those the sweep posts the same pointer as a comment and leaves the
  item open: the rule becomes visible where the write is allowed, and nothing is closed behind a person's
  back.
- Collisions are **reported, never repaired**: which item legitimately carries an `nvid` is recorded in the
  migration table, not in a script. The sweep names them in the workflow's job summary so they become visible
  without a write.
- `.github/workflows/sensors.yml` gains one step that runs the sweep in the mode the workflow already
  computes (a scheduled run applies, a dispatch defaults to a dry run) — no new workflow file, no new token,
  no new cadence.
- The sweep prints a third, read-only list: open items whose id appears in a commit that is an ancestor of
  `main`, so the "work landed, item still open" family is visible in one place. It closes none of them: a
  commit naming an id is not proof that the *item* is delivered (a cherry-pick names the same id, and a
  commit can say "WIP #N"), which is why that half stays with a person or with the loop that landed the
  work.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `backlog-sensors`: one requirement is added — **a mechanical sweep runs in the scheduled worker and closes
  only what its own rule states**: a duplicate group closes the younger item with a pointer to the survivor,
  an identifier collision is reported and never repaired, and a candidate list is printed without a write.
  The existing requirements (a check is a declared row; a check acts only on what it owns; a repeating
  condition escalates) are unchanged — the sweep is a step beside the checks, not a check, because it
  measures no condition of the project.
- `backlog-source-of-truth`: one requirement is added — **one fingerprint, one item**: the tracker SHALL NOT
  keep two open items carrying the same fingerprint marker, and a duplicate that is not caught at filing
  time SHALL be found by a sweep that names the surviving item.

Previous specifications changed: `backlog-sensors`, `backlog-source-of-truth`. Referenced but not modified:
`stale-citation-resolution` (the sweep beside it), `agent-issue-loop` (it closes what it lands, and its own
duplicate rules are unchanged).

## Impact

Scope: **tooling only**. Affected files:

- `tools/issues/duplicates.sh` (new) — the sweep: `--apply` and `--dry-run`, `--json`, `--limit` (the read
  bound) and `--max-items` (the write bound), exit codes in the shape the other verbs use (0 ok, 1 a write
  failed, 2 usage, 3 policy).
- `tools/issue-tooling-cases.sh` — nine `duplicates#` cases beside the suite's own harness (the stub `gh`, the
  fixtures and the assertion helpers already live there, so a second driver would be a second copy of them),
  plus `tools/issues/fixtures/scenarios/duplicates/issues.json`.
- `tools/duplicates-mutations.json` (new) — one mutation per invariant that decides a closure (the survivor
  rule, the authorisation rule, the candidate filter and the dedupe marker), listed by the issue-tooling CI step like the other sets.
- `.github/workflows/sensors.yml` — one step after `resolve-stale.sh`, same `SENSORS_MODE`.
- `tools/issues/lib.sh` — only if the marker extraction is not already there; the sweep must reuse the
  fingerprint marker definition rather than re-derive it.
- `guidelines/Backlog.md` §9 `Checks` — the sweep joins the list of what the scheduled worker runs, with the
  rule that collisions are reported and duplicates close the younger item.
- `openspec/specs/backlog-sensors/spec.md`, `openspec/specs/backlog-source-of-truth/spec.md` — the two
  deltas.

Additive or breaking: **additive**. No label vocabulary, no item format and no workflow token changes; a
tracker without duplicates and collisions is untouched (`nothing to do` and exit 0). Rollback path: remove
the workflow step and the script; items it closed stay closed, and `reopen.sh` reverses any of them.

Verification: the new offline selftest (no token, no network), `tools/issue-tooling-selftest.sh` unchanged
and green (the sweep is a new verb, not a change to an existing one), `tools/check-portability.sh --quiet`,
and one dispatched run of `sensors.yml` in dry-run mode against the real tracker whose summary lists what a
scheduled run would close. No Gradle gate: no compiled file is touched.

Open question carried into `design.md`: whether the sweep should be a registry **row** (with a `cmd`, a
`cron` and `lifecycle: file+close`) instead of a workflow step, and what it would cost — the registry's own
requirement says a row's declared input is published by a build pipeline and checked against its producer,
which a tracker-internal sweep has no input for.
