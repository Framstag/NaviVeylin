# Reviewer — round 3 (final gate check), issues #958 and #962

Round under review: branch `fix/issue-958-962-a-path-no-child-wrote-is-not-a-write`, base `69be8dd5`, tip
`a412345e`. Diff: 7 files, +168/−8 — `tools/loop/{lib.sh,loop.conf,run.sh,shared.tsv,worktree.sh}`,
`tools/loop/tests/{loop-cases.sh,loop-mutations.sh}`. Change artifacts read by path from
`openspec/changes/a-path-no-child-wrote-is-not-a-write/` (gitignored, untracked by design —
`git check-ignore -v …/spec.md` → `.gitignore:82:openspec/changes/*/`). Evidence read by path:
`.pi/issue-loop/items/958/{gate-4.txt,gate-3.txt,gate-2.txt,gate.txt,mutation-29.7.log,mutation-29.8.log,
mutation-29.9.log,mutations-check.txt,suite.txt,reviewer-report-1.md,reviewer-report-2.md}`.

Reading set, `bash tools/check-rule-routes.sh --diff 69be8dd5..a412345e` (the one derivation the round asks for):

```
check-rule-routes: the reading set for 69be8dd5..a412345e
  Backlog.md §1
  Backlog.md §4
  BacklogTooling.md §9
  BuildGate.md §2a
```

Plus `tools/loop/prompts/reviewer.md` (the contract I grade against), `tools/loop/layout.tsv`/`shared.tsv`, and the
shipped requirements the delta modifies in `openspec/specs/agent-issue-loop/spec.md`.

Nothing was edited and no suite or gate was run. The three commands I did run are read-only: the identity
derivation the round asks for, `bash tools/check-rule-routes.sh --diff`, and `openspec validate <change> --strict`
(→ `Change 'a-path-no-child-wrote-is-not-a-write' is valid`), plus library-level calls of the new helpers in a
throwaway shell, and one `git init` in `/tmp` (removed) to establish what `-uall` prints.

## Round 2's five findings, verified one by one

**1. "the gate's tree digest is not this tree's" — CLOSED.**
`gate-4.txt:5` records `LOOP-GATE-TREE: head=a412345eba7fa55661408f2a37a15a3510f6f5a8 diff=77dc0323008d9bfb`, and
`gate-4.txt:1737` records `LOOP-GATE: tests=1152 failures=0 errors=0`. Deriving it the way `gate.sh` does —
`source tools/loop/lib.sh; source tools/loop/loop.conf; loop_change_identity "$PWD" 69be8dd5` — returns, on this
tree, exactly `head=a412345eba7fa55661408f2a37a15a3510f6f5a8 diff=77dc0323008d9bfb`. The two agree byte for byte, so
the `tasks.md` correction round 2 caught is gone. **No file post-dates the gate:** the identity's listing
(`git diff --name-only 69be8dd5`, `git ls-files --others`, the family's ignored files less `**/evidence/**`) is
**261 paths**, and the newest mtime among them is `tools/loop/worktree.sh` at `2026-11-18 06:04:34 +0100`
(05:04:34Z) — mutation 29.8's restore, which wrote the pre-mutation bytes back (`git diff 69be8dd5..a412345e --
tools/loop/worktree.sh` is the change's own 2-line hunk, nothing else). `gate-4.txt` was written
`2026-11-18 06:22:59 +0100`, and its own earliest stamped step output is `2026-11-18T05:06:05Z`; the newest change
artifact (`proposal.md`, `2026-11-18 06:00:58 +0100` = 05:00:58Z) and the tip commit (`05:04:59Z`) both precede it.
The gate measured this tree, and no artefact was touched afterwards (round 2's `tasks.md:21:23:53` edit is not in
this tree).

**2. "`design.md` D3 still states `:-`" — CLOSED.** `design.md` D3 now reads "`loop_write_ignore` reads
`${LOOP_WRITE_IGNORE-$LOOP_WRITE_IGNORE_DEFAULT}` — the `-` form, not `:-`. … an empty value is a statement:
`LOOP_WRITE_IGNORE=""` means the raw reading, the tree with nothing dropped. With `:-` the profile could not say
that, because the empty string would silently re-arm the default and the run would filter again." That is
`lib.sh:992` exactly (`printf '%s\n' "${LOOP_WRITE_IGNORE-$LOOP_WRITE_IGNORE_DEFAULT}" | tr ' ' '\n'`), and I
verified the behaviour of the empty value in a throwaway shell: with `LOOP_WRITE_IGNORE=""`,
`loop_is_ignorable_write .a.swp` → 1 (a write), and with the profile's list it → 0. `loop.conf:59-62` states the
same ("an empty value means exactly that, not "use the default""), and D3 and the profile now agree with the line.

**3. "`tools/loop/prompts/reviewer.md` is changed with no owner" — CLOSED.**
`git diff 69be8dd5..a412345e -- tools/loop/prompts/reviewer.md` is **0 bytes**; the hunk is reverted to the base
file. `git diff --stat 171af62b..a412345e` is `reviewer.md 10 ++++------`, `loop-cases.sh 10`, `loop-mutations.sh 7`
— the third round's three edits and nothing else.

**4. "mutation 29.8's record quotes a fixture line" — NOT CLOSED. The new quote is not this case's assertion
(BLOCKING below).** `mutation-29.8.log:8` now reads
`       assertion: the merge was refused on a build directory: loop: the project tree is not clean — refusing to
merge onto the base: ?? newmodule/ (the fixture's own git progress line is what case_reason records first — issue
#964)`. Three facts make that line impossible as this case's failure:
- the case it names, `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge`, creates `architecture/bin/Thing.class`
  (`loop-cases.sh:10313`) and never `newmodule`; `grep -rn newmodule tools/loop/` finds it only in the *run-side*
  case (`loop-cases.sh:9849-9858`) and in 29.9's comment;
- 29.8's substitution keeps `-uall` (`loop-mutations.sh:288` → `dirty=$(gitr status --porcelain -uall)`), so the
  read it prints is per file: a throwaway repo with `architecture/bin/Thing.class` prints
  `?? architecture/bin/Thing.class` under `-uall` and `?? architecture/` under plain `porcelain` (both demonstrated);
  `?? newmodule/` is the *collapsed* form, which is 29.9's subject, not 29.8's;
- the harness prints no `assertion:` line — `loop-mutations.sh:1715` prints the timestamped reason and `:1762` the
  `red-when-mutated:` summary, and `grep -n 'assertion:' tools/loop/tests/loop-mutations.sh` is empty, so line 8 is
  a hand-written addition inside a file that is otherwise harness output.

**5. "the run's `-uall` has no case and no mutation" — CLOSED.** `case_run_anEditorSwapFileIsNotAChildsWrite`
(`loop-cases.sh:9841-9859`) now writes both an editor temporary and a build directory under an untracked parent —
`mkdir -p "$LOOP_CHILD_ROOT/newmodule/bin"`, `printf "class\n" > "$LOOP_CHILD_ROOT/newmodule/bin/x.class"`
(`:9849-9850`) — and asserts the third one too (`:9858`). Mutation `29.9` (`loop-mutations.sh:291`) is
`s@^  git -C "\$LOOP_ROOT" status --porcelain -uall 2>/dev/null$@  git -C "\$LOOP_ROOT" status --porcelain
2>/dev/null@`, which matches `run.sh:1474` verbatim, and `mutation-29.9.log:3-4` records it red at
`2026-11-18T05:04:17Z` with the case's own first assertion ("a swap file in the project tree does not stop the item:
expected 'done', got 'blocked'), then restored green. I confirmed the mechanism the record claims:
`loop_is_ignorable_write newmodule` → 1 (a write), `loop_is_ignorable_write architecture/bin/Thing.class` → 0 — so
without `-uall` the collapsed `?? newmodule/` matches no pattern and the item is blocked, exactly as 29.9 shows.

## Item-by-item against `tools/loop/prompts/reviewer.md`

### Item 1 — scenario coverage

| delta scenario | asserting case | verdict |
|---|---|---|
| An editor's swap file is not a child's write | `run#anEditorSwapFileIsNotAChildsWrite` | PASS |
| A build directory does not block a landing | `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge` | PASS |
| A tracked edit still refuses the landing | `worktree#aTrackedEditStillBlocksTheMerge` | FAIL — the case creates an *untracked* file (BLOCKING 2) |
| A process that writes outside its tree is caught (modified; gains the run-side case) | `run#childWriteOutsideTheWorktreeIsNoted`, `run#mergesOverADifferenceItCannotPlace`, `run#anEditorSwapFileIsNotAChildsWrite` | PASS |
| A merge over a project tree that is not clean is refused, and nothing is closed (modified; gains the two `worktree#` cases) | `worktree#theLandingDecidesByTheReadingItIsHanded`, `worktree#refusesToMergeOverADirtyProjectTree`, `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge`, `worktree#aTrackedEditStillBlocksTheMerge` | PASS for the refusal; its "a tracked file that changed refuses whatever its name" clause is unexercised — BLOCKING 2 |
| The 13 + 6 unchanged scenarios the two `MODIFIED` blocks repeat | the cases the shipped spec names | PASS |

- `run#anEditorSwapFileIsNotAChildsWrite` — `PASS` at `gate-4.txt:497`. Assertions: `--strict-outside-write yes`
  with a child that writes `.mise.local.toml.swp` and `newmodule/bin/x.class` into `$LOOP_CHILD_ROOT`, then
  `assert_eq "done" … 'a swap file in the project tree does not stop the item'` (`loop-cases.sh:9855-9856`),
  `assert_not_contains "$note" "outside the worktree"` (`:9857`), and the build file's presence (`:9858-9859`). Its
  right-reason half is mutation 29.7 (`RED 29.7 … 2026-11-18T05:03:50Z — a swap file in the project tree does not
  stop the item: expected 'done', got 'blocked'`), and 29.9 for the `-uall` half.
- `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge` — `PASS` at `gate-4.txt:234`; assertions
  `loop-cases.sh:10313-10317` (`fail "the merge was refused on a build directory: $out"`, the fast-forward,
  `?? architecture/` still in the tree). Green, and 29.8 does redden it — but see the BLOCKING: the record's
  right-reason quote is not this case's message.
- `worktree#aTrackedEditStillBlocksTheMerge` — green at `gate-4.txt:236`, and **FAIL as coverage**: its setup
  creates a file instead of modifying one. `new_project` (`loop-cases.sh:1448-1476`) creates and commits no
  `README.md`, so `printf 'a tracked edit\n' >> "$p/README.md"` (`loop-cases.sh:10336`) creates an untracked file —
  a throwaway repo built the same way prints `?? README.md` for that line and `git ls-files README.md` is empty —
  and `loop_is_ignorable_write README.md` → 1 (no pattern matches it), so the refusal the assertions read
  (`loop-cases.sh:10338-10340`) comes from an untracked entry and not from the tracked modification the scenario's
  WHEN names. See BLOCKING 2. No mutation record reddens this case either.
- Unchanged scenarios: comparing every scenario block of the delta's `MODIFIED` blocks with the shipped spec
  programmatically — "Each item is worked by a fresh process…": body byte-identical (213 chars), 14 of 14 shipped
  scenarios present, none missing, none added, **1 changed** ("A process that writes outside its tree is caught");
  "The item is closed on a base that carries its fix": body byte-identical (756 chars), 7 of 7 scenarios, **1
  changed** ("A merge over a project tree that is not clean is refused…"). So the 19 unchanged scenarios are repeated
  verbatim, as `AGENTS.md` requires.
- The `ADDED` requirement's body is **433** chars (limit 500) and states a contract ("SHALL leave out the paths no
  child of the run could have written … only untracked entries SHALL be droppable"), not an implementation.
- Timestamps: the harness prints no per-case timestamp, so a case's "timestamp" is the gate step's
  (`gate-4.txt:13` `--from-git` 897 s, file written 05:22:59Z, after the tip's 05:04:59Z) or a mutation log's red
  stamp. Round 2's observation stands unchanged.

### Item 2 — cause or gap scope

PASS. The production seam is the named one, and it is minimal: `lib.sh:986` `LOOP_WRITE_IGNORE_DEFAULT`, `:988`
`loop_write_ignore`, `:995` `loop_is_ignorable_write` (whole path and basename, trailing `/` stripped), `:1009`
`loop_drop_ignorable_writes [untracked]`, `:1025` `project_state_differs`; `run.sh:1486` (`project_state_diff`
filters), `:1474` (`-uall`), `:1624-1626` (the in-time reading prints its FIRST line only when
`project_state_differs`), `:2470` (the after-the-fact test); `worktree.sh:711` and `:754` (`-uall |
loop_drop_ignorable_writes untracked`); `loop.conf:55-62`; `shared.tsv:23`. The phase is decided by the filtered
comparison and not by the description: `outside_write_decides` (`run.sh:1596-1599`) reads only
`${outside_write_note}`, which is assigned at `:2473` inside the `project_state_differs` branch, and all three
call sites of `outside_write_clause_of` (`:1588`, `:1626`, `:2473`) are inside that same branch — so description and
decision cannot disagree (design D1). I re-verified the two contracts the profile states: on the landing side a
tracked ` M build.tmp` is kept while `?? build.tmp` and `?? architecture/bin/T.class` are dropped; on the run's side
the tracked one is dropped too, which is the deliberate looseness design's Non-Goals now states in words. The one
foreign hunk round 2 flagged is reverted (item 3 of the closure list), so nothing in the diff is out of scope. One
sentence of the design is not what the tree does, though: D2's "a tracked edit refuses the landing exactly as
before, which is `worktree#aTrackedEditStillBlocksTheMerge`" names a case that modifies no tracked file
(BLOCKING 2).

### Item 3 — gate reality

PASS. `LOOP-GATE-TREE: head=a412345eba7fa55661408f2a37a15a3510f6f5a8 diff=77dc0323008d9bfb` (`gate-4.txt:5`) equals
this tree's recomputed identity, and `LOOP-GATE: tests=1152 failures=0 errors=0` (`:1737`) is an execution, not a
cache: every step is a shell step with its own wall time (`loop-selftest … --from-git 69be8dd5 897s`, `:1271`
`all 484 cases passed … 895.75 s, selection: --from-git 69be8dd5`), `grep -c '^FAIL' gate-4.txt` = 0, 735 `PASS`
lines, and no `FROM-CACHE`/`UP-TO-DATE` appears anywhere in the file. The right requirement: the delta touches
`tools/loop/**` only, so `tools/loop/gate.sh` is its gate and `BuildGate.md` §2a owes no Gradle build, no flavour
pair and no device run.

### Item 4 — tasks claiming done

`1.1`–`1.4`, `2.1`–`2.4`, `3.3`, `3.4`, `3.5` are `[x]`, and two of them are not quite true: `2.3`'s third case
is "`worktree#aTrackedEditStillBlocksTheMerge` (the guard against the filter becoming a licence)" and that case
asserts an untracked refusal (BLOCKING 2), and `3.2`'s "each red for the named assertion" is false for 29.8
(BLOCKING 1). The rest hold at the tip (four helpers plus
`LOOP_WRITE_IGNORE_DEFAULT` at `lib.sh:986-1030`; both landings at `worktree.sh:711`, `:754`; the refusing arm
prints `$1` verbatim one path per line at `worktree.sh:667-668` because `run.sh:518-521` parses those lines into
`merge_refused_paths`; three mutations 29.7/29.8/29.9; gate-4 measured the final tree). `3.1` is true at the tip but
cites a stale run — NOTE.
`4.1` is correctly left `[ ]`, and `3.6` is this round. The duplicate-`3.5` malformation round 2 recorded is gone
(ids run 1.1–1.4, 2.1–2.4, 3.1–3.7, 4.1) and `openspec validate --strict` now reports nothing but `is valid`.

### Item 5 — revert-check

- `29.7` — PASS. Targets the deciding site (`run.sh:2470`), fails for the right reason with the case's own
  assertion text and a timestamp (`mutation-29.7.log:4`, `2026-11-18T05:03:50Z`), restores green.
- `29.9` — PASS. Targets `run.sh:1474`, fails on the case's first assertion ("expected 'done', got 'blocked'") at
  `2026-11-18T05:04:17Z` (`mutation-29.9.log:4`), restores green. It shares 29.7's message text, which is expected
  (one case, two ways to be blocked) and does not weaken either record: each mutation moves one line.
- `29.8` — **FAIL, BLOCKING below.** Right site (`worktree.sh:754`), restores green, but the only "assertion" in
  the record is a hand-written line naming a path the case never creates.
- `worktree#aTrackedEditStillBlocksTheMerge` has neither a red run nor a mutation record, and the `untracked`
  argument both landings pass is mutated by nothing: no case modifies a tracked path whose name one of the seven
  patterns matches, so substituting `loop_drop_ignorable_writes` for `loop_drop_ignorable_writes untracked` would
  redden no case at all — the one case written to catch that creates an untracked `README.md` (BLOCKING 2). The
  clause "a tracked file that changed refuses whatever its name", which the delta and `loop.conf:59-60` both state,
  is therefore pinned by no case and no record.
- No mutation marker is left: `git status --porcelain` shows only `?? osmscout-client-java/bin/`, and
  `git diff 171af62b..a412345e` touches no production file, so all three production files are restored exactly.

### Item 6 — evidence pointers

- `gate-4.txt:5`, `:1271`, `:1737`, the three mutation logs and `mutations-check.txt` are each a command's output
  and each command is in the record. The gate's full log is still only `app/build/test-results/loop-gate/gate.log`
  (a build-directory copy, `gate-4.txt:7`) — fragile across a clean, as round 1 noted.
- `mutation-29.8.log:8` is the one line in the record that no command produced (BLOCKING).
- Two counts are stale: `tasks.md` 3.2 says "`418 records apply`" and `proposal.md`'s retake line says
  "`418 records`", while `mutations-check.txt:1` reads `selected 175 of 419 records (files: …)` and `gate-4.txt`
  reads `loop-mutations: all 419 cases passed`. NOTE (no claim depends on the figure).
- `tasks.md` 3.1 cites `.pi/issue-loop/items/958/suite.txt` (`all 484 cases passed … 974.77 s`) for "the three cases
  green", but that file was written `2026-11-17 20:39:41 +0100` and `tools/loop/tests/loop-cases.sh` was last
  written `2026-11-18 06:00:56 +0100` — so the cited run predates the `newmodule` assertions it is offered for.
  The tip's own evidence is `gate-4.txt` (`PASS` at `:234`, `:236`, `:497`). NOTE.

### Item 7 — spec and guideline honesty, per derived rule

**`Backlog.md` §1 "Status, and what this document owns"** — PASS; the change re-states neither the tracker's state
nor the document's ownership.

**`Backlog.md` §4 "The invariants"** — PASS with one NOTE. I8/I9/I10/I12 are untouched: the filter narrows which
differences are *reported*, grants no agent git and adds no writer. I13 ("A project fact exists in one file … a
repository name, an author, a vocabulary value or a path appears in an engine") is not falsified — the patterns are
generic globs, and D3 now argues the engine's default and the profile's precedence correctly — but
`loop.conf:62`'s value is a byte-identical copy of `lib.sh:986`'s default, so the same seven patterns live in two
files and only a reader can keep them equal (NOTE).

**`BacklogTooling.md` §9 "Checks"** — PASS. The delta adds no check; the three new cases are declared in
`LOOP_SHELL_CASES` (`loop-cases.sh:123-124`, `:169`) and in the dispatch (`:897-898`, `:944`), and
`driver#everyCaseDeclaresTheFilesItCanReach` is `PASS` at `gate-4.txt:9`.

**`BuildGate.md` §2a "Agent iteration protocol"** — PASS on the rules, two NOTEs. "A `tools/loop/**` delta reads its
two tables" holds: the layout bounds are respected today's lengths are `lib.sh` 1028 against 1130, `run.sh` 4394 against 4742 and `worktree.sh` 1133
against 1414 (`tools/loop/layout.tsv:20`, `:26`, `:28`) and `shared.tsv` gained `project_state_differs`. The
"Name every exclusion" rule's statement is gone from the rewritten `tasks.md` (`head -8` of the file is the title and
"## 1. The reading…" — the sentence round 2 read at `tasks.md:4-5` is not there any more), NOTE. And the protocol's
"the change's proposal carries [the surface] with the command that produced it" is still met by two suite commands
rather than by the `grep -l` count, NOTE (the counts themselves are now right except one, below).

**The delta is the right kind** — two `MODIFIED` blocks carrying every shipped scenario verbatim plus one narrowed
`ADDED` requirement (item 1 above). It states a contract, not an implementation, and `loop.conf:55-61` states what
the key means rather than restating the code.

### Item 8 — new overreach

- UNPROVEN: "29.8's record quotes the assertion that failed" (`a412345e`'s message) and `tasks.md` 3.2's "each red
  for the named assertion" — the record's line names `?? newmodule/`, which is not this case's output (BLOCKING).
- UNPROVEN: **#964** and **#963** are claimed filed (`tasks.md` 3.7, `mutation-29.8.log:8`) and I could not verify
  either without the tracker; `.pi/issue-loop/items/` holds a `960` directory and none for 963/964. NOTE.
- UNPROVEN (unchanged from round 2, text untouched): "Measured 2026-11-17T10:58Z: #950 ended `outside-write` after
  3 670 s … 15.9 Mtok spent and 2 commits left unlanded" (`proposal.md` Why) and "`merge-item` refused #960's
  landing on `?? architecture/bin/`" — the run state supports the wall and the spend, not the instant or the two
  commits, and no record carries the #960 refusal. NOTE.
- UNPROVEN: `proposal.md`'s `surface:` line — the counts are now right ("4 helpers and 1 default", "1 key", "1 row",
  "5 call sites", "3 cases", "3 mutation records", "24 scenarios in 1 delta file") except "7 of them added or
  changed", where the delta has **5** (2 modified + 3 added), and the line still names no `grep -l` command. NOTE.
- `proposal.md`'s "**Two cases and two records.**" bullet lists *three* case names and only `29.7` and `29.8`,
  omitting 29.9 — the same sentence contradicts its own `surface:` line. NOTE.
- Tested claim — **supported**: "the gate red on `run#doesNotCloseOnAMergeTheBaseCannotTake`, `expected '2', got
  '1'`" (`worktree.sh:657-659`), by `gate-2.txt:5` and `:699`/`:703`.
- Tested claim — **supported, and the measurement is honest**: D1's "the first draft filtered `project_state_diff`
  alone and `run#anEditorSwapFileIsNotAChildsWrite` still ended `blocked`" is exactly what mutation 29.7 reproduces.

## Findings

BLOCKING: `mutation-29.8.log:8` — the line round 3 added to close round 2's finding 4 — is not this case's failing assertion, so the record still proves only that the case reddened. It reads `assertion: the merge was refused on a build directory: loop: the project tree is not clean — refusing to merge onto the base: ?? newmodule/`, but the case it names creates `architecture/bin/Thing.class` (`loop-cases.sh:10313`) and never `newmodule` (`grep -rn newmodule tools/loop/` → only the run-side case, `:9849-9858`), and mutation 29.8 keeps `-uall` (`loop-mutations.sh:288`), whose reading for that tree is `?? architecture/bin/Thing.class` — demonstrated in a throwaway repo, where `-uall` prints the file and plain `porcelain` prints `?? architecture/`. `?? newmodule/` is the collapsed form 29.9's comment describes, i.e. another case's subject. The line is also not harness output: `loop-mutations.sh:1715` prints the timestamped reason and `:1762` the summary, and no `assertion:` string exists in the harness, so line 8 is hand-written inside a file that is otherwise the harness's — which is why the commit message's "29.8's record quotes the assertion that failed" and `tasks.md` 3.2's "each red for the named assertion" are both false as they stand. Fix (subtractive — no new claim, case or assertion): re-quote the case's real message, which is `fail "the merge was refused on a build directory: $out"` with `$out` carrying `loop: the project tree is not clean — refusing to merge onto the base:` and `?? architecture/bin/Thing.class`; take it from the case's own `<case>| ` line in the harness log — the same shape `gate-2.txt:699` carries for `run#doesNotCloseOnAMergeTheBaseCannotTake`, because `loop-selftest.sh` prefixes every case line with the case's name — or from `loop-mutations.sh --only 29.8`, label any hand-written reproduction as such, and drop the invented path.

BLOCKING: `worktree#aTrackedEditStillBlocksTheMerge` — the case the delta names for "A tracked edit still refuses the landing", and the guard `tasks.md` 2.3 calls "the guard against the filter becoming a licence" — modifies no tracked file, so the clause "At a landing, only untracked entries SHALL be droppable" is exercised by nothing. Its setup is `printf 'a tracked edit\n' >> "$p/README.md"` (`loop-cases.sh:10336`), but `new_project` (`loop-cases.sh:1448-1476`) creates and commits no `README.md`, so the redirect *creates* the file: `git ls-files README.md` is empty and `git status --porcelain -uall` reads `?? README.md` (both reproduced in a throwaway repo built the same way). That line is kept by the filter (`loop_is_ignorable_write README.md` → 1, no pattern matches it), so the refusal the case asserts (`loop-cases.sh:10338-10340`, `assert_eq "5" "$status"`) is the refusal the pre-fix code also gave: the case is green at `gate-4.txt:236` and would be green on HEAD. What it cannot catch is the defect it exists for — substituting `loop_drop_ignorable_writes` for `loop_drop_ignorable_writes untracked` (`worktree.sh:711`, `:754`) leaves every case green, because no case has a *modified tracked* path matching a pattern; and it cannot redden for the scenario's own WHEN ("a tracked file in the project tree carries a modification"), so per the contract's item 1 the scenario is FAIL, and design D2's "a tracked edit refuses the landing exactly as before, which is `worktree#aTrackedEditStillBlocksTheMerge`" and `tasks.md` 2.3's description of the case are both false as the tree stands. Fix (adds one setup line and one mutation record): create and commit a pattern-matching path in the fixture *before* `init_state` — `printf 'x\n' > "$p/build.tmp"; git -C "$p" add build.tmp; git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm base` — then modify it (`printf 'a tracked edit\n' >> "$p/build.tmp"`), so the refusal can only come from the `untracked` arm, and record `loop_drop_ignorable_writes untracked` → `loop_drop_ignorable_writes` as a mutation on this case (the record reddens with `assert_eq`'s own message, `expected '5', got '0'`, because the landing would take the merge) — the ordering matters, because a commit after `init_state` moves `main` and turns the assertion into a base-moved refusal.

NOTE: `tasks.md` 3.1 cites a suite run that predates the assertions it is offered for — `.pi/issue-loop/items/958/suite.txt` was written `2026-11-17 20:39:41 +0100`, `tools/loop/tests/loop-cases.sh` `2026-11-18 06:00:56 +0100`; the tip's evidence is `gate-4.txt` (`PASS` at `:234`, `:236`, `:497`). Re-point the task at `gate-4.txt`, or state both.
NOTE: two counts are stale by one: `tasks.md` 3.2's "`418 records apply`" and `proposal.md`'s "(`418 records`)", against `mutations-check.txt:1` (`selected 175 of 419 records`) and `gate-4.txt` (`all 419 cases passed`). Nothing depends on the figure.
NOTE: `proposal.md`'s "**Two cases and two records.**" bullet lists three cases and two of the three records, and its `surface:` line says "7 of them added or changed" where the delta has 5 (2 modified, 3 added); the line still names no `grep -l` command, which `BuildGate.md` §2a asks for.
NOTE: the adopted landing carries the same filter with no case and no mutation — `worktree.sh:711` (`adopt-item`) is identical to `:754`, and only `merge-item` is asserted (`worktree#anIdeBuildDirectoryDoesNotBlockTheMerge`) and mutated (29.8). A sentence in `design.md` naming the second site as covered by symmetry, or a second assertion, would close it (round 2's note stands).
NOTE: `shared.tsv` is still one row short by its own rule — `loop_drop_ignorable_writes` is defined at `lib.sh:1009` and called from `worktree.sh:711`, `:754` and `run.sh:1486`, but no row declares it, because the census cannot see a pipeline-position call (`tasks.md` 3.7 records this as #963). The fix belongs in the census, not here.
NOTE: the third reading of the project tree is deliberately left raw and this tree now trips it: `run.sh:1118`'s pre-flight read (`project_dirt=$(git -C "$LOOP_ROOT" status --porcelain)`) neither filters nor passes `-uall`, and `git status --porcelain` in this repository prints `?? osmscout-client-java/bin/` (`gate-4.txt:13` even lists it among the change's files). A fresh `bash tools/loop/run.sh` here would refuse at step 5c with "the project tree carries uncommitted work", for exactly the class of path #962 named. Not a contradiction — the `ADDED` requirement is narrowed to the two readings on purpose — but the directory should be removed or ignored before this branch is merged, or the third reading added to a follow-up item.
NOTE: `-uall` is still unbounded where the landing refuses: the refusing arm prints the reading verbatim, one path per line (`worktree.sh:667-668`), which `run.sh:518-521` turns into `mergeRefused.paths` and `report.sh` prints; only the merging arm's clause is bounded (ten paths, 120 characters, `worktree.sh:660-662`). Since that format is a parse contract (`run#doesNotCloseOnAMergeTheBaseCannotTake`), a future fix must cap the number of lines, never join them — round 2's note stands, and `tasks.md` 2.2 now states the rule in the right place.
NOTE: `run#anEditorSwapFileIsNotAChildsWrite`'s third assertion (`loop-cases.sh:9858`, `assert_eq "class" "$(cat "$p/newmodule/bin/x.class")"`) proves the file is in the tree, not that the reading saw it file by file; the "read file by file" claim rests on the first assertion plus mutation 29.9. The assertion is sound, its message is not quite what it measures.
NOTE: `loop.conf:62` sets `LOOP_WRITE_IGNORE` to a byte-identical copy of `lib.sh:986`'s default, so the seven patterns exist in two files and nothing checks they stay equal. D3 records why the engine keeps a default, so I13 is not falsified, but the profile's copy makes the default dead for this project and the two can drift.
NOTE: no `{{DIFF_PATH}}.identity` exists for this round (`.pi/issue-loop/items/958/` holds no `*.diff`; `run.sh:1651` writes one only for a run-driven round), so item 3's "quote both recorded values" was answered by recomputation. They now agree, so nothing turns on it — but a run-driven round should hand the identity beside the diff.
NOTE: `tasks.md` no longer names the exclusion round 2 verified at its lines 4-5 (no compiled file, no Gradle suite, no device run), which `BuildGate.md` §2a's "Name every exclusion" rule asks a task list to carry; `gate-4.txt` carries no `excluded:` line either.
NOTE: the red-case provenance is still partial: no run of the three new cases is recorded red before the fix (29.7 and 29.9 cover the run side, 29.8 the landing's merging arm).

BLOCKING: 2
VERDICT: FAIL
