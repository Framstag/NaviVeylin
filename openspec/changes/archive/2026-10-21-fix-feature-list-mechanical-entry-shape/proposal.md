## Why

A run without a phrasing command composed catalogue entries that the run's own prose gates then refused, so it
wrote neither `FEATURES.md` nor `RELEASE-NOTES.md` — at all, for every capability, until someone edited code.
The run in the item report showed the first gate to fire; the second was hidden behind it.

- **Verified in the tree today** ✓ (base commit `2e732f0e00fa771d04569483ffe80bc2ca3fa974`, 2026-10-21):
  `bash tools/gen-feature-list.sh --release 2026-10-10-1`, whose only prose source is the mechanical mode, exits 1
  with `the catalogue was not written` and seven entries over the bound:
  `entry paragraph is 321 characters, more than the 320 allowed (line 183)` … `322 … (line 652)`
  (`evidence/real-run-on-HEAD.log`). The count differs from the report's four because the tree moved on; the
  condition is the same one, and it is not fixed.
- **Root cause**: the unprompted mode prints text the gates measure and never measures it itself.
  `render_mechanical` (`tools/gen-feature-list.sh:485`) clips the joined requirement names to `$maxpara`
  (`:499`) and only then puts the `— ` lead-in in front of them and a full stop behind them, and the gate
  measures the paragraph *including* those (`tools/feature-list/gate.awk`, `length(para)` after the keyword's
  closing `**`), so the result is 321 or 322 characters whenever the clip lands near the bound. The release
  note is the same mode with a worse shape: it printed the whole requirement name as the bold keyword, which
  the six-word rule refuses. Both sites compose from a requirement name and neither asks what shape the gates
  hold that name to.
- **The second site was hidden by the gate order, measured**: with the catalogue clip fixed and nothing else,
  the same run reaches the note gate and fails 99 times with `entry keyword is 7 words, more than six`
  (`evidence/real-run-after-catalogue-fix.log`). The catalogue gate runs first (`tools/gen-feature-list.sh:665`)
  and exits before the note gate (`:817`), so a fix that stops at the observed symptom still leaves the run
  writing nothing.
- **Red test** (2026-10-21T12:21:06+02:00, `tools/gen-feature-list-selftest.sh`, its own tally — a shell suite has
  no JUnit XML): `tests=107 failures=2` on HEAD, `tests=107 failures=0` with the fix. The two cases assert on the
  *written documents*, so each one names the site it covers:
  `length: a mechanical catalogue entry is composed inside the bound` — HEAD: `exit 1: entry paragraph is 322
  characters, more than the 320 allowed (line 32)` (`evidence/red-on-HEAD.log`);
  `length: a mechanical release-note entry is composed inside the bound` — HEAD fails in the same run, at the
  catalogue gate that runs first; its own reason is the 99 refused keywords above.
- **Owning requirement**: `feature-list-generation` — "A paragraph stays short"
  (`openspec/specs/feature-list-generation/spec.md:170`) and "Every entry leads with a keyword and a paragraph"
  (`:77`), whose six-word rule the note entry breaks. Owning guideline: `guidelines/FeatureList.md` §1 "The
  catalogue presents only user-visible capabilities" carries both bounds and their cases (line 42, "One entry is
  one selling point, and it is short"); §6 "The prose gates, not a reviewer's memory" owns the gate mechanics and
  says where `gate.awk` lives. The item routed the bound to §6, which states the gates and not the numbers — the
  numbers, and the cases a change owes, are §1's.
- **Why it matters**: the two documents are derived, so nothing but a run can produce them, and the run can only
  produce them by passing its own gates. Every change that wants fresh catalogue output was blocked behind this,
  which is the asymmetry the item reports between the classification half (green) and the prose half (red).

## What Changes

- `render_mechanical` (`tools/gen-feature-list.sh:485`) composes the paragraph to the
  bound the gate measures: the names are clipped with the lead-in and the full stop reserved
  (`clip($max - 5)`, `:499`), the reserve being the widest form of `— ` (three bytes where the gate counts bytes
  in a C locale) plus the full stop.
- Two helpers, `entry_keyword` and `entry_paragraph` (`:524`, `:527`), shape a requirement name into the entry
  shape the gates enforce, and the three places the unprompted mode composes an entry use them: the mechanical
  catalogue entry keeps its own jq clip (it composes many names, not one), the changed capability's note entry
  (`:820`) and the removed capability's note entry (`:802`).
- `tools/gen-feature-list-selftest.sh` gains `within_bound`, an assertion over a written document measured the
  way `gate.awk` measures it, and two cases that fail on HEAD and pass with the fix, the second one needing a
  real delta (a baseline run, then a lost requirement) so that both note branches are exercised.
- `guidelines/FeatureList.md` §1 names the two cases and states that the bounds hold for the entries the run
  composes without a model.

No gate is weakened and no bound is raised: the gates are unchanged, and the mechanical mode is now held to
them. Additive in the sense that prose from `--phrase-cmd` is untouched; a run in the degraded mode produces
different (shorter) text than before, which is the point. Rollback: `git revert` — nothing persisted depends on
the change, and the snapshot format is unchanged.

## Impact

- Modified: `tools/gen-feature-list.sh` (three composing sites, two helpers), `tools/gen-feature-list-selftest.sh`
  (one helper, two cases), `guidelines/FeatureList.md` §1.
- Affected specs: `feature-list-generation` — two MODIFIED requirements, each gaining one scenario.
- No compiled file, so no Gradle task runs: the structural check is `bash tools/gen-feature-list-selftest.sh`
  (107 cases) plus a real mechanical run over the shipped specs, which is what `guidelines/Build.md` §2 asks for
  a delta that touches no Kotlin, Java, C++ or resource file.
- Scope: tooling only — no phone, car or AAOS behaviour, no UI, no rendering, no native boundary.
- Not addressed, measured, and left for a separate item: `tools/feature-list/pair.awk` reads the first line of
  its *second* file as a removal when the removal list is empty (`NR == FNR` with an empty first file), so an
  additions-only release reports the first added capability as `— no longer available.`
  (`evidence/pair-awk-empty-removed.log`). That is a separate root cause with its own symptom, and this change
  neither touches nor claims it; the second case's fixture keeps a real removal so it does not stand on it.
