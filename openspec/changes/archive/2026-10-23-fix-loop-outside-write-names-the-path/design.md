# Design — the run names what changed when a child writes in the project's own tree

## D1 — The root cause, and the one fix

`project_state` already produces everything a reader needs: the tree's own answer to "am I a work tree" (which is
how #241's `core.bare=true` flip is caught) and its `git status --porcelain` lines. `run_child` compares two
readings of it and, when they differ, writes a constant sentence. The information is generated and then thrown
away one line later.

The fix keeps it: `project_state_diff <before> <after>` is the symmetric difference of the two readings
(`sort | uniq -u` over the concatenation, so a line that *appeared* and a line that *vanished* are both named), and
`outside_write_line` quotes that difference inside the one machine-readable line the transcript gains:

```
LOOP-OUTSIDE-WRITE: the project tree changed while this process ran: ?? demo-owned.txt; process(es) whose working
directory is inside the project: 1429033 sleep 30
```

Two properties of that shape are deliberate. The clause quotes the **status line**, not the path, so a reader sees
`??` for a file that appeared and ` M` for one that was changed — a bare path would not say which. And a
difference that is only the work-tree line is named as exactly that (`work-tree=false`), because in #274's own
event that is very likely what flipped: the transcript's writes were all inside `.pi/worktrees/loop/**` and
`git status --porcelain` was empty before and after, so the difference the run saw cannot have been a path.

Both block reasons (`tools/loop/run.sh:1014` for the worker, `tools/loop/run.sh:1161` for a round-2 worker) now
carry that line verbatim, and the block's escalation carries the reason onto the tracker (`run.sh`'s blocked exit
path). This is the whole of (c) of the finding: the decision the item asks becomes answerable from the comment a
person reads, not only from a state file under `.pi/`.

## D2 — Naming a process, and what "the project" means for that question

The second half of the line is `outside_write_pids <root> <worktree>`: every process whose working directory lies
under the project root **and outside the worktree**. `loop_pids_under` already answers "which processes work in
this tree" and the cap's own sweep uses it (issue #272); the exclusion is the part this question needs, because the
worktree is a tree a phase *may* write, so a Gradle daemon standing in it is not evidence of anything.

Two mechanisms keep the sweep from answering with itself, and both are needed:

- `loop_pids_under` skips this shell and its parent, which is not enough on its own — a command substitution is a
  process of its own (`$$` still expands to the parent's pid inside one) and it inherits the caller's working
  directory, which is the project. So the caller reads the answer from outside the tree it asks about
  (`holders=$( cd / && outside_write_pids … )`), and every stage of that pipeline — including the shell that runs
  the sweep — starts in `/`.
- The `for pid in $(loop_pids_under …)` substitution inside `outside_write_pids` inherits that same `/`.

The run does not end what it names here. Ending a survivor is the cap's job, with its own reason and its own line
(`LOOP-ENDED:`); this line is a statement about what the tree showed, and a decision the owner answers.

## D3 — Bounds

A damaged tree carries thousands of `git status` lines, and a block reason is a sentence posted on a tracker. So
the paths are capped at ten (each line cut to 120 bytes) and the processes at five (each `<pid> <command>` cut to
120), with `; and N more` past either cap. The cap is engine policy — a bound on a report — and not a project fact,
so it is a literal in the function rather than a `loop.conf` key (D16 forbids a project fact in an engine, not a
bound of the engine's own output).

## Blast radius

- The line `run#childWriteOutsideTheWorktreeIsBlocked` asserts is the one this change rewrites; the case is
  rewritten with it (the red case already failed on HEAD for exactly the missing path).
- `run#reviewerWriteInTheProjectIsDiscarded` reads the same two readings through the round's own `read_only`
  comparison (`tools/loop/run.sh:1060`, `:1096`), which this change does not touch — verified by re-running it.
  The reviewer child now also gets a `LOOP-OUTSIDE-WRITE` line in its transcript; nothing reads that field from a
  reviewer report, and the round's own refusal is decided from the comparison, not from the transcript.
- Mutation record `12.3` quoted the old `printf`; it now quotes the call site. The invariant it removes (the run
  catching a child's write outside the change root) is unchanged and so is the case it names.
- Prompts and fixtures: no mention of the field, no change.

## Rollback

Revert the commit: the case file, the mutation record and the guideline paragraph go with it. Nothing is
persisted, nothing is migrated, and no state file carries the line — the transcript and the run state are written
fresh by each run.

## Alternatives the evidence eliminated

- **Append one line per changed path.** `report_field` reads a field's *last* line, so the block reason would carry
  only the last path — the run would still not say what all changed.
- **Name the paths only, without the processes.** Then a difference whose only content is the work-tree line reads
  as an anonymous event again, which is #274's own case; and the item's finding asks for the process "when the run
  can see one", which the same sweep already answers.
- **End the survivors the line names.** That is the cap's behaviour, tied to a child the run itself ended
  (`LOOP-ENDED:`); a survivor the run did not start is a person's to look at, and ending it would destroy the
  evidence the line exists to preserve.
- **Make `project_state` itself fail loudly when `git` cannot answer.** A transient `git` failure is one cause of a
  difference and the diff now names it (`work-tree=` with no value); making it a refusal would block items on a
  reading #241 deliberately keeps tolerant.
