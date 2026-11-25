# A route resolves against the section its own document carries

Item #1080, restoring C6 of the parent #974. `tools/loop/evidence-check.sh:53` `run_checks()` runs five checks — the
migration's reference table, test tallies, `/tmp` pointers, leftover mutation markers and `file:line` — and not one of
them reads a route. A change says which guideline section its work was routed by in one line, `LOOP-ROUTE:
guidelines/<doc>.md §<id>`, and nothing in the tree compares that id with an `## ` heading the named document
actually carries: at the run's base commit the tool is 294 lines and its self-test prints 13 `ok` arms, none about a
route.

**Root cause, in one sentence:** the landing's mechanical checks resolve every pointer a report makes except its
route, so a route naming a section the document does not carry is filed, reviewed and archived as a route.

**The red test:** one fixture change whose markdown carries a route naming an id its document does not carry — the
engine committed at fa8c6aa reads that fixture `evidence-check: PASS` (exit 0), and the same fixture against this
change's engine exits 1 and names the sections the document does carry (`evidence/red-on-base-probe.txt`, taken by
`scratch/probe-route.sh`, which runs the base engine and the change's engine over one fixture). A shell self-test
carries no JUnit XML, so the tally is the arm: `ok a route resolves against the document's own heading` does not
exist on the base commit, and the self-test prints 20 `ok` arms after this change against the base's 13
(`evidence/selftest-after.log`).

**The owning requirement:** `agent-issue-loop` — the requirement "A report's route resolves to a section its document
carries", added by this change's delta. The owning guideline section is the landing recipe of
`guidelines/Backlog.recipes.md`, whose paragraph at `guidelines/Backlog.recipes.md:1160` gains the rule.

**The route this change names:**

- LOOP-ROUTE: guidelines/Backlog.recipes.md §7c

**Surface:** `surface: 7 self-test arms added, 1 fixture document added, 1 bound row raised, 2 configuration keys
added — tools/look.sh 'C6' --path tools/loop/evidence-check.sh; tools/look.sh
'LOOP_EVIDENCE_ROUTE|LOOP_GUIDELINE_DIR' --files`. No loop-cases case is written here: the sibling child of #974 owns
the `check#` case, the prompt obligation and the mutation records, so every scenario of this delta names the self-test
arm it reads.

## What Changes

- **C6 in `run_checks`** (`tools/loop/evidence-check.sh:185`): the anchored route lines of the change's markdown are
  read, each one's document must sit under the project's guideline directory and exist, and its id must be carried by
  one of that document's own `## ` headings — `## 2a. Thing` is the id `2a`, and the `## §<id> Thing` shape this project
  also writes is read the same way. A route that does not resolve is a FAIL whose message prints the sections the
  document does carry. A change carrying no route line is a WARN until the project's configuration reads the line as
  required, and a FAIL after that.
- **C1 learns the route is not a migration reference** (`tools/loop/evidence-check.sh:70`): a line carrying a route is
  skipped by the table check, because its id names a section of a guideline document and not a row of the migration's
  table.
- **Two configuration keys** (`tools/loop/loop.conf:992`): `LOOP_GUIDELINE_DIR` names the directory the documents live
  in and `LOOP_EVIDENCE_ROUTE` the mode, `warn` at the landing of this rule, because the items the first run works
  wrote their reports before it existed.
- **The bound row** (`tools/loop/layout.tsv:22`) moves from 368 to 498 — the file's length plus a quarter, rounded up
  (398 lines), which is what the table's own rule asks of a row whose file grew.
- **The landing recipe** (`guidelines/Backlog.recipes.md:1160`) states the rule the check enforces, so the section a
  change routes its work by carries the obligation rather than only the tool.
