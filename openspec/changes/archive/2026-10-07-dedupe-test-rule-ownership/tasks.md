# Tasks

Delta under implementation: `specs/build-test-gate/spec.md` (1 modified requirement
"Local and CI gate recipes agree", 1 added requirement "A gate rule or measurement has one documented
home"). Every task names the scenario it serves; the mapping is enumerated in §5.1.

Carried over from the design: this change edits four text files and nothing else. It owes **no** compile
evidence and **no** unit-test evidence, and that is proven mechanically rather than asserted — see 5.3.

## 1. Establish the owner and its mirror list (`guidelines/Build.md`)

- [x] 1.1 Name the mirror sites in the maintenance rule (`guidelines/Build.md:7-8`): `AGENTS.md`, the
      `openspec/config.yaml` tasks/archive guidance, and the build/test skills, each with the note that a
      restated rule or measurement is repaired by pointing at the owning section. **Spec**:
      `build-test-gate` — "A gate rule or measurement has one documented home" /
      "A document that needs a rule can reach the owner". **Verify**: read the amended rule back; it names
      all four sites.
- [x] 1.2 Confirm the owner is single inside its own document: the four measured suite sizes
      (`215 classes / 1635 tests`, `219 / 1666`, `218 / 1649`, `221 / 1719`) each appear where their dated run
      is recorded (§2 baseline, §2 after-change, §6 fork table, §6 gate row) and nowhere else in the file.
      **Spec**: same requirement / "A suite measurement is quoted in exactly one document". **Verify**:
      `grep -nE '[0-9]+ classes( /|,)' guidelines/Build.md` — every hit sits next to its date and its run.

## 2. `AGENTS.md` points at the owner instead of restating it

- [x] 2.1 Remove the two restated numbers from the iteration loop (`AGENTS.md:257` "measured 8m25s on
      2026-10-04", `AGENTS.md:263` "the same 215 classes (~3 minutes per gate)"), keep the rule they support
      (one flavor's suite per iteration, the second owed at completion) and reference
      `guidelines/Build.md` §2/§6. **Spec**: "A suite measurement is quoted in exactly one document".
      **Verify**: `grep -nE '8m25s|[0-9]+ classes' AGENTS.md` returns nothing; the iteration rule still reads
      as a rule with a section reference.
- [x] 2.2 Re-word the classloader section (`AGENTS.md:383-406`) so the normative rule ("any test class that
      instantiates `FakeOSMScoutClient` … MUST run under `@RunWith(RobolectricTestRunner::class)` with the
      default sandbox config") is a pointer to `guidelines/Build.md` §6, while the project facts it owns stay:
      the two committed host stubs, the test source sets, the `libosmscout_client_javad` fallback name, and the
      consequence ("the stub binds to the first classloader that loads it, so a second sandbox config in the
      same JVM fails with *already loaded in another classloader*"). **Spec**: "A document that needs a rule
      can reach the owner". **Verify**: `guidelines/Build.md` §6 still carries the full rule verbatim (2.3);
      `AGENTS.md` carries the facts plus the §6 reference.
- [x] 2.3 Leave `guidelines/Build.md:496-516` (the §6 test constraints) untouched as the normative copy —
      confirm it is complete on its own: the stub path, the default-sandbox requirement, both forbidden
      annotations, and the full-suite failure mode. **Spec**: same. **Verify**: read back; no sentence in §6
      depends on `AGENTS.md` for its meaning.
- [x] 2.4 Check `guidelines/Design.md:533` (the one-clause trap reference in §11 "Testing") still reads as a
      trap and not as a second normative statement, and add the section reference if it lacks one.
      **Spec**: "A restated rule is removed rather than maintained". **Verify**: the clause either carries the
      §6 reference or is demonstrably not a restatement (it points at the trap, which §6 owns).

## 3. Dedupe the machine-local skill (`.pi/skills/run-tests/SKILL.md`)

- [x] 3.1 Replace the blocks that mirror `guidelines/Build.md` §4 and §6 — the evidence/verdict rules, the
      cache-hit and timestamp checks, and the byte-identical classloader bullet — with section references,
      keeping the skill's own procedure (status message, foreground redirect, the liveness probe, the report
      commands) intact. **Spec**: "A restated rule is removed rather than maintained" / "including inside
      files that are not tracked by the repository". **Verify**: every removed block is found in its owning
      section (`grep` for two distinctive sentences: "a cached run is not a run", "already loaded in another
      classloader"); the skill still names the section for every rule it needs.
- [x] 3.2 Record in the task's completion note that this edit is machine-local (`.pi/` is gitignored, per
      `AGENTS.md:291-294`) and is therefore housekeeping, never evidence that the drift is fixed. **Spec**:
      "A document that needs a rule can reach the owner". **Verify**: the note exists and does not claim
      durability.

## 4. Bring the change-artifact guidance onto the documented forcing rule (`openspec/config.yaml`)

- [x] 4.1 In `rules.tasks`, amend the revert-check entry so its green half prescribes
      `-PforceTests --no-build-cache` (and `--rerun-tasks` only when the change touched more than the test
      sources), matching `guidelines/Build.md` §2/§4/§6. Keep the entry quoted — an unquoted `KEY: text` entry
      parses as a mapping and the CLI silently drops that operation's guidance. **Spec**:
      "Local and CI gate recipes agree" / "Generated change tasks prescribe the documented forcing rule".
      **Verify**: `openspec doctor` clean; `openspec instructions apply --change <any> --json | jq -r
      '.operationGuidance[]' | grep -n 'forceTests'` shows the rule and the guidance array is not empty.
- [x] 4.2 Run the CI hygiene step's own commands locally so the edit cannot fail the workflow: the
      unquoted-guidance `awk` from `.github/workflows/build.yml:115` prints nothing, and the tasks-marker
      check still passes for this change's `tasks.md`. **Spec**: "A third prescription site is covered by this
      requirement". **Verify**: both commands exit 0 with no output.

## 5. Verify the two requirements, and falsify the new one

- [x] 5.1 Enumerate the delta's scenarios and name the check that exercises each — a table in the change
      record, scenario → check, with no scenario left unnamed:

      | scenario (delta) | check |
      |---|---|
      | CI task set | local grep of `:github/workflows/build.yml:305` vs `Build.md` §3 — no edit, confirm unchanged |
      | CI reports flavors | unchanged; confirm the step still exists |
      | divergence is a defect | 4.1 + `grep -rn 'rerun-tasks' guidelines/Build.md openspec/config.yaml` shows one rule, not two |
      | generated tasks forcing | 4.1's `jq` check |
      | third prescription site | 4.2 |
      | measurement quoted once | 1.2 + 2.1 greps |
      | restated rule removed | 2.4 + 3.1 |
      | owner reachable | 2.2 + 3.2 |

      **Spec**: both requirements. **Verify**: every row above has been run and its output quoted.
- [x] 5.2 **Revert-check** for the new invariant ("one documented home"): mutate by pasting
      `215 classes / 1635 tests` back into the iteration-loop bullet in `AGENTS.md:263`; the named check
      `grep -nE '[0-9]+ classes' AGENTS.md` MUST report it (a hit outside `guidelines/Build.md` is the
      violation); restore the file; re-run the same `grep` and it must be empty again. One mutation only.
      **Spec**: "A suite measurement is quoted in exactly one document". **Verify**: both grep outputs quoted
      in the change record — the failure first, then the green. This check is a `grep`, not a test task, so
      "forced green" is the re-run of that command; no build cache is involved.
- [x] 5.3 Prove that no Gradle gate is owed, mechanically: `git diff --name-only` (plus
      `git status --porcelain` for the new untracked change files) lists only `*.md`, `*.yaml` and `.pi/**`
      paths — no source, test, build script, manifest, resource, workflow or native file. **Spec**: both
      requirements (the change alters no behaviour). **Verify**: paste the file list into the change record;
      if any path outside those kinds appears, the change has grown and a compile/test gate becomes owed.
- [x] 5.4 State the two things this change explicitly does not owe, so no later reader infers them:
      (a) no on-device or emulator evidence — no UI, rendering, car-surface, template or lifecycle behaviour
      changes, so no `pixel-check`/`device-check` step applies; (b) no new unit test — the artifact under test
      would be documentation, and the delta's scenarios are `grep`-checkable instead. **Spec**: both
      requirements. **Verify**: the two sentences exist in the change record.

## 6. Close out

- [x] 6.1 Run the OpenSpec gates: `openspec validate dedupe-test-rule-ownership --strict` (the added
      requirement's description stays within the length budget) and `openspec doctor`. **Verify**: both
      exit 0; quote the output.
- [x] 6.2 Do not add the deferred CI drift guard in this change. Leave Open Question 1 of `design.md` (the
      pattern and its allow-list) as the hook for a later change; the requirement it would enforce now exists.
      **Verify**: `git status --porcelain .github/` is empty.
