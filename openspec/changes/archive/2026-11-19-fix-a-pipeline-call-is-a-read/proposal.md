# A call in pipeline position is not a census read

`tools/loop/tests/loop-cases.sh:18031` is the derivation `tools/loop/shared.tsv` is compared against: it reads every
engine file twice (an assignment pass and a mention pass) and reports the names one file assigns and another reads.
Whether a mention is a read is `is_call` (`:18031`-`:18051`), which normalises `;|&(){}[]` to spaces and then accepts
a name only as the first word of the line, after `CMDSUB|if|then|elif|else|while|until|do|time|!`, or after `[=$]`.
A separator that opens a command is not the whitespace it sits in: `a | name` normalises to `a   name`, the word
before `name` is `a`'s last word, and the call is not a call at all. The same helper is the second copy at
`:18226`, where the source-order check asks which file calls which.

The consequence is not one missing row. With the derivation as the base ships it, `driver#theSharedNamesAreDeclared`
reported `ok` on a declaration three of whose lines are wrong by that file's own header: `loop_drop_ignorable_writes`
(the name this item names, called in `project_state_diff`'s pipeline and twice in `worktree.sh`'s
`git status --porcelain -uall | loop_drop_ignorable_writes untracked`) had no row, `loop_shaping_verdicts` (called
after `&&` in `tools/loop/labels.sh:153`) had no row, and `loop_die`'s row named six of its nine readers — `measure.sh`,
`phase.sh` and `report.sh` all call it as `|| loop_die …`. `driver#aCallToALaterFilesFunctionFails` was the same
reading from the other side: a pipeline call to a function only a later file defines was accepted.

**Root cause, in one sentence:** `is_call` read the list separator a command is opened by as the whitespace it sits
in, so the name that follows it was compared against the previous command's last word instead of being read as the
command itself.

**The red test:** `driver#aCallInPipelinePositionIsARead` fails with `is_call` as the base ships it — record `963.1`
puts exactly that reading back (`" SEP "` → `" XSEP "`, a marker no predecessor rule accepts) — and reports
`a call in pipeline position is a read of the name: expected 'shared_fn<TAB>a.sh<TAB>b.sh', got ''`
(`evidence/red-on-HEAD-pipeline-call.log`; the run's own line is transcribed as
`evidence/red-on-HEAD-pipeline-call.xml`, `tests=1 failures=1 errors=0`, `2026-11-19T01:49:28Z`).

**The owning requirement:** `agent-issue-loop` — "Every name two engine files share is declared in one place"
(`openspec/specs/agent-issue-loop/spec.md:5111`), whose scenario "The declaration covers what the files share" is
`driver#theSharedNamesAreDeclared`; and, for the second copy of the helper, "The source order is declared and cannot
be violated silently" (`openspec/specs/agent-issue-loop/spec.md:5152`), whose scenario is
`driver#aCallToALaterFilesFunctionFails`.

**The owning guideline section:** `guidelines/Backlog.md` section 7 "The loop's procedure", the paragraph that names the
two declaration files and prices a delta against them (`guidelines/Backlog.md:363`-`368`); and
`guidelines/BuildGate.md` §2a "Agent iteration protocol", the paragraph that says an engine change is estimated from
`shared.tsv`'s row before it is iterated (`guidelines/BuildGate.md:68`-`73`).

surface: 3 files (`git diff --numstat` → `3 1 tools/loop/shared.tsv`, `53 4 tools/loop/tests/loop-cases.sh`,
`1 0 tools/loop/tests/loop-mutations.sh` — **57 insertions, 5 deletions**), 1 case added —
`bash tools/loop/tests/loop-selftest.sh --list | wc -l` → **491** declared cases (490 at the base) — and 1 mutation
record added — `bash tools/loop/tests/loop-mutations.sh --list | wc -l` → **426** records (425 at the base); 2 spec
requirements MODIFIED with 2 scenarios added.

## What Changes

1. **The separator is a command boundary.** In both copies of `is_call`, `;|&` stand in for a `SEP` word, and `SEP`
   joins `CMDSUB` and the shell's reserved words in the set of predecessors that open a command. One token covers
   `;`, `|`, `||`, `&&`, `|&` and a closing `&`, because the derivation needs the fact that a command starts there
   and not which separator started it.
2. **A case holds the reading** (`driver#aCallInPipelinePositionIsARead`): a stage of a pipeline, and the second
   operand of `&&` and of `||`, are reads of the name; and the row such a read owes is accepted by
   `layout_shared_refusals` in both directions, because a derivation that cannot see the read refuses the row as one
   nothing assigns.
3. **The declaration carries what the fixed derivation asks for** (`tools/loop/shared.tsv`): a row for
   `loop_drop_ignorable_writes` and one for `loop_shaping_verdicts`, both `lib.sh`-assigned, and `loop_die`'s row
   gains `measure.sh`, `phase.sh` and `report.sh`.
4. **The second copy is held too**: `driver#aCallToALaterFilesFunctionFails` gains a pipeline fixture and a `&&`
   fixture, each asserting the refusal that names the caller, the function and the file.
5. **Record `963.1`** puts the eliminated reading back and `driver#aCallInPipelinePositionIsARead` goes red on it,
   for that reading and no other.
