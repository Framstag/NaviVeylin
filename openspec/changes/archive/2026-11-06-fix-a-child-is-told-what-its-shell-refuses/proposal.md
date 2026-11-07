# A child is told what its own shell and tools refuse, before it is refused

## Why

The phase prompt is the whole of what a loop child is handed in place of the project's context files
(`adapter#theChildIsStartedWithoutContextFiles`, `prompt#thePhasePromptsCarryTheirOwnRoute`), and its section
"Where the project's rules are" routes the child to the document map, the section-list call, the capabilities and the
change artifacts — `tools/loop/prompts/worker.md:39-56` on the base commit — and says nothing about what the process
the child is started in will refuse. The shell policy that refuses a tree search is named one clause down
(`worker.md:51`, `tools/pi-shell-refusal.mjs`), and the rest is not in the prompt at all.

**Root cause, in one sentence:** the four refusal families a child meets are properties of the process it is started
in — the approval no unattended run can be asked for (`git stash`, `git checkout`, `git apply`, `git add`,
`git worktree`), inline code, an edit call whose text has moved, and a shell search of the tree — and the one document
that speaks to the child before its first tool call names none of them, so a child learns each by being refused and
pays a turn for the lesson in the item's own context.

**The item's own count** (the tracker entry #799, over the items' `events.jsonl` for the twenty attempts of
2026-11-06): 55 of 161 tool calls came back refused or rejected — 17 approvals no unattended process can be asked for
(`git stash` 6, `git apply` 3, `git checkout` 2, `git add` 2, `git worktree` 1, 3 paths outside the working
directory), 16 refusals of inline code (13 `python3 <<`, 1 `python3 -c`, 2 command substitutions) and 22 `edit` calls
rejected for text that had moved (`could not find the exact text` 13, `found N occurrences` 8, `no changes made` 1).
Three of the four families are work a worker reasonably believes is its own: it commits and stashes because a
red-on-HEAD proof reads like it needs them, reaches for `python3` because the change it writes carries numbers, and
edits text it read twenty turns ago — which is what the prompt's own section one down asks of it ("a file you have
read once is cited, not read again").

**The red test, on the base commit `26b4140bb4b5ffbeb7c4d0467b3fac94dfcc96dd`** (`prompt#thePhasePromptNamesWhatItsShellRefuses`,
`tools/loop-selftest.sh --case prompt#thePhasePromptNamesWhatItsShellRefuses`, output kept as
`evidence/red-on-HEAD.log`, tally as `evidence/red-on-HEAD.xml`, `tests=1 failures=1`):

```
prompt#thePhasePromptNamesWhatItsShellRefuses|   the worker's prompt names what its own shell refuses: 'What your own shell and tools refuse' not found in: # Worker — one item, phases A–D
```

**Owning capability and requirement.** `openspec/specs/agent-issue-loop/spec.md`, requirement "A child carries its own
route, and not the project's whole document": the prompt carries what the child needs, and what its own shell refuses
is the half of that it does not carry. The delta adds the requirement beside it. Guideline section:
`guidelines/BuildGate.md` §2a "Agent iteration protocol" — the protocol a child follows, and the one that already says
"look then measure before changing code" and "batch independent questions"; `AGENTS.md`'s "Agent iteration loop (measure
first)" row is the route to it.

**Surface, counted before the plan** (`./tools/look.sh 'What your own shell and tools refuse' --files` after the
block's text existed in the three skeletons): 5 matches in 4 files — the three skeletons a child is handed
(`worker.md`, `worker-gate.md`, `worker-tail.md`) and the case that asserts them. The rule's pointer already stood in
`worker.md:51`; the verb list it named was 5 of the policy's 8, which is the drift the case now holds.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `./tools/look.sh 'What your own shell and tools refuse' --files`
