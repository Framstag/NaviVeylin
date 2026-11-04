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
cite into `{{EVIDENCE_DIR}}` as they are produced, and archive nothing. The run starts the tail process from those
lines, and that process reads the change's `tasks.md`, the gate line and the last 200 lines of your own tool output
— which is why you stop here rather than carrying this session's whole context through the archive.

A gate that did not pass is not yours to repair either: the tail records the item `blocked` with the failing output
you printed. What you must leave behind is the evidence of what you did — the XMLs in `{{EVIDENCE_DIR}}`, the change
in the change root, and the block below.
