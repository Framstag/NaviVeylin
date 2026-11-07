**The gate is not yours this item.** The run has deferred it to a fresh child: stop when the change is applied, and
do not run `{{GATE_CMD}}` — not once, not "to check", not to quote a number. A separate, fresh reviewer reads your
diff *before* that gate, and the gate child applies what it finds in one pass, so the item's one gate is spent on a
change a reader has already agreed with. A gate you run here would be a second one, and a second gate is how a run
spends its budget on a change nobody has read yet.

So your session ends at the **diff**, not at a gate: copy the XMLs you cite into `{{EVIDENCE_DIR}}` as they are
produced, archive nothing, and print the block below with `LOOP-DIFF-READY` in place of the gate line. The gate child
is handed your answer and runs the gate; the tail that follows it writes the evidence and the archive.
