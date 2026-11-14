# The withheld volume is printed as a share of the repeat volume it sits beside

## Why

Issue **#866**. The trend's `reads:` line prints the repeat rule's own figure three clauses after the repeat volume it
is the withheld part of, and neither figure says whether the rule agreed with the reads it saw:

> `40 of 101 file reads were a second read of a path the session already had (40 %) · 236268 chars in the repeat
> results · 526 chars of them withheld by the repeat rule`

— the run of 2026-11-11T20:36:10Z, item #859, quoted from the item. A reader takes the second figure for waste; the
fourth is the only one that says the rule withheld 0.2 % of it, and the item's own evidence says why such a share can
be near zero without the rule being wrong: the six reads of `review-1.diff` in that item are `lines:1-200`, `96-300`,
`200-400`, `310-520`, `410-620`, `515-786` — one 786-line diff walked in windows because it cannot enter an 8 000-char
tool result any other way.

**Verified in the tree at the run's base `d25972c1`:**

- The clause prints the volume alone: `tools/loop/report.sh:461`–`:465` builds `$wline` as
  `"\($w | add) chars withheld by the repeat rule"` (`:464`) and the line joins it to the repeat characters with a
  `·` (`:465`), so the two are separate clauses of one sentence and no figure is called a share of the other.
- The comment withdraws the share today, deliberately: `tools/loop/report.sh:443` says the line "says the characters
  the rule withheld and does not call them a share of the repeat results", and `:440`–`:443` gives the reason — since
  #858 the ledger the rule measures a read against is the item's, so the withheld volume may exceed `dupChars`.
- A repeat is counted per path and per session, never per range: `tools/loop/state.sh:1220`–`:1223` reduces the reads
  with the key `(($x.s | tostring) + "|" + $x.p)`, so the six windows of one diff are five repeats of a path the
  session holds, though together they are that file read once and each carries material the session did not have. The
  state records no range at all, so nothing in the record can tell a window from a second read of the same text.
- Nothing states either fact: the one live sentence that refuses the share is `tools/loop/report.sh:443`
  (`tools/look.sh 'a share of the repeat'` → 1 match in `tools/loop/report.sh`, 1 in this change's own case, 3 in this
  change's own proposal), and `tools/look.sh 'in windows'` → no match in `tools/loop/**`, `guidelines/**` or
  `openspec/specs/**`, so no line names a windowed read as the count's own limit.

**Root cause, in one sentence:** the trend prints the repeat rule's withheld volume as a figure of its own, never as a
share of the repeat volume beside it, so the line cannot say how much of that volume the rule agreed with — and nothing
in the file says that the volume it is a share of counts a path read in windows as repeats, which is the reading that
makes a small share look like a broken rule rather than a file read once.

**The red case** — `report#anItemRecordsItsRepeatReads` (`tools/loop/tests/loop-cases.sh`), whose `reads:` assertion is
rewritten to expect the share, one `bash tools/loop/tests/loop-selftest.sh --case report#anItemRecordsItsRepeatReads`
run against the base's `report.sh` at 2026-11-14T04:19:42Z:

```
report#anItemRecordsItsRepeatReads|   the trend prints the run's repeats with the two counts they came from, the
characters in the repeat results, the characters the rule withheld as a share of those characters (…): '3 of 7 file
reads were a second read of a path the session already had (43 %) · 90 chars in the repeat results · 60 chars withheld
by the repeat rule (66.7 % of the repeat results) · worst #21 2 of 5' not found in:   reads:  3 of 7 file reads were a
second read of a path the session already had (43 %) · 90 chars in the repeat results · 60 chars withheld by the repeat
rule · worst #21 2 of 5
FAIL report#anItemRecordsItsRepeatReads
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-head.log` is that run and `evidence/red-on-head.xml` its tally (`tests=1 failures=1 errors=0`),
transcribed because the shell runner writes no JUnit XML of its own (the shape `fix-a-role-tally-records-its-model`'s
evidence uses).

**Owning requirement:** `agent-issue-loop`, "A run counts the reads its sessions repeated", whose scenario "The reads
a session repeated are recorded, printed and named" prints the withheld volume and not its share
(`openspec/specs/agent-issue-loop/spec.md:4079`–`:4083`).

**Owning guideline section:** `guidelines/Backlog.recipes.md` section 7e "Leftovers, the report and the queue" (`:1485`–`:1489`,
the list of figures the report prints), whose source section 7a "Running one item: the script, the child and the package"
states the same clause at `:183`–`:186` and the reason the share was refused at `:184`–`:185`. Those are the base's line
numbers: this change's own two edits push section 7e down four lines and lengthen section 7a's paragraph, so the sections are named by
their titles as well as their lines.

surface: 1 report clause and its comment rewritten, 1 case rewritten with 1 phase added, 1 mutation record added, 2 guideline
statements realigned, 1 requirement modified — `tools/look.sh 'chars withheld by the repeat rule'` → 5 matches in 5
files (3 in the archived change `2026-11-12-fix-a-repeat-ledger-spans-the-item`, frozen), `tools/look.sh 'withheld'` →
161 matches in 44 files whose live reach is `tools/loop/report.sh` 6, `tools/loop/tests/loop-cases.sh` 28,
`tools/loop/tests/loop-mutations.sh` 6 records, `guidelines/Backlog.recipes.md` 6 statements, and
`openspec/specs/agent-issue-loop/spec.md` 7. Both counts were taken before the plan was fixed, and the archive the
third line of the first count names is frozen history: `tools/look.sh 'chars withheld by the repeat rule'` counts it
and this change edits neither.
