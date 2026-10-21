## Context

One capability: `feature-list-generation`, the deterministic half of the catalogue pipeline
(`tools/gen-feature-list.sh`). The defect is in the mode the script falls back to when neither `--sections` nor
`--phrase-cmd` is given — the mode that composes an entry out of the capability's own requirement names. It is the
mode a plain `bash tools/gen-feature-list.sh --release <version>` uses, and the one the item reports.

Measured on base `2e732f0e00fa771d04569483ffe80bc2ca3fa974` (2026-10-21): the run exits 1 and writes nothing, with
seven catalogue paragraphs at 321–322 characters (`evidence/real-run-on-HEAD.log`); with the catalogue half fixed
it exits 1 on 99 release-note keywords of 7 to 14 words (`evidence/real-run-after-catalogue-fix.log`); with the
whole fix it exits 0, writes both documents, and two runs are byte-identical
(`evidence/real-run-after-fix.log`, `evidence/real-run-after-fix-2nd.log`; longest paragraph 317 in the catalogue,
81 in the notes, longest keyword 6 tokens in both).

## Root cause

The unprompted mode prints text the gates measure and never measures it itself.

`render_mechanical` (`tools/gen-feature-list.sh:485`) joins the capability's requirement names with `; `, clips
that string to `$maxpara`, and then prints `- **<keyword>** — <clipped names>.`. The gate
(`tools/feature-list/gate.awk`) measures the paragraph as everything after the keyword's closing `**` up to the
line's end — that is, `— ` + the names + `.` — so a clip that lands at 318 to 320 characters yields 321 or 322 and
fails. The release note is the same mode with a second violation: the mechanical note entry reprinted the
requirement name whole as the bold keyword, which the six-word rule refuses, and the removal entry did the same.
None of the three sites asks what shape the gates hold a requirement name to; each prints it.

That the note half was invisible in the report is an ordering fact, not a second cause: the catalogue gate runs at
`tools/gen-feature-list.sh:665` and exits at `:739`, before the note gate at `:817`, so the run never reached the
second violation.

## The single fix

Compose to the shape the gates measure, at all three sites, and nowhere else:

- `render_mechanical`, paragraph: `clip($max - 5)` (`:499`). The reserve is the widest form of the text the entry
  adds around the names — `— ` is three bytes where the gate counts bytes in a C locale, which is its widest form,
  plus one for the full stop.
- `entry_keyword` / `entry_paragraph` (`:524`, `:527`): one requirement name, shaped into a six-token keyword and a
  paragraph inside the bound. Used by the changed capability's note entry (`:820`) and the removed capability's
  note entry (`:802`).

Nothing else moves: the gates, the bound, the prompt, the snapshot format and the catalogue's jq clip for
multi-name paragraphs are unchanged.

## Alternatives the evidence eliminated

1. **Raise the 320 bound.** Eliminated: nothing measured says the bound is wrong — the paragraphs exceeded it by
   one or two characters, which is a clip off by the lead-in, not a paragraph that needs more room. The bound is
   what keeps a 239-entry document from becoming 19 walls of text (`guidelines/FeatureList.md` §1), and raising it
   would relax the model-written case as well, which never failed.
2. **Trim the four paragraphs by hand.** Impossible: both documents are regenerated whole on every run and
   `guidelines/FeatureList.md` §9 says never to edit them by hand, so a hand trim is discarded by the next run.
   And the text is not authored — it is the requirement names joined — so "trimming" means renaming capabilities
   to please a formatter, which is a product decision this defect does not license.
3. **Report the offending requirement names instead (the item's fix candidate).** Eliminated as *the* fix: the run
   would then name what it cannot compose, on every run, for ever — the paragraph is recomposed identically from
   the same names, so no report can be acted on and the documents stay unwritten. The report already names the
   line and the paragraph's first 120 characters, which is enough to find the entry. This change leaves the
   report alone.
4. **Let the mechanical mode skip a capability whose names do not fit.** Eliminated: an entry that is not written
   is a selling point the reader does not get, and the shortlist check would then fail instead
   (`selling point not reached`), moving the failure rather than removing it.
5. **Exempt the mechanical mode from the gates.** Eliminated: an unprompted run would publish prose no gate ever
   read, which is exactly what the gates exist to prevent, and the exemptions would grow.

## Blast radius and risk

- `tools/gen-feature-list.sh`: three composing sites plus two helpers, ~25 lines. The only behaviour that changes
  is the text an unprompted run emits: shorter paragraphs in the catalogue, a six-token keyword and a clipped
  paragraph in the release note. A run with `--phrase-cmd` or `--sections` is untouched.
- `tools/gen-feature-list-selftest.sh`: one assertion helper and two cases; the helper measures a *written*
  document the way the gate measures it, so a future regression in either site fails a case instead of blocking
  a release.
- `guidelines/FeatureList.md` §1 gains the two case names and one sentence; §6 is unchanged because the gates did
  not change.
- No threading, no lifecycle, no UI, no device work: this is a text tool that reads files and invokes no model.
- Residual risk, accepted: a requirement name longer than the six-token keyword still loses its tail in the
  degraded note ("A frame from the previous orientation yields"), because the gate's rule leaves no other
  mechanical option. The alternative in the mode that matters — a phrasing command — is unaffected.
- Rollback: `git revert`; no persisted state changes shape, and the next run in either direction rewrites both
  documents whole.

## Verification

- `bash tools/gen-feature-list-selftest.sh` — 107 cases: 105 passed / 2 failed on HEAD, 107 passed / 0 failed with
  the fix (`evidence/red-on-HEAD.log`, `evidence/green.log`).
- The two new cases assert on the written documents, not on the exit status alone, so the catalogue bound and the
  note shape are told apart; the note case carries a baseline run and a lost requirement so both note branches
  (changed and removed) are exercised.
- Revert-check: the production change reverted in place (`git show HEAD:tools/gen-feature-list.sh`) makes exactly
  those two cases fail with `entry paragraph is 322 characters`; restored, the suite is green — run and recorded
  as `evidence/red-on-HEAD.log` and `evidence/green.log`.
- A real mechanical run over the shipped specs exits 0 and writes both documents, and two runs are byte-identical.
