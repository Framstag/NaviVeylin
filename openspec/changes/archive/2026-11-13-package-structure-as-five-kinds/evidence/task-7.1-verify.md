# Task 7.1 `Verify:` — narrowed wording as landed (pre-gate finding, item #873)

`tasks.md` 7.1 now reads (Verify clause):

> probe `tools/look.sh '^import ' --path core/src/main/java/com/naviveylin/core/port` = 3 imports / 0 android / 0 of ours (`items/873/port-imports.log`); `portImportsNoAndroidType`/`portImportsOnlyConcept` land with task 1.1; the favourite write-serialisation rule in `AGENTS.md` is preserved, and `FavoriteRepository`'s tests stay green.

What exists at this base:

- probe output → `items/873/port-imports.log` (3 imports, 0 `android`/`androidx`, 0 of `com.naviveylin` besides `concept`).
- the two cases are NOT run: the change's `:architecture` suite is task 1.1, unlanded — `settings.gradle.kts` has no `include(":architecture")`, `architecture/` holds only `structure.tsv`.
- write-serialisation preserved → `evidence/revert-check.md` (mutation red `expected:<[First, Second]> but was:<[First]>`, restore, green).
- `FavoriteRepository` tests → `tests="50" failures="0" errors="0"` (`evidence/revert-check-green.xml`).
