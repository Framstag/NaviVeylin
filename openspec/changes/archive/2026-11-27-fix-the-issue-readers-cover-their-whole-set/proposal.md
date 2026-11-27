# Fix: the issue-side readers cover the whole set they decide on

## Why

Three readers took the tracker through a fixed window and then decided on it as if it were the set (#1096):

- `tools/issues/duplicates.sh:35` defaulted `limit=400` and `:76` read `gh issue list --state all --limit
  "$limit"`, so a duplicate pair whose older item stood below #681 (the tracker holds 739 issues) was neither
  found, nor closed, nor commented — while the sweep reported a clean tracker, and the dedup guard it backs
  (`tools/issues/create.sh:383`) was blind in the same direction.
- `tools/issues/check-metadata.sh:150` read `--limit 400` in `--live` mode, so the metadata check answered
  "consistent" over 400 of 739, and a container whose children are older than the window read as an empty
  container — the #911 shape the check exists to catch.
- `tools/issues/resolve-stale.sh:116` read `--limit 400` of the open issues, so a citation broken in an older
  item was never named, never repaired and never reported, while `stale-citation-resolution` is stated over the
  backlog.

The whole-set reader those three needed landed first (`issues_set_count`, `issues_issue_list`,
`issues_page_records`; #1093). These are its remaining call sites.

Red, on the base commit — each case names one reader, and each fails there because the window is the defect:

```
$ bash tools/issues/tests/check-metadata-selftest.sh --case 'check#theLiveReadIsTheWholeSet'
  FAIL the whole set is read, so the old child is a violation: expected [1] got [0]
  FAIL the count is the tracker's own set: [405 issue(s) checked] not found in:
       check-metadata: 400 issue(s) checked · 0 violation(s) · 0 untyped
  FAIL duplicates#aDuplicateOutsideTheNewestPageIsFound     (the pair below the window is not found: survivor null)
FAIL resolver#anOldItemsCitationIsStillNominated            (.read expected 405, got 400)
```

The same three runs, taken again with the pre-fix window put back into one reader at a time and the file restored
byte for byte (`evidence/red-when-mutated.log`, `evidence/green-on-change.log`), are the falsification: each
mutation reddens exactly the case that names that reader, and the restored tree is green.

## What changes

One rule, one implementation, three call sites plus the caller that asserted the window:

- `tools/issues/lib.sh` — `issues_set_count [open|all]` counts a scope; `issues_set_window` returns that count
  plus one, the window a whole-set read asks for; `issues_set_covered` is the refusal a read owes when it comes
  back as full as the window it was asked for. `issues_page_records` refuses through the same function, so the
  pass and these three readers cannot state coverage in two voices.
- `tools/issues/duplicates.sh` — the read is the tracker's own set unless `--limit` narrows it, and a narrowed
  read that fills its window is refused rather than reported as coverage. `--max-items` stays the write bound.
- `tools/issues/check-metadata.sh --live` — the read is the counted set plus one, refused when full.
- `tools/issues/resolve-stale.sh` — the read is the *open* set plus one, refused when full.
- `.github/workflows/sensors.yml:186` — the scheduled sweep passes `--limit 400`, which is the same window
  asserted by its caller; it now reads the set.
- `guidelines/BacklogTooling.md` §9 — the sentence that stated `--limit` as the reader's bound now states it as a
  narrowing, and the whole-set read as the default.

## Why no second mechanism

The refusal path already exists (`issues_refuse "issue-set-uncovered"`, #1093) and the count read already exists.
Nothing new is asked of the tracker: the three readers ask the same count question the pass asks and then read the
set it reported.

LOOP-ROUTE: guidelines/BacklogTooling.md §9
