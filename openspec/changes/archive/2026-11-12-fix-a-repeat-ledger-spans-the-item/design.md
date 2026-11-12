# Design — the ledger the item keeps, not the one the process keeps

## Root cause

`tools/pi-trim-result.mjs`'s hook keeps the reads it has handed over in one `Map` per child process (`:200-202` at the
base), and `repeatResult` (`:126` at the base) replaces a read only when its sha matches an entry in that `Map`. Every item that is
split at its gate is therefore several sessions over one tree, and the second one meets the first one's reads as first
reads: the text enters the context whole, once more, and every later turn of that child re-reads it. The rule's own
record says how little of the repeats it caught — 1 370 of 486 700 characters (0.3 %) over the run of
2026-11-11T11:37:08Z — while the item's metrics count the repeats over the item's whole stream, which is the two orders
of magnitude the item reports.

## The one fix

One file, not one rule: the ledger has always had a path form (`readLedger`/`writeLedger`, what `--ledger` drives in the
cases, both in `tools/pi-trim-result.mjs`), so what changes is **which path the run hands each child** and what the
entry records.

- `tools/pi-child-events.sh` — the adapter is where an item's own paths are already named (the trim record lands beside
  the child's event stream), so it names the ledger there too: `LOOP_TOOL_RESULT_LEDGER` =
  `<dirname events>/tool-result-ledger.tsv`. A child started by hand with no stream keeps the process-local ledger.
- `tools/pi-trim-result.mjs` — the hook opens that file at its first read and writes it back after every read
  (`writeLedger` even when the result was handed over whole: a first read is what the next one is measured against). The
  entry gains the session that read the path, and `alreadyReadNotice` names it —
  `was read at turn 3 in the worker-prompt session` — because "this session" is false for every child after the first.
  A ledger line is `sha <tab> turn <tab> size <tab> session <tab> path`; a four-column line (a ledger of the older
  shape) is dropped rather than misread, and a dropped entry costs one read, never a stale one.
- `tools/pi-child-events.sh` also names the session: `LOOP_CHILD_SESSION` is the prompt's basename, the run's own
  artifact and the same input `run.sh`'s `child_role` reads — so no new engine line, and no second copy of the role
  vocabulary in the adapter.
- `tools/loop/report.sh` — one clause of the trend's `reads:` line. `metrics.withheld` comes from the item's record, so
  it now counts a repeat across two children, which `metrics.dupChars` (per session, by the shipped requirement) does
  not: "N chars **of them** withheld" would be false and now reads "N chars withheld by the repeat rule".

## Blast radius

The rule's two entry points (hook and CLI) and the one file the run names for them; the trend's clause; the guideline
section that states the rule (`guidelines/Backlog.recipes.md` section 7b); the cases that assert the notice and the trend line.
Nothing reads the ledger but the rule itself, and nothing in the run reads `metrics.withheld` to decide anything — it is
a warning figure — so no item's outcome moves.

What it costs per read: one `Map` of every path the item read, and one rewrite of the item's ledger file per read tool
call. That rewrite is one line per path the item read, and the item's own turn bound bounds how many paths that is; the
volume it saves is the one the item measured — 389 156 characters of repeat reads over the six unbounded items, at
`tools/loop-spend.py`'s amplification of 43–55. A ledger that cannot be written costs a repeat, never the result
(`writeLedger` swallows its own error, as it already did).

## Rollback

Revert the four files: the ledger the environment names is then read by nobody, and the hook falls back to the
process-local `Map` it kept before (`ledgerFile` empty). No state field, no `loop.conf` key and no record shape changes,
so a run started on the reverted tree reads its old items as it did.

## Alternatives the evidence eliminated

- **A tighter bound on a tool result** (`LOOP_TOOL_RESULT_MAX_CHARS` lower than 8 000) — #815 refused it: the
  repeat-read count rises as the cap falls, because a cut result is a result the child pages for, and the cap is the
  wrong lever for a read the child already paid for.
- **Counting repeats per item instead of per session**, so the metrics and the rule agree by construction — it changes
  what `report#anItemRecordsItsRepeatReads` measures and the shipped requirement's own sentence ("a repeat counted per
  session so the second session's read is a read and not a repeat"), and it does not hand the child anything. The
  ledger is where the saving is; the metrics can stay as they are, and the report's clause says what it now is.
- **Seeding the ledger across a rework round only** — the item leaves that open. It needs no decision here: the entry is
  content-addressed, so a tree that changed under the child hands the text over whole on a sha mismatch, and a rework
  round is one of the item's children like any other. The risk the item names — a stale notice rather than a stale read
  — is what naming the session answers.
- **A `loop.conf` key bounding the ledger's age or size** — no measurement asks for one; the file is one line per path
  the item read, and an item's read set is bounded by its own turn bound.
