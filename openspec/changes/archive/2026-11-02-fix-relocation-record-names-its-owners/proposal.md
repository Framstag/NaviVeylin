# A relocation record names the owners it cites

## Why

`openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/relocation-entry-document.txt:19` proves
that the entry document's nine-skill table could be removed by citing its owners as a directory listing:

```
    owner: .pi/skills/*/SKILL.md (the skill's own contract) — no guideline owns a skill's job; the nine
```

followed by every skill contract in the tree — 35 of them (`:21` to `:55`). The block it justifies named eight skills,
and the sentence above it says nine
(`git show 4330ce3e^:AGENTS.md | sed -n '/^| Skill | Job |/,/^$/p' | grep -c '^| `'` = 8;
`sed -n '/^B2/,/^B3/p' openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/relocation-entry-document.txt | grep -c '^\s*\.pi/skills/'`
= 35). The record does not show the claim — each skill's job is stated in its own contract — for the owners its
block was about, so a reader cannot check it for the set it names. The change's own review graded that
NOTE on evidence quality (`evidence/known-gaps.md:22`, G2) rather than a defect in the tree, and the archived record
is history: this change does not repair it.

**The root cause, in one sentence.** A relocation record cites the owners of a claim as the directory they live in —
a glob, and everything it expands to — instead of the owners the removed block named, so the record's claim holds
for an unidentified set and no check reports it.

**The red case.** `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported`, added to
`tools/check-openspec-hygiene-selftest.sh`. It builds a throwaway tree whose open change carries exactly the record
above, runs the checker, and requires the record's file and line to be reported; it also requires the same lines
under `openspec/changes/archive/` to be reported by nothing. On base `650028bf` the case fails
(`evidence/red-on-HEAD.log`: `an owner cited as a directory listing passed (exit 0)`, tests=1 failures=1,
2026-11-02T04:25:12Z, taken by `evidence/red-on-HEAD.sh` against the base's checker).

**Owning capability and its home.** `documentation-ownership`, "A relocated rule keeps its statement"
(`openspec/specs/documentation-ownership/spec.md:265`) — the requirement a document change obeys when it proves a
relocation. `AGENTS.md:57` routes "how the guidelines themselves are organized" to that spec, so the rule is stated
there rather than in a guideline: nothing under `guidelines/` is wrong because of this, and the item says so.

**Surface.** The mechanical half lands as a fourth silent-failure class in one checker, and the class is new rather
than widened: `grep -rn '^[[:space:]]*owner[s]*:' openspec/changes/ | wc -l` = 10 owner lines in 1 record, exactly 1
of them carrying a glob, and that record is archived — so no open change in the tree is refused today and the rule
holds the next document change. `grep -c '^case_' tools/check-openspec-hygiene-selftest.sh` = 7 cases (6 at base);
the checker's `clean_tree` fixture gains the passing shape (1 fixture realigned), which is what keeps case
`artifact-hygiene#aWrittenPurposePasses` a proof that the checker reports this class alone.

surface: 1 case added, 1 checker class added, 1 fixture realigned — `grep -c '^case_' tools/check-openspec-hygiene-selftest.sh`,
`grep -rn '^[[:space:]]*owner[s]*:' openspec/changes/ | wc -l`

## What Changes

- **`tools/check-openspec-hygiene.sh`** — class 4: an `owner:` line in an open change's record that carries a glob
  is reported with the record's file and line, because a directory listing stands in for the set the removed block
  named. The open changes are read; `openspec/changes/archive/` is skipped, so the record that produced the rule
  keeps what it recorded.
- **`tools/check-openspec-hygiene-selftest.sh`** — `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported`
  (above), and the clean tree gains an `owner:` line naming its owner as a path, so the class has a passing shape
  and the clean case still asserts that a well-formed record passes.
- **`openspec/specs/documentation-ownership/spec.md`** (delta) — one added requirement, "A relocation record names
  the owners it cites", and one scenario naming the case above.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership`: one requirement added beside "A relocated rule keeps its statement" — a relocation
  record names each owner as a path of its own and never as a glob — the form a directory's complete listing takes —
  and an archived record is not inspected. No shipped requirement's text changes.

## Impact

| path | what changes |
|---|---|
| `tools/check-openspec-hygiene.sh` | one class (4) added, its paragraph and the usage range in the header; the pass and summary lines are unchanged |
| `tools/check-openspec-hygiene-selftest.sh` | one case (`case_g`), one fixture line in `clean_tree` |
| `openspec/specs/documentation-ownership/spec.md` | one added requirement and its scenario, through the delta and the archive |

No compiled file, no `tools/loop/**` path, no `.pi/**` path: the gate this change owes is the shell checks `openspec/*`
and the two `tools/check-openspec-hygiene*` scripts pull in, and `./tools/loop/gate.sh` runs exactly those.
