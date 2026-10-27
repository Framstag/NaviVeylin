# Revert-check — the four records this change adds

A shell gate has no build cache to force, so the mutation harness is the falsification: one record per invariant,
the named case must go red, the restore must go green again (`guidelines/Backlog.md` section 7, `.pi/skills/revert-check`).

Command (from the change root):

```
bash tools/loop-mutations.sh --only "67.1 67.2 67.3 67.4"
```

Output (`evidence/mutations.log`):

```
loop-mutations: selected 4 of 186 records (labels: 67.1 67.2 67.3 67.4)
RED    67.1  trim#aResultAboveTheBoundIsTrimmedAndNamed   (the cut itself, so an oversized result is handed over whole again (issue #660))
restore 67.1 trim#aResultAboveTheBoundIsTrimmedAndNamed green again
RED    67.2  trim#aResultAboveTheBoundIsTrimmedAndNamed   (the line that names the size the result had, so a cut is judged as the whole answer (issue #660))
restore 67.2 trim#aResultAboveTheBoundIsTrimmedAndNamed green again
RED    67.3  trim#theTrimIsRecorded   (the record of what was cut, so the item cannot say which result a claim was read from (issue #660))
restore 67.3 trim#theTrimIsRecorded green again
RED    67.4  adapter#theChildIsStartedWithTheResultBound   (the word that loads the bound with every child (issue #660))
restore 67.4 adapter#theChildIsStartedWithTheResultBound green again

red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0
```

What each record removes:

- **67.1** — `return cut + trimNotice(tool, total, bound);` becomes `return text;` in `tools/pi-trim-result.mjs`: an
  oversized result is handed over whole again, and the case notices because it asserts the cut prefix, the tail
  that must not survive and the notice.
- **67.2** — the notice names `${bound}` where it should name `${total}`: the trim still happens, but the line no
  longer says how big the result was, so a cut reads as the whole answer. The case asserts `20005` is named.
- **67.3** — the record line is replaced by `void record;`: nothing is written beside the child's event stream, and
  `trim#theTrimIsRecorded` is the case that asserts the exact line.
- **67.4** — `extension=(-e "$here/pi-trim-result.mjs")` becomes `extension=()`: children are started without the
  rule, and `adapter#theChildIsStartedWithTheResultBound` finds no `-e` word in the argv.

The harness's own cheap check, `bash tools/loop-mutations.sh --check`, is part of the gate and reports
`all 186 cases passed — every selected record applies to the file it names`, so no record has gone stale against
the line it quotes.
