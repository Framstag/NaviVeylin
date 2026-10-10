---
name: update-to-current-libosmscout-master
description: Updates the libosmscout git submodule (app/src/main/cpp/libosmscout) to the current upstream master commit, merges upstream master into the NaviVeylin-local branch naviveylin-local, pushes that branch to origin, and commits the bumped submodule pointer in the parent NaviVeylin repo. Use when asked to update/bump/sync libosmscout to current master, refresh naviveylin-local, or pull upstream changes into the map rendering stack.
metadata:
  submodule: app/src/main/cpp/libosmscout
  branch: naviveylin-local
  upstream: https://github.com/Framstag/libosmscout
  pull-request: "https://github.com/Framstag/libosmscout/pull/1773 (naviveylin-local → master)"
  parent-commit-style: "Update libosmscout submodule to master (merge into naviveylin-local)"
---

# Update libosmscout submodule

## Background

- The libosmscout submodule (`app/src/main/cpp/libosmscout`) is pinned to a commit in the parent repo, not to a branch. `git submodule status` shows the pinned SHA in parentheses.
- `naviveylin-local` is the NaviVeylin working branch **inside the submodule**. It carries NaviVeylin-specific commits (JNI layer, Android build fixes) on top of upstream master, folded in via merge commits.
- The branch is pushed to origin (`Framstag/libosmscout`) so the new pinned SHA stays fetchable by anyone running `git submodule update`.
- Workflow: fetch upstream master → merge into `naviveylin-local` → push branch → bump parent pointer → commit.
- **PR #1773 is the branch's PR into `master`, and its CI is the authoritative build check** — it runs configurations this repo never builds locally (see *Configuration parity*). A green local Android build is not evidence that the branch is healthy.

## Usage

Fastest path — run the bundled script from the repo root:

```bash
./.pi/skills/update-to-current-libosmscout-master/scripts/update-libosmscout.sh [--check-non-marisa]
```

The script does full preflight checks (clean trees, initialized submodule), fails safely on merge conflicts (aborts the merge, no forced pushes), verifies the remote branch has not moved before pushing, and prints a summary plus the follow-up checklist. `--check-non-marisa` additionally compiles the JNI translation unit in the non-marisa configuration (see *Configuration parity*) by temporarily forcing `#undef OSMSCOUT_HAVE_LIB_MARISA`, then restores the file from a backup.

The preflight tolerates the **submodule gitlink as the only parent change** — that is the expected state when re-running after a conflicted merge: resolve and commit the merge inside the submodule, then re-run the script to do the push, the parity check, and the parent bump. Any other parent change still blocks the run.

Prefer the script unless manual control is needed.

## Manual steps (equivalent)

```bash
# 1. Preflight: clean parent + submodule, submodule initialized
git status --porcelain
git -C app/src/main/cpp/libosmscout status --porcelain

# 2. Fetch upstream
git -C app/src/main/cpp/libosmscout fetch origin --prune

# 3. Checkout local branch (submodule HEAD is usually detached)
git -C app/src/main/cpp/libosmscout checkout naviveylin-local
git -C app/src/main/cpp/libosmscout merge --ff-only origin/naviveylin-local

# 4. Merge upstream master into naviveylin-local (creates merge commit).
#    Conflicts are expected in libosmscout-client-java/ — see "Conflict handling".
git -C app/src/main/cpp/libosmscout merge origin/master --no-edit

# 5. Before pushing: confirm the remote branch has not moved (see Pitfalls #1)
git -C app/src/main/cpp/libosmscout ls-remote origin naviveylin-local
git -C app/src/main/cpp/libosmscout merge-base --is-ancestor <remote-sha> HEAD   # must hold for a fast-forward

# 6. Push branch so the new SHA exists on origin
git -C app/src/main/cpp/libosmscout push origin naviveylin-local

# 7. Bump pointer in parent repo
git add app/src/main/cpp/libosmscout
git commit -m "Update libosmscout submodule to master (merge into naviveylin-local)"
```

## Conflict handling

Conflicts are the normal case, not the exception: upstream and NaviVeylin both extend the JNI layer. The script aborts the merge on conflict (`merge --abort`) — never force-push and never discard the NaviVeylin-local side.

**Recipe (all three files conflict the same way every time):**

1. Inspect with `git diff --name-only --diff-filter=U`, then read the conflict hunks:
   `grep -n "^<<<<<<<\|^=======\|^>>>>>>>" <file>` + the surrounding lines.
2. Expect these shapes:
   - `NavigationPosition.java` — NaviVeylin adds local fields/ctor parameters (e.g. `wayType`) exactly where upstream adds its own (`wayName`/`wayRef`). Resolution: **keep ours** per hunk; upstream's additions usually already sit in the non-conflicting context.
   - `RoadInfo.java` — add/add: both sides created the file. Compare `git show :2:<file>` (ours) and `git show :3:<file>` (theirs); they are frequently identical apart from javadoc. Take upstream's (`git checkout --theirs -- <file>`) unless NaviVeylin has real API differences.
   - `OSMScoutClient.cpp` — content conflict around our plumbing (ctor signature `(...Ljava/lang/String;)V`, `wayTypeJ` local refs, includes) vs upstream's new API. Resolution: **keep ours per hunk**.
3. For the `.cpp` do NOT use `git checkout --ours` on the whole file: it would also drop upstream's *non-conflicting* changes from that file. Resolve hunk-by-hunk instead — e.g. strip markers keeping the `HEAD` side:
   ```bash
   awk '/^<<<<<<< /{keep=1; next} /^=======$/{if(keep){keep=0; drop=1; next}} /^>>>>>>> /{drop=0; next} !drop{print}' \
     file > file.resolved && mv file.resolved file
   ```
4. **Verify what you kept**, per file:
   - `git diff origin/master -- <file>` → should show ONLY the NaviVeylin-local intent (e.g. just the `wayType` addition).
   - `git diff HEAD -- <file>` → should show ONLY upstream's additions (or nothing).
   - No markers left: `grep -c '^<<<<<<<\|^>>>>>>>\|^=======$' <file>` → 0.
5. `git add` the resolved files, then commit the merge (`git commit -F -` with a message listing each resolution — that message is the review artifact on PR #1773).
6. Re-run the *Configuration parity* check: a hunk kept from our side can sit inside `#ifdef OSMSCOUT_HAVE_LIB_MARISA` and break the non-marisa CI build.

## Burn-down: measuring what the branch still costs

Upstreaming NaviVeylin's libosmscout work is what keeps this branch cheap: a feature that lands on
upstream `master` can be resolved as "theirs" at the next merge, and those lines never conflict
again. The conflict surface is therefore exactly the **residual diff**:

```bash
# the whole cost of carrying naviveylin-local, in one line
git -C app/src/main/cpp/libosmscout diff --shortstat origin/master HEAD
```

The script prints this figure in its summary; treat it as a burn-down metric and re-check it after
every update. It includes upstream's `style`-independent noise only if we carry it — e.g. a duplicated
`#include` that upstream has once is pure residual and belongs in the bin, not in a PR.

**Recorded baselines** — append a row after every update, so the next run reports a delta instead of a
bare number:

| date | `naviveylin-local` | upstream `master` | residual |
|---|---|---|---|
| 2026-10-10 | `4f701f1ee` | `0205b359e` | 19 files, +2127/-124 |

How to retire residual, cheapest first:

- **Artifact, zero intent** (duplicated includes, formatting left over from an earlier merge): drop
  it in the submodule directly.
- **Refactor of an upstream file with no NaviVeylin behaviour** (e.g. a table-driven rewrite of an
  upstream test): do not upstream it — resolve the next merge with `git checkout --theirs -- <file>`.
- **Small self-contained fix** (a guard, a colour, an API field): one focused PR upstream at a time;
  each merge then resolves that hunk as "theirs".
- **Larger local-only class** (the favourite store, the path registry): a real upstream PR, usually
  together with the JNI entry points and tests that use it — this is the biggest win, and it is what
  finally stops the recurring `OSMScoutClient.cpp` conflicts.
- **Pending upstream candidate (2026-09-29): the single-open path validation.** `libosmscout-client` now
  carries `IsOpenableDatabaseDirectory()` and `DatabasePathRegistry::RegisterOpenable()`, and
  `OSMScoutClient.cpp` uses them in `openDatabase` (submodule commit `041996945`, change
  `fix-open-database-path-validation`, spec
  `map-render`). It is a self-contained guard with its own `DatabaseOpenTest` cases, i.e. the "small
  self-contained fix" bucket: if upstream has not taken it by the next bump, resolve the conflicts in
  `DatabasePathRegistry.{h,cpp}` and the two JNI branches **in favour of the branch (ours)** — never
  `--theirs` — so a merge cannot drop the validation silently (TODO §82 is the precedent).

Do **not** rewrite or force-push `naviveylin-local` to "clean up": parallel sessions rewrite this
branch, PR #1773 is the review artifact, and old commits left in history cost nothing — only the
diff matters.

## Configuration parity: marisa vs non-marisa (the silent CI break)

`libosmscout-client-java/src/OSMScoutClient.cpp` gates its text-index (free-text) code on `OSMSCOUT_HAVE_LIB_MARISA`.

- **Our Android build always defines it** — vcpkg ships marisa (`vcpkg-overlays/`, `setup-vcpkg.sh`), so the non-marisa code path is *never compiled locally*.
- **The PR's CI mostly does not have marisa**: the `meson` and `cmake` jobs log `Run-time dependency marisa found: NO`. Anything that is only correct in the marisa configuration breaks there (2026-09-19: a helper defined under `#ifdef` but used outside it → `error: use of undeclared identifier 'freeTextLimitReached'` in `cmake + maven`, `meson`, `meson + maven`).

**Rule:** every `#ifdef OSMSCOUT_HAVE_LIB_MARISA` region must compile in *both* configurations. Check by forcing the macro off for this translation unit (the file must be restored afterwards):

```bash
# insert right after the include block (anchor: #include <osmscout/navigation/Engine.h>)
#   // TEMP non-marisa check
#   #undef OSMSCOUT_HAVE_LIB_MARISA
./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a
# then remove the two lines again (git -C app/src/main/cpp/libosmscout diff must be empty)
```

`--check-non-marisa` automates exactly this (backup + `trap` restore + restore verification). Note it is a real native build: ~10-30 s with a warm Ninja/CMake state, up to ~5 min when the edit invalidates the native build graph — run it detached or with a generous timeout, and **do not kill it mid-build**: a hard kill can leave the simulation in the tree, recover with
`git -C app/src/main/cpp/libosmscout checkout -- libosmscout-client-java/src/OSMScoutClient.cpp`
(the script's `trap` handles normal exits and errors, and it verifies the restore afterwards).

Quick static aid before compiling: track the guard depth of the suspicious symbols — a use at depth 0 is a bug:

```bash
awk 'NR>=3600 && NR<=3830 {if (/^#ifdef OSMSCOUT_HAVE_LIB_MARISA/) g++; if (/^#endif/) g--; \
  if (/freeTextEntries|freeTextLimitReached|seenOffsets|TextSearchIndex/) printf "%d guard=%d %s\n", NR, g, $0}' \
  app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp
```

## After the update

1. Upstream really merged: `git -C app/src/main/cpp/libosmscout merge-base --is-ancestor origin/master HEAD` (must succeed).
2. Pointer moved: `git submodule status` — the SHA must equal `git -C app/src/main/cpp/libosmscout rev-parse naviveylin-local`.
3. Native API consistency (the parent's JNI overrides must still link): every `public native` in `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/*.java` needs a JNI symbol in the merged `.cpp`:
   ```bash
   sed -n 's/.*public native [^ ]* \([A-Za-z0-9_]*\)(.*/\1/p' osmscout-client-java/src/main/java/com/framstag/libosmscout/client/*.java | sort -u |
     while read m; do grep -q "_${m}(" app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp || echo "MISSING $m"; done
   ```
   **Overload gotcha:** an overloaded method is name-mangled (`Java_..._searchLocations__Ljava_lang_String_2IJ(`), so a `MISSING` line may be a false positive — confirm with `grep -n "<name>"` in the `.cpp` before acting.
4. Configuration parity: `--check-non-marisa` (or the manual recipe above).
5. Smoke build, single ABI to stay fast: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`. Verify it *really* compiled the merged C++: `:app:buildCMakeDebug[arm64-v8a]` executed (not UP-TO-DATE) and a fresh `libosmscout_client_javad.so` under `app/build/intermediates/cxx/Debug/*/obj/arm64-v8a/` — note the debug suffix: the unsuffixed `libosmscout_client_java.so` exists only in the release variants (`stripped_native_libs/<variant>Release/…`), so a check for that name finds nothing on a debug build and reads as "not fresh" on a perfectly good build.
6. **PR checks** (authoritative):
   ```bash
   gh pr checks 1773 -R Framstag/libosmscout
   gh api "repos/Framstag/libosmscout/commits/<new-sha>/check-runs?per_page=100" \
     --jq '[.check_runs[] | .conclusion // .status] | group_by(.) | map("\(.[0])=\(length)") | join("  ")'
   ```
   Compare against the jobs that failed *before* the push; report the delta, not just "green".
7. Pushing the **parent** commit is a separate decision — the script leaves it unpushed. Ask the user or follow repo convention.
8. Residual burn-down: `git -C app/src/main/cpp/libosmscout diff --shortstat origin/master HEAD` —
   compare with the previous update and note what shrank (see *Burn-down*).

## Pitfalls

1. **A parallel session can force-rewrite `naviveylin-local`.** 2026-09-19: this branch was pushed as a fast-forward and then replaced by a peer's *copies of the same two JNI commits* (same messages, different SHAs) on an older upstream base — `git fetch` showed a forced update, and the peer tip did not contain the incoming upstream commits. Never trust the local remote-tracking ref for this: run `git ls-remote origin naviveylin-local` immediately before pushing and require `merge-base --is-ancestor <remote-sha> HEAD`. If it moved, merge the two lines (`git merge origin/naviveylin-local`, then push the merge — again a fast-forward). Two sessions must not both "claim" the same uncommitted JNI edits; one session owns the submodule update.
   **The safety copy that rewrite left behind, `backup-naviveylin-local-262e7de56`, was audited on
   2026-10-10 and retired** — so a `backup-*` ref is insurance to audit, not evidence of lost work. Neither
   mechanical test decides it: `git cherry` over-reports (pitfall 7), and `git apply --check`/`-R --check`
   classified all ten of its commits as *unclear*, applying in neither direction because the patches are
   511 commits old. What settles it is **line-level presence** — for each added line, test whether the pushed
   branch's version of the same file contains it (a real file, never a pipe into `grep -q`: under
   `set -o pipefail` the writer dies of SIGPIPE and *every* line scores absent — a measured `0/73` for a real
   `73/73`). Result: the four local fixes came back 73/73, 2/2, 381/386 and 910/962, with the identical
   `IsValidUtf8` helper still at `OSMScoutClient.cpp:3453`; the rest were upstream cherry-picks/merges or the
   old line's superseded seed (`setMapDpi` → `withPhysicalDpi` plus the per-render `dpi`), so nothing was
   lost.
2. **`git checkout --ours <file>` during a merge discards upstream's non-conflicting changes** in that file. Only safe when the file's diff vs `origin/master` is exactly the local intent — verify with `git diff origin/master`.
3. **A green local Android build says nothing about the marisa-less configuration** (see above). This is the most common way this branch turns PR #1773 red.
4. **JNI overloads are name-mangled** — symbol greps produce false "MISSING" hits; confirm before "fixing" anything.
5. **Submodule edits are not built by CI or fresh clones until pushed**; keep the submodule tree clean and push the branch *before* the parent bump (the parent's pinned SHA must exist on origin).
6. **The non-marisa check temporarily edits a tracked file.** On a normal or failing exit the script restores it via `trap` and then asserts a clean `git status`; only a hard kill (or a full disk) can leave the `#undef` behind — `git -C app/src/main/cpp/libosmscout checkout -- libosmscout-client-java/src/OSMScoutClient.cpp` puts it back, and `git status` must be empty before you continue.
7. **`git cherry` over-reports local commits.** A feature upstream landed independently keeps our
   commit on the `+` (not-upstreamed) side, because the patch shapes differ — e.g. `moveFavorite`,
   the POI multi-database determinism fix, `getRoadAt`/`RoadInfo`, `wayType`. Measure the *residual
   diff* (`git diff --shortstat origin/master HEAD`), never `git cherry`, when judging what the
   branch still carries.

## Notes

- Submodule not initialized (fresh clone): `git submodule update --init --recursive` first.
- Stylesheets come from the submodule's `stylesheets/` dir and are copied at build time — a bump automatically carries new styles into the next APK.
- Upstream's `openspec/` docs/specs arrive with the merge and are part of the diff; do not be surprised by several thousand added lines in `git diff --stat`.
- This skill is versioned with the project (`.pi/skills/update-to-current-libosmscout-master/`); copy the directory to `~/.pi/agent/skills/update-to-current-libosmscout-master/` to make it available across projects.
