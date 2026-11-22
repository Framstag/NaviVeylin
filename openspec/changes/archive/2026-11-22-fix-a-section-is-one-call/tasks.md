# Tasks — two modes on the wrapper that exists

Every step is a step of the plan; the gate is deferred, so this change archives after the gate child's pass and
nothing here is a step that child takes.

## 1. The red case, before the wrapper moves

- [x] Add `look#aSectionIsPrintedAndTheOtherSectionsAreNot` to `tools/loop/tests/loop-cases.sh` — the case list
      (`:389`), the dispatch (`:1216`), and a body whose first line declares `# files: tools/look.sh`. Three sections
      and a sub-section in one document, a fourth in a second document; it asserts one section prints with its
      heading and body, no line of either neighbour, no line of the other document, the count and the file the first
      line names, that a `###` heading is not a section, that a pattern matching no `## ` heading is exit 1 naming
      the file and the pattern, that `--path` is required and that a directory is refused, and that a section the
      bound cuts arrives cut and named.
- [x] Add `look#aCaseIsPrintedByItsName` beside it (`:390`, `:1217`, body `:20568`): a two-case suite, one case read
      with its `case_` prefix and one without, the `}` that closes it, the count, no other case and no other file, a
      name the suite does not hold as exit 1, `--path` required, a directory refused, a value that is not a case name
      refused, and both modes in one call refused.
- [x] Verify the two cases are red on the base: with `tools/look.sh` put back to `929ea147`'s for the length of the
      run and restored after it, `bash tools/loop/tests/loop-selftest.sh --case 'look#*'` →
      `FAIL look#aSectionIsPrintedAndTheOtherSectionsAreNot` and `FAIL look#aCaseIsPrintedByItsName`, both on
      `look: unknown argument` (base `tools/look.sh:57`), the three shipped `look` cases `PASS`, `2 of 5 cases
      failed`, exit 1. Log: `evidence/red-on-base.log`; tally `evidence/red-on-base.xml`.

## 2. The wrapper's two modes

- [x] `tools/look.sh` — `--section <regex>` and `--case <name>`: each stores its value in the same `pattern` and a
      `mode` (`:70-74`), refuses a mode without `--path`, a directory, `--files`/`--context`, both modes at once and a
      `--case` value that is not a case name (`:96-112`, `:130-133`), picks the range printer before the walk
      (`:156-183`), names a range the file does not hold as exit 1 (`:184-193`), counts what it printed
      (`:211-222`) and cuts it at the recorded result bound like a walk's answer (`:235-247`).
- [x] `tools/look.sh` — the header documents both modes beside the pattern (`:23-36`) and the bare-usage line names
      them too (`:84`).
- [x] Verify: `bash tools/loop/tests/loop-selftest.sh --case 'look#*'` → all five `PASS`, including the two that were
      red in step 1 (`evidence/green.log`).

## 3. The declaration and the two mutations

- [x] `tools/loop/tests/loop-mutations.sh` — `1005.1` neutralises the end of a section at the document's next heading
      of its own level (`if (on) on = 0` → `if (on) on = on`), and `1005.2` makes every line open a case
      (`s|^    $0 ~ ENVIRON["pat"] { on = 1; hits|    { on = 1; hits|`) — `:1542-1547`.
- [x] Verify: `bash tools/loop/tests/loop-mutations.sh --only '1005.1 1005.2'` → both `RED`, each on its own case's
      count assertion (`LOOK: 2 sections` / `LOOK: 2 cases`), both restored green again, `red-when-mutated: 2 ·
      not-noticed: 0 · restore-failures: 0`, exit 0 (`evidence/mutations.log`).
- [x] Verify the declaration the gate's first step reads:
      `bash tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` → `PASS`
      (`evidence/declaration.log`).

## 4. The route a child is given

- [x] `tools/loop/prompts/worker.md:49-54` — the guideline paragraph names the section call beside the section-list
      call, so the prompt says how to read the one section its own routing names.
- [x] `guidelines/Backlog.recipes.md:162` — the paragraph that states what the wrapper answers gains the two
      ranges, their unit, and the item's honest bound on the gain ("calls, not characters").
- [x] Verify: `bash tools/loop/tests/loop-selftest.sh --case prompt#thePhasePromptsCarryTheirOwnRoute` → `PASS`
      (`evidence/green.log`).

## 5. The capability delta

- [x] `specs/agent-issue-loop/spec.md` — one `MODIFIED` requirement, "A child is started with a tree search that
      runs", carrying its three shipped scenarios and the two this change adds (`look#aSectionIsPrintedAndTheOther-
      SectionsAreNot`, `look#aCaseIsPrintedByItsName`), each naming exactly one case.
- [x] Verify: `openspec validate fix-a-section-is-one-call --strict` → `Change 'fix-a-section-is-one-call' is valid`
      (`evidence/structural.log`).

## 6. The checks this delta owes

- [x] No compiled file is touched, so no build stands in for one: `bash -n tools/look.sh`,
      `bash -n tools/loop/tests/loop-cases.sh`, `bash -n tools/loop/tests/loop-mutations.sh`,
      `bash tools/check-case-names.sh`, `bash tools/check-doc-values.sh` and `bash tools/check-doc-routes.sh` — all
      green (`evidence/structural.log`).
- [x] `./tools/loop/measure.sh --base 929ea147 --change fix-a-section-is-one-call` → the change's own block, quoted at
      `evidence/measure.log`: 5 paths, `LOOP-MEASURE-SUITE cases=533`, the five scenarios counted against the five
      cases that name them.
- [x] The red reading and the two mutations are copied into this change's `evidence/` as they were produced, not
      rewritten: `red-on-base.log`, `red-on-base.xml`, `mutations.log`.
