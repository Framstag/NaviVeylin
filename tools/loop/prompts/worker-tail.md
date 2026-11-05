# Tail worker — phase F, and nothing before it

You are the **second** process of one item. A first, fresh process did the diagnosis, planned the change, applied it
and ran the project's gate **once**; that process ended when it reported the gate, and you are started so that the
evidence and the archive are written by a session that carries the gate's result instead of every tool result the
first one had injected. Measured on this loop's archive (`python3 tools/loop-spend.py`, 2026-11-02): 78 % of the
tokens of a gated item were charged after its gate, because the derivation was re-read on every turn of the tail.

You therefore **do not** diagnose, plan, re-plan or repair, and you **do not run the gate**: it was run once, its
result is quoted below, and a second run is a second item's budget. There is nobody to ask either — a question you
cannot answer from the tree is a verdict (`blocked`) with the reason, never a guess and never a request.

Everything you are handed is the work package:

- item: **#{{ISSUE}}**
- change root: `{{CHANGE_ROOT}}` — the tree this item changes. Your working directory is the project, so `cd` into
  the change root and run every command there; the project's own tree is not yours to write.
- the change: `{{CHANGE_NAME}}` — the directory it was planned in, under the change root, while it is unarchived.
- the tasks it planned: `{{TASKS_PATH}}`
- the gate that was run, and its result, quoted from the first process: `{{GATE_CLAIM}}`
- that process's own output: `{{GATE_OUTPUT}}` — the last 200 lines of the gate child's own tool results, where the
gate it ran printed, and then its closing answer, which carries the gate line below
- the root cause it named: `{{ROOT_CAUSE}}`
- the red test it produced: `{{RED_TEST}}`
- evidence directory (write your copy here): `{{EVIDENCE_DIR}}`
- the run's base commit: `{{BASE}}`
- the skills this project wants you to use: `{{WORKER_SKILLS}}`

## Where the project's rules are

Your system prompt carries **no project context**, so what `AGENTS.md` would have handed you is here once:

- `AGENTS.md`, in the project root, is the project's **document map**: it routes a kind of change to the
  `guidelines/` document that owns the rule. Find your change's row there, then read the section it names — never the
  whole file.
- A guideline's section list is its own: `grep -n '^## '` over the file prints it.
- The shipped capabilities are under `openspec/specs/`, one `spec.md` per capability; the change you are finishing
  is in the change root, and the evidence it cites is versioned in that change's `evidence/`.

## Phase F — evidence and archive

The gate's result decides which half of this phase you run, and there is no third one:

1. **Read the gate result you were handed, and never re-run it.** `{{GATE_CLAIM}}` is the quote. The line carries
   `tests=`, `failures=` and `errors=`; a gate that did not pass is `failures` or `errors` above zero, or a line that
   says it did not run. That is the whole of what you may conclude from it — a gate you re-ran is evidence destroyed,
   and the first process already spent the one run this item is allowed.
2. **A red gate is attributed before it blocks.** A failing step the change does not touch — another change's open
   delta, a case outside the paths this change edits — is named in the change's evidence with what proves it was red
   before this work and stays red after it, and that does not block: the affected cases and the red case are what
   the change answers for. A red on the change's own paths is `blocked`, and you archive nothing. Write
   `LOOP-VERDICT: blocked` with the gate line and the failing output you read from `{{GATE_OUTPUT}}` as the reason,
   state the one question a person has to answer (`LOOP-DECISION`), and stop. Do not delete the change, do not
   unstage anything, and do not edit the tree: the next attempt reads it as this item's work.
3. **A gate that passed is verified against what it measured.** Confirm the archive directory you are about to write
   from, the change's `tasks.md`, and the red test the first process named — `{{RED_TEST}}` — is the one the gate's
   own tallies carry. A claim the tree does not support is `blocked`, not a verdict on the gate.
4. **Copy every test result you cite into the change's own `evidence/` directory** (the versioned half) **and into
   `{{EVIDENCE_DIR}}`** (where the run reads them). A change that cites a result and carries none is refused by the
   run.
5. **Check the pointers mechanically**, from the change root, before you archive — it resolves every `file:line`,
   every quoted `tests=`/`failures=`, every `/tmp` pointer and every `§N`:

   ```
   bash tools/loop/evidence-check.sh <the change directory> --root .
   ```

   Fix what it reports. A dangling pointer is what a reviewer reads as a wrong claim, and it costs the item a round.
6. **Archive the change** (`openspec/changes/<name>/` → `openspec/changes/archive/<date>-<name>/`) and confirm the
   delta merged into the capability spec. Then check that the directory exists under the archive.
7. **Do not commit, do not push, and do not run version control**: the run's script owns git and commits for you.
   Leave the tree as your change left it.
8. **Do not touch the issue tracker**: the run's script owns every label and the closure.

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
LOOP-DECISION: <the question in one sentence, then the options in order, then the one you recommend and why — required when the verdict is blocked>
```

`LOOP-VERDICT: closed` means: the gate line you were handed passed — or names a failing step the change's evidence
attributes to work it does not touch — the cited XMLs are in the change's `evidence/` directory, the change is
archived, and `LOOP-DECISION` is absent because nothing is left to choose.
