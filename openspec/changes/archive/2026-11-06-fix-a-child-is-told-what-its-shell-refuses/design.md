# Design — a child is told what its own shell and tools refuse

## Root cause

The child's only prose is its phase prompt, and the section that stands in for the project's context files routes it
to the rules and never says what the process itself refuses (`tools/loop/prompts/worker.md:39-56` on the base commit;
`worker-gate.md:31-41` and `worker-tail.md:32-42` the same, one bullet shorter). Each refusal therefore arrives as a
tool result, inside the item's own context, and the family it belongs to stays invisible: the run commits, so a
`git commit` by the child is refused for an approval nobody can give; the shell policy refuses a tree search, so the
recipe in the prompt had to be a `tools/look.sh` call (#796); an edit is refused for text that has moved since it was
read — which is what the prompt's own "a file you have read once is cited, not read again" asks the child to do.

## The single fix

One block, `## What your own shell and tools refuse`, in the three skeletons a child can be handed for work that
writes the tree — `worker.md`, `worker-gate.md`, `worker-tail.md` — with four items: the commit that is the run's, the
git calls refused for approval, inline code and the command a number is taken by instead, and the edit that needs the
text as it stands now. Its last item is the shell policy's verb list, and `worker.md`'s existing clause one section up
stops naming five of the eight verbs and points at the block instead: one list in the module, three prose copies of it,
which the case holds to that list.

The prompt's list is a copy in prose; the case's is not. `prompt#thePhasePromptNamesWhatItsShellRefuses` reads
`REFUSED_VERBS` out of `tools/pi-shell-refusal.mjs` through a probe it writes in its own scratch directory, and
requires every verb it carries to be named in the rendered prompt. A policy that gains a verb, or a prompt that drops
one, fails the case instead of a run.

## Blast radius

Three prompt skeletons and the case file; no engine script, no profile field, no compiled file. The rendered prompt
grows by ~20 lines and no placeholder is added, so the rendering, the placeholder check
(`run#childUsageIsRecordedPerItem`) and the route check (`prompt#thePhasePromptsCarryTheirOwnRoute`) are unaffected —
the latter is what proves every path the block names resolves. The reviewer's skeleton is untouched: it is bounded to
reading, so a block about commits, edits and refusals is not its business.

## Rollback

Delete the block from the three skeletons and the case `prompt#thePhasePromptNamesWhatItsShellRefuses` with it; the
clause in `worker.md` returns to naming the verbs itself. Nothing else reads the block, so no behaviour, no field and
no measurement depends on it.

## Alternatives the evidence eliminated

- **A section in a guideline instead.** `guidelines/BuildGate.md` §2a already carries the iteration protocol, but the
  child is started without context files and reads a guideline only when it is routed there; a rule about what the
  child's own process refuses cannot be found by a child that has not been refused yet.
- **A sentence in the shell policy's refusal message.** The policy names the tool that answers a tree search
  (`REFUSED` + the call), and that is the right shape for the one family it owns; it cannot reach the git approvals,
  the inline code or an edit call, which are the agent's own and not this project's policy.
- **Teaching it by the count.** The item's own numbers (55 of 161) are the tracker entry's, over the items'
  `events.jsonl`; repeating them in the prompt would be a number a child cannot act on, and a number with no
  command behind it in this change's record.

## Open decisions

None
