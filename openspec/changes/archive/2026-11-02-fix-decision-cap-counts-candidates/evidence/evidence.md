# Evidence — fix-decision-cap-counts-candidates

Item: #745 (`The decision cap counts decisions on items that are not candidates, so parking a roadmap item by
removing `agent/eligible` stops every run`). Base: `71f7eafb`.

| what | where |
|---|---|
| the case fails on the base tree | `red-on-HEAD.log` — `FAIL run#decisionCapCountsOnlyCandidates`, with the base's line `refusing to start: 3 item(s) already await a decision and the cap is 2` and the parked `#45` printed |
| the case passes with the change | `green.log` — the new case and the five neighbours that quote the refusal or read the `decisions` fixture |
| the invariant is falsified once, then restored | `revert-check.log` — `--eligible-only` removed → `FAIL`, restored → `PASS` |
| the structural checks | `checks.log` — `openspec validate --strict`, `check-change-readiness.sh` (`LOOP-CHANGE-READY: ok`), `check-openspec-hygiene.sh`, `check-doc-routes.sh`, `check-rule-routes.sh`, and the suite's own `--group driver` (18 cases, including `driver#everyCaseDeclaresTheFilesItCanReach`) |

The measurement the item states, re-taken on 2026-11-02 before the change:

```
$ ./tools/issues/list.sh --json --limit 400 | jq '[.[] | select([.labels[].name]|index("agent/needs-decision"))] | length'
13
$ ./tools/issues/list.sh --json --limit 400 --eligible-only | jq '[.[] | select([.labels[].name]|index("agent/needs-decision"))] | length'
9
```

The cap recorded at the time was 10, so the run refused with nine questions and one free slot. Mutation record 9.6
(`tools/loop-mutations.sh`) still applies unchanged: it anchors on the comparison line, which this change keeps
verbatim (`mutation#everyDeclaredRecordApplies`, `mutation#aStaleRecordIsNamed` both green).

No compiled file is touched, so no build and no Gradle suite is owed (`guidelines/Build.md` §2, scope block): the
gate for this delta is `tools/loop-selftest.sh`, whose full set is owed before the change is archived for a delta
that owns `tools/loop/**` — run here as the selected cases plus the whole `driver` group, and the selection is
named in `checks.log`.
