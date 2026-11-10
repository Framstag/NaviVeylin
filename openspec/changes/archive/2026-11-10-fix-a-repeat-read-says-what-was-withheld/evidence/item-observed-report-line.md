# The item's observed report line (issue #831)

The report line the item filed against, quoted from the item's own text — `.pi/issue-loop/items/831/work-package.md`,
the `<<<ISSUE_TEXT` block:

> report.sh's trend block prints `172 of 449 file reads were a second read of a path the session already had (38 %) ·
> 939378 chars re-injected`

Those figures are the item's, from the run it observed; this change measures no run of its own and claims no figure of
its own. What this change adds beside that raw volume is the second half — the characters the repeat rule withheld —
so the line can say which of the two a reader is looking at. The figures the change's own cases assert are the ones its
cases measure: `metrics.withheld.reads` `2` and `metrics.withheld.chars` `30` for the item that repeated a path twice,
`30` for the one that repeated it once, and `null` for the item whose directory holds no repeat record
(`report#anItemRecordsItsRepeatReads`, and `repeat#aRepeatIsRecordedAsWhatItWithheld` for the record line itself).
