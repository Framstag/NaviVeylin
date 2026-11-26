# Tasks — the loop's core exists and compiles

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate for every task:
`bash tools/loop/tests/loop-selftest.sh --files tools/loop/…` while iterating, one full suite run before the change
is complete. No Gradle build is owed: no compiled *app* file changes, and the core is compiled by the engine's own
`loop_core` (`guidelines/Build.md` §2, the scope block). Depends on `run-sh-is-split-along-the-stream` for the
layout table the core's files take a bound in. The sibling change `the-run-state-is-typed` switches the scripts
onto this core and is not this change's work.

## 1. The core's skeleton and its compile path

- [x] 1.1 `tools/loop/core/Json.java` — copied by value from `tools/issues/core/Json.java`, with its header saying
  it is a copy on purpose (`PORTING.md`) and that the two cores share no dependency. Verify: it compiles alone
  (`javac --release 17 tools/loop/core/Json.java`).
- [x] 1.2 `tools/loop/core/LoopCore.java` — the verb dispatch: `state` today, an unknown verb exits non-zero naming
  it, and no verb prints anything but one document on stdout. Verify: case 1.5. Satisfies `A verb answers one JSON
  document, and a malformed input is a refusal`.
- [x] 1.3 `tools/loop/lib.sh` gains `loop_core`: digest the core's sources (sorted files, the issue tooling's
  pattern), compile into a cache directory named by the digest, and refuse once with a line naming JDK 17 when no
  compiler answers. Verify: case 1.6. Satisfies `The core is compiled once per source digest, and a missing
  compiler is one refusal`.
- [x] 1.4 `tools/loop/tests/loop-selftest.sh` compiles the core in its `core_ensure` beside the issue tooling's
  (`:424`), and pools the core's case list with the shell's: one selection, one summary, one `--case` flag reaching
  both. Verify: cases 1.7. Satisfies `The core's cases are the core's own, and the suite lists them with the
  shell's`.
- [x] 1.5 Cases `core#theAnswerIsOneJsonDocument`, `core#aMalformedInputIsARefusal`,
  `core#noPartialParseOnFailure`: drive the dispatch with a fixture that is fine, one that is not JSON, and one that
  fails after a field. Each declares `# files: tools/loop/core/ tools/loop/lib.sh`.
- [x] 1.6 Cases `core#theDigestIsTheCompileKey`, `core#oneCompilePerDigest`, `core#aMissingCompilerIsOneRefusal`:
  compile, call twice, count the compiles; then run with `javac` shadowed by a probe that refuses.
- [x] 1.7 Cases `core#theCoreListsItsOwnCases`, `core#oneSummaryCarriesBothSources`,
  `core#aCoreCaseDeclaresItsFiles`: a case the core's list names runs, a run's summary counts both sources, and a
  core case that declares no files fails the case-list check.

## 2. The state verb's first subject

- [x] 2.1 `tools/loop/core/State.java`: the declared fields, the accessors, the writer that refuses an undeclared
  field, and the batch count query (`state counts`) answering the six counts `tools/loop/report.sh:84` asks for. Verify: case
  2.2. Nothing switches onto it in this change — the sibling does the switching.
- [x] 2.2 Cases `core#theStateIsWrittenByTheCore` and `core#theStateAReadCannotParseIsRefused`: write an item
  through the core and read the counts back; then hand it a state with an undeclared field.

## 3. The core as an engine file

- [x] 3.1 Cases `core#thePortabilityCheckReadsTheCore`, `core#theCoresCasesDeclareTheCore`:
  `bash tools/check-portability.sh --quiet` must see the core, and each `core#` case must declare it. Satisfies
  `The core is an engine file, so the portability check and the case list read it`.
- [x] 3.2 `tools/loop/loop.conf` records nothing new on purpose — state that in the change's report; the core's
  cache directory is the engine's business, not the profile's.
- [x] 3.3 The boundary's own guarantees — the exit codes, one call per phase and no case needing a new boundary —
  are **not** this change's: nothing consults the core yet, so there is no boundary to keep. The sibling
  `the-run-state-is-typed` carries them with the scripts that switch, and its cases are its own.

## 4. Documents

- [x] 4.1 `guidelines/Backlog.md`, "## 7. The loop", names the core as the protocol's one owner. Verify
  `bash tools/check-doc-routes.sh`.
- [x] 4.2 `guidelines/Design.md`, "## 12. General engineering principles", gains the rule: a typed value beats a
  stringly protocol, with the loop's core as the worked example. Verify the section's route row still resolves.
- [x] 4.3 `PORTING.md` "What to copy" gains `tools/loop/core/`; "Prerequisites" states that two cores now need the
  JDK. Verify `bash tools/check-portability.sh --quiet` and the portability self-test.

## 5. Verification

- [x] 5.1 Full suite green, with its case count and wall time recorded.
- [x] 5.2 Revert-check for the partial-parse refusal: make the verb answer what it read before the failure and
  `core#noPartialParseOnFailure` must fail; restore, forced green.
- [x] 5.3 Revert-check for the digest cache: drop the digest from the cache key and `core#theDigestIsTheCompileKey`
  must fail; restore, forced green.
- [x] 5.4 Revert-check for the second case source: drop the core's list from the driver's pool and
  `core#oneSummaryCarriesBothSources` must fail; restore, forced green.
- [x] 5.5 Record the measured costs in the change's evidence: cold and warm compile, and the core's invocation
  count for one run.
- [x] 5.6 File every unrelated defect this work finds with `.pi/skills/create-issue`. One was found and is named as a
  `NOTE:` in the worker's report instead of filed, because this run owns the tracker: `driver#anOwnedFunctionIsInItsOwnersFile`
  is red at the base commit too — `tools/loop/report.sh` defines `action_add` and `tools/loop/state.sh` defines
  `cmd_publish_refused`, and neither name stands in its row of `tools/loop/layout.tsv` (whose own row this change edits).
  The suite's parallel pool can also red `run#theWorkerPromptFitsItsBudget`, which passes when run alone.
