## Context

One capability: `feature-list-generation`, the deterministic half of the catalogue pipeline
(`tools/gen-feature-list.sh`). The defect is in the mode the script falls back to when neither `--sections` nor
`--phrase-cmd` is given — the mode that composes an entry out of the capability's own requirement names. It is the
mode a plain `bash tools/gen-feature-list.sh --release <version>` uses, and the one the item reports.

Measured on base `2e732f0e00fa771d04569483ffe80bc2ca3fa974` (2026-10-21): the run exits 1 and writes nothing, with
seven catalogue paragraphs at 321–322 characters (`evidence/real-run-on-HEAD.log`); with the catalogue half fixed
it writes the catalogue and then exits 1 on 99 release-note keywords of more than six words
(`evidence/real-run-after-catalogue-fix.log`); with the whole fix it exits 0, writes both documents, and two runs
are byte-identical (`evidence/real-run-after-fix.log`, `evidence/real-run-after-fix-2nd.log`; longest paragraph 317
in the catalogue, 81 in the notes, longest keyword 6 tokens in both).

## Root cause

The unprompted mode prints text the gates measure and never measures it itself.

`render_mechanical` (`tools/gen-feature-list.sh:485`) joins the capability's requirement names with `; `, clips
that string to `$maxpara`, and then prints `- **<keyword>** — <clipped names>.`. The gate
(`tools/feature-list/gate.awk`) measures the paragraph as everything after the keyword's closing `**` up to the
line's end — that is, `— ` + the names + `.` — so a clip that lands at 318 to 320 characters yields 321 or 322 and
fails. The wrapper is three characters (em dash, space, full stop) and five bytes; the gate's own report counts
characters, 322 for a 319-character clip, so a name has to be shortened to 317 characters to measure 320, and the
clip reserves five — the wrapper's width when it is counted in bytes — which leaves two characters of margin in the
gate's own count. The release note is the same mode with a second violation: the
mechanical note entry reprinted the requirement name whole as the bold keyword, which the six-word rule refuses,
and the removal entry did the same. A note entry prints the same wrapper around the one name it reports
(`- **<keyword>** — <name>.`), so the same reserve applies there and reserving only the full stop leaves a name of
318 to 319 characters at 321 to 322.
None of the three sites asks what shape the gates hold a requirement name to; each prints it.

That the note half was invisible in the report is an ordering fact, not a second cause: the catalogue is
assembled at `tools/gen-feature-list.sh:667` and its gate at `:687` fails at `:756`, before the note gate at
`:828`, so the run never reached the second violation.

## The single fix

Compose to the shape the gates measure, at all three sites, and nowhere else:

- `render_mechanical`, paragraph: `clip($max - 5)` (`:500`). The reserve is the text the entry adds around the
  names — `— ` (three characters, five bytes) plus one for the full stop — so five covers the widest count.
- `entry_keyword` / `entry_paragraph` (`:526`, `:529`): one requirement name, shaped into a six-token keyword and a
  paragraph inside the bound, the paragraph shortened by the same reserve of five because the note entry prints
  the same wrapper. Used by the changed capability's note entry (`:822`) and the removed capability's note entry
  (`:804`).

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
- `tools/gen-feature-list-selftest.sh`: one assertion helper and three cases; the helper measures a *written*
  document the way the gate measures it, so a future regression in any site fails a case instead of blocking a
  release. The note case's long name lives in a capability no highlight names, so the catalogue gate cannot fire
  in front of the note gate, and its run also drops a `beta` requirement so that the removal list is not empty and
  the case cannot stand on the `pair.awk` defect.
- `guidelines/FeatureList.md` §1 gains the two failing case names and one sentence; §6 is unchanged because the
  gates did not change.
- No threading, no lifecycle, no UI, no device work: this is a text tool that reads files and invokes no model.
- Residual risk, accepted: a requirement name longer than the six-token keyword still loses its tail in the
  degraded note ("A frame from the previous orientation yields"), because the gate's rule leaves no other
  mechanical option. The alternative in the mode that matters — a phrasing command — is unaffected.
- Rollback: `git revert`; no persisted state changes shape, and the next run in either direction rewrites both
  documents whole.

## Verification

- `bash tools/gen-feature-list-selftest.sh` — 108 cases: 106 passed / 2 failed on HEAD, 108 passed / 0 failed with
  the fix (`evidence/red-on-HEAD.log`, `evidence/green.log`).
- The two failing cases assert on the written documents, not on the exit status alone, so the catalogue bound and
  the note shape are told apart. On HEAD the note case fails at the note gate for its own reason, `entry keyword is
  32 words, more than six` — its long name sits in `delta`, which no highlight names, so the catalogue gate is not
  in front of it. The same case holds the note entry's reserve: with the fix's helper reserving only the full stop
  it fails at `entry paragraph is 322 characters … (line 11)` (`evidence/red-on-half-fixed.log`).
- Revert-check, in this order: the base commit's `tools/gen-feature-list.sh` restored in place makes the two cases
  fail (`evidence/red-on-HEAD.log`); the reviewed fix with `entry_paragraph` reserving `$max - 1` makes the note
  case fail (`evidence/red-on-half-fixed.log`); the corrected file restored, the suite is green at 12:32:10,
  after both mutations (`evidence/green.log`).
- A real mechanical run over the shipped specs exits 0 and writes both documents, and two runs are byte-identical;
  the documents measure 19 entries / 317 characters and 144 entries / 81 characters, no keyword above six tokens,
  and their sha256 (`66bcaf14…`, `2280ba78…`) is unchanged from the run taken before this correction, because the
  shipped note paragraphs are far inside the bound.
- `bash tools/check-doc-routes.sh` (89 references), `bash tools/check-portability.sh` and `openspec validate --all`
  (176 passed, 0 failed) are in `evidence/` as well; the delta touches no compiled file, so no Gradle gate runs.
