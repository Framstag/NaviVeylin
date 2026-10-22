# Design — the word report is bounded per area and ranked

## Root cause

`tools/feature-list/gate.awk` prints an unverifiable content word the instant the per-entry loop finds it
(`:106`), one line per (word, entry) pair, with nothing that ranks it and nothing that counts what it left out. The
report was written to make a claim the gates cannot settle visible to a reviewer; the shape it was written in makes
it invisible at scale — 352 advisory lines on the first real generation, over 12 areas
(`openspec/changes/archive/2026-10-09-generate-feature-list/apply-evidence.md:484`, `:620`, and `:456` for the 12
areas and 582 bullets). The check is right and the
format is wrong, which is why the recorded decision on issue #103 is to cap the report rather than narrow the check.

## The fix

One mechanism, three parts, all in the gate:

1. **Collect instead of print.** `flush_entry` hands each candidate word to `note_word(w, excerpt)`, which records it
   once per area (first entry that used it, first 120 characters, as today) in discovery order.
2. **Rank at the area boundary.** The gate's first input line per spec (`NR == FNR`) also counts, per word, how many
   specs of the project contain it (`specfreq`). At a `## ` heading and at end of file, `flush_words` sorts the
   area's candidates by that count ascending — words absent from every spec (count 0) first — with the word itself
   as the tie-break, so the report is deterministic, and prints at most `maxwords` of them.
3. **Say what was suppressed.** One summary line per area that has candidates:
   `unsourced words in 'Area One': 13 found, 10 shown (rarest first), 3 not shown`. The line is printed even when
   nothing was suppressed, so a reader can tell a small report from a truncated one without knowing the cap.

`tools/gen-feature-list.sh` declares `max_words=10` beside `maxpara` and passes `-v maxwords=…` to both call sites;
`gate.awk` defaults to 10 when the variable is absent, so the awk file still runs on its own (`guidelines/FeatureList.md`
§6 says it can). The release note's entry is written with a `## <release>` heading of its own
(`tools/gen-feature-list.sh:822`), so that version is the area the note's report is labelled with; `area_label()`'s
`the document` arm is reached only by prose handed to the gate with no `## ` heading at all, which is neither call
site's input.

## Why this bound, and not another

- **10 per area.** The first real generation ran 352 lines over 12 areas — about 29 per area — so 10 keeps the third
  of the report that a reviewer can act on and states the rest as a number. Above 10 the report regrows into a wall;
  below it the rarest vocabulary mismatches (the `favourite`/`favorite` class, which occur in a handful of bullets)
  start being suppressed, which is the failure mode the report exists to prevent.
- **Rarest first, not the paragraph order the gate walks.** A word no spec contains is the strongest evidence of an
  invented term; a word one other spec contains is usually ordinary prose. Ranking is what makes a cap admissible:
  truncation keeps the strongest candidates, so the cap cannot hide the signal it is applied to.
- **Per area, not per run.** Areas are the unit the catalogue is generated and reviewed in (`areas.json`,
  "Only dirty areas are regenerated"), so a cap per area keeps every area's report complete while the run as a whole
  stays readable, and an area that regresses cannot be hidden behind eleven quiet ones.
- **Once per area, not once per entry.** `N words per area` is a count of words; the same connective repeated in five
  bullets is one candidate, and 352 lines for 225 bullets is mostly that repetition.

## Blast radius

- `tools/feature-list/gate.awk` — the candidate path (one print becomes a collect), the area boundary (a new rule in
  the existing `/^#/` arm), the `END` arm (closes the last area), and the `NR == FNR` arm (the per-word spec count).
  The failure gates — marker, figure, keyword, paragraph length, shortlist — are untouched, and so is the union of
  cited spec text they test against.
- `tools/gen-feature-list.sh` — one constant and two `-v` arguments; no control flow.
- `tools/gen-feature-list-selftest.sh` — four cases; the fixture gains no area and no spec, so the existing case
  count moves by exactly four.
- `guidelines/FeatureList.md` §6 only.
- Not affected: `FEATURES.md`/`RELEASE-NOTES.md` content (the report is printed, never written to either), the cache,
  the snapshot format, the prompt digest, the classification gate, `tools/feature-list/pair.awk`.

Memory: `specfreq` holds one counter per distinct word of the shipped specs (`openspec/specs/**/spec.md`, one line
each) — measured at 6 218 distinct words over 177 spec files
(`find openspec/specs -name spec.md -print0 | xargs -0 cat | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '\n' | sed '/^$/d' | sort -u | wc -l`),
against an awk process that already holds the same spec text. The candidate
list is per area and cleared at each boundary.

## Alternatives the evidence eliminated

- **Narrow the report to words in no spec of the project at all** (the item's option 2). Measured against the
  recorded decision: it keeps the vocabulary-mismatch signal but deletes the per-bullet one — "this bullet's own
  specs do not support this word" — which is the sentence the requirement states. Eliminated by the owner's decision,
  not by a defect.
- **Turn it into a gate with a maintained allow-list** (option 3). Requires that every new feature prose be added to
  a hand-kept list before the pipeline goes green: a maintenance cost in a build-time tool whose whole point is that
  the checks are derived. Eliminated by the owner's decision.
- **Cap the report without ranking** (print the first N candidates the gate meets). Rejected on evidence from the
  red run itself: paragraph order put `sole` (a word one fixture spec contains) before `marmot` (a word no spec
  contains), so an unranked cap would truncate the strong candidates first and keep the weak ones — the cap would
  hide exactly what the report exists to surface.
- **Keep every line and summarise at the end.** Leaves the volume that buried the signal, and the item's evidence is
  that the volume, not the absence of a count, was the defect.

## Rollback

`git revert` the change. Nothing persisted depends on it: the report goes to stderr, the caller prints it, and no
snapshot, cache entry, prompt digest or generated document reads it. A run after the revert prints the unbounded
report again — reasoning from the diff, not a measured run — and the gates that fail a run read none of the
removed state.
