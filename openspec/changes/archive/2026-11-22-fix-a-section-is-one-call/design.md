# Design — two modes on the wrapper that exists

## The root cause

`tools/look.sh` is one walk and one printer. Its argument loop (`tools/look.sh:61-81` after this change) takes a
pattern, a scope, `--files` and `--context`, and everything after that is `grep -rnIH` over the target
(`tools/look.sh:179-183`). A scope is a file or a directory and the answer is always the pattern's matches inside it.
So the two units the loop's own routing uses — **one section of a document** and **one `case_*` body of a suite** —
have no call: the listing names the heading (`tools/look.sh '^## ' --path guidelines/Build.md`), and the read of the
body under it is a page range the child assembles itself (`sed -n 'A,Bp'`). Every page is a call, a turn and a
context re-read; the item's model counts 3 545 `sed` calls and 10.78 M characters over its 47 items, and
`sed -> sed` in 46 of them.

The defect is the missing unit, not the bound and not the walk: the wrapper already refuses a scope the tree does not
hold (`look#aPathScopesTheWalkToIt`) and already cuts a long answer (`look#aResultOverTheBoundIsCutAndNamed`). It
needs the same file read as a *range*.

## The single fix

A `--section <regex>` / `--case <name>` pair that stores the value in the same `pattern` variable and a `mode`
(`tools/look.sh:70-74`), refuses what a range cannot use, and picks the printer by mode before the walk
(`tools/look.sh:156-183`):

- **Validation**, once, before any file is read (`tools/look.sh:96-112`, `:130-133`): a mode needs `--path`; that path
  must be a file; `--files` and `--context` are a pattern's and are refused rather than ignored; both modes at once is
  refused; a `--case` value must look like a case name (`[A-Za-z0-9_]`, with or without its `case_` prefix).
- **The section printer** (`tools/look.sh:156-169`): `## ` at the document's own level opens a section and `# ` ends
  the one before it, so a `###` heading is a sub-section of the section it sits in — the level the listing shows
  (`'^## '`) is what a section is. The regex and the label travel in `ENVIRON`, not `-v`, because `awk -v` processes
  escapes and would rewrite a regex a reader quoted.
- **The case printer** (`tools/look.sh:170-178`): from `case_<name>() {` through the first `}` at the left margin,
  which is the end of a case in a suite that is a flat list of them.
- **A name the file does not hold** (`tools/look.sh:184-193`): `awk` exits 3, the wrapper names the file and the name,
  and exits **1** — not-found, as a pattern the tree does not hold, never an empty answer.
- **The answer** (`tools/look.sh:211-247`): the count its first line carries is how many sections or cases the name
  printed, the per-file counts a walk prints are not part of it, and the result bound cuts it like any other answer.

The two ranges do not read a second file, walk a second time or add a bound of their own: `--path` already names the
file, and the cut is the wrapper's existing one.

## Blast radius

- `tools/look.sh` — the argument surface and the printer. The `look` group is five cases: the tree walk, the bound,
  the scope, and the two this change adds; all five are green (`evidence/green.log`).
- `tools/loop/tests/loop-cases.sh` — two cases (list `:389-390`, dispatch `:1216-1217`, bodies `:20498` and `:20568`),
  each declaring `tools/look.sh`.
- `tools/loop/tests/loop-mutations.sh` — two records (`:1542-1547`), one per mode; `--only '1005.1 1005.2'` is red on
  both and green again after each restore (`evidence/mutations.log`).
- `tools/loop/prompts/worker.md` — the route a child with no project context is given, one clause longer.
- `guidelines/Backlog.recipes.md:162` — the paragraph that states what the wrapper answers, one sentence longer.
- The capability delta — one `MODIFIED` requirement of `agent-issue-loop`, five scenarios, three of them shipped.

Nothing compiled is touched, so the structural checks stand in for a build (`evidence/structural.log`).

## Rollback

Revert the five files. The wrapper's old argument loop refuses `--section` and `--case` exactly as it did before, so a
caller that learned the new modes gets exit 2 rather than a wrong answer; nothing else in the tree calls them yet.

## Alternatives the evidence eliminated

- **A page range as the unit** (`--lines A,B`). The item's measurement is that the page range *is* the problem: the
  child has to read the listing, count lines and pick a range, which is three calls for one section and is what the
  archive shows. A range mode would keep the arithmetic in the child instead of removing it.
- **Reading a section through `ctx_read` with `offset`/`limit`.** The same arithmetic, and a child routed to a section
  still has to know where the section starts; the wrapper's listing is what knows.
- **A second wrapper.** `tools/look.sh` is the wrapper every prompt names and the one `tools/pi-shell-refusal.mjs`
  leaves as the tree-search call; a second binary would be a second route to keep consistent, and the issue's fix is
  two modes on the one that exists.
- **Printing the whole file for `--case` (or the rest of the document for `--section`).** The unit is the point; the
  case that fails on exactly that (`1005.2`, `1005.1`) is in the suite.

## What this change does not claim
- **Characters.** The win is calls, not bytes: a `## §N` section in `guidelines/` is 1.3–10.3 kB, so a section read
  can be larger than the 3 kB chunk it replaces. For a suite the case body is the unit and is smaller than the chunk a
  reader would have taken; for a guideline the section is the document's own unit and the child was going to read one
  anyway. The item states this bound and this change repeats it rather than claiming a reduction it did not measure.
- **The other phase prompts.** They still name only the listing (`NOTE:` in the proposal).
- **A new bound, a new walk or a new refusal rule.** Both modes are reads of a file `--path` already accepts, cut by
  the bound the tree already records (`tools/loop/loop.conf`).

## Open decisions

none
