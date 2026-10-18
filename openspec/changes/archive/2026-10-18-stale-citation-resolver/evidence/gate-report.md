# Gate evidence — `stale-citation-resolution` (tasks 6.1–6.4)

## 6.1 The full self-test

```
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-18T09:44:00Z
$ bash tools/resolve-stale-selftest.sh
PASS classifier#fragmentIsNotACitation
PASS classifier#bareHeaderIsNotAPath
PASS classifier#externalLibraryPathIsNotAPath
PASS classifier#bareSourceNameResolvesBySuffix
PASS classifier#submodulePathResolves
PASS classifier#generatedArtefactIsNotMissing
PASS classifier#absentIsTheOnlyStale
PASS prescreen#refusalIsVisibleInTheTracker
PASS resolver#suffixedCitationIsCanonicalised
PASS resolver#renamedFileIsFollowed
PASS resolver#removedSymbolIsReAnchored
PASS resolver#markersSurviveTheRepair
PASS resolver#repairPostsOneComment
PASS resolver#unrepairableCitationIsParked
PASS resolver#ambiguityIsReportedNotRepaired
PASS resolver#neverWritesTheAuthorisation
PASS resolver#boundHoldsAndReportsTheRest
PASS resolver#secondRunChangesNothing
PASS pipeline#scheduledRunRepairs
PASS pipeline#residueIsADeclaredRow
resolve-stale-selftest: all 20 cases passed (JDK 27, offline, no token, no Gradle)
$ echo $?
0
```

20 of 20, exit 0. The run needs `bash`, `git`, `jq`, `grep`, `sed`, `awk`, `sort` and a JDK ≥ 17; every case
builds its throwaway repository under the gitignored `.pi/tmp/` and drives the real classifier, the real
resolver, the real pre-screen and the stub `gh`.

## 6.2 The acceptance measurement — the 49 recorded verdicts, re-decided

The change opened with 49 `stale` verdicts from the pre-screen's crude extraction (#164). The resolver's dry
run over the whole open tracker, 2026-10-18:

```
whole tracker: read=165 due=60 repaired=49 parked=8 ambiguous=6 skipped=105

the 49 recorded verdicts, re-decided:
  25× every citation resolves now (suffix / submodule / generated / mention — no action)
  22× repaired (canonicalised, re-anchored by rename or symbol, or the archived change's directory)
   2× parked (the honest residue of that set)
```

Task 6.2 was written expecting `44 suffix + 4 not-a-path + ≤1 quarantine`. The measurement differs and is
better: while the ticket was open, the classifier gained the submodule index, the external-namespace rule, the
mention-only extensions and the archived-change repair, so verdicts that would have needed a repair are now
not staleness at all. The measured shape is recorded; the guess is kept in the task's text as the comparison it
turned out to be.

## 6.3 The queue measurement

Before (2026-10-18, the 72 authorised open, non-feature items, `bash tools/loop/run.sh --dry-run --items 72`):

```
queued=23 excluded=49 (all `stale`)
```

After:

```
queued=68 excluded=4 (all `stale`)
```

The four are the parked items of the set: each carries `status/on-hold` and a comment naming what the ladder
tried, so the refusal is a query rather than a line in `.pi/issue-loop/run.json`.

## 6.4 The exclusions, confirmed

- **No Gradle build and no Gradle suite was run, and none is required.** The change edits shell, JSON, markdown
  and two workflow steps; `guidelines/Build.md` §2's scope block applies and the named structural check is
  `tools/resolve-stale-selftest.sh` (20 cases) plus the mutation set (9 mutations).
- **No compiled file was changed.** `git diff --name-only` lists no file under `app/`, `auto/`, `core/`,
  `osmscout-client-java/` or `buildSrc/`.
- **The only tracker writer is bounded and named.** `resolve-stale.sh` runs in the sensors pipeline (pinned,
  least privilege, overlap-guarded, the only path allowed to write) with `--max-items 25`, and in the build
  pipeline it runs only as its own self-test, which writes to a stub `gh` and to throwaway repositories.
- **The classifier is one home.** `tools/loop/prescreen.sh` no longer extracts tokens itself: it calls
  `stale_citations_of`, which is `tools/issues/lib.sh`'s function — the same one the resolver asks. Nine
  mutations cover the rules, and each of the nine goes red.
- **Nothing else moved.** `guidelines/Backlog.md` §5 gained the resolver as a writer, §6/§7 name the shared
  rule, §9 names the residue row; `AGENTS.md` routes a stale citation; `.pi/skills/cleanup-todo` carries the
  recipe. `tools/check-doc-routes.sh` and its self-test exit 0.
