# Review round 2 — the blocking finding, and the fix

Round 1 of this item was cut (`.pi/issue-loop/items/898/reviewer-report-1.md` holds `LOOP-TIMEOUT` only;
`evidence/review-round-1-cut.md` records it), so this is the first round that graded the change. The reviewer
was independent and read-only: it read `review-after-merge.diff` (`main...HEAD`, `**/evidence/**` excluded),
this evidence tree, the item's package, and the archived change, and it ran no build, no suite and no gate.

## The finding it raised

```
BLOCKING: (3)+(2) the gate tree is not the tree that would land; decision A's step 2 was run against a stale main.
```

The branch merged `main` at `63f05d68` (`d94ca365`, 2026-11-15 10:23:38), but main's classification of both
settings ids landed after that (`2c6e0627`, "merge: the two settings capabilities are classified before their
change ships (#922)", 10:28:34): git reported a content conflict in `tools/feature-list/specs.json`, with main
holding `"settings-taxonomy": { "area": "settings-legal", "userVisible": false, "surfaces": ["phone", "car"] }`
and the branch holding `userVisible: true`. The `tests=181` green therefore measured a tree that could not merge,
which is the state `openspec/specs/agent-issue-loop/spec.md` refuses ("A stale base stops the next item … both
commits are named").

## The fix — both halves of decision A, in order

1. `main` was merged again, twice, as it advanced: `df23350a` (main `543c6eb0`) and `e2f38592` (main `c4982d8b`).
   The one conflict, `tools/feature-list/specs.json`, is resolved to main's value: `settings-taxonomy` keeps
   `userVisible: false` — its requirement states a placement and labelling rule, not a user-facing capability —
   and `settings-surface` keeps `userVisible: true`. `#922` added both rows to main itself, so the branch now
   carries main's rows rather than a second copy of them.
2. The gate was re-run on the merged, committed tree — not on a dirty one. Its console named the run and the
   tree it measured; that line is not in the captured log, so it is quoted from the console here and reproduced
   in full with its reproduction command in `evidence/gate-console-identity.md`:

```
LOOP-GATE-TREE: head=e2f38592f42fbc0cf2bbeca2e6eaecff1aab653f diff=9c6cfd80b7df6af2
LOOP-GATE-TALLY: app/build/test-results/loop-gate run=2447687
LOOP-GATE: tests=46 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 622s, …)
```

`head=` is this tree's own commit, so the gated tree is the reviewed tree. The step set is the profile's derived
set for this delta — `check-rule-routes.sh` is not among the owed steps at this base, so no base-red subtraction
was claimed (`LOOP-GATE-BASERED` appears nowhere in the log). The base-red measurement of that step on
`c4982d8b` (8 rules reached by no glob, naming no case) is recorded in
`.pi/issue-loop/items/898/base-red-state.json` for a run that owes it.

## The notes it raised, and what each cost

- **Stale per-case reports beside a fresh gate log.** Correct, and repaired: `evidence/` now carries this run's
  `TEST-*.xml` for both settings cases (mobile `12:19:24.671Z`/`12:18:06.578Z`, automotive twins
  `12:13:35.283Z`/`12:12:36.121Z`), the feature-list classification report of this run (`unclassified: 0`,
  `stale: 4`, `delta spec ids: 0`) and the three architecture reports of this run (fresh `12:09:17.698Z`). The
  architecture figures `rows=33 files=853 exemptions=46 rules=10` are the assertions of
  `architecture/src/test/kotlin/com/naviveylin/architecture/StructureReportTest.kt:33-47`, not a message inside
  those reports — a green report carries none. One report stays the earlier run's, because this gate did not owe
  that step: `tools_tests_gen-feature-list-selftest.sh-run584422.xml` now carries its run number in its name.
- **Scenario coverage `UNPROVEN`, not `PASS`, for the group-1 scenarios.** Accepted and not argued: the shipped
  `settings-taxonomy` and `settings-surface` scenarios that need a renderer carry `**Case**: none` in the spec
  text itself, and the three cases this change ships assert the registry, the emission and the car row set —
  never the displayed root. The scenario text is unchanged by this round.
- **`UNPROVEN: item title "Settings writes have one owner"`** — the title names the whole change; the item's
  target is group 1, and 14 main sources still reference `SettingsStorage` under unchecked tasks 3.1-6.x. Left as
  the item's own framing; the closure comment names the scope landed and the groups left.
- **`UNPROVEN: design.md D2 "collects the flow instead of holding a second copy"`** — true of the tree: the
  view-model copies each toggle into `_uiState` and launches the controller. D2 is satisfied only when the
  surface reads the controller's flow, which is group 2's work; not claimed here.
- **The baseline header says the file only shrinks, while this change grows it by two rows.** The two rows are
  `mirror` exemptions for `com.naviveylin.ui.settings` and `…ui.settings.specs`, and both rows were *new* rows
  whose packages violate the mirror rule — the same shape `#870`'s seven day-one rows took. The header's day-one
  paragraph names this pair, so the exception is stated where the entries are.
- **The reading set the delta derives is 357 route entries and no guideline file changed.** Task 2.6 (`UIPhoneMap.md`
  §7/§8 and `UIPhone.md`) is unchecked; the guideline rule is owed with the surface, not skipped.

`VERDICT: FAIL` from round 2 stands on the record; this round's blocking finding is the one fixed above, and the
fix is the merge plus the re-run on the merged tree, both quoted.