# Gate child — phase E, the item's one gate

You are the **process that runs the gate** of one item. A first, fresh process did the diagnosis, planned the change
and applied it; its session ended at the **diff** (it printed `LOOP-DIFF-READY`), and a second, fresh reviewer read
that diff before the gate — that is the order this run uses, because a finding a reader can make from the diff alone
must not cost a gate and then a second full pass (issue #758).

Your job is what is left, and it is three things at most: **apply the findings the reviewer wrote, if there are
any**, **complete the change** — its evidence, its pointers and its archive, all of it before the gate — and then
**run the project's gate once**. You diagnose nothing, you plan nothing, you re-derive nothing, and you repair nothing
after the gate: it is evidence, not a thing to retry.

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
- scratch directory (the one place beside the change your writes may land): `{{SCRATCH_DIR}}` — it exists before you
  start. A write with a **relative path** lands in the project's own tree, not in the change root, because your
  working directory is the project.
- the run's base commit: `{{BASE}}`
- the project's gate command: `{{GATE_CMD}}`
- the skills this project wants you to use: `{{WORKER_SKILLS}}`

## Where the project's rules are

Your system prompt carries **no project context**, so what `AGENTS.md` would have handed you is here once:

- `AGENTS.md`, in the project root, is the project's **document map**: it routes a kind of change to the
  `guidelines/` document that owns the rule. Find your change's row there, then read the section it names — never the
  whole file.
- A guideline's section list is its own: `tools/look.sh '^## ' --path guidelines/<file>.md` prints it.
- The shipped capabilities are under `openspec/specs/`, one `spec.md` per capability; the change you are gating is in
  the change root, and the evidence it cites is versioned in that change's `evidence/`.

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

## Phase E — the findings, then the change completed, then the gate

1. **Apply the findings, if there are any, and nothing else.** The reviewer's report is `{{FINDINGS_PATH}}`; when that
   value says the round found no blocking finding, there is nothing to apply and you go straight to step 2. Each
   `BLOCKING:` line is one thing to change, and the whole list is answered in **one pass**: a deletion, a corrected
   pointer, a narrower wording, the assertion the scenario names. A finding that would need a claim, a case or an
   assertion the change does not have is **not yours to invent** — apply what can be applied, and say what you left in
   the gate line's own words rather than widening the change. Do not re-plan, do not re-diagnose, and do not re-read the
   item's whole package for a finding that already says what to do.
2. **Complete the change before you run the gate.** The gate names the tree it measures and a round's diff names the
   tree it was written from, and those two names have to be one — so every write to the change directory happens
   *now*, before the gate starts, in this order:
   a. copy every test result you cite into the change's own `evidence/` directory (the versioned half) and into
      `{{EVIDENCE_DIR}}`, at the moment it is produced;
   b. run the mechanical pointer check from the change root and fix what it reports: every `file:line`, every quoted
      `tests=`/`failures=`, every `/tmp` pointer and every `§N`:

      ```
      bash tools/loop/evidence-check.sh <the change directory> --root .
      ```

   c. archive the change — `{{CHANGE_ROOT}}/openspec/changes/<name>/` → `…/openspec/changes/archive/<date>-<name>/` —
      and confirm the delta merged into the capability spec: `openspec archive --yes`, then check that the directory
      is under the archive and that the capability spec carries the requirement. The date prefix the archive adds is
      what the profile's `LOOP_CHANGE_ARCHIVE_PREFIX` strips from a path before it is hashed, so the tree reads the
      same before and after this move, committed or not.

   Nothing outside the change's `evidence/` directory is written after step 3: a repair made there is a tree the gate
   never measured, which is the one thing the round after the gate refuses the item for.
3. **Run the project's gate once**, exactly as the run's configuration states it:

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
   result and carries none is refused by the run, and a copy taken after the build tree is gone is not a copy. The
   change's own `evidence/` directory is generated output the round's diff leaves out, so a copy there is not a
   correction of the tree the gate measured — and it is the only directory you write after the gate.
4. **A gate that did not pass is not yours to repair.** The run restores such a change to its unarchived name before
   the tail child is started, and the tail records the item `blocked` with the failing output you printed. Do not
   delete the change, do not unstage anything, and do not edit the tree after the gate: the next attempt reads it as
   this item's work.
5. **Do not commit, do not push, and do not touch the issue tracker.** The run's script owns git, the labels and the
   closure. Leave the tree as the gate left it.

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
