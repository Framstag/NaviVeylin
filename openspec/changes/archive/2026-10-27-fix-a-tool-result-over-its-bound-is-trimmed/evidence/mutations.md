# Revert-check — the six records this change adds

A shell gate has no build cache to force, so the mutation harness is the falsification: one record per invariant,
the named case must go red, the restore must go green again (`guidelines/Backlog.md` section 7, `.pi/skills/revert-check`).

Command (from the change root, taken 2026-10-27T00:28:05Z):

```
bash tools/loop-mutations.sh --only "67.1 67.2 67.3 67.4 67.5 67.6"
```

Output (`evidence/mutations.log`):

```
loop-mutations: selected 6 of 188 records (labels: 67.1 67.2 67.3 67.4 67.5 67.6)
RED    67.1  trim#aResultAboveTheBoundIsTrimmedAndNamed   (the cut itself, so an oversized result is handed over whole again (issue #660))
restore 67.1 trim#aResultAboveTheBoundIsTrimmedAndNamed green again
RED    67.2  trim#aResultAboveTheBoundIsTrimmedAndNamed   (the line that names the size the result had, so a cut is judged as the whole answer (issue #660))
restore 67.2 trim#aResultAboveTheBoundIsTrimmedAndNamed green again
RED    67.3  trim#theTrimIsRecorded   (the record of what was cut, so the item cannot say which result a claim was read from (issue #660))
restore 67.3 trim#theTrimIsRecorded green again
RED    67.4  adapter#theChildIsStartedWithTheResultBound   (the word that loads the bound with every child (issue #660))
restore 67.4 adapter#theChildIsStartedWithTheResultBound green again
RED    67.5  adapter#theChildIsStartedWithTheResultBound   (the line that names the trim record beside the child's own event stream, so every trim record silently vanishes (issue #660))
restore 67.5 adapter#theChildIsStartedWithTheResultBound green again
RED    67.6  trim#aResultUnderTheBoundIsHandedOverWhole   (the boundary that hands a result of exactly the bound over whole, so a result that fitted is cut (issue #660))
restore 67.6 trim#aResultUnderTheBoundIsHandedOverWhole green again

red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0
```

The assertion each record turns red, and the line it stands on — the harness discards the case's own output, so the
assertion is named from the case that owns it (`evidence/mutations.log` carries the same list):

| record | case | assertion that fails |
|---|---|---|
| 67.1 | `trim#aResultAboveTheBoundIsTrimmedAndNamed` | `tools/loop-cases.sh:6571` `assert_eq "$kept" "$(head -c 8000 "$out")" "the first <bound> characters are handed over unchanged"` |
| 67.2 | `trim#aResultAboveTheBoundIsTrimmedAndNamed` | `tools/loop-cases.sh:6575` `assert_contains "$(cat "$out")" "8000" "and the bound it was cut to"` |
| 67.3 | `trim#theTrimIsRecorded` | `tools/loop-cases.sh:6636` `assert_eq "LOOP-TOOL-RESULT-TRIM: ctx_read chars=20005 kept=8000" "$(cat "$d/trims.log")"` |
| 67.4 | `adapter#theChildIsStartedWithTheResultBound` | `tools/loop-cases.sh:6673` `[ -n "$line" ] \|\| fail "the child is started without the result bound: no -e in $got"` |
| 67.5 | `adapter#theChildIsStartedWithTheResultBound` | `tools/loop-cases.sh:6690` `assert_eq "$d/tool-result-trims.log" "$(cat "$d/events-argv.txt.record" …)" "the trim is recorded beside the child's own event stream, in the item's directory"` |
| 67.6 | `trim#aResultUnderTheBoundIsHandedOverWhole` | `tools/loop-cases.sh:6612` `assert_eq "$(wc -c < "$exact")" "$(wc -c < "$out")" "a result of exactly the bound is handed over whole"` |

What each record removes:

- **67.1** — `return cut + trimNotice(tool, total, bound);` becomes `return text;` in `tools/pi-trim-result.mjs`: an
  oversized result is handed over whole again, and the case notices because it asserts the cut prefix, the tail
  that must not survive and the notice.
- **67.2** — the notice names `${total}` where it should name `${bound}`: the trim still happens, but the line no
  longer says what the result was cut to, so a cut reads as the whole answer. The case asserts `8000` is named.
  (Round 1 described this record backwards, as removing the size; it removes the bound — review of #660.)
- **67.3** — the record line is replaced by `void record;`: nothing is written beside the child's event stream, and
  `trim#theTrimIsRecorded` is the case that asserts the exact line.
- **67.4** — `extension=(-e "$here/pi-trim-result.mjs")` becomes `extension=()`: children are started without the
  rule, and `adapter#theChildIsStartedWithTheResultBound` finds no `-e` word in the argv.
- **67.5** — the adapter's `export LOOP_TOOL_RESULT_TRIM_RECORD="$(dirname "$events")/tool-result-trims.log"`
  becomes a no-op: the hook still trims and still writes a record when a path names one, but no path ever does, so
  every trim record vanishes from the item. Deleted, all four other cases stayed green until round 2 added the
  assertion.
- **67.6** — the pass-through's `text.length <= bound` becomes `text.length < bound`: a result of exactly the bound
  is cut to nothing and carries a notice, and `trim#aResultUnderTheBoundIsHandedOverWhole` drives exactly 8 000
  characters so the boundary the sentence names ("at most the recorded bound") is exercised.

The harness's own cheap check, `bash tools/loop-mutations.sh --check`, is part of the gate and reports
`all 188 cases passed — every selected record applies to the file it names`, so no record has gone stale against
the line it quotes.
