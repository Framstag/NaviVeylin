# Proposal — the report names what the repeat rule withheld, not the raw volume it never handed over

## Why

**Root cause.** The repeat rule replaces a read whose content sha matches the earlier one with a single line
(`tools/pi-trim-result.mjs:124`, `repeatResult` → `alreadyReadNotice`), and on the base commit it wrote nothing to the record it is given: `trimResult`
appended its `LOOP-TOOL-RESULT-TRIM` line (`tools/pi-trim-result.mjs:61`) and the repeat arm returned the notice with
no record at all (`git show c0ddb97c:tools/pi-trim-result.mjs` — `return alreadyReadNotice(read.path, earlier.size,
    earlier.turn);`). So the item's `tool-result-trims.log` held bound cuts only, `tools/loop/state.sh`'s dup fold
summed the **event stream's** raw result sizes into `metrics.dupChars`, and `tools/loop/report.sh` printed that sum as
`chars re-injected` — the volume the rule had already kept out of the context, reported as material the child paid
for twice.

**Evidence, verified against the records (issue #831).** `.pi/issue-loop/run.json` carries the figures the item quotes:
449 file reads, 172 repeats, 939 378 characters; `.pi/issue-loop/items/808/tool-result-trims.log` holds 82
`LOOP-TOOL-RESULT-TRIM` lines and 4 180 such lines exist over the item directories, while the base commit's
`repeatResult` writes no record line at all (the red run below reads exactly that absence), and
`tools/look.sh 'tool-result-trims.log' --files` lists `tools/pi-child-events.sh` — the path the adapter names — with
no line under `tools/loop/report.sh`. The volume the rule withheld is therefore invisible to every reader, and the
report cannot tell a repeat the rule replaced from one whose sha differed and was handed over whole.

**The red test.** `report#theRepeatRuleNamesWhatItWithheld`, added to `tools/loop/tests/loop-cases.sh` and registered in
its case list and dispatcher. Run against the base commit's three production files (`items/831/red-on-HEAD-probe.sh`
— a hardlink copy of the change root, its `tools/pi-trim-result.mjs`, `tools/loop/state.sh` and
`tools/loop/report.sh` replaced by `git show c0ddb97c:…`, since `git checkout` and `git stash` are refused to an
unattended child) it reports:

```
FAIL report#theRepeatRuleNamesWhatItWithheld
  and the record names the tool, the path, the characters the rule withheld and the line that entered instead:
  'LOOP-TOOL-RESULT-REPEAT: ctx_read path=… chars=40 kept=' not found in:
```

`items/831/red-on-HEAD.xml` — `tests=1 failures=1` (`<property name="run" value="2026-11-10T01:08:29Z"/>`),
`items/831/red-on-HEAD.log` the same run's output, exit 1. The case is green on the tree this change leaves:
`bash tools/loop/tests/loop-selftest.sh --case 'report#theRepeatRuleNamesWhatItWithheld'` → `PASS`.

**The requirement the item contradicts.** `openspec/specs/agent-issue-loop/spec.md:3951`, "A run counts the reads its
sessions repeated", whose text said the record carries "the characters those repeats re-injected" and whose scenario
printed "the characters re-injected and the item that repeated most". A repeat the rule replaced has no re-injection:
the figure the requirement names is the one the rule withheld, and the two are equal only where the rule did not run.
The requirement gains the pair (`metrics.repWithheld`, `metrics.repKept`), the source they are read from, and the
prohibition on calling the raw volume a re-injection.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md:172`–`:190`, section 7a "Running one item: the
script, the child and the package", states the repeat figure, where the state reads it and what the report prints;
section 7e "Leftovers, the report and the queue" names the same figure in the trend (`:1226`–`:1228`). Both were
updated; no other guideline states it — `tools/look.sh 'repWithheld' --files` reports the rule in
`guidelines/Backlog.recipes.md` alone (9 matches in 5 files: this file, `tools/loop/report.sh` 2, `tools/loop/state.sh`
4, `tools/loop/tests/loop-cases.sh` 1).

**The surface.** `surface: 1 case added, 1 case's trend assertion rewritten, 1 mutation record rewritten and 1 added,
0 fixtures realigned — tools/look.sh 'repWithheld' --files` → `9 matches in 5 files`. The three engine files
(`tools/pi-trim-result.mjs`, `tools/loop/state.sh`, `tools/loop/report.sh`), their case file and their mutation
harness; no fixture and no compiled file.

**What this change does not do.** It does not change the repeat rule itself: what is withheld, when the sha differs,
and what the notice says are all #685's. It does not read the trim record from `report.sh` either — the state is the
file every reader already opens, so the figures are recorded there once (`tools/loop/state.sh:931`–`:940`,
`:774`, `:1087`) and the report prints them, the rule the count and its two companions of #814 already keep.
