# Proposal — a section is one call, and a case is one call

## Why

**The condition, verified on the base.** `tools/look.sh:16` (base `929ea147`) documents three arguments and no more —
`<regex> [--path <path>] [--files] [--context N]` — and its argument loop answers `--section` with
`look: unknown argument --section` and exit 2 (`tools/look.sh:57` on that base). So the wrapper answers "where does
this pattern occur" over the walked tree and "which headings does this one document have" (`--path`), and it answers
no call that prints **one range** of a file it already accepted as a scope.

**Root cause, one sentence.** The routing a child is handed names a section of a guideline or a case of a suite as the
unit to read, while the wrapper's only unit is a pattern's matches over a document — so the child pages the file with
`sed -n 'A,Bp'` instead, and every page is a call, a turn and a context re-read.

**Evidence.** The item's own model over `.pi/issue-loop/items` (`tools/loop-spend.py`'s reader, 47 items, 10 506
shell calls): 6 261 read-verb calls hit a tracked tree file, 13.57 M characters at 2 167 per call; `sed` is the first
verb at 3 545 calls and 10.78 M characters (3 041 per call, against `ctx_read`'s 1.5 k), and `sed -> sed` appears in
46 of the 47 items. Nine paths carry 2 224 of those calls and 5.16 M characters, and every one is section-, case-
or row-structured: `tools/loop/tests/loop-cases.sh` 519 calls in 34 items (47 reads inside item #961),
`tools/loop/run.sh` 331 (41 inside #969), `tools/loop/tests/loop-selftest.sh` 238,
`tools/loop/tests/loop-mutations.sh` 226, `openspec/specs/agent-issue-loop/spec.md` 205,
`tools/check-rule-routes.sh` 190, `AGENTS.md` 180, `guidelines/Backlog.recipes.md` 173, `guidelines/Backlog.md` 162.
That model is quoted here and **not re-taken**: this change claims the item's number, not a fresh one.

**The two missing capabilities, with the pointer that shows each is absent.** A section of a document:
`tools/look.sh`'s own argument loop has no arm for it (`:57` on the base refuses the flag). One `case_*` body of a
suite: the same loop, the same refusal — and the suite is organised exactly that way, which is why
`tools/loop/tests/loop-cases.sh` can be read 47 times in one item.

**The owning guideline section.** `guidelines/Backlog.recipes.md:49` "Running one item: the script, the child and
the package", the paragraph that hands a child its route through the wrapper (`:155-160`): it names the
section-list call and stops there, so the unit the routing actually uses is the one it does not name. That paragraph
is where this change's edit is owed.

## What Changes

- `tools/look.sh` — two modes beside the pattern: `--section <regex>` prints the `## ` heading the regex matches and
  its body (sub-sections included, up to the document's next `## ` or `# ` heading), and `--case <name>` prints the
  `case_<name>() {` body of the suite `--path` names, from its opening line through the `}` that closes it. Both
  require `--path` and one file, both print `path:N:text` as a match does, both are cut by the recorded result bound
  (`look#aResultOverTheBoundIsCutAndNamed`), and a name the file does not hold is exit 1 that names the file and the
  name — never an empty answer.
- `tools/loop/tests/loop-cases.sh` — two cases: `look#aSectionIsPrintedAndTheOtherSectionsAreNot` and
  `look#aCaseIsPrintedByItsName`, each with `# files: tools/look.sh` as its declaration.
- `tools/loop/tests/loop-mutations.sh` — two records, `1005.1` (a section that never ends at the next heading of its
  own level) and `1005.2` (a `--case` that opens on every line, so the whole suite arrives).
- `tools/loop/prompts/worker.md:49-54` — the route names the section call beside the section-list call, so the prompt
  a child is started with names the unit its own routing uses.
- `guidelines/Backlog.recipes.md:162` — the paragraph that states what the wrapper answers gains the two
  ranges and the honest bound on the gain.
- `openspec/changes/fix-a-section-is-one-call/specs/agent-issue-loop/spec.md` — one `MODIFIED` requirement, "A child
  is started with a tree search that runs", carrying its three shipped scenarios and the two this change adds.

surface: 2 cases added, 0 rewritten, 2 mutation records added, 0 fixtures realigned — `./tools/look.sh 'case_look_' --path tools/loop/tests/loop-cases.sh --files` (10 matches: five cases' dispatch lines and five bodies) and `./tools/look.sh '1005\.|look#' --path tools/loop/tests/loop-mutations.sh --files` (6 matches: the two records and their comment)

Surface, with the command that counted it: `./tools/look.sh "'\^## ' --path" --files` → `62 matches in 38 files` —
of those, the lines that teach a child its route are the four phase prompts and the route paragraph of
`guidelines/Backlog.recipes.md:49`; this change edits
the two the item names (`tools/loop/prompts/worker.md:50` by one clause, `guidelines/Backlog.recipes.md:155-160` by
one sentence) and adds two cases with no fixture outside their own scratch trees. The wrapper's own argument surface is
asserted by behaviour rather than by its help text: both new cases drive the flags themselves, as the shipped cases
call it with a pattern.

## Out of scope

- The other three phase prompts. `tools/loop/prompts/{reviewer,worker-gate,worker-tail}.md` name the section listing
  too, and a reviewer routed to one scenario of a spec would gain as much as a worker; the item's own fix names
  `worker.md:50` and one paragraph of the guideline, and a second prompt is a second change with its own cases.
  Recorded here so the reviewer meets it named: `NOTE: the three other phase prompts still name only the listing.`
- Re-taking the item's model. The numbers above are the item's recorded measurement over its archive; a second
  model would be a second number for one decision (`tools/loop-spend.py` is the reader, not a thing this change
  fixes).
