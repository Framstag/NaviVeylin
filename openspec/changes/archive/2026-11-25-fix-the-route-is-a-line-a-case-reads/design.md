# Design: fix-the-route-is-a-line-a-case-reads

LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

## Root cause

The route C6 resolves exists in three places that owe it, and two of them were missing. The tool resolves it
(`tools/loop/evidence-check.sh:199`, landed by #1080), the spec requires it, and then:

- `tools/loop/prompts/worker.md:205` asked the child for a sentence — "Name the owning capability and the owning
  guideline section" — so the report block it ends with carries no route token at all, and a reader cannot tell a
  report that resolved a `§` from one that only named a document (171 of 200 reports name a `guidelines/…` file, 109
  name a `§N`);
- `tools/loop/tests/loop-cases.sh` holds no case for C6, so the check is reached only by
  `tools/loop/evidence-check.sh --selftest`'s fixtures and never by a case that builds a project and reads a change
  in it. The attempt that wrote one (#974) declared `# files:` without `tools/pi-child-events.sh`, which
  `new_project` starts, and the declaration case — the cheapest check such a delta owes — was not run before the suite
  (`.pi/issue-loop/items/974/gate.log:9`).

## The fix

1. `tools/loop/prompts/worker.md`: Phase A step 4 names the anchored form `LOOP-ROUTE: guidelines/<doc>.md §<id>` and
   says it goes in the report block and in the change's own markdown; the report block gains the line; one paragraph
   under the block says the markdown copy is the one C6 reads.
2. `tools/loop/tests/loop-cases.sh`: `check#aRouteSectionResolvesAgainstItsDocument`, declared in `LOOP_SHELL_CASES`
   and dispatched, built on `new_project` and driving `evidence-check.sh` over a project whose document carries
   `## 1. The queue` and `## §2a Agent iteration protocol`: the resolved count, the refusal of `§99` with
   `its sections are: §1 §2a`, the dotted shape, the quoted form read as prose, the `warn` arm and the `fail` arm. Its
   `# files:` line names `tools/pi-child-events.sh`; the arm `driver#everyCaseDeclaresTheFilesItCanReach` was run
   before anything else and passes.
3. `tools/loop/tests/loop-cases.sh`, `prompt#thePhasePromptsCarryTheirOwnRoute`: two assertions on the skeleton — the
   anchored form, and `` `LOOP-ROUTE` is the line `` — which are the red case, because both are false on HEAD.
4. `tools/loop/tests/loop-mutations.sh`: records `974.1` (`if grep -qxF "$id" <<<"$ids"` → `if true`, so any id
   resolves) and `974.2` (`if [ "$route_mode" = "fail" ]` → `if true`, so the missing route is refused under `warn`),
   each pointing at the new case.
5. `guidelines/Backlog.recipes.md`, the paragraph that states the route rule (the section this change's
   `LOOP-ROUTE` names): it now also names the skeleton's line and the case.

## Blast radius

`tools/loop/prompts/worker.md`, `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh` and one
paragraph of `guidelines/Backlog.recipes.md`. Nothing compiled, nothing the app links, no engine script: the engine
that resolves a route is unchanged, and the change adds the reading of it and the line that owes it.

## Rollback

Revert the four hunks: the skeleton returns to asking for a sentence, the case and its two records disappear with it,
and the guideline paragraph comes back out. Nothing else in the tree reads either addition, so a revert leaves the
engine and its spec exactly as #1080 landed them.

## Alternatives the evidence eliminated

- **Extend `evidence-check.sh --selftest` only, and skip the suite case.** Rejected: the self-test's fixtures are the
  tool's own; a route resolved against a project's document is what a run reads, and no case built one. The suite is
  where a delta that owns `tools/loop/**` is measured, so a check reachable only from the self-test is a check the
  gate can leave red.
- **A second reader of the `# files:` declaration inside the new case.** Rejected: `driver#everyCaseDeclaresTheFilesItCanReach`
  owns that reading (the issue names the omitted token), and a second reader is the defect that case exists to stop.
