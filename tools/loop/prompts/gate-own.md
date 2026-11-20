**Complete the change before you run the gate** (issue #837). The gate names the tree it measures and a round's diff
names the tree it was written from, and those two names have to be one — so everything that writes the change
directory is done *before* the gate: copy the XMLs you cite into the change's own `evidence/` directory and into
`{{EVIDENCE_DIR}}`, run `bash tools/loop/evidence-check.sh <the change directory> --root .` from the change root and
fix what it reports, then classify the capability this change's own delta introduces, and only those: read the
capability ids under `openspec/changes/{{CHANGE_NAME}}/specs/` — read before the archive moves the directory — and
the entries in `tools/feature-list/specs.json`, and append one line, in that file's own shape, for each id it does
not name. `area` is chosen from `tools/feature-list/areas.json`; `userVisible` and `surfaces` are read from the
requirement the delta writes. The arm is bounded to this change's own ids — an id this change does not introduce, a
second failing check, or an area the catalogue does not hold is **not this step**: that red stays the gate's red and
the item's `blocked` verdict, never an edit here (issue #914). Then archive the change with the delta it merges
(`openspec archive --yes`) — the change child is
this child in this shape, so the archive is yours; a rework round starts with the change already archived, and there
you complete nothing and leave the directory where it is. The date prefix the
archive adds is what the profile's `LOOP_CHANGE_ARCHIVE_PREFIX` strips from a path before it is hashed, so the tree
reads the same before and after the move. Nothing outside the change's `evidence/` directory is written after the gate:
a repair made there is a tree the gate never measured.

Run the project's gate **once**, exactly as the run's configuration states it:

```
cd {{CHANGE_ROOT}} && nohup {{GATE_CMD}} > {{EVIDENCE_DIR}}/gate.log 2>&1 &
```

A shell whose policy refuses to let one program start another drops the `nohup` and keeps the trailing `&` with both
streams in that log; the gate outlives the call either way. Keep the path absolute as it stands — `{{EVIDENCE_DIR}}`
is, and a bare `gate.log` would land in the project's own tree.

**The wall this call runs into, and the way through it (issue #957).** The harness you run under ends one tool call
at a **ceiling** of its own — 900 s measured on this host, 1 500 s in the run that filed this issue — and the gate is
bounded by the profile's own clocks, which are longer than that (`LOOP_GATE_STEP_MAX_SECONDS` for one step,
`LOOP_ITEM_GATE_SECONDS` for an item's whole gate). So a gate that passes can outlast the call that started it, and a
gate run inline ends as a call that was cut: no `LOOP-GATE:` line, no tally, and a diff the round after the gate
refuses for carrying no gate of its own. So the gate is **started detached and polled** — the call that starts it is
not the call that reads it — and each poll is one `sleep` and one `tail -n`: a wait and a range read, over a file under
the item's own directory rather than a path of the project's tree, so reading its end is no search of a tree at all:

```
sleep 600 && tail -n 40 {{EVIDENCE_DIR}}/gate.log
```

The gate is over when the log's last line begins `LOOP-GATE:` — its verdict, the numbers your report quotes — or
`LOOP-GATE-REFUSED:`, which measured nothing. **A call that ends at its ceiling is the call ending, not the gate**: the
process it started keeps running (this loop's own record, `tools/loop/loop.conf`), so resume by polling again, and
never start a second gate — a gate started while one is running refuses with `LOOP-GATE-REFUSED: another gate of this
tree holds …` and exit `4`, which is the engine saying one gate per tree and not a red gate to repair. Do not end your
session before that log carries the tally: the gate line you print below is the gate's own, copied.

The gate is evidence, not a thing to retry: it is run once per item and its result is quoted, not improved. A
gate that does not complete inside the run's budget — the run ends a child that outlasts its configured
wall-clock, and that includes every command you started — is not yours to report: the run records the item
`blocked` with your log itself, and a second attempt is not yours to make. Two attempts at one gate is how a run
spends its whole budget inside one child.

**The gate is where your session ends.** When it has run, print the gate block below and stop: copy the XMLs you
cite into `{{EVIDENCE_DIR}}` as they are produced, and write nothing else into the tree. The run starts the tail process
from those lines, and that process reads the archived change's `tasks.md`, the gate line and the last 200 lines of your
own tool output — which is why you stop here rather than carrying this session's whole context through the record.

A gate that did not pass is not yours to repair either: the run restores the change to its unarchived name and the tail
records the item `blocked` with the failing output you printed. What you must leave behind is the evidence of what you
did — the XMLs in `{{EVIDENCE_DIR}}`, the archived change in the change root, and the block below.
