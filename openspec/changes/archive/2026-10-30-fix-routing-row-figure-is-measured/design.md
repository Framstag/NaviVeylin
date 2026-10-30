# Design — fix-routing-row-figure-is-measured

## The root cause

`tools/check-doc-routes.sh:193` decides a priced row with one regular expression —
`printf '%s' "$row" | grep -qE '[0-9]+([.,][0-9]+)?[[:space:]]?kB' && continue` — and the section costs the
same script computes for `--sizes` are built in a different branch of the script, printed, and never compared with
it. One mechanism: the figure is a *presence* to the clause and a *number* only to `--sizes`, so a row can state a
figure the tree contradicts and both halves stay green (`0.1 kB` for a 1 012-byte section passes,
`evidence/measure-the-rows.txt`).

## The single fix

The costs become the clause's input instead of only the flag's output, and one clause compares them.

- **The ruler is computed once.** The `LC_ALL=C awk` block that builds `$tmp/sizes` (`file`, `num`, `bytes`,
  `lines` — a section's byte count from its `## ` heading through the line before the next one) moves out of the
  `--sizes` branch and runs for every invocation. `--sizes` then prints that file, so the flag and the clause
  cannot disagree about what a section costs: one computation, two readers.
- **The clause reads a row the way the table writes one.** A row is tokenised on backticks; a token that is a
  bare `Doc.md` sets the row's current document, and every `§<number><letter>` after it on that row is one of its
  priced references, exactly the pairing `tools/check-doc-routes-selftest.sh`'s `table_pairs` derives
  independently. Two forms are compared, and they are the two forms the tree writes:
  - **a figure after a reference** — `§15a 3.1 kB`, `§10a "Phone surface while a car session is active" 2.2 kB`:
    optional whitespace, the reference's quoted heading text if it carries one, an optional `—`/`–`/`-`/`:`/`=`
    separator, then `<number>[.,]<digits> kB`. It must equal the section's cost as
    `sprintf("%.1f", bytes / 1000)` — the `bytes/1000, one decimal` convention the rows already state in their
    own text;
  - **a range** — `1.7–5.3 kB per section`, `1.1–5.3 kB each`: two numbers joined by a dash with no space between
    them and `kB` after the second. It must state the cheapest and the costliest of the lettered sections the row
    names.
- **A past measurement is not a figure.** Three rows carry `against 23.2 kB and 23.4 kB for §2 and §4 before the
  split`, `against 22.1 kB for §6 before the split`, `against 31.6 kB for §10 before the split` — the size of the
  unsplit section, which no tree can measure and which the row states as history. Under the two forms above such
  a number matches neither (no reference precedes it, and it is not a dash-joined pair), so it is left alone by
  construction rather than by a second rule that would have to guess what "history" looks like.
- **A disagreement is one report per figure**, naming the row's label, the reference, the stated number and the
  measured one, and setting the failure the check already exits with. The summary line gains
  `, N row(s) state a cost their sections do not measure`.
- **The tree is brought to its own measurement.** Two rows fail on the first run: `1.7–5.3 kB per section`
  against `1.9–7.0` and `1.1–5.3 kB each` against `1.3–5.3` (`evidence/measure-the-rows.txt`). Both are edited to
  the measured span, and the eleven single figures are left as they are because they are already true.

Nothing else moves: the reference resolution, the ambiguity rule, the skill-path rule, the presence clause and
`--sizes`' own output shape are untouched, which is what the two new mutations pin.

## Blast radius

- Three tool files (the check, its self-test, its mutation set), two documents (`AGENTS.md`'s Documentation Map
  sentence and two rows, `guidelines/Backlog.md` §9), and one ADDED requirement in
  `openspec/specs/documentation-ownership/spec.md`. Six files, plus this change's artifacts.
- Nothing compiled: no Kotlin, Java, C++, Gradle, manifest, resource or device. The gate's build half is not owed
  (`guidelines/Build.md` §2's scope block names `*.md` and `tools/**` as no compiled file).
- **The self-referential risk, named:** `Backlog.md` §9 is 15 389 bytes and already one of the eight sections the
  `routed-section-size` row convicts, and this change makes its paragraph longer, not shorter. The alternative —
  stating the rule somewhere other than the section that owns the routing table's doctrine — would split one
  doctrine across two homes to keep a number under a bound, which is the erosion the row exists to make visible.
  §9's split is a change of its own and the row will file it.
- **The check gets stricter on a tree that is already checked**: everything that resolved before still resolves,
  and the only new failures are figures that disagree. The self-test asserts the project's own table passes, so a
  future stale figure is a red suite rather than a red pipeline.

## Rollback

Revert the commit. The clause, the two cases, the two mutation records, the two realigned ranges, the widened
`AGENTS.md` sentence and the `Backlog.md` §9 paragraph go together; the check returns to reading a figure's
presence, `--sizes` keeps its output (it now shares the one computation, which reverts with it), and the tree
keeps the two stale ranges it carried before.

## The alternatives the evidence eliminated

- **Leave presence-only, as #197 deliberately did.** This is not an option the item leaves open: #197's own
  round-2 note calls presence "the deliberate first step" and names the reason it could not go further — the
  comparison needed a ruler, and `--sizes` now is that ruler. The presence half is also already failing its own
  purpose: two of the nine rows it accepts state a figure the tree contradicts. Keeping the step is keeping the
  drift the item reports.
- **Compare the `before the split` aggregates as well.** Impossible: `23.2 kB` is the size of `Build.md` §2 as one
  section before the 2026-10-22 split, and the unsplit section no longer exists in the document, so no ruler can
  measure it. A comparison there would convict every one of the three rows forever unless the history were
  deleted, and the history is the provenance the row exists to keep.
- **Put the comparison in a sensor row instead of the check.** Impossible under the registry's own rule: a row
  files items and closes them by identity, while this is a claim about the table's own text that a build step can
  refuse — the same reason `routed-read#route` lives in the check and not in `sensors.json`. A row for "a row
  states a figure that disagrees" would also need the check's parser, which would then live in two homes.
- **Have the clause run `--sizes` as a subprocess and diff its report.** Eliminated by measurement: the report is
  text for a reader (146 lines on this tree), so the check would parse its own output back into numbers, and a
  change to the report's wording would silently disable the clause. Computing the costs once and giving both
  readers the same array is one code path and cannot drift.
- **Compare figures by truncation** (`bytes / 1000` cut at the first decimal, as some documents read). Eliminated
  by the tree: `UI.md` §3c is 2 763 bytes and its row states `2.8 kB`, and §6a is 6 043 bytes with `6.0 kB` — the
  rows round, they do not truncate, so a truncating comparison would convict rows that are correct.
- **Report the whole row rather than each figure.** Eliminated by the case: a row states three figures and a
  reader needs to know which one is wrong; the two-number report is what `routed-read#route`'s message already
  does for a missing cost, one step further.
