# fix-resolver-line-names-the-ambiguity

## Why

**Root cause, with evidence.** The Sensors run of 2026-10-22T13:09Z printed

```
resolve-stale: read=110 due=0 repaired=0 parked=0 ambiguous=5 skipped=110 waiting=0 writes=0 dry_run=no
```

and then five item lines each reading `repaired` — #239, #122, #120, #93 and #12, every one
`suffix=1 absent=0 ambiguous=1` with no repair listed. The summary says nothing was repaired; the lines say
five items were.

`tools/issues/resolve-stale.sh`'s per-item label chain is `if .reopened … elif .stillParked …
elif .unresolved … else "repaired" end`. A token that classifies as `suffix` and resolves to more than one
tracked path is handled in the **suffix** loop, which sets `ambiguous` only and `continue`s — it never sets
`unresolved`. So such an item reaches the chain with `.repaired` false, `.unresolved` false and `.ambiguous`
positive, and falls through to `repaired`. The filter that selects the line already carries the data that
tells the three apart; only the label is wrong (issue #258, `class/bug`, priority band p3).

**The single fix.** Name the ambiguity: `elif .repaired then "repaired" elif (.ambiguous > 0) then
"AMBIGUOUS" else "skipped" end`. Nothing else changes: the counters, the repair verification and the write
paths are all correct, which is what the issue states as its bound — *a line that names a write must not
contradict the summary that counts the writes*.

## What changes

- `tools/issues/resolve-stale.sh`: the plain-text per-item line's label chain.
- `tools/resolve-stale-cases.sh`: `resolver#ambiguityLineNamesTheAmbiguity`.
- `tools/resolve-stale-mutations.json`: mutation `5.16`.

## Spec

**No delta.** `stale-citation-resolution` already requires an ambiguous match to be *reported, not repaired*,
and that contract is met — the ambiguity is counted per token and reported with its candidates. The line's
label is presentation, which the spec deliberately leaves to the implementation (*"Specs define the contract,
not the implementation"*).

## Cases and evidence

`resolver#ambiguityLineNamesTheAmbiguity` — two files end with `Dup.kt`, so the citation is ambiguous: the
item line must read `AMBIGUOUS` and must not read `repaired`, beside the summary's `repaired=0`. Mutation 5.16
replaces `"AMBIGUOUS"` with `"repaired"` and the case goes red, then green on the restore.
`tools/resolve-stale-selftest.sh` 34/34.

## Impact

`tools/issues/resolve-stale.sh`, `tools/resolve-stale-cases.sh`, `tools/resolve-stale-mutations.json`. No
compiled file, no Gradle build. Rollback: revert the commit; the counters were never wrong.
