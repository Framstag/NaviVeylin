## Why

The feature catalogue's advisory word report is printed without any bound, so the first real generation buried its
own signal: **352** `unsourced word:` lines for 225 bullets in 12 areas. The requirement behind it is deliberate —
a claim the prose gates cannot settle should be visible to a reviewer — but at that volume nobody reads it, which
defeats the requirement instead of serving it (issue #103, recorded decision on the item: **option 1, cap it**).

- **Verified in the tree today** ✓ (2026-10-22, run base commit `9aac52b5d9778a6d5cc2a92cb95a35dc9c7eb00e`):
  `tools/feature-list/gate.awk:106` — `if (index(low, w) == 0) printf "unsourced word: %s | %s\n", w, … >
  "/dev/stderr"` — is the whole implementation. It is inside the per-entry loop, so the report is one line per
  (word, entry) pair, unbounded, in paragraph order, with no count of anything the reader cannot see. The caller
  prints whatever arrived (`tools/gen-feature-list.sh:725-728`), so the volume reaches the terminal unchanged.
  Nothing in the tree caps it or ranks it: `grep -n 'maxwords\|cap' tools/feature-list/gate.awk` returns nothing.
- **The volume is recorded, not guessed**: `openspec/changes/archive/2026-10-09-generate-feature-list/evidence.md`
  — "the advisory word report came back with 352" (line 484) and "352 lines on the first fanout, 104 after the …"
  (line 620) — so the condition is a measured one and it is not fixed.
- **Root cause**: the report has no bound, because `gate.awk` prints a candidate word the moment it finds one
  instead of collecting the area's candidates, ranking them and showing a bounded number — the report's format, not
  its check, is the defect (the item's own lesson: the 352 lines did flag a real British/American vocabulary
  mismatch, which is now a rule in `tools/feature-list/prompts/tone.md`, and was found *because* every instance was
  printed).
- **Red test** (2026-10-22T09:26:32Z, `tools/gen-feature-list-selftest.sh`, whole suite):
  `tests=119 failures=4` on HEAD, the fix not yet in the tree; `evidence/red-on-HEAD.xml`, its log in
  `evidence/red-on-HEAD.log`:
  - `gates: the word report's cap is declared by the tool` — no `max_words` in `tools/gen-feature-list.sh`;
  - `gates: the word report shows no more than its cap in one area` — `expected [10], got [13]`, thirteen
    candidate words in one area and thirteen lines;
  - `gates: the word report counts the candidates it did not show` — no count line exists;
  - `gates: the word report names the rarest word first` — `marmot at line 16, sole at line 15`, i.e. paragraph
    order, though `sole` occurs in one fixture spec and `marmot` in none.
- **Owning requirement**: `feature-list-generation` — "Prose the run cannot verify is reported for review"
  (`openspec/specs/feature-list-generation/spec.md:238`), whose "SHALL report … every content word" is what the
  delta rewrites into a bounded, ranked report.
- **Owning guideline**: `guidelines/FeatureList.md` §6 "The prose gates, not a reviewer's memory" — the gates table's
  `words` row (line 160), the paragraph that explains why the word report is not a gate and must stay readable
  (lines 178-181), and the case list (lines 183-185).
- **Surface, counted before the plan was fixed** (`grep -c 'unsourced\|word report'` over each file):

surface: 4 cases added, 5 existing gate cases kept green and named, 1 spec requirement rewritten (6 scenarios: 3 kept + 3 added), 1 guideline section (§6: table row, one paragraph, the case list), 2 `gate.awk` call sites in `tools/gen-feature-list.sh` (catalogue and release note), 1 `max_words` constant declared beside `maxpara` — command: `grep -c 'unsourced\|word report' tools/gen-feature-list-selftest.sh (8), tools/feature-list/gate.awk (1), guidelines/FeatureList.md (2), openspec/specs/feature-list-generation/spec.md (6)`.
- **Why it matters**: the report is the only place the pipeline's own judgement is visible. A report nobody reads
  is a check that does not exist, and the next vocabulary mismatch — the kind the 352 lines did catch — lands
  unseen in `FEATURES.md`.

## What Changes

- `tools/feature-list/gate.awk` collects an area's candidate words instead of printing them as it walks the
  catalogue. At the area boundary (`## `, i.e. a feature area; end of file closes the last one) it shows at most
  `maxwords` of them, rarest first — a word no spec of the project contains ranks ahead of one that a single spec
  contains, ties alphabetically — names each candidate once per area with the first entry that used it, and prints
  one summary line carrying three numbers: found, shown, not shown.
- `tools/gen-feature-list.sh` declares `max_words=10` beside `maxpara` and passes it to both `gate.awk` call sites,
  so the cap is a number the guideline can state and the self-test can read.
- `tools/gen-feature-list-selftest.sh` gains four cases: the tool declares the cap, one area with thirteen
  candidates yields exactly the cap's worth of lines, the summary line counts what was not shown, and a word in one
  spec is ranked behind a word in none. The five existing gate cases (`the unsourced word is named`, `the report
  does not stand in for the figure gate`, `a fully sourced bullet is not reported`, `an unsourced word is reported
  and the run stays green`, `a well-sourced section passes`) keep passing unchanged — the per-word line keeps its
  exact form, so the reports they assert on are still there.
- `guidelines/FeatureList.md` §6's `words` row and its paragraph state the bound, the ranking and the count, and the
  case list names the new cases.

No gate is weakened: the report stays advisory, the figure gate is untouched, and the per-bullet check is intact —
only what the report shows of it is bounded, which is exactly the recorded decision. Rollback: `git revert`; nothing
persisted depends on the change (no snapshot, cache or prompt digest reads the report).

## Impact

- Modified: `tools/feature-list/gate.awk`, `tools/gen-feature-list.sh` (one constant, two call sites),
  `tools/gen-feature-list-selftest.sh` (four cases), `guidelines/FeatureList.md` §6.
- Affected specs: `feature-list-generation` — one MODIFIED requirement, 3 scenarios kept and 3 added.
- No compiled file, so no Gradle task runs: the structural check is `bash tools/gen-feature-list-selftest.sh`
  (119 cases), which is what `guidelines/Build.md` §2 asks of a delta that touches no Kotlin, Java, C++ or resource
  file. `tools/check-doc-routes.sh` and `tools/check-portability.sh` run because a guideline changed, and
  `openspec validate --all` is carried in the evidence.
- Scope: build-time tooling only — no phone, car or AAOS behaviour, no UI, no rendering, no native boundary.
