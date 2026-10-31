# Build Gate Guidelines — the iteration protocol, the guard, the JVM and the action runtimes

The rules a session follows around the build gate: what a delta owes, how to keep runs live, how the
daemon JVM and the workflow actions are pinned. This document was cut from `Build.md`; the numbers did not
change, only the document that carries them.

> The rationale behind these rules — the timing record, the JVM criteria derivation, the liveness anecdotes —
> is `guidelines/BuildGate.why.md`; the command transcripts are `guidelines/BuildGate.recipes.md`.

---

## 2. Common behavior

All four skills follow the same contract:

- **Rule: a skill wraps the existing Gradle calls** (`./gradlew ...`) — no new build system, no wrapper
  scripts, no duplicated command logic.
- **Rule: a skill prints status messages before and after each run** (e.g. "Building debug APK
  (all ABIs, both flavors)…", "Build succeeded — …").
- **Rule: a long build or suite runs in the foreground, redirected to a log.** A full build or suite
  exceeds the shell tool's ~120s **output** cap, so run it in the **foreground** with
  `> /tmp/<name>.log 2>&1` and grep the verdict, the `e: ` / `w: ` lines and the tallies back. Do **not**
  background it (`nohup … &`): the harness kills the process group when the tool call ends, and the run
  then dies mid-build with no verdict (`setsid` is not available either, so there is no way to detach that
  survives the tool call).
- **Rule: a result is evaluated by return code AND build output**:
  - Exit code `0` **and** output contains `BUILD SUCCESSFUL` → success
  - Exit code non-zero **or** output contains `BUILD FAILED` → failure
- **Rule: a failure is reported as a clear verdict with actionable error excerpts.**

Every question this section answers has its own sub-section; read the one that owns the question rather than
the section whole — §2a "Agent iteration protocol", §2b "Liveness guard and shell recipes", §2c "Gate timing
record and baseline", §2d "The build JVM (daemon JVM criteria)", §2e "The actions' Node runtime".

## 2a. Agent iteration protocol

A session that changes this repository works in the loop below. The measurements behind it, the levers it
uses and the evidence it must quote live where the loop points: §4b (declared-cases pass, the forcing flag,
the per-module freshness check), §6c–§6e (test constraints and the gate's forcing rule), §10 and §11 (the
on-device and phone-measurement recipes), and the skills §1 names. This subsection states the rules; it
quotes no measurement. (The costs that motivated them are `guidelines/BuildGate.why.md` §2a.)

- **Rule: a delta owes the verification its file roster names, and no more.** Which verification a delta
  owes follows its file roster, not the habit of running everything:
  - **No compiled file changed** (`*.md`, `openspec/**`, `.pi/**`, `guidelines/**`, `tools/**`): run **no
    build and no suite**. Say in the task that the delta compiles nothing, and name the check you ran
    instead — for a docs/spec/skill/backlog delta that is the structural check of the files it edits, the
    ones CI already runs for it (`.github/workflows/build.yml`, the steps `Check OpenSpec artifact hygiene`
    and `Check documentation routes`; text-only, no build, no device) plus the CLI's own (`openspec doctor`,
    `openspec validate`).
  - **Only test sources changed**: run the changed or new cases through the affected module's `--tests`
    filter (§3), not the suite.
  - **Library or app code changed**: the gate this document defines — the suite of the flavor the change
    can affect, and the both-flavor gate before the change is called complete (§3, §4b).
  - **Name every exclusion**: any test or task a run skipped, and why. A green run that quietly dropped a
    suite reads as a gate it was not.
- **Rule: look, then measure, before changing code.** A symptom that lives in pixels cannot be settled by
  reading projection, fit or layout code. Look at the artifact, add a coordinate-free diagnostics line
  (numbers — indices, pixels, band, verdict — never a position, spec `auto-diagnostics`), read it on the
  device, and measure the artifact with the `pixel-check` skill's script. Only measured numbers and that
  script's exit code are evidence; a description of what a picture shows is triage, not a verdict. When the
  description and the measurement disagree, the disagreement is the bug. A change that rewrites a rule
  rather than an artifact owes the same measurement on a second axis, and it comes before the plan is fixed:
  **count the surface** — `grep -l` the rule's text and its symbol over the cases (`tools/*-cases.sh`), the
  engine, the prompts and the fixtures, and list the cases that assert the behaviour being changed. The
  count, not the first file the plan names, is what the plan's estimate is set against, and the change's
  proposal carries it with the command that produced it (`surface: 2 cases added, 2 rewritten, 4 fixtures
  realigned — <command>`) so the next reader can repeat it instead of trusting it.
- **Rule: iterate with focused suites, gate once.** Run the change's own declared cases first
  (`tools/declared-cases.sh`, §4b), then the module suite of the flavor the change can affect, and the full
  both-flavor gate once before the commit. A declared-cases pass is never gate evidence. **The filter is the
  inner loop; a selection naming the case file is the gate.** `tools/loop-selftest.sh --case <name>` (or a
  small `--group`) is what an edit is checked with between rounds; a `--files` selection that names
  `tools/*-cases.sh` selects nearly the whole suite and is run **once**, as the gate, never per edit — a
  caller's per-call ceiling can sit below this project's own case and child bounds (§6c,
  `LOOP_CASE_MAX_SECONDS`), and a suite call that outlives it is killed with the child's work uncommitted.
  **State that ceiling when you start one** — `LOOP_CALL_CEILING_SECONDS=<n> …` (`tools/loop/loop.conf`) —
  and the call ends itself inside it with a line naming what it covered, instead of being killed mid-call
  with nothing said.
- **Rule: batch independent questions.** Ask the owner two to four independent questions in one call rather
  than one question per round, and ask for the observation (where, when) before proposing a cause.
- **Rule: one builder per working tree.** Two Gradle invocations in one tree corrupt each other's outputs: a
  foreign build has killed a merge task's cache write, and a foreign edit mid-run has forced the Kotlin
  compiles to re-execute, inflating the measured gate several times over. Isolate a long verification run in
  its own worktree, or confirm the other session is done first; a wait loop that never saw a quiet window
  must abort rather than fall through into the run. Detection must be able to match the real process
  (`pgrep -af 'gradle-wrapper\.ja[r]'`, §2b), and the same probe covers an **edit** in flight: run
  `find <module> -newermt '-10 minutes'` beside the wrapper probe, and if a peer is mid-edit, quote the
  evidence already collected and stop.
- **Rule: a reviewer is a reader, not a runner.** A review round is handed what it reads — the change's
  diff, the project's rules, the evidence by path — and it edits nothing, runs no gate and no suite, uses no
  network, and quotes a green result from the evidence instead of reproducing it. The text is
  `tools/loop/prompts/reviewer.md`, and a session that spawns its own reviewer hands it that same text. A
  diff review is a reading task.
- **Rule: answer a tree question with one call.** A search is `tools/look.sh` (the whole tree at once, the
  submodule included), `ctx_grep` or `ctx_read` — never a shell `grep`/`head`/`ls`/`sed`/`cat` chain. The
  child's own policy refuses a shell *tree search* and names the alternatives: `sed -n 'N,Mp'` and `tail -n`
  to read a range of a known file, and a filter of another command's output (`… | grep -c`), still stand.

## 2b. Liveness guard and shell recipes

- **Rule: the "no other build is running" guard must be able to match the real process.**
  `pgrep -f 'GradleWrapper[M]ain'` never matches: the wrapper runs as
  `java -Xmx64m … -Dorg.gradle.appname=gradlew -jar …/gradle-wrapper.jar <tasks>`, so
  `org.gradle.wrapper.GradleWrapperMain` lives *inside* the jar and never appears in the argument vector
  (the daemon shows as `gradle-daemon-main-<version>.jar`). Use `pgrep -af 'gradle-wrapper\.ja[r]'` — the
  bracket keeps the pattern from matching the `pgrep` call itself — or track the PID the launcher printed,
  and then confirm with the `BUILD SUCCESSFUL|BUILD FAILED` verdict line in the log. A liveness probe that
  cannot match the real process reports "finished" for work still in flight, and a guard's own `echo` must
  never state a verdict the guard did not measure.
- **Rule: use the allowlist recipes instead of re-discovering them.** `find <dir> -print | xargs -r rm -rf`
  (never `find … -exec rm -rf {} +`); `awk` for arithmetic (`bc` is not allowed); `perl -0pi` is blocked —
  for a bulk edit use the editor's `replace_all` with an anchor on a neighbouring unique line; no `$()` or
  backtick substitution at command position (prepare the command once, print it, paste it verbatim); a
  helper script under `/tmp` is not runnable — inline the commands; the projection head unit binary needs
  `lean-ctx allow desktop-head-unit` (or a human at the DHU window).
- **Rule: a shell trap is not a build failure.** zsh (the harness shell) does **not** word-split — a
  `$filters` variable or `for x in "a b"` arrives as one argv element, so Gradle answers "Task ' --tests …'
  not found" (pass filters literally); an `adb` call inside a `printf | while read` loop consumes the
  pipeline's stdin (add `</dev/null` to every `adb` call in the loop); and `adb logcat -d --pid <pid>` hangs
  — use `-t <N>` plus an `awk` filter on the PID column instead.
- **Rule: an init-script trap is a compile error.** In `*.init.gradle.kts` a deprecated Gradle member is a
  **compile error**, not a warning (a Groovy `*.init.gradle` would only warn). Probe the script API by
  reflection in a throwaway Groovy init script before writing the Kotlin one. A harness that overrides a
  value the build script also sets must apply in `gradle.projectsEvaluated { }` (an earlier hook is
  overridden by the script's own `configureEach`) and must be verified by an observable — the worker-JVM
  count, not the flag it was given.
- **Rule: a tool that infers a directory layout needs a shape check.** `tools/prune-native-configs.sh`
  deleted ABI directories *inside* hash trees once because the layout had shifted one level; refuse a root
  whose grandchildren are ABI names, and dry-run with the **same** arguments as the real run — a dry run
  validates only the exact invocation that is repeated.
- **Rule: a suite run that died mid-flight is not neutral.** "No verdict in the log" means *killed*, not
  failed: split the gate per module instead of re-running the aggregate. Before the next run, delete the
  dead run's results (`rm -rf app/build/test-results/<flavor>`) — otherwise the next invocation can abort
  with `java.nio.file.NoSuchFileException: …/in-progress-results-generic.bin` *after* printing its tallies,
  losing the per-test detail. A killed run also leaves its daemon `BUSY`: `./gradlew --status`,
  `./gradlew --stop`, then re-run. Free the machine before that re-run: `free -m` shows the RAM the dead run
  still holds, and an orphan test worker is killed by PID (`ps -eo pid,rss,args --sort=-rss`, confirming the
  worker's `-Djava.library.path` points at this repository) rather than by name; a run that died with
  several workers in flight is retried at `--max-workers=1`.
- **Rule: the allowlist is probed, not assumed.** `python3`, `perl` and `tesseract` all **run** in this
  shell, so do not write a plan around a tool being blocked. `jq`/`grep`/`sed`/`awk` stay the house style
  for text work, and `openspec` interaction always goes through the CLI's own `--json` output — never a
  script. Reach for `python3` only when shell text tools genuinely cannot express the thing.
- **Rule: never `pkill -f "gradlew …"`.** `-f` matches the calling tool's own command line, kills the
  shell, and the rest of the chained command (e.g. a `git commit`) silently never runs. Kill by PID:
  `ps -o pid,args | grep 'gradle-wrapper\.ja[r]'` → `kill -9 <pid>`.
- **Rule: do not use `/tmp` for large map imports.** It is a RAM tmpfs (~7.7 GB quota), so an import there
  competes with the build for memory — use a disk path.

## 2c. Gate timing record and baseline

**Rule: every gate run can leave a machine-readable record of what it cost**, so "the gate got faster" is a
measurement instead of an impression (change `speed-up-build-test-gate`, spec `build-test-gate`):

```bash
./gradlew -I tools/gate-timings.init.gradle.kts <tasks…>   # writes build/gate-timings.json
bash tools/gate-timing-report.sh                           # defaults: build/gate-timings.json  .
```

**Rule: the timing script only observes; it never adds an action to a task.** A task action would change the
task's action class and with it the build-cache key, so every instrumented run would miss the cache and
re-execute everything the record exists to measure. It reports per task (path, outcome, duration) and per
suite (wall time from the first and last result-XML `timestamp`, class count, test count, failures). Its own
behaviour is covered without a device: `bash tools/gate-timings-selftest.sh`.

**Rule: to force execution of the tests, use `-PforceTests --no-build-cache`, not `--rerun-tasks`.** It
re-executes the test tasks and leaves the rest of the graph up to date (§4b).

The baseline, the levers measured against it and the aggregate gate's numbers are recorded as history in
`guidelines/BuildGate.why.md` §2c — quote the wall clock and the per-suite windows, and read a phase sum as
work, never as elapsed time.

## 2d. The build JVM (daemon JVM criteria)

The daemon JVM is **not** selected by `JAVA_HOME` or by the `-Dorg.gradle.java.home` in the recipes below:
the checked-in daemon JVM criteria in `gradle/gradle-daemon-jvm.properties` **take precedence over both**
(Gradle 9.6.1 manual, "Daemon JVM Toolchains"). The versioned `gradle.properties` carries no
`org.gradle.java.home` line at all — a machine-local absolute path is wrong on every machine but the one
that wrote it, and where the criteria apply it selected no JVM. That absence is guarded twice, and the guard
is the shape the repair owes: the same CI step refuses a line that returns, and the `:app` case
`VersionedGradleJavaHomeTest` fails on it, so a copy-paste from an archived design reddens a test run as well
as the pipeline instead of landing silently. Three rules follow; CI enforces the first two in the step
`Check daemon JVM criteria` (`.github/workflows/build.yml`, placed before the SDK/vcpkg work so it fails in
seconds). (The derivation of each rule is `guidelines/BuildGate.why.md` §2d.)

- **Rule: version 21, no vendor.** The file pins `toolchainVersion=21` and carries **no** `toolchainVendor`
  line, so any installed JDK 21 satisfies it — the workstation's JBR 21 and the runner's Temurin 21 both
  start the daemon from what they have, and the routine path downloads nothing. A vendor line would exclude
  exactly the JDK CI installs, which is how the build broke.
- **Rule: provisioning URLs are vendor endpoints, never index package ids.** `updateDaemonJvm` writes foojay
  Disco *record* URLs (a hash id under `api.foojay.io/disco/v3.0`); the index prunes records, a pruned id
  answers HTTP 400, and Gradle then cannot provision at all — the build dies at daemon start, before its
  first task and after the whole SDK/vcpkg setup. The committed URLs are the vendor's permanent endpoint for
  version 21 (`.../latest/21/ga/<os>/<arch>/jdk/hotspot/normal/eclipse`, so a new 21 patch needs no edit);
  only a machine with no JDK 21 at all uses them. `FREE_BSD`/`UNIX` are deliberately absent — Adoptium
  publishes no such build — and Gradle then reports "No defined toolchain download url", a first-party
  message instead of a third party's 400.
- **Rule: regeneration is deliberate.** `./gradlew updateDaemonJvm` rewrites the file with package-id URLs;
  replace them with the vendor endpoints before committing, or the CI step fails the run.

Checks (none of them needs a device or a JDK download) are the recipes in `guidelines/BuildGate.recipes.md`
§2d. The daemon JVM is the **only** JVM the build needs, and a check proves it: `buildSrc` compiles its build
logic with the daemon JVM the criteria select (21) and declares no toolchain of its own, so a machine that
satisfies the criteria also configures `buildSrc` without a second installation. Two surfaces hold the
absence in place — the `:app` case `BuildSrcJvmRequirementTest` ("the build script names no JVM of its own")
and the buildSrc predicate of the CI step above.

## 2e. The actions' Node runtime

A workflow's `uses:` pin, not the runner, decides which Node runtime an action runs on: a major that predates
Node 24 makes the runner print `Node.js 20 is deprecated` on every step and force the action onto Node 24
anyway. Six pins in this repository did — five `actions/upload-artifact@v5` steps in
`.github/workflows/build.yml` and `actions/checkout@v4` in `.github/workflows/sensors.yml` — and the notice is
a warning, so nothing failed and no test could see it. Two rules follow.

- **Rule: pin the first major whose committed `action.yml` declares `runs.using: node24`, or a later one.**
  The declaration is the fact, not the release note: read it at the tag
  (`raw.githubusercontent.com/actions/upload-artifact/v6/action.yml` → `using: 'node24'`, where `v5` says
  `node20`).
- **Rule: a new action is recorded before it is pinned.** `tools/check-action-runtimes.sh` holds the floor
  table — action → first Node 24 major → the `action.yml` URL it was read from — and fails on an action it
  does not record, on a pin below its action's floor (naming file and line), and on a ref it cannot resolve
  offline (`@main`, a SHA). What carries no Node runtime of its own is skipped: a local action and a
  `docker://` one.

CI enforces both in the step `Check action runtimes` (`.github/workflows/build.yml`, text only: no build, no
device, no network; its cases are `tools/check-action-runtimes-selftest.sh`). The recorded floor is a memory,
not a fact: when an action publishes a major or the runner retires Node 24, the table and the URLs beside it
are what a reader re-reads. The check fetches nothing on purpose — a gate that needs egress fails on a runner
without it and passes on a workstation with it.
