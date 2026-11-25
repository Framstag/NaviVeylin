# The route a report names is a line a case reads

LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

Item #1081, the residue of #974 after #1080 landed C6. `tools/loop/prompts/worker.md:205` (Phase A step 4) says only
"Name the owning capability and the owning guideline section. Route with `AGENTS.md`'s Documentation Map; never read a
guideline in full." — an instruction to write an English sentence, and nothing parses it: the report block carries no
token for it, so of the 200 items that wrote a work report 171 cite a `guidelines/…` document and only 109 name a
`§N`. The same change's own gate found the second half: no case in `tools/loop/tests/loop-cases.sh` runs C6, so the
route is asserted by the tool's self-test fixtures and by nothing that reads a project — and the attempt that wrote
that case declared a `# files:` line narrower than the files it reaches
(`.pi/issue-loop/items/974/gate.log:9`: `check#aRouteSectionResolvesAgainstItsDocument:tools/pi-child-events.sh(new_project)`).

**Root cause, in one sentence:** the anchored line C6 resolves was written into the tool and its spec and nowhere that
owes it — the skeleton that asks a child for a route asked for prose, and the suite that reads a project never ran the
check.

**The red test:** on HEAD the skeleton's Phase A step and report block carry no `LOOP-ROUTE:` line, so the assertion
this change adds to `prompt#thePhasePromptsCarryTheirOwnRoute` — the anchored form, in the step and in the block —
fails there and passes here (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`; the run is taken on `worker.md`
restored byte for byte, sha256 `3db85287…` on HEAD, `ec1eea44…` before and after).

**What this change is not:** it does not touch `tools/loop/evidence-check.sh`, `tools/loop/loop.conf`,
`openspec/specs/agent-issue-loop/spec.md`'s existing requirement or the migration's route rule — #1080 landed those,
and this delta only adds the obligation that the skeleton states the line and that a case reads it.
