# Design: fix-the-route-resolves-against-its-document

## Root cause

`tools/loop/evidence-check.sh:53` `run_checks()` resolves five pointers a report makes — the migration table's `§`
rows (C1), test tallies (C2), `/tmp` pointers (C3), leftover mutation markers (C4) and `file:line` (C5) — and no sixth
reads the route the worker's Phase A owes (`tools/loop/prompts/worker.md:205`). The report states the guideline section
its work was routed by in prose, the landing runs every mechanical check it has, and none of them compares that section
with a heading the named document carries. So a route naming a section that does not exist is filed, reviewed and
archived as a route: of the 200 items #974 measured, 171 cite a `guidelines/…` path, 109 name a `§`, and nothing in the
tree could say whether that `§` exists.

## The fix

One check, C6, in the same shape C1 already has (`tools/loop/evidence-check.sh:185`): the change's markdown is read for
route lines, each line's document must sit under `LOOP_GUIDELINE_DIR` and exist, and the line's `§<id>` must be carried
by one of that document's own `^## ` headings — `## 7c The gate and its suite` is the id `7c`, and the `## §<id> …`
shape this project also writes is read the same way. The ids are read out of the heading lines and never out of the
document's body, which is what makes a route a pointer to a section rather than to a mention of one.

Three consequences follow, and each is a decision this design took:

- **A false route is a FAIL, a missing route is a WARN.** A route naming a section the document does not carry is a
  pointer to nothing and prints the sections the document does carry, so a reader repairs it without opening the
  document. A report carrying no route line at all is a WARN until `LOOP_EVIDENCE_ROUTE` reads it as `fail`, because
  the run that lands this rule works items whose reports were written before the line existed (issue #974's own
  measure: 62 of 171 reports name no `§` at all). The mode is the project's configuration and not a second rule in the
  tool, so a later run flips one value and the same arm decides.
- **The route line is anchored.** Only list or quote markers and space may precede `LOOP-ROUTE:`, so a paragraph that
  quotes the form — this change's own proposal does, and the guideline's prose does — is prose about a route and not a
  route. Without that, a document that explains the line would be read as carrying every id its example names.
- **C1 skips a route line** (`tools/loop/evidence-check.sh:70`): a route's `§<id>` names a section of a guideline
  document, not a row of the migration's reference table, so the table check must not read it against its own rows.
  That is the one place where two checks could disagree about one `§`, and the fix is to give it one owner.

Two configuration keys carry it (`tools/loop/loop.conf:992`): `LOOP_GUIDELINE_DIR` names the directory the documents
live in, `LOOP_EVIDENCE_ROUTE` the mode. A project that names neither owes no route, and C6 says so in one `note` line
rather than passing silently.

## Blast radius

`tools/loop/evidence-check.sh` — C6, its two options (`--guidelines`, `--route`) and seven `--selftest` arms; the file
grows from 294 to 398 lines, which is why its row in `tools/loop/layout.tsv:22` moves from 368 to 498 (the table's own
rule: the measured length plus a quarter, rounded up) and `driver#noEngineFileExceedsItsBound` is what refuses the
unraised row. `tools/loop/loop.conf` gains the two keys, section 7c of `guidelines/Backlog.recipes.md` gains the paragraph that
states the obligation the check enforces, and the change's own `specs/agent-issue-loop/spec.md` adds the requirement.
Every consumer of the check keeps its call: the landing recipe runs it on a change directory, and a change directory
that carries no markdown at all is a `note`, not a FAIL.

## Rollback

`git revert` of the one landing commit restores the five-check tool, drops the two keys and the guideline paragraph.
The delta leaves the capability spec with the archive, so the revert is the change plus its spec paragraph; no record,
no state file and no fixture depends on C6 having run.

## Alternatives the evidence eliminated

- **Resolve the route in `tools/check-doc-routes.sh` instead.** That tool validates every `§` pointer the docs carry;
  it reads the docs of the project, not a change directory, and the landing already owns a single mechanical pass over
  a change (`LOOP_EVIDENCE_*`). A second owner would give one `§` two verdicts again — the reason C1 had to be taught
  about route lines in this change, and the reason not to repeat the shape.
- **Read the section's *text* to check it is the section the work belongs in.** The check cannot know which section
  owns an item's work; the honest part is the pointer, and a false pointer is what the review of a route costs. C6
  resolves existence and prints the list a reader chooses from.
- **Make a missing route a FAIL at once.** The items the first run works were written before the line existed; a FAIL
  there refuses every one of them for a rule landed after their report. `warn`, with the flip recorded in
  `LOOP_EVIDENCE_ROUTE`, is the same arm read one way and then the other — which is also why the two-mode arm is a
  scenario of the delta rather than a comment.
