# Design — fix-metadata-drift-check-never-runs

## The root cause, in one sentence

The priority invariant's per-item half lives only in `check-metadata.sh`'s two tracker-reading modes, and no
unattended caller selects either of them — CI runs the token-free self-check, the loop's pre-screen never reads
the pair, and the registry's eight rows all measure something else — so a drift written by any later writer stays
invisible until a person runs `--live` by hand.

## The single fix

A ninth registry row owns the invariant, and its measurement is the command that already owns the rule:

| piece | what it does |
|---|---|
| `tools/sensors/metrics/metadata-invariant.sh` | runs `tools/issues/check-metadata.sh --live --json`, emits one `violation` per `(issue, code)` it reports, one `clear` per recorded open condition that stopped drifting, the row-level `clear` when nothing was found, and `error` when the read is unusable |
| `tools/sensors/sensors.json` | the row: `0 9 * * *`, `threshold` 1, `boolean`, `consecutive` 1, `bug` / `specs-and-process`, `file+close`, fingerprint `["issue","code"]` |
| `.github/workflows/sensors.yml` | the row joins the `--rows` list of the step that runs for any record — the registry's execution plan is those two lists |
| `guidelines/Backlog.md` §9 | one paragraph, beside `stale-citations`, saying what the row watches and that it reports rather than repairs |

The `file+close` lifecycle already gives everything else: a drifted item is filed once, a second run annotates
that issue instead of filing again, a repaired pair closes it on the marker's own instant, and a condition that
keeps returning escalates past the registry's recurrence bound.

## Why the identity is `(issue, code)` and not the check

`guidelines/Backlog.md` §9's first rule: "identity is finer than the check … a different cause under the same
check is a new item". Two items drifting, and one item drifting in two ways, are three conditions a person
repairs three times, so each is one issue under its own fingerprint — and `code` is the invariant's own
vocabulary (`label-without-marker`, `marker-without-label`, `band-mismatch`, `multiple-bands`,
`marker-incomplete`, `score-out-of-range`, `band-out-of-vocabulary`), which `check-metadata.sh` already owns, so
the measurement invents no vocabulary of its own.

## Why the measurement delegates

The alternative — a metric that lists the tracker and re-implements the seven violation codes and the marker
grammar — is exactly what went stale: two readers of one grammar (`issues-priority`, "The marker is read where
the pass writes it", and the `metadata-mutations.sh` harness that keeps the reader honest). Delegating means the
row inherits every future refinement of that grammar for free, and the same shape is already shipped for the
other tracker-watching row: `stale-citations.sh` measures through `resolve-stale.sh`'s dry run rather than
counting citations itself (`tools/sensors/metrics/stale-citations.sh:5-10`).

## The one judgement the delegation needs

`check-metadata.sh --live` reports `{"checked":N,"violations":[…]}` and exits 1 when N > 0 violations exist.
`gh` failing is already its exit 2 (`check-metadata.sh:126`), which the metric reads as the third outcome. The
residue is a read that *succeeds* and reports `checked: 0` — an unparseable tracker answer does exactly that,
because its `jq` selects no number and the loop over zero items reports nothing. The metric therefore treats
`checked == 0` as the third outcome instead of as a clean tracker: this project's tracker carries 306 items, so
a zero-count read is a read that could not be made. Both halves are asserted in `sensors#metadataDriftRow`
(invalid JSON fixture → `verdict=error`).

## Blast radius

Read-only against the tracker, additive in the registry. Touched: one new script, one registry array entry, one
`--rows` list plus its comment, two cases, one mutation entry, one guideline paragraph, two header lines of
`check-metadata.sh`. Nothing in `run.sh`, `lib.sh`, `guard.sh` or `report.sh` changes — the engine keeps naming
no project value (`sensors#engineNamesNoProjectValue`), and the foreign-project fixture keeps its own registry,
because a project's row set is its own configuration (`Backlog.md` §10).

## Alternatives the evidence eliminated

- **A pre-screen guard in `tools/loop/run.sh`** (the item's option 2) — eliminated by the measurement, not by
  taste: drift was found on items #304 and #305 today, and neither is queued for work, so a guard that fires only
  when an item is picked leaves exactly the drift that nobody works invisible.
- **Refusing `prio/*` in `tools/issues/update.sh`** (option 3) — it prevents one write path and cannot prevent a
  hand edit on the web UI, which is the drift source that produced both current violations; the owner recorded it
  as a separate, later item, so it is not this change's fork.
- **Repairing the drifted items here** — which half of a pair is wrong is a judgement (a stale label on a settled
  item, or a marker the pass wrote and somebody overwrote), so this change reports and repairs nothing.

## Rollback

Revert the commit: the row leaves the registry and the pipeline, the metric script leaves the tree, and the two
cases fail on the base exactly as `evidence/red-case-head.log` records.
