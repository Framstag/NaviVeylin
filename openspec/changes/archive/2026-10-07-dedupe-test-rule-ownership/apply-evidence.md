# Apply evidence — `dedupe-test-rule-ownership`

Session 2026-10-07 (after 22:50). Delta: `specs/build-test-gate/spec.md` (1 modified requirement
"Local and CI gate recipes agree", 1 added "A gate rule or measurement has one documented home").

**Shared-tree note.** A peer session was applying `fix-phone-map-layer-stack` in this tree throughout
(its artifacts at 22:42, its emulator screenshots `.pi/logs/fix-phone-map-layer-stack/*.png` after 22:50,
its change advanced 2/20 → 15/20 during this session). The owner chose to proceed in the same tree, so
`AGENTS.md` carries the peer's 18 uncommitted lines (a `pi-view-image`/libtui note and a "modal band" note)
next to this change's edits, and the peer's next `AGENTS.md` commit will include this change's lines. No
Gradle run was started by this change, so the rule-4 build-corruption case does not arise.

## Edits

| File | Change |
|---|---|
| `guidelines/Build.md` | maintenance rule (7-8) now names the mirror sites and the repair rule; the §4 revert-check bullet now prescribes the same forcing rule as §4's "How to force it" |
| `AGENTS.md` | iteration loop: the five restated measurements removed, section references added; the forcing flag corrected to `-PforceTests --no-build-cache`; classloader section reduced to the rule pointer + the consequence it owns |
| `guidelines/Design.md` | §11's classloader MUST reduced to the design obligation + `guidelines/Build.md` §6 |
| `openspec/config.yaml` | `rules.tasks` revert-check entry: `--rerun-tasks` → `-PforceTests --no-build-cache` (with the graph-wide case kept) |
| `.pi/skills/run-tests/SKILL.md` | the §4 evidence rules, the cache/timestamp checks, the sweep rule, the `-PnoCoverage`/`FROM-CACHE` numbers and the byte-identical classloader bullet replaced by section pointers — 13 751 B → 11 878 B |

## 1.2 — the owner is single inside its own document

```
$ grep -nE '[0-9]+ classes( /|,)' guidelines/Build.md
149:| `:app:testMobileDebugUnitTest` | 2m47s — 215 classes, 1635 tests, 0 failures |
150:| `:app:testAutomotiveDebugUnitTest` | 2m38s — 215 classes, 1635 tests, 0 failures |
161: … `:app` mobile 219 classes / 1666 tests, automotive 219 / 1666 …
276-278: the declared-cases stage (9 classes / 11.1 s) against the whole suite (220 classes / 252 s)
542-543, 575-577: the fork-budget and concurrency tables (49/516, 218/1649, 76/779)
713, 719: coverage baselines (662 classes; 2 classes JNI)
$ grep -cE '[0-9]+ classes' AGENTS.md
0
```

Every occurrence sits next to its own dated run or its own table. No measurement was moved.

## 2.1 / 2.2 / 2.4 — AGENTS.md and Design.md point at the owner

```
$ grep -nE '8m25s|[0-9]+ classes|~3 min|30-45 s|10-12 %|22m37s' AGENTS.md
(no output, exit 1)
```

The iteration loop now reads: the declared-stage cost and the single-flavor rule with
`(guidelines/Build.md §4)` / `§4, §6`; the gate is "forced on the test tasks with
`-PforceTests --no-build-cache` (`guidelines/Build.md` §2 records the baseline and every measured cost)".
The classloader section now reads: the normative rule is in `guidelines/Build.md` §6, this section owns the
consequence ("the stub binds to the first classloader that loads it … *already loaded in another
classloader*"), and `Design.md` §11 carries the design obligation plus the same §6 reference.

## 2.3 — the owner is complete on its own

`guidelines/Build.md` §6 still carries the stub path, the default-sandbox requirement, both forbidden
annotations and the full-suite failure mode (`grep` at line 505: `already loaded in another classloader`).
No sentence in §6 depends on `AGENTS.md` for its meaning.

## 3.1 / 3.2 — the machine-local skill

```
$ grep -rn -i -e 'cached run is not a run' -e 'already loaded in another classloader' guidelines/Build.md
289:- **A cached run is not a run**: `BUILD SUCCESSFUL in 3s` with `UP-TO-DATE` / `FROM-CACHE`
505:  cause "already loaded in another classloader" failures in full-suite runs.
$ wc -c .pi/skills/run-tests/SKILL.md      # 13751 before
11878 .pi/skills/run-tests/SKILL.md
$ grep -o 'guidelines/Build.md. §[0-9]*' .pi/skills/run-tests/SKILL.md | sort | uniq -c
      1 §2      4 §4      1 §6      2 §7
```

Both removed statements are found in their owning section, and the skill names an owning section for every
rule it still needs. **This edit is machine-local** (`.pi/` is gitignored, `AGENTS.md` "Agent iteration
loop"); it is housekeeping and is not evidence that the drift is fixed — the durable halves are the owner
statement in `Build.md`, its named mirror list and the spec requirement.

## 4.1 / 4.2 — the change-artifact guidance

```
$ openspec instructions tasks --change <any> --json | jq -r '.rules[]' | grep -n forceTests
11: Every new invariant … re-run green forced with `-PforceTests --no-build-cache` (`--rerun-tasks` only
    when the change touched more than the test sources; the gate's own rule is `guidelines/Build.md` §2/§4)

$ awk '/^  (apply|archive):/…' openspec/config.yaml          # CI hygiene, unquoted-guidance check
(no output)                                                   # pass
$ openspec doctor                                             # exit 0, root ok
$ openspec instructions apply|archive … | jq '.operationGuidance|length'
apply 22   archive 17                                         # quoting intact, nothing dropped
```

Note on task 4.1's verify line: the edited entry lives in `rules.tasks`, so it is served by
`openspec instructions tasks … | jq -r '.rules[]'`, **not** by `.operationGuidance` of the apply operation
(that array holds `operations.apply.guidance` and merely stayed non-empty as the quoting proof).

## 5.1 — scenario → check

| scenario (delta) | check | result |
|---|---|---|
| CI task set | `.github/workflows/build.yml:306` = `test -PforceTests --no-build-cache` vs `Build.md` §3 | unchanged, agrees |
| CI reports flavors | step exists (line 305 + the tallies step after it) | unchanged |
| divergence is a defect | `grep -rn 'rerun-tasks' guidelines/Build.md openspec/config.yaml` | one rule: the graph-wide cases only (§4 lines 135-139, 291-297, §2's record); `config.yaml:66` now matches |
| generated tasks forcing | the `jq .rules[]` check above | passes |
| third prescription site | 4.2 | passes (awk clean, guidance arrays non-empty) |
| measurement quoted once | 1.2 + 2.1 greps | passes |
| restated rule removed | 2.4 + 3.1 | passes |
| owner reachable | 2.2 + 3.2 | passes |

## 5.2 — revert-check (failure first, then green)

```
$ md5sum AGENTS.md   (backup)  d784472f9f2a5df46ea1ef5d28b704ba
$ sed -i 's|per edit; the full|per edit (215 classes / 1635 tests); the full|' AGENTS.md
$ grep -nE '[0-9]+ classes' AGENTS.md
256:   Otherwise: … --tests "com.naviveylin.ui.route.*"` per edit (215 classes / 1635 tests); the full
exit=0                      <- the violation is reported
$ cp <backup> AGENTS.md
$ grep -nE '[0-9]+ classes|8m25s' AGENTS.md
(no output) exit=1          <- green again, and md5 matches the pre-mutation hash
```

One mutation only. The check is a `grep`, so "forced green" is the re-run of that command; no build cache
is involved.

## 5.3 / 5.4 — nothing is owed, and why

Files this change edited (proved by mtime, `find … -newermt '2026-10-07 22:50'`):
`AGENTS.md`, `guidelines/Build.md`, `guidelines/Design.md`, `openspec/config.yaml`,
`.pi/skills/run-tests/SKILL.md` — markdown, YAML and a gitignored skill. No source, test, build script,
manifest, resource, workflow, native or JNI file. The remaining dirty paths in `git status` belong to the
concurrent peer session and its pre-existing work, not to this change.

Stated explicitly so no later reader infers otherwise: **(a)** no on-device or emulator evidence is owed —
no UI, rendering, car-surface, template or lifecycle behaviour changes, so no `pixel-check`/`device-check`
step applies; **(b)** no new unit test is owed — the artifact under test would be documentation, and the
delta's scenarios are `grep`-checkable instead.

## Added scope (surfaced, not silent)

1. **Four more restated measurements than the tasks named.** The iteration loop also carried `~3 min` (the
   single-class cost), `~30-45 s` (the declared-stage cost), `10-12 %` (the Kover agent) and `22m37s` (a
   foreign edit's effect on a gate). All are owned in `Build.md` §4/§7 — except `22m37s`, which no document
   owns, so the claim stays qualitative ("inflated a measured gate many times over") instead of quoting a
   number without a home. Same requirement, same file, so it was done here rather than split out.
2. **A second divergence site found inside the owner.** `Build.md` §4's revert-check bullet prescribed
   `--rerun-tasks` while §4's own "How to force it" bullet prescribes `-PforceTests --no-build-cache`.
   Aligned to the cheaper flag (a revert-check is not a completion gate). Required by the delta's
   "every site that prescribes the gate … same forcing rule"; not in the proposal's edit list.
3. **Residual, not fixed:** `TODO.md:981` still restates `8m25s` → `22m37s` ("a foreign edit mid-run inflated a
   measured 8m25s gate to 22m37s"). `TODO.md` is not in this change's declared files and is dirty from the
   peer session. The requirement's scope is "the repository's documents", so this needs a follow-up edit or
   a decision that backlog history is out of scope.

## 6.1 / 6.2 — gates

```
$ openspec validate dedupe-test-rule-ownership --strict     exit 0, valid
$ openspec doctor                                            exit 0, OpenSpec root ok
$ git status --porcelain .github/                            (no output) — no CI drift guard added
```
