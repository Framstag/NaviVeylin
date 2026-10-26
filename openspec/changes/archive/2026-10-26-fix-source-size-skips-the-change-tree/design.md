# Design — fix-source-size-skips-the-change-tree

## Root cause

`tools/sensors/metrics/source-size.sh:28`

```bash
sizes=$(git ls-files -z '*.kt' '*.java' 2>/dev/null | xargs -0 -r -n 100 awk '…')
```

The list is every tracked Kotlin or Java path in the repository, with no pathscope. The change tree is tracked
too, and a change that shrinks a file keeps the pre-change file as evidence — `2026-10-25-fix-auto-map-renderer-file-size`
kept `openspec/changes/archive/2026-10-25-fix-auto-map-renderer-file-size/evidence/AutoMapRenderer-head.kt`,
2 307 lines, the file its own change brought under the bound. The row therefore measures a copy nobody compiles
and files one condition per path against it; the condition cannot clear, because the copy is frozen at 2 307
lines. That is one mechanism: *the measurement's file list has no pathscope, so evidence copies are candidates*.

## The single fix

One line of the measurement, plus the header sentence the file uses to declare its own scope:

```bash
sizes=$(git ls-files -z '*.kt' '*.java' ':(exclude)openspec/changes/' 2>/dev/null | xargs -0 -r -n 100 awk '…')
```

`git ls-files` applies the exclude pathspec to the same listing, so nothing else about the row moves: the bound,
the comparison, the consecutive count, the fingerprint field and the registry row are untouched, and the
threshold still arrives in `SENSORS_THRESHOLD`.

The clearing half needs one branch, and it is checked on the real tree (`evidence/sensor-condition-clears.log`):
the loop that reports a recorded open condition computes the path's size from the ranked list, so a path the
exclusion removed is absent from it — and an absent path has no line count, so the loop reports
`"measurement":"outside-scope"` and `"verdict":"clear"` under the condition's own identity instead of the `0` it
would otherwise carry. That number is the one the item closes on: without the branch the clear would state a count
nobody took, which is the defect the row exists to report, restated in its own clearing half (issue #670).

## Why these alternatives were eliminated

- **Rename the archived snapshot (`AutoMapRenderer-head.kt` → `AutoMapRenderer-head.kt.txt`).** It clears one
  path and leaves the mechanism: the next change that splits a file and keeps the head as evidence refiles the
  same item about its own evidence. It also edits an archived change's evidence, the record a reviewer reads to
  compare the two states — the archive is what the change is judged by (`guidelines/Backlog.md` §6, the item
  becomes work and archives with its evidence). Measured today: the tree carries exactly one Kotlin file under
  `openspec/changes/` (`git ls-files 'openspec/changes/**/*.kt'` → one path, the snapshot), so a rename would
  make the row green today and say nothing about the next change.
- **Exclude only `**/evidence/**` instead of the change tree.** It excludes strictly less for no measured
  benefit: the change tree's prose (`proposal.md`, `design.md`, `tasks.md`, `specs/**`) is not Kotlin or Java, and
  an open change's directory is gitignored (`.gitignore:75 openspec/changes/*/`), so a scratch source file there
  is untracked and never reaches `git ls-files` anyway. The tree-wide exclusion is the honest statement of the
  scope the header already claims — the tree a build compiles.
- **Retune the bound.** The bound is the owner's recorded decision (1 500 lines, file level, C++ out) and the
  live file is under it; raising it would stop the row from reporting the next real oversized file.

## Blast radius

- One metric script's file list; no other row reads it (`hotspot-report.sh` walks its own history, not this
  list), and no registry field moves.
- `tools/sensor-cases.sh`: the case list, the dispatch table and the new function. The fixture repository is
  built inside the case (`scratch`), so the shared fixtures under `tools/sensors/fixtures/` are unchanged.
- `tools/sensor-mutations.sh`: one entry (`8.28`), the falsification of the new case.
- `guidelines/Backlog.md` §9: one clause in the paragraph that states this row's unit and scope.
- `openspec/specs/backlog-sensors/spec.md`: one sentence and one scenario on the requirement that owns the row.
- Not touched: any archived change (its evidence stays as the reviewer's record), the registry, the workflow.

## Verification (the one gate run)

`./tools/loop/gate.sh` ran once and ended red on four cases, all in `tools/loop-selftest.sh` (its own tally:
`tests="240" failures="4"`, `evidence/gate-tallies/tools_loop-selftest.sh_--from-git_4a4b….xml`); the change's
own suites were green — `tools/sensor-selftest.sh` reported "all 62 cases passed"
(`evidence/gate-tallies/tools_sensor-selftest.sh.xml`), `sensors#sourceSizeSkipsTheChangeTree` included. The
four were attributed rather than blamed (`guidelines/Build.md` §4d): each passes alone on the same tree
(`evidence/gate-red-attribution.log`, `PASS`, exit 0, 2026-10-26T22:01:39Z–22:02:09Z), and none names a path this
delta touches. Both numbers are quoted in that file; the residual is a person's to file, because this worker does
not write the tracker. The gate's own aggregate line is `evidence/gate-summary.txt`.
The first gate attempt left no log: the worker harness's own call timeout ended it inside step 1 (no
`gate: … — Ns` line, no summary, no tally), which is why there is a second run and not a suite that failed.

## Rollback

Revert the exclusion expression to `git ls-files -z '*.kt' '*.java'` and drop the case, its list entry, its
dispatch arm and mutation `8.28`. The tree returns to the state #670 was filed from — the snapshot is a violation
again — and nothing in the archive, the registry or a fixture needs restoring.
