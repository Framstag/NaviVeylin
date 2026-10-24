# Round 2 — what the reviewer refused, and what this round changed

Round 1's review (`review-round-1-requested.md`) carried one **blocking** finding and six notes.
Nothing in the delta's contract moved: no new claim, no new case, no new assertion, and the spec
delta still has one requirement and one scenario naming
`run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`.

## The blocking finding — the archive move's `openspec/*` gate

`tools/loop/loop.conf:141`–`143` maps an `openspec/` path to `bash tools/gen-feature-list.sh
--check-classification` and `tools/check-doc-routes.sh`, and says the mapping exists for "the delta a change
writes, **and the archive move that ships it**". The gate recorded in `gate.log` ran before the archive, while
`.gitignore:75`–`76` still ignored `openspec/changes/*/` — so its owed list could not contain either check, and
the archived tree was covered by nothing.

Taken, on the archived tree, after this round's edits
(`evidence/structural-checks-post-archive.log`):

| command | result | tally |
|---|---|---|
| `bash tools/gen-feature-list.sh --check-classification` | exit 0 — `specs read: 177`, `classified: 180`, `unclassified: 0`, `delta spec ids: 1`, `stale: 3` (the three entries predate this change), 2 s | `evidence/bash_tools_gen-feature-list.sh_--check-classification.xml` (`tests="0" failures="0" errors="0"`) |
| `tools/check-doc-routes.sh` | exit 0 — all 152 section references and all 4 skill references resolve, 0 s | `evidence/tools_check-doc-routes.sh.post-archive.xml` (`tests="0" failures="0" errors="0"`) |

`tasks.md` task 5 now carries both, and its earlier "untracked" wording is corrected: the path was *ignored*,
and the archive move is what makes it gated.

## The notes

1. **`loop-branch` count** (`proposal.md`) — was "→ 6" for a command run on the base. Re-measured:
   `git grep -n 'loop-branch' f2d2773 -- 'tools/loop/*.sh' | wc -l` → **5** (`run.sh` 1, `worktree.sh` 4), and
   **6** in this tree, the refusal arm's new read being the sixth. Corrected.
2. **`evidence-check.sh` and `openspec validate` output not kept** — both are now in
   `evidence/evidence-check.log`: `evidence-check: PASS` (353 `file:line` pointers resolved, 0 ambiguous, 0 tallies
   drifted, 0 `/tmp` pointers), and `openspec validate agent-issue-loop --type spec
   --strict`, which exits 1 on eight `>500 characters` warnings about requirements 3, 6, 19, 27, 28, 36, 37 and
   38 — all shipped before this change; the requirement this delta added is 53 and is not among them.
3. **Two `file:line` pointers unresolved in `design.md`** — the bare `report.sh` and `state.sh` pointers at
   lines 60 and 260 resolved only by
   basename, and a basename also matches the scratch copies the self-test leaves in the loop's own temporary
   directory. They now read   `tools/loop/report.sh:60` and `tools/loop/state.sh:260`, which resolves outright.
4. **`revert-check-53.log` quoted no failing assertion** — the harness discards a case's own output
   (`tools/loop-mutations.sh` `run_case`: `>/dev/null 2>&1`), so the RED line cannot carry it. The three
   declared mutations were therefore reproduced by hand with the same `sed` expressions from
   `tools/loop-mutations.sh:352`–`354`, each case run kept with its failing line, and both files restored
   byte-identical with the pristine tree green afterwards: `evidence/revert-check-53-round2.log`.
   Measured lines: `53.1` → `'leftovers' not found in: reported success but the tree is still at the base
   commit` (the case's first failing assertion under that mutation), `53.2` → `expected '1', got '2'`,
   `53.3` → `'#21' not found in: the item could not be started: loop: the worktree is not clean — refusing to
   start #22:`.
5. **`state.sh`'s join keeps the first record's `result`** — now stated where the rule lives,
   `guidelines/Backlog.md:315`: the later account writes the note and the escalation label only, so an item
   that ended `done` is not counted as `blocked` by leftovers found later, and the stop reason is what names it.
6. **`gate.log` carries no timestamp of its own** — unchanged; the ordering still rests on the per-case branch
   markers inside it (`loop-20261023-171803` … `171947`), which place it after `red-HEAD.xml`
   (`2026-10-23T17:11:48Z`) and `revert-check-53.log` (`2026-10-23T17:16:05Z`).

## What was not run again, and why

The gate itself. `gate.log` is the run's one gate for this item, its result is quoted and not improved, and
the work this round adds touches no compiled file and no shell suite: the two checks above are the
`openspec/*` and `*.md` half of the profile's own mapping, taken on the tree that merges.
