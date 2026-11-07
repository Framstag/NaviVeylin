# Tasks — fix-route-calculation-database-not-loaded

## 1. Red case

- [x] 1.1 Write the two app cases: `RoutingFailureWordingTest.notYetLoadedRoutingDataIsNotUnusableRoutingData`
      (the kind words itself, asks for a retry, offers no action) and
      `RoutePanelViewModelFailureReasonTest.aCalculationThatOutrunsTheDatabaseOpenOffersNoAction` (the panel
      carries the cause). Run them with the mapping of the new kind removed — the pre-fix state of that line.
      Verify: `21 tests completed, 2 failed`, the two cases named, the first with
      `expected:<DATABASE_NOT_LOADED> but was:<UNKNOWN>` (`evidence/red.log`, 2026-10-29).
      *(spec: `routing-failure-reporting` — A database list that is still filling is reported as not loaded
      yet)*

## 2. The native half

- [x] 2.1 Land the module commit on `naviveylin-local`: `kRouteFailureDatabaseNotLoaded=7` and
      `RouteFailureCause::DatabaseNotLoaded` with its mapping, reported at both `databases.empty()` branches,
      `RouteCallback.KIND_DATABASE_NOT_LOADED = 7`, the module's spec delta and the new row in
      `Tests/src/RouteFailureKindTest.cpp`. Verify: the commit exists on the remote branch
      (`git ls-remote origin refs/heads/naviveylin-local` → `fab852ff9`, the change's own commit `f61ff8789`
      under it), and the mapping probe prints `tests=1 failures=0` (module change's `verification.md`).
      *(spec: `routing-failure-reporting` — A database list that is still filling is reported as not loaded
      yet)*
- [x] 2.2 Bump the pin in the superproject to that commit. Verify: `git diff --cached -- app/src/main/cpp/libosmscout`
      names the new subproject commit and `git -C app/src/main/cpp/libosmscout log -1` names the same one.
      *(spec: `routing-failure-reporting` — A database list that is still filling is reported as not loaded
      yet)*

## 3. The app half

- [x] 3.1 Add `FailureReason.DATABASE_NOT_LOADED`, map `RouteCallback.KIND_DATABASE_NOT_LOADED` to it, word it
      in `en` and `de` with a retry, and leave `hasMapManagerAction()` answering false for it.
      Verify: the three cases of task 1.1 and the car case are green (`evidence/green.log`: 13 + 8 + 5 cases,
      0 failures).
      *(spec: `routing-failure-reporting` — A database list that is still filling is reported as not loaded
      yet)*
- [x] 3.2 Check the parity rule the car case asserts: the same label on both surfaces, no phone referral for
      this reason. Verify: `RoutingFailureNoticeTest.theCarLabelEqualsThePhoneLabelForEveryReason` includes
      the new reason and passes.
      *(spec: `routing-failure-reporting` — No action is offered for it; `guidelines/UI.md` §1)*

## 4. Verification

- [x] 4.1 Run the gate over the changed pathspec (`./tools/loop/gate.sh`) and record its summary line in
      `evidence/gate.log` with the tallies under `evidence/gate-tallies/`.
      Verify: `LOOP-GATE: tests=… failures=0 errors=0`.
- [x] 4.2 Validate the change: `openspec validate fix-route-calculation-database-not-loaded --strict` exits 0.
      Verify: record its output in `evidence/openspec-validate.log`.
- [ ] 4.3 On device (owed, needs `emulator-5556` and `nordrhein-westfalen-27-20260820-0826`): issue a
      calculation immediately after `openDatabases` and confirm the new kind on the surface with the retry
      wording and no map-manager action; the 416-point Dortmund sweep is the reproduction. Verify:
      coordinate-free logcat lines per `guidelines/Build.md` §10a/§10b.
      *(spec: `routing-failure-reporting` — A database list that is still filling is reported as not loaded
      yet)*
