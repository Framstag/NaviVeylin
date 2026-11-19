# Design — the list separator is a command boundary

## Root cause

`is_call` answers one question: is this occurrence of `name` the word that opens a simple command? It answers it by
normalising the line and asking what the word *before* the name is:

```
gsub(/[;|&(){}[\]\t"']/, " ", line)                       # tools/loop/tests/loop-cases.sh:18038
...
if (i == 1) return 1                                      # the first word of the line
if (prev == "") return 1                                  # the indent is an empty first field
if (prev ~ /^(CMDSUB|if|then|elif|else|while|until|do|time|!)$/) return 1
if (prev ~ /[=$]$/) return 1
```

`;`, `|` and `&` were in that normalisation class, so the one fact the question turns on — "a command starts here" —
was replaced by a space, and the word before the name became the last word of the *previous* command. `printf '%s\n'
x | shared_fn` normalised to `printf  x   shared_fn`: `shared_fn`'s predecessor read as `x`, which is none of the
accepted words, so the name was not a call, was not a read, and the row `shared.tsv` owes it was never asked for.

The mechanism is one line and one class member, and it explains both symptoms: the census that harvests reads
(`:18031`, through `layout_shared_awk` and `layout_shared_refusals`) and the source-order check (`:18226`, through
`layout_call_refusals`) call the same helper, so both were blind to a call in pipeline position.

## The single fix

Stand a word in for the separator instead of a space, and accept that word as a command opener:

```
gsub(/[;|&]+/, " SEP ", line)                             # tools/loop/tests/loop-cases.sh:18038
if (prev ~ /^(CMDSUB|SEP|if|then|…)$/) return 1
```

in both copies of the helper (`:18038` and `:18233`). `[;|&]+` is one token for `;`, `|`, `||`, `&&`, `|&` and the
`&` that closes a background command, because the derivation needs the fact that a command opens there and not which
separator opened it — the accepted-predecessor list is a list of openers, and `SEP` is the opener this line writes.

Not `[=$]$` and not a separate rule per separator: a second reading of "a pipeline stage is any word after a `|`"
would have to keep the separator as itself through the normalisation, which is the same change with more classes,
and it would still answer the wrong question for `&&`, which opens a command it does not pipe into.

## Blast radius

- `tools/loop/tests/loop-cases.sh` — the helper twice, one case added, one case extended (53 insertions, 4
  deletions).
- `tools/loop/shared.tsv` — three lines: two rows added and one row's reader list completed (3 insertions, 1
  deletion). The rows the fixed derivation demands were counted, not guessed: with the helper fixed and the
  declaration untouched, `driver#theSharedNamesAreDeclared` names exactly three disagreements
  (`evidence/census-undeclared-reads.log`).
- `tools/loop/tests/loop-mutations.sh` — one record (1 insertion).
- No engine script changes. The line bounds in `tools/loop/layout.tsv` are untouched because `tools/loop/*.sh`
  keeps its length; the file that grows is the suite's own case file, which no bound covers.
- The census's own numbers move with the declaration: three rows a person reading the file first found by hand are
  now rows the suite asks for.

## Alternatives the evidence eliminated

1. **Add the row and leave the derivation.** This is the item's first instinct and it is refused by measurement: with
   the row added and `is_call` as the base ships it, `driver#theSharedNamesAreDeclared` goes red in the *reverse*
   direction — `shared.tsv: <name> is declared and no file a second one reads assigns it` — because the derivation
   never puts the name in the computed set (`layout_shared_refusals`' `comm -23`,
   `tools/loop/tests/loop-cases.sh:18135`-`:18150`, and the item's own recorded measurement). The table cannot
   describe the code while the two directions disagree about which reads exist.
2. **Widen the class instead of adding an opener** (`[;|&]` → a space and a rule "any word after whitespace opens a
   command"). Eliminated: it makes every word a command opener, so `printf foo shared_fn` — a call passed as an
   argument — becomes a read, and the declaration would owe rows for every name used as an argument anywhere.
3. **A regex on the raw line before normalisation** (`/(^|[;|&])\s*name\b/`). Eliminated: it puts a second reading of
   one question beside the first, and the two disagree on the shapes the first refuses — a `case` arm's `)` opens a
   command (`*) loop_die …`, `measure.sh`), the first reading refuses it, and a derivation whose answer depends on
   which of the two was asked is not the comparison `shared.tsv` is declared against.

## Rollback

Revert the three files: `git checkout -- tools/loop/shared.tsv tools/loop/tests/loop-cases.sh
tools/loop/tests/loop-mutations.sh` against the base. The rows must go with the helper: the fixed rows and the
unfixed derivation are the red state `evidence/census-undeclared-reads.log` records. Nothing outside `tools/loop/`
depends on the reading, so the revert is the base tree exactly.

## What this does not fix

- **A closing bracket that opens a command is still read as whitespace.** `;`, `|` and `&` are list separators and
  are fixed here; `)` — the `case` arm's opener (`*) loop_die …`) — and `;;` between arms are the same kind of
  boundary and are still normalised away. The three `loop_die` mentions they hide are on names already declared, so
  this delta's declaration is complete without them, but a name shared *only* through a `case` arm is invisible to
  the census today, exactly as a pipeline stage was. Out of this item: a NOTE on the item, and a candidate item of
  its own with its own red case.
- `layout_shared_awk`'s reader list is sorted from index 2 (`tools/loop/tests/loop-cases.sh:18112`), so a row's
  *first* reader is whatever awk's hash order emits first and the rest follow sorted. The declaration therefore
  depends on the awk a host runs, and `loop_drop_ignorable_writes` is declared `worktree.sh run.sh` rather than
  `run.sh worktree.sh` for that reason. Out of this item's mechanism — a NOTE on the item, and a candidate item of
  its own.
