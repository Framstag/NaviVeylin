**Complete the change before you run the gate** (issue #837). The gate names the tree it measures and a round's diff
names the tree it was written from, and those two names have to be one — so everything that writes the change
directory is done *before* the gate: copy the XMLs you cite into the change's own `evidence/` directory and into
`{{EVIDENCE_DIR}}`, run `bash tools/loop/evidence-check.sh <the change directory> --root .` from the change root and
fix what it reports, and archive the change with the delta it merges (`openspec archive --yes`) — the change child is
this child in this shape, so the archive is yours; a rework round starts with the change already archived, and there
you complete nothing and leave the directory where it is. The date prefix the
archive adds is what the profile's `LOOP_CHANGE_ARCHIVE_PREFIX` strips from a path before it is hashed, so the tree
reads the same before and after the move. Nothing outside the change's `evidence/` directory is written after the gate:
a repair made there is a tree the gate never measured.

Run the project's gate **once**, exactly as the run's configuration states it:

```
{{GATE_CMD}}
```

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
