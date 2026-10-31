# Backlog — procedure and recipes

This is the procedure layer of `guidelines/BacklogTooling.md`: the runs, commands and transcripts behind its
§9 and §10, read against the numbered section the base document carries. An anchor here names a section number
the base document carries; the base document states the rules, this companion the procedure that puts them into
practice. The backlog's own procedures are `guidelines/Backlog.recipes.md`.

## §9 The outcomes a run of a check writes, and the declared rows

```
outcome        present  -> file, or reopen the item this condition already has, or annotate it
               cleared  -> comment the measurement, close once the consecutive count is met;
                           with no open item carrying the identity, record it on the row's own
                           record issue, so a green check reaches the tracker instead of reading
                           as one that never ran (issue #257)
               failed   -> write NOTHING, fail the run loudly            (I6)
```

A row that measures a build reads a file, so **the pipeline that made the build publishes it**: `build.yml`
writes the gate's wall time, the per-module coverage summary, the build result and the package as one
artifact (`tools/build-inputs.sh`, the same script that refuses a registry naming an input nothing
publishes), and the checks' pipeline restores that record for the commit it examines before it selects a row
(`tools/fetch-build-inputs.sh`, exit 3 = the build has not finished, so a pending build reads as a skipped
run rather than four failed checks). The record is fetched with the workflow's own read-only token, with the
conclusion the build's run ended with reported beside it; every write to the tracker is still the
repository-scoped one. A red build cannot produce a coverage report — Kover's report task is skipped once its
tests fail — nor, if it fails earlier, the package or the timing record, so that record is partial by
construction. The rows that read the build's own outputs are therefore attempted only for a run that
concluded `success`; the rest run for any record, so **"the build is red" is exactly one row's verdict** —
`broken-main`, which reads the result record the publisher always writes — and no row reports a failure a
person would have to read as "the build was red, the check was fine".

One row exists to watch the mechanism rather than the project: `stale-citations` reports the **count of
citations nothing accounts for**, measured by the resolver's own dry run — the items it reports unresolved plus
the ones a park before #281 left holding one — so a residue that grows — a repair that stopped reaching
something, a scheduled run that stopped happening — becomes a check's verdict instead of a surprise
(`stale-citation-resolution`, D6). The repair itself is a pipeline step, not a row: a row files,
comments on or closes *the issue it owns*, and a citation repair edits other items.

One row re-reads the tracker's own **per-item invariants**, because nothing scheduled did: `metadata-invariant`
runs `tools/issues/check-metadata.sh --live --json` — the command that owns the per-item half, so the row and a
person's hand run cannot disagree about what a drift is — and files one item per drifted item and per
disagreement, under that item's number and the rule it breaks. An item whose band label and stored marker
drifted apart therefore reaches the tracker as a verdict within a day, whoever wrote the edit (the pass,
a skill, a hand edit on the web UI), instead of waiting for somebody to remember the live mode; and so does an
item whose body keeps a settled state of its own (`roles.settledRecordMarker`) while its `status/*` label is not
one of `roles.settledStatuses` — the drift a citation park left behind, which made a tombstone read as work
(#683). It reports and
repairs nothing: which half of a drifted pair is the wrong one is a judgement, so dropping a stale label or
rewriting both halves together stays a person's edit. The row is the answer the owner recorded on #292
(2026-10-25, option 1 of three); the prevention it was weighed against — the refusal of a band label in
`update.sh` — landed later as the other half of the same root cause (issue #671): a call that names a
`prio/<band>` label carries the `--body` that leaves the pair intact — the band it adds is the band the
marker records, and a call that removes a band and adds none carries a body recording none — or is refused
with `band-label-unpaired`/`band-label-disagrees` (`tools/issues/lib.sh`, "the band pair"), and `create.sh`
writes no band at all, because the body it files carries no marker for one to agree with. A `--body` that
names no band label stays ungated, since seeing that its marker disagrees with the item's stored label needs a
read of the item; that write, and a hand edit on the web UI, are why this row still has work.

One row watches a source unit's **size**, because nothing else does: `hotspot` scores size × change count, so
a file nobody touches scores nothing however large it is — and because the score is a product, its bound sits
**above the largest file the row watches** (1 000 000 against 535 814 bytes measured 2026-10-26), so crossing it
takes at least a second change and *existing is not heat*: at the old 5 000 bound 358 paths of this tree were past
it, and one tick filed 337 items at one every six seconds (issue #665). The bound is the row's, so refitting it is
one edit in `sensors.json`, and a tree that grows a larger file than the bound reddens the suite that asserts it.
`source-size` measures **lines per tracked file**
over `*.kt` and `*.java`, tests included, the native sources out and the change tree out — `openspec/changes/**`
holds a change's prose and the frozen copies it keeps as evidence, which no build compiles, so a copy a change
kept of the file it brought under the bound is not a condition (issue #670) — and files one item per path over
the bound: the path is the identity, so an item closes when that file drops under it. A recorded condition whose
path the tree no longer measures clears on its own identity and carries no line count rather than a number
nobody took. The number, the unit and the scope are the owner's recorded decision (2026-10-21: 1 500 lines, file
level, C++ out); the bound is the row's, so retuning it is one edit in `sensors.json`. It bounds a *file* and not a class: a file carrying
several classes is one condition here, and separating them needs a counter this tooling does not have.

Two rows bound **what a session must read before it works**, because nothing did (issue #197):
`routed-section-size` measures every section the routing table names — the pair (document, section) is read
out of `AGENTS.md`'s own Documentation Map, so a section the table stops routing stops being a condition
without an edit to the row — and `entry-document-size` measures the entry document itself, which every session
is handed before it reads anything else. A section's cost is the byte count of its lines from its `## ` heading
through the line before the next one; the line count is reported beside it and a bound is read in lines too, at
80 bytes/line (12 000 bytes ≈ 150 lines), because a document's size is thought of in lines. A section, or a
document, the table does not route is not part of the start cost and is never a condition. The identity is the
document and the section (and the path, for the entry document), so one over-long section is one item that
closes when that section shrinks; a recorded condition whose pair the table no longer routes clears on its own
identity and carries no byte count. The unit, the two bounds (12 000 bytes per routed section, 32 000 bytes for
the entry document) and the exclusion are the owner's recorded decision (2026-10-27), and each bound is its own
row's, so retuning one is one edit in `sensors.json`. Measured on the tree this landed on, the bound convicts
eight sections — `UI.md` §1 (25 206 bytes) and `UIPhone.md` §8 (19 681), `MapRendering.md` §1 (22 592), `UIPhone.md` §7a (13 102),
this §9 (15 389 bytes), `Design.md` §5 (12 781) and §8 (12 058), and `Build.md`
§7 (12 052) — which is exactly the silent erosion the row exists to make visible.

The routing table has one condition of its own, and it is not a row because it files nothing:
`tools/check-doc-routes.sh` reports a row that names a lettered sub-section of a document and states no cost, and
`tools/check-doc-routes.sh --sizes` prints the cost of every section of every document the table names, routed or
not, so the figure a row restates has a measurement it can be compared with. That measurement is a comparison and
not a presence (issue #712): a figure written after a lettered reference must equal that section's cost in kB of
bytes/1000 rounded to one decimal, a range must state the cheapest and the costliest of the lettered sections the
row names, and a figure a row writes as a past measurement (`against 23.2 kB for §2 before the split`) is not
compared at all — a disagreement is reported with the row, the reference and both numbers. The bound a cost is
held to is still the row's, never this check's.

One row watches **what the loop's own children cost**, because nothing did: `loop-spend` runs
`tools/loop-spend.py --quiet` over `.pi/issue-loop/items` and measures the **duplicate-read share** of the reads
it counts — the share of a session's file reads that repeat a path it already had, 2 847 of 6 021 (47 %) over the
archive on 2026-10-28 — and files when it stays past 60 % for two consecutive days. It is a *ratio* and never a
total: items/day fell 32 → 5 and returned to 31 while per-item cost went 10.65 → 18.96 → 10.24 Mtok on the same
days, so a total fires on a quiet week and stays silent through a regression, while the share of counted reads
cannot move with how many items a day carried. The bound sits six points above the measured ceiling (41 → 48 → 54 %
per day) so it fires when the archive passes its own worst day rather than at it. The archive is machine-local and
gitignored, so the pipeline has none: an absence is a **`clear` under a second identity** (`kind=stream-absent`,
with `no-archive`, `no-items` or `no-reads` as the measurement) and therefore lands on the row's record issue and
nowhere else — it can neither file a false condition nor clear a real one — while a stream that exists and cannot be
read is still an `error`, so a tool whose output shape changed fails loudly instead of reporting a calm zero. What
it cannot do is decide whether a share that rose is a regression or a week of harness work; that is the reader's
judgement, on the comment the row leaves.


## §10 Portability — the engine and the project

```
engine, copied unchanged            the project, adapted
  the gate, the taxonomy reader       tools/tooling.json      repo · allowlist · reserved labels ·
  identity and dedup                    · vocabulary values · the engines' roles ·
  the verbs, the closure rules          · bounds · band edges
  the loop's phases and refusals      sensors.json            commands · cadence · bounds · counts
  the metadata pass                   loop.conf               the agent command · the skills the
  the checks' runner and guards         · worker uses · the gate command ·
                                        · the submodule a worktree checks out ·
                                        · the branch every item descends from
                                      the source adapter      the one-time record format of THIS project
```

