# Landing verification — the gate re-run on the tree the change lands on

The change was produced on base `f5f2a2a7` (issue #286's commit) and landed on main's tip
`9e77cd69cc2a0fc6fa51d68f145c5f22594f558e`, 19 commits later. This is the re-run the landing owes: the
rebased branch was measured again, in the loop worktree, before the merge. The logs beside this file are
that run's, not the base run's (`gate.log`, `green-full-run.log`).

## The rebase

`git rebase main` in `.pi/worktrees/loop`, clean — no conflict, no manual resolution. None of the files
this change edits (`app/build.gradle.kts`, `.github/workflows/build.yml`, `guidelines/Build.md`,
`openspec/specs/build-sbom/spec.md`, `tools/loop/loop.conf`) changed on main since `f5f2a2a7`
(`git diff --stat f5f2a2a7..main -- <those paths>` → empty), so the delta applied byte-for-byte.

## The red, re-measured on main's tip before the change

`VCPKG_ROOT=/tmp/i270/no-vcpkg-here ./gradlew test -PforceTests --no-build-cache` → `BUILD FAILED in 14s`,
exit 1:

```
Could not determine the dependencies of task ':app:generateSbomAutomotiveDebug'.
> Could not create task ':app:mergeNativeSbom'.
   > vcpkg installed-status file not found at /tmp/i270/no-vcpkg-here/installed/vcpkg/status — run ./setup-vcpkg.sh first.
```

The same failure the item was filed on, in a shorter wall clock because the configuration cache was warm.

## Green on the rebased tip

- `green-full-run-on-main-tip.log` — `VCPKG_ROOT=/tmp/i270/no-vcpkg-here ./gradlew test -PforceTests
  -PjvmTestsOnly --no-build-cache` → `BUILD SUCCESSFUL in 5m 11s`, `164 actionable tasks: 17 executed,
  147 up-to-date`, **exit 0**, and **no** `:app:generateLicenseAssets…` / `:app:generateSbom…` /
  `:app:mergeNativeSbom` line in the log (`grep -cE '> Task :app:(generateLicenseAssets|generateSbom|
  mergeNativeSbom)'` → 0). Tallies: `:app` mobile 2105/0/0, `:app` automotive 2105/0/0, `:auto` 880/0/0,
  `:core` 646/0/0, `:osmscout-client-java` 48/0/0, `buildSrc` 7/0/0 — **5 791 tests, 0 failures**.
- `bash tools/check-jvm-test-graph-selftest.sh` → `all 3 cases passed (--dry-run configurations, no build,
  no network)`.
- `./gradlew -p buildSrc test --tests '*JvmOnlyTestRunTest'` → `TEST-com.naviveylin.build.JvmOnlyTestRunTest.xml`
  `tests="7" skipped="0" failures="0" errors="0"`.
- `bash tools/loop/gate.sh --base 9e77cd69…`, `gate-on-main-tip.log`: **22m 29s**, `LOOP-GATE: tests=348
  failures=1 errors=0`, the one red being `tools/loop-selftest.sh --from-git 9e77cd69…` (`1 of 207 cases
  failed`). Every other suite the profile owed is green — `bash tools/loop-mutations.sh --check` 128/0,
  `tools/check-action-runtimes-selftest.sh` 10/0, `bash tools/gen-feature-list.sh --check-classification`
  4s, `tools/check-doc-routes.sh` 0s, `tools/check-jvm-test-graph-selftest.sh` 3/0 — and the gate's own
  compiled command ran green in 325 s.

## The one red, attributed

`run#aWriterTheRunCanPlaceBlocksTheItem` (gate-on-main-tip.log:603, `loop-selftest: 1 of 207 cases failed`).
It is **not this change's**: the case fails on main's tip without this change — the landing re-measured it
in the project tree at `9e77cd69`, clean, before the merge branch existed: `bash tools/loop-selftest.sh
--case run#aWriterTheRunCanPlaceBlocksTheItem` → `expected 'blocked', got 'done'`, `1 of 1 cases failed`. It
is the subject of its own open item (#288, "The outside-write case fails on a clean checkout of main and
flips with the engine tree's own dirt"), whose root cause is unestablished, and it is the second case of
#287's pair. This change edits `tools/loop/loop.conf` (a suite mapping and `LOOP_GATE_COMPILED_CMD`), which
is why the profile selects that suite at all; nothing else in the delta touches the loop engine. Its
repair is #288's, in a change of its own, not a re-scope of a `build-sbom` change.
