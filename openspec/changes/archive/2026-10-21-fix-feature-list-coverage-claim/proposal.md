# Proposal — fix-feature-list-coverage-claim

## Why

Root cause: `guidelines/FeatureList.md` states its gate cases and its gate table by hand, and nothing derives
them from the suite that enforces them, so when the catalogue was narrowed to the shortlist
(`tools/feature-list/highlights.json`) the prose kept the *pre-shortlist* coverage claim — a refusal of "a
user-visible spec that no bullet reaches" that the tool does not perform and that the shipped spec set
contradicts — and four other case names in the same file name checks that do not exist.

The Phase A evidence block, verbatim:

```
Root case today:  `guidelines/FeatureList.md:143` (gate table, row "coverage") states the gate refuses
                  "a user-visible spec that no bullet reaches"; `guidelines/FeatureList.md:116-117` names the
                  case `gates: a user-visible spec no bullet reaches fails the run`; `:109-112` argues from the
                  same claim ("no user-visible *spec* is dropped silently"). The tool's coverage refusal is
                  `selling point not reached: <id>`, selected over `$required` — the highlights whose spec set
                  is still shipped — at `tools/gen-feature-list.sh:703,717`, and its case is
                  `gates: an unreached selling point fails the run` (`tools/gen-feature-list-selftest.sh:409`).
                  A spec no highlight names cannot be reached at all: an entry citing it fails as
                  "cites specs the shortlist does not name" (`tools/gen-feature-list.sh:720-722`).
Measurement:      user-visible shipped specs named by no highlight =
                  `jq -r --slurpfile h tools/feature-list/highlights.json '(($h[0].highlights | [.[].specs[]]) as $n
                  | [.specs | to_entries[] | select(.value.userVisible == true) | .key]
                  | map(select(. as $s | ($n | index($s)) == null)) | length)' tools/feature-list/specs.json`
                  → 39 (2026-10-21; the item measured 38 on 2026-10-13), while highlights = 19.
Red probe:        `bash .pi/issue-loop/items/133/red-probe.sh --root <tree at 44b673f4>` → exit 1,
                  "stated cases: 39, without a carrying case: 7", naming
                  `gates: a user-visible spec no bullet reaches fails the run`, `gates: the keyless bullet is
                  named`, `density: one entry per capability fails the run`, `density: a merged area passes`,
                  `catalogue: the report states the density it achieved`,
                  `catalogue: the car-only capability of a feature area stays under that area`,
                  `catalogue: it is not moved into the car-only area`.
                  (structural check — no compiled file is touched, so no JUnit tally exists for this delta)
Spec:             feature-list-generation / "The catalogue is a selection, not an inventory"
                  (`openspec/specs/feature-list-generation/spec.md:115-121`: "a capability that no highlight
                  names is deliberately absent, however user-visible it is")
Guideline:        guidelines/FeatureList.md §1, §4, §5, §6; .pi/skills/feature-list/SKILL.md §3, §4
```

The defect contradicts nothing in the specs: `feature-list-generation` already states the shortlist rule, and
`tools/gen-feature-list.sh` already implements it. The guideline is the side that is wrong, and
`openspec/config.yaml`'s specs rule says which way that is resolved — "If a spec contradicts a guidelines/ doc,
update the guideline in the same change". Exactly one fix therefore follows: restate the guideline and the
skill over selling points and correct the four case names in the same file that no suite carries. The
alternative the item offered — restoring a spec-level coverage check — is impossible, not merely unchosen: it
would fail every run on 39 shipped user-visible specs and would contradict the spec requirement quoted above
(design.md D1/D2).

## What Changes

- **`guidelines/FeatureList.md` §6** — the coverage row of the gate table refuses "a selling point that no entry
  reaches"; the `source marker` row keeps its meaning (a bullet, one key per line).
- **`guidelines/FeatureList.md` §4** — the `userVisible` bullet no longer promises spec-level coverage; it says
  what the shortlist decides and that the coverage gate fails only when a named selling point reaches no entry.
  The
  coverage case leaves §4's case list: it belongs to §1, where the shortlist rule is stated, and it is named
  there as `gates: an unreached selling point fails the run`.
- **`guidelines/FeatureList.md` §1** — the second `Cases:` block is removed: it repeated the keyword cases and
  named three `density:` cases and a density report that neither the tool nor the spec has. The length bound
  sentence above it keeps the cases that do exist.
- **`guidelines/FeatureList.md` §5** — the case list names the cases that exist
  (`catalogue: a dual-surface capability tags both surfaces`, `catalogue: the car-only area is rendered under
  its own heading`, `catalogue: a dual-surface capability appears once`) instead of two that never existed.
- **`guidelines/FeatureList.md` §6** — `gates: the keyless bullet is named` becomes the case the suite actually
  prints, `gates: the keyless entry is named`.
- **`.pi/skills/feature-list/SKILL.md`** — §3 says a catalogue section covers the shortlisted capabilities of
  its area (the bundle's highlights); §4's failure list says "a selling point no entry reaches".
- **No tool, no spec and no compiled file changes.** The behaviour is right; the prose is what was wrong.

## Impact

- Files: `guidelines/FeatureList.md`, `.pi/skills/feature-list/SKILL.md`, this change's artifacts and evidence.
- No module, no Gradle input, no JNI, no stylesheet, no data file. Nothing compiled, so no build is owed
  (`guidelines/Build.md` §2, scope block).
- Additive to the docs, breaking to nothing. Rollback: revert the two prose files — they carry no code path.
- A reader (human or agent) who measured coverage against the old row measured the wrong set; after this change
  the row, the case name and §4's argument name the set the tool measures.
