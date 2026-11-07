# Build Gate — why

Rationale for `guidelines/BuildGate.md`: the measurements that proved each rule, the dated provenance, and
the history the normative document no longer carries. Read beside the section it anchors to.

## §2 Common behavior

Measured 2026-10-22: the five sub-sections cost 1 904–5 238 bytes each, against 23 235 bytes for §2 before the
split, which is why §2 is an index of them.

## §2a Agent iteration protocol

The costs that put these rules in place:

- **Rule 1 (count the surface).** One change's plan made before this pass cost a factor of two: 292
  insertions over six files, ~60 % of it test surface, against a plan of two functions (issue #238).
- **Rule 2 (iterate, gate once).** A `--files` selection that names `tools/*-cases.sh` selects nearly the
  whole suite — 256 of 256 measured, 405–507 s alone. A suite call that outlived its caller's ceiling was
  killed with the child's work uncommitted, measured twice on 2026-10-28 at 900 s (issue #692).
- **Rule 5 (a reviewer is a reader).** One item recorded a reviewer that ran a suite to answer a question the
  evidence already answered: one suite held open for 4 min 1 s of a 4 h 40 m change, 20 min in total, ~7 % of
  it (issue #236).
- **Rule 6 (one call per tree question).** The loop's children spent 9 338 of 16 987 shell calls (55 %) on
  `grep`/`head`/`ls`/`sed`/`cat` at a p50 of 52 per item, and chained them (`grep -> grep` 1 017× in 132
  items, `grep -> sed` 385×), which is why the child's own policy refuses a shell *tree search* (issue #687).

## §2b Liveness guard and shell recipes

- The liveness probe that cannot match the real process was measured 2026-10-04 and again 2026-10-04/05: it
  reported "finished" for work still in flight.
- A suite run that died mid-flight was recorded as `TODO.md` §40.3; the daemon `BUSY` residue extends §40.4,
  and the several-workers retry is §134.64.
- The allowlist probe was verified 2026-10-03 (python3 3.14.7, perl, tesseract 5.5.3; `TODO.md` §106's device
  pass used `tesseract`).
- `pkill -f "gradlew …"` is §40.2; the `/tmp` import limit is §40.7; the edit-in-flight probe is §40.46.

## §2c Gate timing record and baseline

**Baseline (2026-10-04)** — `:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest
:app:assembleMobileDebug :app:assembleAutomotiveDebug --rerun-tasks --continue`; host with 15 GB RAM of
which ~1 GB free, Android Studio and the Pixel_8 emulator running, load average 0.8:

| phase (sum of executed task durations) | baseline |
|---|---|
| compile & package | 7m57s over 107 tasks |
| tests | 5m38s over 2 tasks |
| other executed | 18.0s over 89 tasks |
| Gradle's own build (buildSrc) | 0.8s over 3 tasks |
| **wall clock** | **8m25s** (log: `BUILD SUCCESSFUL in 8m26s`, `189 actionable tasks: 189 executed`) |
| `:app:testMobileDebugUnitTest` | 2m47s — 215 classes, 1635 tests, 0 failures |
| `:app:testAutomotiveDebugUnitTest` | 2m38s — 215 classes, 1635 tests, 0 failures |

The per-task sums exceed the wall clock because Gradle overlaps compile, packaging and test tasks: quote the
wall clock and the per-suite windows, and read a phase sum as work, never as elapsed time.

**After the change (2026-10-05)** — the levers this baseline was measured against, each with its numbers: the
two `:app` suites run at the declared two forks (mobile 2m50s → 2m02s, automotive 2m35s → 2m02s per suite,
§6d); the Kover agent costs 10-12 % and is dropped for an iteration that feeds no report (§7); a JVM-only
change builds no native artifact, one ABI is buildable for a device target, and `app/.cxx` is kept at 4
configuration trees instead of 13 (§4c); forcing the test tasks instead of the whole graph leaves the
compiles `UP-TO-DATE` (§4b); a shared-source change runs one flavor instead of two (§4b). The full gate — all
modules, both flavors, instrumentation attached — ran green: `:app` mobile 219 classes / 1666 tests,
automotive 219 / 1666, `:auto` 76 / 779, `:core` 39 / 444, JNI 2 / 26, all with 0 failures and 0 errors
(measured 2026-10-08; the suites have since grown — see the aggregate record below).

**The aggregate is not the sum of those levers — measured, and worth reading before quoting the table above.**
One clean full gate (`test -PforceTests --no-build-cache`, all four modules, no other build on the machine,
4.4 GB RAM free) took **9m46s** wall with only **17 of 185 tasks executed** (168 up-to-date) and suites
`:app` mobile **4m54s**, automotive **3m56s**, `:auto` 1m39s, `:core` 32.8s, JNI 1.7s. The suites share the
invocation, so their forks (2+2+1) plus the daemon contend for CPU and — on a box with ~4 GB free — for
memory: 12m00s of test-task work compressed into 9m46s of wall, and every suite is slower than its isolated
measurement (2m02s alone at two forks). Consequences to keep in mind: the per-suite table in §6d describes
**single-suite** runs, the aggregate gate is bounded by suite work rather than by any flag this change added,
and the declared two forks may be too optimistic when several modules' suites run at once — see
`TODO.md` §132.

**The same shape measured again 2026-10-21, with the fork counts left as they are declared** (`test
-PforceTests --no-build-cache -I tools/gate-timings.init.gradle.kts`, all four modules, 16-core host, ~4 GB
RAM free at the start, no other build on the machine, worker-JVM RSS sampled every 2 s): **5m45s** wall
(345 107 ms), 21 of 189 tasks executed, suites `:app` mobile **2m40s** / 2 096 tests, automotive **3m00s** /
2 096, `:auto` **1m16s** / 866, `:core` **19.9s** / 642, JNI **2.8s** / 48 — **5 748 tests, 0 failures, 0
errors** — and a peak test-worker footprint of **1.89 GiB in one worker, 3.42 GiB across the two concurrent
`:app` forks**. That is the number the aggregate shape has to fit, and it fits the ceiling the declared forks
were chosen against (§6c), so `:app` stays at two forks: dropping to one costs ~28 % of every iteration and
buys nothing measurable here. Each suite is still slower than its own isolated row (`:app` 2m02s → 2m40s /
3m00s, `:auto` 40.0s → 1m16s), which is the contention this paragraph is about — read §6d's table as
single-suite, and this record as the gate the project actually runs (issue #83).

## §2d The build JVM (daemon JVM criteria)

The criteria take precedence over `JAVA_HOME` and `-Dorg.gradle.java.home` (Gradle 9.6.1 manual, "Daemon JVM
Toolchains"). The versioned `gradle.properties` carries no `org.gradle.java.home` line because a machine-local
absolute path is wrong on every machine but the one that wrote it, and where the criteria applied it selected
no JVM (issue #91). The absence is guarded twice: the CI step refuses a line that returns, and the `:app` case
`VersionedGradleJavaHomeTest` fails on it, so a copy-paste from an archived design reddens a test run as well
as the pipeline (issue #248).

Rule 2's history: `updateDaemonJvm` writes foojay Disco *record* URLs; the index prunes records, a pruned id
answers HTTP 400, and Gradle cannot provision at all — the build died at daemon start after the whole
SDK/vcpkg setup, which is how CI broke on 2026-10-06 (`gradle/foojay-toolchains#196`, closed NOT_PLANNED with
"regenerating one is a solution" — which only re-emits expiring ids).

`buildSrc` declared a Java 17 **toolchain** from 2026-10-06 to 2026-10-24 (issue #92, migrated
`TODO.md` §146), which nothing could provision — `buildSrc` has no `settings.gradle.kts`, so the foojay resolver above
never reaches its build, and only auto-detection could satisfy it — so a machine without a JDK 17 failed at
configuration before its first task, with a message that named neither the file nor the fix. The pin bought
nothing either: `buildSrc` carries no Java source at all (`:buildSrc:compileJava NO-SOURCE`), so the toolchain
only ever set Kotlin's `jvmTarget`.

## §2e The actions' Node runtime

GitHub's deprecation changelog, 2025-09-19: a major that predates Node 24 makes the runner print `Node.js 20
is deprecated` on every step and force the action onto Node 24 anyway. The six pins that did (issue #240); the
case that failed for it is the one in `tools/check-action-runtimes-selftest.sh` that checks this repository's
own pins.
