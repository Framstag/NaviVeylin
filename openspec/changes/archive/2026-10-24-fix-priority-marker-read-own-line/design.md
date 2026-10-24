# Design — fix-priority-marker-read-own-line

## Root cause

One mechanism, in one home: the reader identifies the marker by searching the body for the opening and closing
delimiters instead of for the *line* the pass writes, so the first occurrence in the body wins — and the
prose that stands above the record is the first occurrence whenever an item quotes the grammar.

`tools/issues/lib.sh:349` (`issues_prio_marker`) is the reader `check-metadata.sh:160` and
`prioritise.sh:110` both call, and `tools/issues/lib.sh:618` (`issues_body_with_marker`) repeats the same
search on the write path, where a quotation's line is replaced by the marker. `issues_body_hash`
(`tools/issues/lib.sh:507`) strips the delimiters wherever they stand, so a quotation — prose, part of the
claim — is removed from the digest the basis is computed over.

Measured on HEAD (`evidence/red-probe-head.log`): 5 of 6 probe checks red.

## The single fix

One predicate owns the shape, and everything else reads it:

```sh
# tools/issues/lib.sh — the marker's line number, or nothing (variables `mo`/`mc`: `close` is a gawk builtin)
issues_prio_marker_line() {   # <body> → the 1-based line number of the marker
  printf '%s\n' "${1:-}" | awk -v mo="$ISSUES_PRIO_MARKER_OPEN" -v mc="$ISSUES_PRIO_MARKER_CLOSE" '
    function is_fence(line,   t) { t = line; sub(/^[ \t]+/, "", t); return t ~ /^(```|~~~)/ }
    function is_marker(line,   t) {
      t = line; sub(/\r$/, "", t); sub(/[ \t]+$/, "", t)
      return length(t) >= length(mo) + length(mc) && index(t, mo) == 1 &&
             substr(t, length(t) - length(mc) + 1) == mc
    }
    { if (is_fence($0)) { infence = !infence; next }
      if (infence) next
      if (is_marker($0)) { print NR; exit } }'
}
```

The line has to start at column 0 and end at the close delimiter, and it must not stand inside a fenced span.
The three helpers then go through it instead of searching:

- `issues_prio_marker` prints that line (trimmed of trailing whitespace) or nothing;
- `issues_body_hash` drops that line and keeps a quotation, so the digest covers what the item claims;
- `issues_body_with_marker` replaces that line, or appends the marker below a blank line when there is none.

## Why the line alone is not enough, and why the fence is not an exemption

Phase A fixed the shape the red case must carry: the item's own fix shape says *a line of its own, anchored at
line start and end* **and** *a case should carry a body quoting the grammar in a fenced span*. A fenced span
puts the quoted marker on a line of its own, so an anchor without the fence reads that quotation as the
record — the same defect one indentation deeper.

The fence rule is not an extra exemption bolted on: it is the boundary of the same rule. The marker is the
`<!-- prio: … -->` block comment the pass writes at column 0, and it is a record only if the body *renders* it
as one; GitHub (and every Markdown renderer the project reads a body with) renders a fenced or four-space
indented line as code, not as a comment. Reading the marker where it renders is therefore the same statement
as reading it where the pass writes it — a code span is prose by construction, and the quotation is the case
that proved it (item #292).

Leading indentation is *not* trimmed, because indentation changes the block the line belongs to (four spaces is
a code block, `> ` is a quotation block). Trailing whitespace *is* trimmed, because it changes no rendering and
the marker is otherwise identical — the requirement says so since round 2, because a marker line ending in a
space is a record here and the requirement's "closing at its end" alone read narrower than the code.

## Each anchor owes a case, not a probe

The shape has three clauses — column 0, the close at the line's end, and the fence — and a bound is held only by
something the gate runs. Round 1 shipped the first two held by a probe against a copy of `lib.sh` outside the
tree (`evidence/revert-check-anchor.log`): mutating `index(t, mo) == 1` to `index(t, mo) > 0` left all three
cases green. A probe is a person's argument; the gate runs cases. So the fixture carries one prose line per
anchor and the case asserts the reader over them directly, and the write path's own arm (`NR == n`) got its
stored-marker half, which is the only thing that can fail when the replace arm is broken. Round 2's four
mutations (`evidence/revert-check-293.sh`) each go noticed by a named case.

## Alternatives the evidence eliminated

- **Anchor at line start and end, without the fence.** Eliminated by the red case this item's own fix shape
  requires: a fenced span quoting the grammar is a line of its own, and it still read as the record
  (`evidence/red-probe-head.log`'s second line is exactly that shape).
- **Exempt only the inline code span.** Eliminated by the same probe: an inline span is the shape #292 was
  *repaired into*, not the only prose shape a body uses; a guideline excerpt pasted into a body is fenced.
- **Require the marker to close the body, or be the last line.** Eliminated because the pass appends the marker
  below a blank line but *replaces* it in place when the body already carries one, and a body can grow below a
  stored marker through any later edit; the position is not a fact the shape owns.
- **Strip every occurrence in the digest (leave `issues_body_hash` alone).** Eliminated because the digest is
  what decides whether an item is re-interpreted: a quotation is part of the claim, so editing it must move the
  basis — otherwise an item can change its claim and stay unre-read.

## Blast radius

Three helpers and one new predicate in `tools/issues/lib.sh`; two cases in
`tools/issues/check-metadata-selftest.sh`, one in `tools/issue-tooling-cases.sh`, one fixture, and the five
roster entries that dispatch the cases. Round 2 extends the same files rather than adding any: the fixture gains
two prose lines (one per anchor), `check#aQuotationIsNotAMarker` gains the two anchor assertions (and a `missing`
answer when the predicate is absent), `prioritise#theWriteKeepsAQuotation` gains the replace half, and the
invariants row in `guidelines/Backlog.md` §4 states where the marker stands. No app source, no Kotlin, no
resource, no build logic, no skill text.

The shipped marker is unchanged: the pass writes the same line at column 0 through
`issues_prio_marker_text`, so every body that carries a marker today is read exactly as before and its digest
is byte-identical (the stripped line was the whole line then too, and the digest collapses whitespace).

`tools/issues/metadata-mutations.sh`'s `the-writer-skips-the-marker` mutation patches the first `local body=`
line of `issues_body_with_marker`; that line survives this change, so the mutation still applies and still
loses the marker. `check-metadata.sh`'s `marker-grammar-duplicated` rule still holds: the new code spells no
delimiter, it reads the two constants.

## Rollback

Revert the change's commit. The three helpers return to the delimiter search, the fixture and the three cases
disappear with their rosters, and `bash red-probe-head.sh tools/issues/lib.sh` reproduces `5 of 6 checks
failed`.

## Verification

- the probe and the three cases, red on the base commit and green on the tree, from one script
  (`evidence/red-cases-293.sh` → `red-probe-head.log`, `red-cases-head.log`, `green-probe-head.log`,
  `green-cases.log`);
- the four clauses of the shape mutated one at a time in the tree, each making a named case fail, the restore
  compared byte for byte (`evidence/revert-check-293.sh` → `revert-check.log`);
- `bash tools/issue-tooling-selftest.sh` (121 cases) and `bash tools/issues/check-metadata-selftest.sh`
  (9 cases), each ending `all N cases passed`;
- `./tools/loop/gate.sh` once: the profile maps `tools/issues/*` and `tools/issue-tooling-cases.sh` to
  `tools/issue-tooling-selftest.sh`, `tools/issues/check-metadata.sh` and
  `tools/issues/metadata-mutations.sh --list`, and `openspec/*` to the hygiene, doc-route and classification
  checks. No compiled path changes, so Gradle is not run.
