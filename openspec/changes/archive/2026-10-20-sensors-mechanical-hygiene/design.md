# Design

## Context

Two writers already act on the tracker without a person: the loop (which lands and closes what it worked) and
the scheduled sensor job in `.github/workflows/sensors.yml` (every 30 minutes, `issues: write` for its job
only, `concurrency: sensors-main`, `SENSORS_MODE` = apply on `schedule` and dry-run on dispatch). The second
one is the right home for rules that are pure functions of the tracker's own fields, and it already runs two
of them: `labels.sh --apply` (vocabulary repair) and `resolve-stale.sh --apply --max-items 25` (stale
citations).

What the tracker still keeps without a writer: two open items carrying the same `<!-- fingerprint: … -->`
marker (nothing sweeps markers after filing), and two items claiming the same `<!-- nvid: N -->` (which
makes `list.sh --id N` ambiguous, and that is how a `§N` citation resolves).

Constraint discovered while designing, and it decides the shape: **`close.sh` refuses a closure for an item
without the authorisation label** (`missing-eligibility-label`), and only `--sensor <id>` relaxes that — and
only for an item carrying that check's own label. A hygiene sweep owns no label, so it may close only items
that are already authorised for automated work.

## Decisions

### D1 — A workflow step, not a registry row

**Alternatives.**

1. **A row in `tools/sensors/sensors.json`.** The registry's own requirement is that a row declares a `cmd`,
   a `cron`, a bound and a *declared input published by a build pipeline and checked against its producer*,
   and that a check owns the issues it files by its label. A duplicate sweep has no such input (its input is
   the tracker), owns no label, and its "measurement" is not a number of the project but a fact about the
   tracker. Filing it as a row would either stretch the registry's contract or add a fake input.
2. **A separate workflow file.** A second cadence, a second concurrency group and a second token scope to
   keep in step with the first — the failure mode is two writers racing on the same issues, which the
   existing `concurrency: sensors-main` exists to prevent.
3. **A step in the existing workflow** — chosen. Same cadence, same mode computation (a scheduled run
   applies, a dispatch defaults to a dry run), same serialisation, same permission block.

**Risk.** A long step delays the sensors after it. Mitigation: the two bounds are separate — `--limit` is what
is read (400, because a fingerprint group can pair the newest item with an old one), `--max-items` what is
written in one run (25, as `resolve-stale.sh` splits it) — and the sweep exits 0 with `nothing to do` on a clean
tracker.

### D2 — Which duplicate survives: the older item

**Alternatives.** (a) The item with more labels, or the one whose body is longer — unstable: a metadata pass
changes it, and then the survivor changes with it. (b) The item with the lowest issue number — chosen: it is
immutable, and for migrated items it is the one the migration table's `nvid` points at. The closure comment
names the survivor, and the survivor gets a comment naming the closed sibling, so neither direction is lost.

### D3 — Collisions are reported, never repaired

Two items carrying one `nvid` is a data error whose truth is in the migration table
(`tools/issues/list.sh --id N` resolves through it, and `cleanup-todo` names collisions as its own job). A
script that picked a winner would rewrite the one thing the `§N` resolution depends on. So the sweep prints
the collision group with both issue numbers and exits 0; the workflow's job summary shows it. **Risk:** a
collision nobody reads. **Mitigation:** the list is printed on every run, so it becomes visible the day it
appears rather than the day someone looks.

### D4 — "Work landed, item still open" is printed, not closed

**Alternatives.** (a) Close an open item whose id appears in a commit that is an ancestor of `main` — 
rejected: today's landing practice cherry-picks, so one commit's message names the id on `main` *and* on the
item branch, and a message may say `WIP #N` or `reopen #N`. The sweep cannot tell "this commit delivered the
item" from "this commit mentions it". (b) Print the candidate list for a person or for the loop that landed
the work — chosen. The list is cheap, read-only, and turns a hunt into a reading.

### D5 — The sweep writes only through the tooling's own verbs

Same rule as every other writer here: the closure goes through `tools/issues/close.sh` (reason and evidence
first, then the close), the pointer comment through `tools/issues/update.sh --comment`, and the verdict
`not-planned` records that the item was not actioned because it duplicates another. Nothing writes a label
the vocabulary does not declare, and the reserved authorisation label is only ever *read*.

Consequence, stated plainly: on a tracker where most items are not authorised for automated work, the sweep
closes few items and comments on the rest. That is the intended behaviour — it makes the duplicate visible
where the tooling is allowed to speak, and it never widens the authorisation to do more.

### D6 — The judgement half stays a skill

`cleanup-todo` also merges duplicates whose fingerprints differ (#64 into #65 on 2026-10-20, by hand),
closes items whose work is provably done, and repairs a status that a run left behind. None of that is a
function of fields alone. Adding a model to the scheduled job is the one thing this design rules out: that
worker's token may close items, and an unsupervised model with that token is precisely what `close.sh`'s
evidence rule and the reserved label are built to prevent.

## Files and structure

| file | change |
|---|---|
| `tools/issues/duplicates.sh` | new: `--apply`/`--dry-run`, `--json`, `--limit` (read), `--max-items` (write); reads `<!-- fingerprint: … -->` and `<!-- nvid: N -->` from every waiting item; groups duplicates and collisions; closes the younger duplicate through `close.sh` when it carries the authorisation label, points the survivor at it, comments an unauthorised duplicate **once** (the marker is the dedupe), and prints the landed-candidate list |
| `tools/issue-tooling-cases.sh` | nine `duplicates#` cases in the suite that already owns the stub `gh` and the fixtures, over the new `tools/issues/fixtures/scenarios/duplicates/` |
| `tools/duplicates-mutations.json` | new: four mutations (survivor rule, authorisation rule, candidate filter, dedupe marker), listed by the issue-tooling CI step |
| `tools/issues/lib.sh` | reuse the fingerprint-marker reader rather than re-deriving the pattern in a second place |
| `.github/workflows/sensors.yml` | one step after `resolve-stale.sh`, in `$SENSORS_MODE`, with `--limit 400 --max-items 25`, its output also appended to the job summary |
| `guidelines/Backlog.md` §9 `Checks` | the sweep joins the list of what the scheduled worker runs, with the two rules (older item survives, collisions are reported) |
| `openspec/specs/backlog-sensors/spec.md`, `openspec/specs/backlog-source-of-truth/spec.md` | the deltas |

No threading, lifecycle, Compose or native surface: this is shell and `jq`, offline in the suite, one HTTP
client (`gh`) in production.

## Verification

- Offline: `bash tools/issue-tooling-selftest.sh` — the nine `duplicates#` cases, red before the script exists
  (the first case fails on "the script is missing"), green after; `tools/issue-tooling-selftest.sh` still
  green (a new verb must not change an existing one); `tools/check-portability.sh --quiet` unchanged.
- Structural: `bash -n` on the new scripts; `openspec validate sensors-mechanical-hygiene --strict`.
- No Gradle gate: no compiled file is touched (`guidelines/Build.md` §2, the scope block); the exclusion is
  named in `tasks.md`.
- Real tracker, read-only: one `workflow_dispatch` of `sensors.yml` without `apply`, whose summary must list
  what a scheduled run would close — the dry run is the proof against the live backlog, and it costs no
  write.
- Falsification: one mutation in `tools/loop-mutations.sh`'s sibling for this tooling (or a case-level
  revert-check) that makes the sweep choose the *younger* item as survivor — the first case must go red.
