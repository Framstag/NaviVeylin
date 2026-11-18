# Tasks — a path no child of the run wrote is not a write (#958, #962)

## 1. The reading, on the run's side (#958)

- [x] 1.1 `lib.sh`: `LOOP_WRITE_IGNORE_DEFAULT` (an editor's temporary, a build directory), `loop_write_ignore`,
  `loop_is_ignorable_write` (whole path and basename, trailing `/` stripped) and `loop_drop_ignorable_writes` — only
  `??` lines are droppable, because a tracked file that changed is never droppable.
- [x] 1.2 `run.sh`: `project_state_diff` pipes the reading through the filter, `outside_write_seen` prints its FIRST
  line only when `project_state_differs`, and the after-the-fact comparison takes the same test.
- [x] 1.3 `run.sh`: `project_state` reads `git status --porcelain -uall`, because plain `porcelain` collapses an
  untracked directory to its root (`?? architecture/`) and no pattern can see that name.
- [x] 1.4 `loop.conf`: `LOOP_WRITE_IGNORE` documented as the profile's value; empty means raw, which is why the
  substitution is `${LOOP_WRITE_IGNORE-…}` and not `:-`.

## 2. The landing, on both of its paths (#962)

- [x] 2.1 `worktree.sh`: `merge-item` and `adopt-item` read `git status --porcelain -uall | loop_drop_ignorable_writes
  untracked` — only untracked entries can be dropped.
- [x] 2.2 `worktree.sh`: the merging arm's record is bounded (ten paths, 120 characters each) because a damaged tree
  carries thousands of lines; the refusing arm prints the reading verbatim, one path per line, because `run.sh` parses
  the paths it records out of that message.
- [x] 2.3 Cases `run#anEditorSwapFileIsNotAChildsWrite` (a swap file *and* a build directory an untracked parent
  hides), `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge` and `worktree#aTrackedEditStillBlocksTheMerge` (the guard
  against the filter becoming a licence), each declaring its `# files:`.
- [x] 2.4 Mutation `29.7` (the run's comparison taken raw again), `29.8` (the landing's read taken raw again) and
  `29.9` (`-uall` dropped from the run's own reading, so the collapsed `?? newmodule/` matches no pattern).

## 3. Verification

- [x] 3.1 The three cases green at the tip: `.pi/issue-loop/items/958/gate-4.txt` (`PASS` at `:234`, `:236`, `:497`).
  `suite.txt` is the earlier `--from-git` run and predates the round-3 assertions, so it stays as history, not as the
  proof.
- [x] 3.2 The mutation set for the touched files: `.pi/issue-loop/items/958/mutations-check.txt` — `420 records apply`;
  `mutation-29.7.log` … `mutation-29.10.log`, each red for the named assertion and green again, with each red run's
  own output beside it (`mutation-29.8-red.txt`, `mutation-29.10-red.txt`) because `case_reason` records the fixture's
  first line (#964).
- [x] 3.3 Review round 1: `BLOCKING: 5`, all answered (`.pi/issue-loop/items/958/reviewer-report-1.md`).
- [x] 3.4 Review round 2: `BLOCKING: 5`, all answered (`.pi/issue-loop/items/958/reviewer-report-2.md`) — the stale
  gate digest, D3's stale substitution, the foreign `reviewer.md` hunk, `29.8`'s fixture-line reason, and `-uall`
  having no case.
- [x] 3.5 Review round 3: `BLOCKING: 2`, both answered (`.pi/issue-loop/items/958/reviewer-report-3.md`) — the
  hand-written assertion line replaced by the red run's own output, and
  `worktree#aTrackedEditStillBlocksTheMerge` no longer creating the file it claimed to modify (it commits `keep.tmp`
  before `init_state`, so the edit reads ` M`, and mutation `29.10` makes the case fail when the landing drops tracked
  entries too).
- [x] 3.6 The gate at the final tree: `.pi/issue-loop/items/958/gate-5.txt` — `LOOP-GATE-TREE` and `LOOP-GATE:`
  recorded after the last write to any change artefact, with the identity recomputed over the tree to show the two
  agree. The four earlier runs are kept beside it: `gate.txt` (red, measured `e874e71f`), `gate-2.txt` (red on
  `run#doesNotCloseOnAMergeTheBaseCannotTake`), `gate-3.txt` (green at `171af62b`) and `gate-4.txt` (green at
  `a412345e`, before round 3's corrections).
- [x] 3.7 File every unrelated defect this work finds: **#963** (the shared-name census cannot see a pipeline call)
  and **#964** (a mutation record quotes the fixture's first output line, not the failing assertion).

Exclusions: no compiled file is touched, so no Gradle build, no test suite and no device run is owed — the profile's
`tools/loop/**` gate (`gate.sh`) is the whole verification, beside `openspec validate --strict` and
`tools/check-change-readiness.sh`.
- **The evidence digest does not reproduce.** `gate-5.txt` records `LOOP-GATE-TREE: head=ba95f4e5fd5ce2600c9af799f351d26db64213eb diff=84bd6856e563a627`; `loop_change_identity` recomputed over the same, untouched tree returns `diff=ae99c1d4fd926438`, stable across repeats seconds apart, with `git status` carrying only `?? osmscout-client-java/bin/` (mtime older than the gate). Same tip, same seven diff paths. The gate itself is green (`LOOP-GATE: tests=1153 failures=0 errors=0`), and the owner chose to land on that green tally with the disagreement recorded here rather than to hold the branch. The probe that settles it: dump `loop_tree_identity`'s sorted listing at gate start and diff it against a post-gate recomputation — the listing hashes `git ls-files --others --exclude-standard`, which carries exactly the editor output #962 is about, so an untracked tree that an open editor touches can move the digest on its own.
