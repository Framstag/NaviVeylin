# Tasks — fix-uncarried-guardrail-entries

Every task names its own verification. The delta touches no compiled file, so no Gradle task appears: the
structural check of the files it edits stands in (`guidelines/Build.md` §2, the scope block). Parent
requirement for 3–7: `documentation-ownership`, "A recorded guardrail is stated in the section that owns it";
scenario → case: `record-carriage#eachRecordedRuleIsStatedInItsSection`.

## 1. Audit the record against the tree

- [x] 1.1 Transcribe issue #69's 37 entries (62–98) with the carrier each names, and check the carrier's
      section for the rule, at base `44b673f`. Verify: `evidence/audit.md` holds 37 rows, each with the
      carrier's `file:line` and one state (`carried`, `partial`, `absent`), and exactly four rows are not
      `carried` (§134.64, §134.65, §134.73, §134.76).

## 2. The red case on HEAD

- [x] 2.1 Write `evidence/red-probe.sh`: per entry, locate the owning section by its heading text and require
      the rule's **one distinguishing token** inside it (one token per entry — the guard shows the rule reached
      the section, not that every clause of a multi-clause bullet survived); text only, no build, no device, no
      network. Verify: on HEAD it prints `entries=4 missing=4` and the `FAIL
      record-carriage#eachRecordedRuleIsStatedInItsSection … (exit 1)` line, recorded as `evidence/red-HEAD.txt`
      with the capture time (2026-10-21T11:41:24Z) in its own header. The `(exit 1)` text is the probe's printed
      verdict rather than a separately observed code — `evidence/revert-mutation.txt` records an observed
      `EXIT=1` for the same branch. No JUnit XML: the delta compiles nothing.

## 3. Carry the two rules owned by `guidelines/Design.md`

- [x] 3.1 Add §134.73 to `guidelines/Design.md` §11's "Kotlin / Compose / Robolectric traps" list: native
      methods cannot be relaxed or intercepted, a relaxed `StateFlow`-returning member throws
      `KotlinNothingValueException` on `collect`, a relaxed answer is `false`/`null`, the module's fakes and
      real `MutableStateFlow`s are the substitute. Verify: the probe's §134.73 line changes from `MISSING` to
      `STATED`.
- [x] 3.2 Add §134.76 to the same list: `LifecycleRegistry` state order, the protected `Screen` constructor,
      `ShadowDialog` over a `Box` overlay, `PartiallyExpanded` as a fraction of content, `assertIsDisplayed`
      inside a `verticalScroll` in Robolectric's 320×470 dp window, rule-member vs extension symbols. Verify:
      the probe's §134.76 line changes from `MISSING` to `STATED`.

## 4. Carry the two rules owned by `guidelines/Build.md`

- [x] 4.1 Extend §2's killed-run bullet (`:172-177`) with the missing half of the recovery order: `free -m`,
      the orphan test worker killed by PID after confirming its `java.library.path` points at this repository,
      and the retry at `--max-workers=1`. Verify: the probe's §134.64 line changes from `MISSING` to `STATED`.
- [x] 4.2 Add one line below §3's command table: a run meant to cover the whole suite adds `--continue`, and
      the executed-task list is read instead of the flavors one expects. Verify: the probe's §134.65 line
      changes from `MISSING` to `STATED`, and `tools/check-doc-routes.sh` still resolves every reference.

## 5. Green and falsified

- [x] 5.1 Re-run the probe on the fixed tree. Verify: `entries=4 missing=0`, the `PASS
      record-carriage#eachRecordedRuleIsStatedInItsSection (exit 0)` line, recorded as
      `evidence/green-after.txt` with its capture time (2026-10-21T11:42:28Z) in the file's header; the round-2
      re-run after the review's corrections prints the same four `STATED` lines (task 6.3).
- [x] 5.2 Falsify the probe with exactly one mutation (`guidelines/Build.md` §4 "One mutation per revert
      check"): delete the §134.76 bullet. Verify: the probe fails naming `§134.76`; restore the bullet and the
      probe passes again — recorded verbatim in `evidence/revert-mutation.txt`.

## 6. Structural checks (in place of the build)

- [x] 6.1 Run `tools/check-todo-references.sh` — the four new `§134.NN` citations must resolve through the
      migration table (`| 134 | #69 |`) and the tracker marker. Verify: exit 0 with no unresolved citation.
- [x] 6.2 Run `tools/check-doc-routes.sh` and `openspec validate --all`. Verify: both exit 0; the section
      references the edited lines touch still resolve, and the change's delta validates. Record all three in
      `evidence/checks.txt`.
- [x] 6.3 Re-run the same four commands after the review's corrections (the §134.65 row's evidence, the probe's
      stated scope, the capture headers, the scenario's probe path): probe `entries=4 missing=0` with the four
      `STATED` lines; `tools/check-todo-references.sh` and `tools/check-doc-routes.sh` exit 0;
      `openspec validate --all` exit 0. Verify: appended under `## round-2 structural checks` in
      `evidence/checks.txt`, nothing red and nothing `FROM-CACHE` (nothing compiles).

## 7. Archive

- [x] 7.1 Archive the change. Verify: `openspec/changes/archive/2026-10-21-fix-uncarried-guardrail-entries/`
      exists with its `evidence/`, and `openspec/specs/documentation-ownership/spec.md` carries the new
      requirement and its scenario.
