# Tasks

Parent: `proposal.md`. This change carries `skip_specs: true` (no spec-level behaviour changes), so every task
traces to a `## What Changes` bullet or an `## Impact` row of the proposal instead of to a spec requirement.
`design.md` is deliberately skipped: the artifact's own condition (cross-cutting change, new dependency, or
ambiguity needing a technical decision) does not apply — the one decision, the allowlist shape, is measured and
recorded in the proposal.

## 1. The ignore rule

- [x] 1.1 Replace the blanket `.pi/` (`.gitignore:14`) with the allowlist from proposal §What Changes bullet 1 —
      `.pi/*`, `!.pi/skills/`, `.pi/skills/*`, `!.pi/skills/*/`, `.pi/skills/openspec-*/`, plus the two
      `!` lines for `openspec-open-changes/` and `openspec-proof-gap-audit/` — and put the measured failing
      variant in a comment above it.
      Verify: `git check-ignore -v .pi/skills/openspec-apply-change/SKILL.md .pi/logs/a.log .pi/settings.json`
      still names `.gitignore` for all three, and `git check-ignore -v .pi/skills/build-app/SKILL.md` prints
      nothing and exits 1. Paste both outputs into the change report.
- [x] 1.2 Falsify the rule once (revert-check for the new invariant in proposal §What Changes bullet 2). Copy
      `.gitignore`, insert `.pi/` above the allowlist, and confirm the named case fails: the skill directory is
      invisible again, i.e. `git check-ignore -v .pi/skills/build-app/SKILL.md` now reports `.pi/`. Restore the
      allowlist and confirm `git check-ignore .pi/skills/build-app/SKILL.md` exits 1 with no output once more.
      Record all three outputs with the command that produced them.
- [x] 1.3 Stage exactly the 15 hand-written skill directories, by name, never `git add .pi`.
      Verify: `git ls-files .pi | wc -l` reports 20, and
      `git ls-files .pi | grep openspec-` prints exactly the three paths
      `openspec-open-changes/SKILL.md`, `openspec-open-changes/scripts/open-changes.sh`,
      `openspec-proof-gap-audit/SKILL.md` — no CLI-generated skill. Run the same two commands in the report.

## 2. Documentation that the rule makes false

- [x] 2.1 Rewrite the `.pi/` is gitignored paragraph in `AGENTS.md` (proposal §Impact row 2) into the
      tracked/untracked split: hand-written skills versioned with the project, OpenSpec-generated skills never.
      Verify: read the paragraph back and run `grep -n 'gitignored' AGENTS.md`, which must no longer name the
      skills.
- [x] 2.2 Rewrite the per-skill location sentence in `guidelines/Build.md` §1 "Skills" (proposal §Impact row 3)
      into the split, state what makes a hand-written skill with the `openspec-` prefix visible (the `!` line),
      and give the read-back command.
      Verify: the section names the tracked set, the prefix exception and the command, and running
      `git ls-files .pi | wc -l` prints 20.
- [x] 2.3 Correct `guidelines/Build.md` §11: the `.pi/` is gitignored sentence becomes the split, and the
      "delete it before committing" advice is scoped to the scratch script rather than to the now-tracked
      `device-check` directory.
      Verify: `grep -n 'gitignored' guidelines/Build.md` shows only the release-version-state and
      `ki_processing_failures.log` occurrences, and §11 reads correctly with `device-check` tracked.
- [x] 2.4 Correct the `compose-geometry` "machine-local" mention in `guidelines/Build.md` §11 (proposal
      §Impact row 3). Verify: `grep -n 'machine-local' guidelines/Build.md` shows only the
      `release-version.properties` occurrence.
- [x] 2.5 Narrow the five skill tails that call the skill itself gitignored — `build-app/SKILL.md:56`,
      `release-build/SKILL.md:46`, `run-tests/SKILL.md:153`, `native-bridge-signature-change/SKILL.md:115`,
      `update-to-current-libosmscout-master/SKILL.md:200` — so each states that the tracked copy is the
      project's source and the `~/.pi/agent/skills/` copy is for cross-project use, without claiming a
      precedence Pi does not document.
      Verify: `grep -rn 'which is gitignored\|(gitignored)' .pi/skills/*/SKILL.md` returns nothing for a
      skill-location sentence, and each of the five tails still names its user-level copy path.

## 3. Landing

- [x] 3.1 Prove the staged set is only this change's files. Verify:
      `git diff --cached --name-only` lists nothing outside `.gitignore`, `AGENTS.md`,
      `guidelines/Build.md` and `.pi/skills/**`, and in particular carries none of the in-flight work —
      `auto/src/test/java/com/naviveylin/auto/AutoMapRendererRenderCadenceTest.kt`,
      `openspec/specs/adaptive-zoom/spec.md`, `tools/feature-list/**`, `tools/gen-feature-list.sh`. Paste the
      list.
- [x] 3.2 Record that this change cannot exercise the compile and test gates, and why: no Kotlin, Java, C++,
      Gradle or manifest file changed, so the gate has nothing to say about it. Verify:
      `git diff --cached --name-only | grep -E '\.(kt|java|cpp|h|kts)$|AndroidManifest'` returns nothing, and the
      change report states the omission rather than implying a green gate.
- [x] 3.3 Read the tracked set back as a clone would receive it, after the commit, and confirm the skill
      directories are complete. Verify: `git ls-tree -r HEAD --name-only -- .pi | wc -l` reports 20, all five
      shell scripts are present in that list, and `git ls-tree -r HEAD --name-only -- .pi | grep openspec-`
      prints the three hand-written paths and no generated skill.
- [x] 3.4 Commit the staged set with a subject naming `track-custom-skills`.
      Verify: `git show --stat HEAD` lists exactly the expected files, and `git status --porcelain` still shows
      the unrelated in-flight edits the commit left alone.
- [x] 3.5 Push to `upstream/main`. Verify: `git log --oneline -1 upstream/main` matches `HEAD`, and
      `git status -sb` reports no ahead/behind for `main`.

## Workflow follow-up

- Archive the change after it lands: `openspec archive track-custom-skills`. With `skip_specs: true` nothing is
  synced into `openspec/specs/`; the change directory moves under `openspec/changes/archive/`, which
  `.gitignore:57` un-ignores, so commit that move separately (the repository tracks 241 archived changes).
- If the split ever needs enforcement, wire a `git ls-files` read-back into `.github/workflows/build.yml`
  beside the `tools/check-doc-routes.sh` step (line 131) — deferred out of this change by the proposal.
