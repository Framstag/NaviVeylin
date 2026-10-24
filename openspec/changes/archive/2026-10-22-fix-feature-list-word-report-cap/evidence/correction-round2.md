# Correction round 2 — the spec's figure scenario named a case that does not assert it

The first review refused the change on one blocking finding (`LOOP-BLOCKING: 1`): the delta's scenario
`The report does not stand in for the figure gate` named `gates: the report does not stand in for the figure gate`,
a case that asserts a green run on an unsourced word — the opposite of the scenario's THEN, "the run fails rather
than reporting". The mapping is authored by this change (the capability spec had no `Case:` lines at all), so the
change owns it. The recorded decision on the item (option 1, cap the report) is untouched: the production change,
its red case and its falsification all stand.

## What changed in this round

| file | change |
|---|---|
| `openspec/specs/feature-list-generation/spec.md` | the figure scenario's `Case:` now names `gates: an invented figure fails the run` (`tools/gen-feature-list-selftest.sh:460`, `is "…" "1" "$last_rc"`, `6-1-1-3`) |
| this change's `specs/feature-list-generation/spec.md` | same one line, so the delta and the merged capability agree verbatim |
| `tasks.md` | the four evidence pointers that named files the evidence directory never held: `check-doc-routes.log` → `doc-routes103.log`, `check-portability.log` → `portability103.log`, `openspec-validate.log` → `openspec103.log` and `openspec103b.log`; and 4.1 now names where the restore's green run lives (`green103b.log`, `green-final.log`) instead of implying the mutation log holds it |
| `design.md`, `proposal.md` | the two citations of `…/2026-10-09-generate-feature-list/evidence.md` → `apply-evidence.md` (the file that exists) |
| `design.md` | three claims the review marked UNPROVEN. The release note's area label: the note's entry opens with `## <release>` (`tools/gen-feature-list.sh:822`), so the version is the area, and `area_label()`'s `the document` arm is reached by neither call site. `specfreq`'s size: measured at 6 218 distinct words over 177 spec files, with the command in the text, instead of "a few tens of thousands". The 352-line figure: cited to the lines that carry it (`apply-evidence.md:484`, `:620`, and `:456` for 582 bullets in 12 areas), with the issue's own "225 bullets" no longer presented as coming from them. Rollback: the "prints the unbounded report again" claim is marked as reasoning from the diff, not a measured run. |

Nothing in `tools/` changed in this round, so there is no compiled file and no Gradle task; the checks below are the
ones a documentation delta owes.

## Verification of this round

```
tools/gen-feature-list-selftest.sh          119 passed, 0 failed   (correction-round2-selftest.log, 09:51:44Z)
tools/check-doc-routes.sh                   all 120 section references resolve, all 5 skill references resolve
openspec validate --all                     177 passed, 0 failed (177 items)
```

Every `Case:` in `openspec/specs/feature-list-generation/spec.md` names a case the self-test really holds — 14 of
14 resolve:

```
grep -o '\*\*Case\*\*: `[^`]*`' openspec/specs/feature-list-generation/spec.md | sed 's/.*`\(.*\)`/\1/' \
  | while read -r c; do grep -qF "$c" tools/gen-feature-list-selftest.sh && echo "ok   $c" || echo "MISSING $c"; done
```

## Observed, not changed

- `gates: the report does not stand in for the figure gate` (`tools/gen-feature-list-selftest.sh:474`) is a
  pre-existing case name whose assertion is that the catalogue still prints on a run carrying an unsourced word.
  With the scenario remapped it no longer stands behind a spec scenario. It is a kept case of the pre-existing
  suite and is named in `guidelines/FeatureList.md`'s case list, so renaming it is outside this correction.
- The requirement text keeps its pre-existing "a entry" (twice) — kept text, not rewritten here.
