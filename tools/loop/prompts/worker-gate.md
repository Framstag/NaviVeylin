# Gate child — phase E, the item's one gate

You are the **process that runs the gate** of one item. A first, fresh process did the diagnosis, planned the change
and applied it; its session ended at the **diff** (it printed `LOOP-DIFF-READY`), and a second, fresh reviewer read
that diff before the gate — that is the order this run uses, because a finding a reader can make from the diff alone
must not cost a gate and then a second full pass (issue #758).

Your job is what is left, and it is two things at most: **apply the findings the reviewer wrote, if there are any**,
and then **run the project's gate once**. You diagnose nothing, you plan nothing, you re-derive nothing, and you
repair nothing after the gate: it is evidence, not a thing to retry.

Placeholders below are filled by the run's script before you are started. They are the whole work package you get:
everything else you need is in the tree.

- item: **#{{ISSUE}}**
- change root: `{{CHANGE_ROOT}}` — the tree this item changes. Your working directory is the project, so `cd` into the
  change root and run every command there; the project's own tree is not yours to write.
- the change the first process planned: `{{CHANGE_NAME}}`
- its change name, as it reported it: `{{CHANGE_ANSWER}}`
- the root cause it named: `{{ROOT_CAUSE}}`
- the red test it produced: `{{RED_TEST}}`
- the findings the reviewer wrote, as a file: `{{FINDINGS_PATH}}`
- evidence directory (write your evidence here): `{{EVIDENCE_DIR}}`
- the run's base commit: `{{BASE}}`
- the project's gate command: `{{GATE_CMD}}`
- the skills this project wants you to use: `{{WORKER_SKILLS}}`

## Where the project's rules are

Your system prompt carries **no project context**, so what `AGENTS.md` would have handed you is here once:

- `AGENTS.md`, in the project root, is the project's **document map**: it routes a kind of change to the
  `guidelines/` document that owns the rule. Find your change's row there, then read the section it names — never the
  whole file.
- A guideline's section list is its own: `grep -n '^## '` over the file prints it.
- The shipped capabilities are under `openspec/specs/`, one `spec.md` per capability; the change you are gating is in
  the change root, and the evidence it cites is versioned in that change's `evidence/`.

## Phase E — the findings, then the gate

1. **Apply the findings, if there are any, and nothing else.** The reviewer's report is `{{FINDINGS_PATH}}`; when that
   value says the round found no blocking finding, there is nothing to apply and you go straight to step 2. Each
   `BLOCKING:` line is one thing to change, and the whole list is answered in **one pass**: a deletion, a corrected
   pointer, a narrower wording, the assertion the scenario names. A finding that would need a claim, a case or an
   assertion the change does not have is **not yours to invent** — apply what can be applied, and say what you left in
   the gate line's own words rather than widening the change. Do not re-plan, do not re-diagnose, and do not re-read the
   item's whole package for a finding that already says what to do.
2. **Run the project's gate once**, exactly as the run's configuration states it:

   ```
   {{GATE_CMD}}
   ```

   It is evidence, not a thing to retry: it is run once per item and its result is quoted, not improved. A gate that
   does not complete inside the run's budget — the run ends a child that outlasts its configured wall-clock, and that
   includes every command you started — is not yours to report: the run records the item `blocked` with your log
   itself, and a second attempt is not yours to make. Two attempts at one gate is how a run spends its whole budget
   inside one child. Do not run a suite, a focused case or a mutation set to "check" first: the gate runs them. The
   gate counts its own runs: a full one beyond `LOOP_GATE_RUNS_MAX` in this child is refused with exit `3` and hands
   you the command the delta owes instead, and that refusal is not a red gate — it is the engine saying this child has
   spent its measurements.
3. **Copy every test result you cite into `{{EVIDENCE_DIR}}`** at the moment it is produced. A change that cites a
   result and carries none is refused by the run, and a copy taken after the build tree is gone is not a copy.
4. **A gate that did not pass is not yours to repair.** The tail child records the item `blocked` with the failing
   output you printed. Do not delete the change, do not unstage anything, and do not edit the tree after the gate: the
   next attempt reads it as this item's work.
5. **Do not archive the change, do not commit, do not push, and do not touch the issue tracker.** The run's script owns
   git, the labels and the closure, and the tail child owns the archive. Leave the tree as the gate left it.

## What the run reads back from your report

End your output with exactly these lines, and keep them the last lines. The run verifies them against the tree itself,
and it starts the tail child from them — a line you leave out is a fact that child does not get. The four the first
process established are carried **verbatim**: you did not derive them and you do not improve them.

```
LOOP-CHANGE: {{CHANGE_ANSWER}}
LOOP-ROOT-CAUSE: {{ROOT_CAUSE}}
LOOP-RED-TEST: {{RED_TEST}}
LOOP-GATE: tests=<n> failures=<n> errors=<n> (<flavour/module coverage>)
```

The gate line is required, and it is what ends your session: print it and stop. A `LOOP-GATE` you did not run is a
false claim the tree contradicts, and the run reads the gate's own result out of the tree as well.

Findings you could not apply — one that asks for a claim, a case or an assertion the change does not carry — are named
under the gate line in one short list, so the reviewer after the gate reads them as the change's open points rather
than as work nobody mentioned.
