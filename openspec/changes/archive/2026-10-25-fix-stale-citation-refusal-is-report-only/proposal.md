# fix-stale-citation-refusal-is-report-only

## Why

The classifier answers `absent` for a token no file, submodule or generated artefact accounts for
(`tools/issues/lib.sh`, `issues_citation_class`). Two consumers read that as *the record cites something the
tree lost*:

- `tools/loop/prescreen.sh:306-314` — verdict `stale`, and `:373-388` writes `agent/excluded-stale`, so the
  item is refused before any worker sees it;
- `tools/issues/resolve-stale.sh:429-441` — `status/on-hold` plus a parking comment, which the same script then
  skips forever.

**Root cause, with evidence.** The two sentences are not the same. Absence has legitimate causes the class
vocabulary cannot express — a path the change itself creates (`core/OverrunWindow.kt` #73,
`buildSrc/settings.gradle.kts` #92, the metric script #197 names), a deleted path the body says was deleted
(#41), a run's own artefact (`reviewer-report-1.md` #227, `worker-prompt.md` #169). Measured 2026-10-24 on the
live tracker: of the 35 items carrying the parking comment, **2 were the proposed shape, 16 were other
shapes, and 18 had no stale citation left at all** — their bodies had been repaired by hand, the tree had
gained the file, or a rename had settled it, and nothing could notice because the park was the marker that
stopped anyone looking (issue #281).

The refusal's precision is therefore poor, and its false positive is the most expensive action in the
tooling: an item invisible to the loop, measured at four days on #197. Its false negative costs one worker
phase on an item the worker reads anyway.

**The fix.** A citation the tree does not hold is *reported* and never refuses and never parks. The classifier
keeps answering `absent`; both consumers ask it and act on it as a report. The authoring guard moves to filing
time, where it needs no new vocabulary: `create.sh` already inspects absent citations and will say what to do.

The alternative — a token form marking a proposed path in `citations.json` — was measured and rejected: it
covers 2 items, needs a grammar authors must learn, must expire (a forgotten marker reports a lie beside an
`exact` file), and is a patch on the refusal rather than on its cause. The decision and its reasoning are on
#281.

## What changes

- `tools/loop/prescreen.sh`: the citation sensor reports. A missing citation no longer produces a verdict; the
  absent token is named in the item's record (`absentCitation`) and in the run's report. The refusal-label
  clearing is kept, so an item still carrying a historical `agent/excluded-stale` sheds it on the first run
  whose verdict is not a refusal.
- `tools/issues/resolve-stale.sh`: an unresolved citation is counted and reported (`unresolved`), and the run
  writes nothing for it — no `status/on-hold`, no parking comment. The repair ladder is unchanged, and
  `--revisit-parked` keeps clearing the residue this tool created.
- `tools/sensors/metrics/stale-citations.sh`: the residue row counts `unresolved + stillParked`, so it still
  measures every citation nothing accounts for.
- `tools/loop/report.sh`: the "repair the citation" action source is gone with the refusal.
- `tools/issues/create.sh`: unchanged behaviour, one point of the comment made explicit — an absent citation
  is printed with the two things it can mean.

## Spec

Delta to `openspec/specs/stale-citation-resolution/spec.md`:

- `MODIFIED` **One definition of what a citation is** — `absent` is a report, not a verdict, and the scenario
  that read it as stale is adjusted to say so.
- `REMOVED` **The staleness verdict is visible in the tracker** — it existed only to make the citation refusal
  readable in the tracker; the generic refusal trace for the loop's other verdicts is unchanged.
- `MODIFIED` **A citation is repaired when the repair is mechanical** — an unrepairable citation is reported,
  not parked.
- `MODIFIED` **What cannot be repaired is parked, never guessed** → **reported, never guessed**.
- `MODIFIED` **The resolver runs on its own, bounded and idempotent** — the bound is spent on repairs alone,
  and a token in the title is reported rather than parked.

## Impact

`tools/loop/prescreen.sh`, `tools/loop/report.sh`, `tools/issues/resolve-stale.sh`,
`tools/sensors/metrics/stale-citations.sh`, `tools/loop-cases.sh`, `tools/resolve-stale-cases.sh`,
`tools/sensor-cases.sh`, `openspec/specs/stale-citation-resolution/spec.md`. No compiled file, no Gradle
build. Rollback: revert the branch; the residue this change stops creating is still clearable by
`resolve-stale.sh --revisit-parked`.

## Open

Nothing calls the resolver on a schedule in-repo; the residue row (`stale-citations`) is the measurement that
would show a pile growing. The proposed-path marker stays unbuilt; it comes back only on a measurement that
report-only wastes worker phases.
