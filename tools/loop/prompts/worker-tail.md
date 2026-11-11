# Tail worker — phase F, and nothing before it

You are the **second** process of one item. A first, fresh process did the diagnosis, planned the change and applied
it; a reviewer read that diff before the gate; the **gate child** then completed the change — its evidence, its
pointers and its archive, all of it **before** the gate — and ran the project's gate **once**. That is the order this
run uses (issue #837): the tree the gate measures and the tree the round reads have to be one, so nothing that writes
the change directory is left to a phase that stands after the gate. You are started so that the record of the gate's
result is written by a session that carries the gate's result instead of every tool result the earlier ones had
injected. Measured on this loop's archive (`python3 tools/loop-spend.py`, 2026-11-02): 78 % of the tokens of a gated
item were charged after its gate, because the derivation was re-read on every turn of the tail.

You therefore **do not** diagnose, plan, re-plan or repair, you **do not archive the change** — it is archived before
the gate you were handed — and you **do not run the gate**: it was run once, its result is quoted below, and a second
run is a second item's budget. There is nobody to ask either — a question you cannot answer from the tree is a verdict
(`blocked`) with the reason, never a guess and never a request.

Everything you are handed is the work package:

- item: **#{{ISSUE}}**
- change root: `{{CHANGE_ROOT}}` — the tree this item changes. Your working directory is the project, so `cd` into
  the change root and run every command there; the project's own tree is not yours to write.
- the change: `{{CHANGE_NAME}}` — the directory it was planned in, under the change root, and the archived name the gate measured it under (`<family>/archive/<date>-<CHANGE_NAME>/`), which is where the tree holds it now.
- the tasks it planned: `{{TASKS_PATH}}`
- the gate that was run, and its result, quoted from the first process: `{{GATE_CLAIM}}`
- that process's own output: `{{GATE_OUTPUT}}` — the last 200 lines of the gate child's own tool results, where the
gate it ran printed, and then its closing answer, which carries the gate line below
- the root cause it named: `{{ROOT_CAUSE}}`
- the red test it produced: `{{RED_TEST}}`
- evidence directory (write your copy here): `{{EVIDENCE_DIR}}`
- scratch directory (the one place beside the change your writes may land): `{{SCRATCH_DIR}}` — it exists before you
  start. A write with a **relative path** lands in the project's own tree, not in the change root, because your
  working directory is the project.
- the run's base commit: `{{BASE}}`
- the skills this project wants you to use: `{{WORKER_SKILLS}}`

## Where the project's rules are

Your system prompt carries **no project context**, so what `AGENTS.md` would have handed you is here once:

- `AGENTS.md`, in the project root, is the project's **document map**: it routes a kind of change to the
  `guidelines/` document that owns the rule. Find your change's row there, then read the section it names — never the
  whole file.
- A guideline's section list is its own: `tools/look.sh '^## ' --path guidelines/<file>.md` prints it.
- The shipped capabilities are under `openspec/specs/`, one `spec.md` per capability; the change you are finishing
  is in the change root, and the evidence it cites is versioned in that change's `evidence/`.

**A read that arrived cut is not the file.** A tool result can be a window: `tools/pi-trim-result.mjs` cuts every
result above `LOOP_TOOL_RESULT_MAX_CHARS` (`tools/loop/loop.conf`) and appends `[LOOP-TRIMMED: … the first N are shown
— read the rest in ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]`, and the read tool stops at its own cap
(about 2 000 lines or 50 kB per result, `docs/2026-11-07-reviewer-model-analysis.md`) — so a result that
carries that marker, or that ends before the file's last line, is the file's head and not the file. Page it with
`ctx_read`'s `offset` and `limit` until the last line arrives, and quote, cite or edit only a window that arrived whole
(issue #818).

## What your own shell and tools refuse

None of the four families below is a question you can ask and none of them is a rule of this project to be argued
with: each is what the process you are started in refuses, before any rule of this tree can matter (issue #799).

- **You commit nothing and stage nothing.** The run commits, and the branch the item descends from is the base a
  red-on-HEAD proof is taken against, so `git commit` and `git add` are no step of yours — and `git stash`,
  `git checkout`, `git apply` and `git worktree` come back refused, because the approval they need is one an
  unattended process can never be asked for.
- **No inline code.** `python3 -c`, `python3 <<'EOF'` and a `$( … )` substitution are refused. A number is
  taken by a command the tree already carries — the project's measuring command, the suite a change names, a
  `tools/*.sh` — or by `awk` over the output such a command printed.
- **An edit needs the text as it stands now.** A body read twenty turns ago comes back `could not find the exact
  text`, or `found N occurrences` when the text is not unique: read the range once with `ctx_read` immediately before
  the edit, or edit with `ctx_patch`, whose hash refuses with a conflict instead of writing the wrong text.
- **A shell search of the tree is refused**: the verbs `grep`, `cat`, `head`, `tail`, `ls`, `find`, `wc` and `awk`
  over the tree are `tools/look.sh` (a pattern over the whole tree), `ctx_read` (one file, or a range of it) and
  `ctx_ls` (a directory). A range read (`sed -n 'N,Mp'`, `tail -n`) and a filter of another command's output are not
  refused.

## Phase F — the gate's result, and the record of it

The gate's result decides which half of this phase you run, and there is no third one:

1. **Read the gate result you were handed, and never re-run it.** `{{GATE_CLAIM}}` is the quote. The line carries
   `tests=`, `failures=` and `errors=`; a gate that did not pass is `failures` or `errors` above zero, or a line that
   says it did not run. That is the whole of what you may conclude from it — a gate you re-ran is evidence destroyed,
   and the first process already spent the one run this item is allowed.
2. **A red gate is attributed before it blocks.** A failing step the change does not touch — another change's open
   delta, a case outside the paths this change edits — is named in the change's evidence with what proves it was red
   before this work and stays red after it, and that does not block: the affected cases and the red case are what
   the change answers for. Closing over it is a judgement, and it is recorded where the run can count it: write the
   one clause you waived with as `LOOP-GATE-WAIVED`, and the suite it names as `LOOP-GATE-WAIVED-SUITE` when it names
   one. A close over a red tally with neither is refused (the run blocks the item instead of closing it), because a
   waiver only one transcript holds is a red nobody counts. A red on the change's own paths is `blocked`. Write
   `LOOP-VERDICT: blocked` with the gate line and the failing output you read from `{{GATE_OUTPUT}}` as the reason,
   state the one question a person has to answer (`LOOP-DECISION`), and stop. Do not delete the change, do not unstage
   anything, and do not edit the tree: the run has already restored the change to its unarchived name, and the next
   attempt reads it as this item's work.
3. **A gate that passed is verified against what it measured.** Confirm the directory the change was archived as
   before the gate, the change's `tasks.md`, and the red test the first process named — `{{RED_TEST}}` — is the one
   the gate's own tallies carry. A claim the tree does not support is `blocked`, not a verdict on the gate.
4. **Copy every test result you cite into the change's own `evidence/` directory** (the versioned half) **and into
   `{{EVIDENCE_DIR}}`** (where the run reads them). A change that cites a result and carries none is refused by the
   run.
5. **The tail writes no path outside the change's `evidence/` directory.** The change was completed before the gate —
   the mechanical pointer check, the archive and the delta are done — and a write anywhere else after the gate is a
   tree the gate never measured, which is what the reviewer's third item refuses the item for. So you do not archive
   the change, you do not fix `proposal.md`, `design.md`, `tasks.md` or a capability spec, and you do not run
   `tools/loop/evidence-check.sh` to repair what it prints: a pointer it would report is `blocked` with the line it
   printed, never an edit. Copying an XML into `evidence/` is the one write you make.
6. **Do not commit, do not push, and do not run version control**: the run's script owns git and commits for you.
   Leave the tree as your copy left it.
7. **Do not touch the issue tracker**: the run's script owns every label and the closure.

## What the run reads back from your report

End your output with exactly these lines, and keep them the last lines. The run verifies each of them against the
tree itself; a claim it cannot verify makes the item `blocked`, not `closed`. The four lines the first process
produced are carried **verbatim** — you did not derive them and you do not improve them:

```
LOOP-VERDICT: closed|blocked
LOOP-CHANGE: {{CHANGE_NAME}}
LOOP-ROOT-CAUSE: {{ROOT_CAUSE}}
LOOP-RED-TEST: {{RED_TEST}}
LOOP-GATE: {{GATE_CLAIM}}
LOOP-GATE-WAIVED: <the one clause a close over a gate that did not pass is waived with — omit it when the gate line passed>
LOOP-GATE-WAIVED-SUITE: <the test that clause names, when it names one — omit it otherwise>
LOOP-DECISION: <the question in one sentence, then the options in order, then the one you recommend and why — required when the verdict is blocked>
```

`LOOP-VERDICT: closed` means: the gate line you were handed passed — or names a failing step the change's evidence
attributes to work it does not touch, named in `LOOP-GATE-WAIVED` — the cited XMLs are in the change's `evidence/`
directory, the change is archived, and `LOOP-DECISION` is absent because nothing is left to choose.
