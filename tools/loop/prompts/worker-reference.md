# Worker reference — the blocks a worker consults, and the numbers behind them

This file is rendered for the item beside the worker prompt and handed to the child with it, and the prompt names it:
read it **once**, before your first tree question, and quote it from there rather than reading it again. It is not
re-sent with the prompt — every turn of your session re-reads the prompt alone — so what stands below is the long
form of the three blocks the prompt carries in short: where the project's rules are, what your own shell and tools
refuse with the exemptions, and how a measurement is taken and how independent calls are batched. Nothing here is
optional, and nothing here is a second rule: each paragraph is the text the prompt used to carry whole (issue #1082).

## Where the project's rules are

Your system prompt carries **no project context**: this child is started without the project's context files, so
what `AGENTS.md` would have handed you on every turn is here instead, once. It is the same routing, and it is
the whole of it:

- `AGENTS.md`, in the project root, is the project's **document map**: it routes a kind of change to the guideline
  that owns the rule. Find your change's row there, then read the section it names — never the whole file.
- Every guideline is one document under `guidelines/`, and its section list is the document's own:
  `tools/look.sh '^## ' --path guidelines/<file>.md` prints it, and `tools/look.sh --section '<regex>' --path
  guidelines/<file>.md` prints the one section whose heading the regex matches, so you read the section that owns the
  rule and never the whole document. That wrapper is what a tree question is answered with here: the shell form of it
  is refused by the policy every child is started with (`tools/pi-shell-refusal.mjs`, its verb list in the block
  below), and a tool that fails on your host is not an answer about the tree either — the wrapper is a call, not a hint.
- The shipped capabilities are under `openspec/specs/`, one `spec.md` per capability; the change you plan lives in
  `openspec/changes/<change-name>/`, and the evidence it cites is versioned in that change's `evidence/`.
- The rules a phase like yours breaks most often are the ones measuring them: what the gate's result means and
  what a red run is, the test constraints, and the rule that **no log or diagnostics line carries a coordinate** —
  an object, a database or file name, a magnification, a pixel, an accuracy or a bearing instead.

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
- **A shell search of the tree is refused**: the verbs `grep`, `cat`, `head`, `tail`, `sed`, `ls`, `find`, `wc` and
  `awk` over the tree are `tools/look.sh` (a pattern over the whole tree), `ctx_read` (one file, or a range of it —
  the range is its own `offset` and `limit`, not a shell call) and `ctx_ls` (a directory). A quiet `sed`'s line range
  and a `tail`'s range over a file of the tree are that same read, and are refused with it. Not refused: a range read
  of a log outside the tree or of one of your own item files, a `sed` that substitutes or writes, and a filter of
  another command's output.

## One call per measurement, and one turn for the independent ones

Run `{{MEASURE_CMD}}` once, from the change root, before you plan. It prints the change's own numbers as one
block — the paths it touches, the checks that pathspec owes (with the command that runs each), the tallies the
tree already carries, the project's declared case count with the command that printed it, the gate's own
measured timing, and the change's spec scenarios counted against the cases that name them. **It runs none of
them**: the gate is run once, by `{{GATE_CMD}}`, and a number the block lacks is a defect in the block rather
than a reason to walk the tree by hand.

**Which selection is whose.** The `LOOP-MEASURE-CHECK` lines are the gate's: the gate owns the full selection
its own step names — the child that runs it once, or your own last phase when the run makes the gate yours — and a
check you run to prove one of them is that selection paid twice. Yours is the named red case and the cases your
delta reaches: run those while you iterate, and cite the block for every other check instead of running it. Two
things stay yours beside that split: the check that stands in for a delta which compiles nothing — a docs, spec,
skill or backlog delta runs the structural check of the files it edits and names it in the task — and the number
the round after you asks the record to carry, which is the block's line quoted, not a check run again.

**A number the change quotes is the output of a command in its record.** The block's lines are that command's
output for the facts it carries, so quote them. A number the block does not carry is one you took yourself, and
then the command that took it belongs in the change's evidence beside the number.
A number with neither is not a claim: leave it out of the change — a reviewer cannot tell a measurement from a
guess, and a wrong number it cannot check turns into a refusal and a whole second round (issue #244).

Two answers the block cannot give by guessing, and asks for instead:

- **Which change.** With one change open it measures that one; with none, the newest archived change. With two
  open it prints `LOOP-MEASURE-CHANGE ambiguous` and names them rather than measuring a stranger's delta — pass
  `--change <name>` then, and on a correction round pass the name of the archived change you are correcting.
- **The tallies after a build tree is gone.** The block reads tallies a run already wrote, under the gate's own
  directory and the change's `evidence/`. A `clean` empties the first, and then the block carries no
  `LOOP-MEASURE-TALLY` line at all, which is not the same as a green number: cite the copy in `evidence/`.

Every line of the block carries the prefix `LOOP-MEASURE`. Quote those lines into your report and into the
change's evidence instead of re-deriving them. The declared case count is `LOOP-MEASURE-SUITE cases=`; the
gate's own timing is `LOOP-MEASURE-GATE seconds=`. Neither is counted again by hand.

**A file you have read once is cited, not read again.** When a later step needs a fact from a file already in
your transcript, read the line you have; a second read of the same path is a second model turn and buys
nothing. The same holds for a command: a measurement you have taken is quoted, not re-taken. Measured on five
items of this loop (issue #246): 8 to 49 of every item's 22 to 79 file reads were a second read. Prose did not hold
it: `tools/pi-trim-result.mjs` counts each second read of a path you were handed — `metrics.repeatReads` in the
report — and the recorded arm (`LOOP_REPEAT_READ=refuse`) answers it with the earlier result's line range, not the
text. And a path
has a count bound: past `LOOP_REPEAT_READ_REFUSALS` reads of it in one session the same line is the answer whatever arm
the run records, and it names how many times you have asked and the turn the result you should quote instead came in —
so a third ask for one file is the signal to quote the result you hold, not to ask again.


**Independent calls belong in one turn, not in one turn each.** A read, a search, a `git log` and a `jq` that do
not need each other's output are one turn's work: issue them together and read the four answers once. Every turn
re-reads the whole conversation, so the second call of a pair paid a turn costs the item its context again without
buying a fact — measured over this loop's archive (227 items, 2026-11-03): 36 825 tool calls in 33 183 turns,
**1.11 calls per turn**, 78 k tokens per turn and 97 % of the spend charged as cache read, and the run reports the
ratio per item and per run beside the recorded floor `LOOP_ITEM_CALLS_PER_TURN_MIN`, naming an item that falls
below it. A chain of single-call turns is what turns an item into a decision: it is the same budget spent as one
long conversation, and the run's turn bound for this role (`LOOP_CHILD_TURNS_MAX_WORKER`) ends the child that runs
into it. Batch
the lookups, and split a phase at its first gate (`tools/loop/phase.sh`) rather than carrying one session past it.
