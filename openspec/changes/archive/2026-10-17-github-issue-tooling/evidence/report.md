# Change report — `github-issue-tooling`

The tracker surface: a label taxonomy, a gate, identity, closure, and the one project profile that makes the
whole thing portable. Everything below is a record of a run, not a claim about one.

**Gate:** `bash tools/issue-tooling-selftest.sh` → **30 of 30 cases pass** on JDK 27, offline, no token
(`evidence/selftest.txt`, which also shows the same command under `env -i PATH="$PATH"` — what the CI step
runs). **12 of 12 revert checks** falsified their named case and restored green (`evidence/revert-checks.txt`).

## What landed

New:

| path | what it is |
|---|---|
| `tools/tooling.json` | **the project profile** — repo, `allowedAuthors`, `requiredLabels`, `reservedLabels`, the seven namespaces, the source renames, the `status`→label rules, the input bounds. The only file a second project adapts |
| `tools/issues/lib.sh` | exit-code contract, discovery (working tree → root, `gh`/git → repo, `<root>/tools/tooling.json` → profile), the gate and label checks, the core runner |
| `tools/issues/labels.sh` | creates/refreshes the taxonomy, `--dry-run` by default, `--apply` to create |
| `tools/issues/create.sh`, `update.sh`, `close.sh`, `list.sh`, `policy.sh` | the verbs; every write path refuses a reserved label and a value outside the closed vocabulary before touching `gh` |
| `tools/issues/import.sh` | the generic reconciler: findings JSON → issues, idempotent on the source id, `--dry-run` by default |
| `tools/issues/import/todo-md.sh` | the **source adapter** for this project's prose format (with `core/TodoAdapter.java`) |
| `tools/issues/core/{Json,Profile,Taxonomy,Fingerprint,TodoSections,Policy,WorkPackage,TodoAdapter,Cases}.java` | the decisions, as single-file Java (`java <file>.java`), not a Gradle source set |
| `tools/issues/fixtures/**` | the stub `gh` (reads answered from fixtures, writes refused unless `GH_STUB_RECORD` names a log), its scenarios, and the status-shape fixtures |
| `tools/issue-tooling-selftest.sh`, `tools/issue-tooling-cases.sh` | the gate: 11 core cases + 19 shell cases |
| `.pi/skills/{create-issue,update-issue,close-issue,list-issues}/SKILL.md` | the four operations, for agents |
| `openspec/changes/github-issue-tooling/evidence/**` | this run's evidence |

Modified: `.github/workflows/build.yml` (one step, `Check issue tooling`), `tools/feature-list/specs.json`
(the `issue-backlog` classification line), `guidelines/Backlog.md` (§1's row and status paragraph, §3's
namespaces, §5's reservation bullet, two §12 rows).

## Evidence

| file | what it proves |
|---|---|
| `evidence/selftest.txt` | the gate: 30/30, plus the empty-environment run |
| `evidence/revert-checks.txt` | all 12 mutations turned their named case red; every restore left it green |
| `evidence/todo-dry-run.json`, `todo-dry-run.txt` | the real dry run over `TODO.md`: 137 sections → 137 findings → 137 previews, 0 skipped, 0 created, the two renames, the label histogram, the status histogram, `agent/eligible: 0`, and the preflight refusal |
| `evidence/tracker-state.txt` | the tracker at the same moment: 0 issues, 0 comments, 45 labels — with the qualification below |
| `evidence/traceability.md` | 22 scenarios, 22 rows, each naming its case and task |
| `evidence/collect.sh`, `evidence/revert-checks.sh`, `evidence/skill-commands.sh` | how the machine-generated files are reproduced |
| `evidence/skill-commands.txt` | tasks 6.1-6.4: every command the four skills document, run as documented against the stub `gh` — 33 invocations, each with its documented exit code |
| `evidence/classification.txt` | task 8.3: the gate green as shipped, and red-and-naming-`issue-backlog` with this change's delta installed and the line removed |
| `evidence/touched-files.txt` | tasks 6.5 and 8.2: the `git status` output for the files the plan names as untouched, and for the Gradle source sets |

Classification (task 8.3): `bash tools/gen-feature-list.sh --check-classification` exits 0. Because the gate
names only specs already in `openspec/specs/`, the load-bearing check copies this change's delta into a
throwaway root and removes the line — the same command then fails naming `issue-backlog`. As shipped it also
reports `stale: 1` for that line (the spec is not shipped until this change archives); the gate exits 0 with
the warning, and the line becomes live at archive.

## Exclusions, stated rather than implied (task 8.2)

No Gradle build and no test suite was run: `tools/issues/**` is outside every Gradle source set, and no
module, manifest, resource, native file or Gradle configuration was touched. `./gradlew` was never invoked.
`git status --short` lists nothing under `app/`, `auto/`, `core/`, `osmscout-client-java/` or `buildSrc/`.
No device or pixel measurement applies: this change produces no UI and nothing is read on a screen.

Task 6.5: `AGENTS.md`, `guidelines/*` (except the one section set this change makes stale), `openspec/config.yaml`
and the 12 existing skills are deliberately **not** updated — listing the four new skills in the documentation
map is change 2's task, and until then no session is pointed at an empty tracker.

## Deviations, additions and corrections

1. **The plan drifted from the tree, and the operator chose to amend the plan.** `TODO.md` now holds **137**
   sections (the plan said 136), class counts are improvement 77 / bug 51 / feature 9, and §149 carries a
   seventh status shape, `retired — <date>: <reason>`, which the plan's taxonomy did not cover. The
   amendments: `status/retired` added to the profile's status namespace and to design D5's mapping table,
   `open (unproven on a device)` → `status/unverified` recorded in both, the counts updated in proposal and
   design, and tasks 2.2/5.2/5.4 changed from a hard-coded 136 to the file-derived count. No entry is closed
   at import: §149's issue stays open and carries `status/retired`.
2. **A probe script wrote to the public tracker.** While debugging the self-test I ran
   `tools/issues/labels.sh --apply` **without the stub `gh` on PATH**, so it created the 36 taxonomy labels on
   `Framstag/NaviVeylin` (`class/*` 3, `category/*` 14, `status/*` 7, `source/*` 4, `agent/*` 4, `prio/*` 4).
   No issue, comment or pull request was created. The operator decided to keep the labels and to qualify
   task 5.5's "only the 9 defaults" expectation rather than delete them; that is what
   `evidence/tracker-state.txt` records. The dry run above changed nothing.
3. **The source adapter is two files, not one.** `import/todo-md.sh` is the wrapper and `core/TodoAdapter.java`
   is the mapping; the grammar itself is `core/TodoSections.java`. The design named a single adapter file; the
   split is what keeps the reconciler free of the format (`grep -nE '^## |\*\*id:\*\*' import.sh` prints
   nothing, asserted by `adapter#emitsTheSameFindingsAsTheDryRun`).
4. **Six core files beyond the design's three.** `Json.java` is a small JSON reader, because the JDK has none
   and the core must not depend on a build system; `Profile.java` reads the project profile; `Taxonomy.java`
   and `WorkPackage.java` are the mapping and the data-block renderer; `Cases.java` is the case runner. Also
   `tools/issue-tooling-cases.sh`, so the shell cases live beside the runner rather than inside it.
5. **Fingerprint matching is on a body marker.** `create.sh` writes `<!-- fingerprint: <sha256> -->` and the
   live dedup matches that marker; recomputing an existing issue's fingerprint from its prose would make the
   check depend on prose formatting. The design said only "matches in the Java core", so this fixes the source
   of the comparison rather than changing it.
6. **The reserved-label check has one guarded point per write path, and three routes.** The finding's own
   labels array, `--label` and `--status` all reach one check. `--status agent/eligible` is refused by the
   vocabulary rather than by the reservation (a namespaced string is not a status value); the case asserts
   exit 3 for all three routes and names which reason applies to which.
7. **The portability check landed, in the form this change can prove.** `lib#noProjectValueInTheEngine` fails
   when an engine file names a project value — the repository/actor names by literal, and every `category/`
   value by reading the profile's vocabulary, so the check cannot become a stale hand-written list. It
   excludes `core/Cases.java`, which is a test driver and necessarily carries fixture expectations.
   `tooling-portability` (change 6) still owns the synthetic foreign project that proves the profile seam in a
   project that differs; the design's risk entry said this property was unenforced until then, and it now has
   the weaker, local form.
8. **Two cases were mutation-blind and were fixed by this change's own revert-check pass** — which is the
   point of running it: `Policy#gateMatrix` derived the allowlisted author from the profile instead of from
   the identity the stub reports, and `WorkPackage#oversizedBodyRefuses` built its fixture as
   `maxBodyBytes + 1`, so raising the bound could never falsify it. Both are now red under their mutation
   (7.1, 7.5 in `evidence/revert-checks.txt`), and `policy#preflightRefusesUnknownLogin` gained the matching
   "the allowlisted identity still passes" assertion.

## Rollback

Delete `tools/issues/**`, `tools/issue-tooling-selftest.sh`, `tools/issue-tooling-cases.sh`,
`tools/tooling.json`, the four `.pi/skills/*-issue/` directories, the one CI step, the classification line and
the spec delta. The tracker keeps the 36 labels it now carries (the operator's decision); no issue, comment or
label created by the tooling's own runs exists, because every run was a dry run and every write path in the
self-test goes through the stub.
