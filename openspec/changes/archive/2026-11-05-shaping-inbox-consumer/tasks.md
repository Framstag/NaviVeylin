# Tasks — the inbox's consumer

Requirement under change: `shaping-inbox`, "The inbox is read by a consumer that proposes and never decides".

## 1. The skill

- [x] 1.1 Add `.pi/skills/reshape-issue/SKILL.md` with the front-matter description, the "when to use / when not
      to use" list, and the call it makes.
      Verification: the file exists, `head -3` names `name: reshape-issue`, and `tools/check-doc-routes.sh` passes.
- [x] 1.2 The skill reads the inbox through `tools/issues/shaping.sh --json` and repeats each item's verdict and
      clause as the queue printed them, never as a summary.
      Verification: case `shaping#theConsumerProposesFromTheItemsOwnStatement`.
- [x] 1.3 The proposal form is stated: one mechanism or a set of children, each with a `file:line` read in the tree,
      and the cost of taking it; a child that is not a tier-A finding says so.
      Verification: the same case asserts both proposal forms and the `file:line` requirement; the skill's text
      names `create.sh`'s two shapes.
- [x] 1.4 The skill writes nothing before the owner chooses, and says so in one sentence the case reads verbatim.
      Verification: case `shaping#theConsumerWritesNothingBeforeTheOwnerChooses`.
- [x] 1.5 On approval the skill files children with `tools/issues/create.sh` and records the item's shape with
      `tools/issues/update.sh`, naming no other write path.
      Verification: case `shaping#theApprovedReshapeLeavesThroughTheExistingWriters` greps for `create.sh`,
      `update.sh`, and the absence of `gh issue edit` / `gh issue close` in the skill.

## 2. The pointer

- [x] 2.1 `guidelines/Backlog.md` §5 names the skill beside `tools/issues/shaping.sh`, in the shaping paragraph,
      so the state's reader is reachable from the paragraph that owns the state.
      Verification: `grep -n 'reshape-issue' guidelines/Backlog.md` prints one line and
      `tools/check-doc-routes.sh` passes over the new skill path.
- [x] 2.2 `guidelines/Backlog.md` §5's claim that the queue "is a listing, not a ranking" stays true: the skill's
      text must not rank, and `triage-todo` is not edited.
      Verification: `git diff --name-only` over this change names no file under `.pi/skills/triage-todo/`.

## 3. The cases

- [x] 3.1 `tools/issue-tooling-cases.sh` gains the three `shaping#` cases and their entries in
      `ISSUES_SHELL_CASES` and the dispatch table.
      Verification: `bash tools/issue-tooling-selftest.sh --list | grep -Ec 'shaping#(theConsumer|theApproved)'` is 3.
- [x] 3.2 Each case is red before `SKILL.md` exists and green after — the falsification the change owes.
      Verification: the case file is added first, its run names the missing path, then the skill is added and the
      suite is green.
- [x] 3.3 Mutate the skill once — delete the stop sentence — and confirm the case goes red, then restore it.
      Verification: `shaping#theConsumerWritesNothingBeforeTheOwnerChooses` fails with the sentence removed, and
      `tools/issue-tooling-selftest.sh` is green after the restore.

## 4. The archive

- [x] 4.1 `openspec validate shaping-inbox-consumer --strict` passes before the apply pass closes, and the delta's
      requirement body is read against the 500-character limit.
      Verification: the command's own exit status and output.
