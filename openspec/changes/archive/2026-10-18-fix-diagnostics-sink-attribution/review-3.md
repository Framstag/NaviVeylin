# Independent review 3 (fresh read-only reviewer)

Scope: the 9 scenarios, the diff (5 test files + `guidelines/Design.md` §11), `evidence/**`, `report.md`,
`review-2.md`, `tasks.md`. Verdict as returned:

**REVIEW-VERDICT: FAIL**

All nine scenarios PASS, and review 2's MAJOR (the shared `ROUTE` tag as scenario 8's filter) was confirmed
closed: `val calcDone = "${NavigationEngine.ROUTE_TAG} calc done:"` filters both the entry selection and the
file assertion, and the only other `ROUTE` producers (`RouteGeometry`, `RoutePanelViewModel`) do not emit
`calc done:`.

## Findings and disposition

| # | severity | finding | disposition |
|---|---|---|---|
| 1 | P0 | `report.md` recorded "Review 3 — PASS" before this review ran, and no `review-3.md` existed — the same defect that blocked review 2 | fixed: this file is the record and the report states the FAIL; the rule "a verdict is recorded only after its review file exists" is written into the report |
| 2 | P1 | the residual list claimed exhaustiveness and missed `CarHostGuardsTest.kt:69` (whole-sink negative) and `NavigationEngineRerouteTest.kt:486` (`single { }` on the shared `REROUTE` tag) | fixed: both named in `report.md`; no code change owed — both are outside this diff |
| 3 | P2 | arithmetic: "seven total `entries.size` counts" (there are six: `EngineFaultHandlerTest` 3, `NotificationHostSendLoggingTest` 2, `SessionCarSurfaceHostTest` 1); `proposal.md` said "four more assertions" and listed six | fixed: six in both |
| 4 | P2 | `design.md` still said "revert the four test files" and "the same four classes forced, `:app` 12 tests" after task 2.7 added a fifth file and a ninth assertion | fixed: five files, `:app` 18 tests / `:auto` 27 = 45 |
| 5 | P2 | scenario 1's THEN ignored the count, which also fails on a duplicated or missing own line | fixed in the spec delta |
