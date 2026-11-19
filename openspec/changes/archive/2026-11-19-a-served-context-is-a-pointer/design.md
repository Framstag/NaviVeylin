# Design — a served context is a pointer

## Decision 1: what the `context:` block becomes

**Alternatives**

- **A. Keep the block as it is.** Rejected: it is a second written authority for facts the repository states once.
  The drift is not hypothetical — its `Guidelines:` line priced whole-document reads at `Build.md ~29k tokens` and
  `MapRendering.md ~23k` while the routing table it points at prices *sections* (1.4–7.0 kB, measured 2026-10-22),
  and its stack, ABI, Android Auto and distribution lines restate `AGENTS.md`'s Tech Stack table and Module
  Structure section.
- **B. Move the block *and* the rules into `AGENTS.md`.** Rejected: `rules:` and `operations.*.guidance` are keyed
  per artifact and delivered only by `openspec instructions`; flattened into an always-read document they are paid
  on every session instead of when that artifact is written (proposal+specs+design+tasks ≈ 5 kB, apply 23 entries,
  archive 21 — roughly 4k tokens per session).
- **C. (chosen) Pointer + a check that holds it.** Three lines naming `AGENTS.md` and its routing table, with the
  artifact-hygiene check refusing a restated block. Cost: ~60 tokens per `instructions` call, and the facts keep one
  home. Rollback is a revert of five files.

**Risk and its limit:** a consumer that sees only the `instructions` JSON and never loads `AGENTS.md` now receives a
pointer instead of the facts. Accepted, because the pointer names the file and its routing table, and because the
facts it names are `AGENTS.md`'s own — a consumer that will not read the file that owns them cannot be served by a
copy that drifts from it either. The check bounds *shape* (≤3 lines, names the owner) and cannot bound *content*: a
three-line block could still be wrong, and that is what the review of a future edit is for.

## Decision 2: where the four facts the block alone carried go

Each goes to the section that owns it, not to a new section:

| fact | home |
|---|---|
| upstream `https://github.com/Framstag/libosmscout` | `AGENTS.md` "Native Integration" (beside the submodule-pin sentence) |
| libosmscout stays Android-free outside its frozen `Android/` directory (CI-gated) | same paragraph, as the consequence for a change that needs an Android API |
| no `python3` for OpenSpec interaction or CLI output processing | `AGENTS.md` "OpenSpec Workflow" |
| an item enters the loop only when exactly one fix follows from a proven root cause | `AGENTS.md` "Agent iteration loop" |

**Why not a new section:** the entry document is bounded by what a session needs before it reads anything else
(`documentation-ownership`, "The entry document is bounded by what a session needs"); a fifth section for four
sentences would cost a heading and a reader's detour.

## Decision 3: the check's shape

An `awk` pass mirroring the guidance pass beside it: it reads the indented lines between `^context:` and the next
unindented key, counts the non-empty ones, and reports either the count or the missing owner name. Absent block →
no output, so a project without a served context is not refused. The threshold is three lines because a pointer
needs a subject, a destination and the routing instruction, and the facts the block carried needed sixteen.

## Files that change

`openspec/config.yaml` · `AGENTS.md` · `tools/check-openspec-hygiene.sh` ·
`tools/tests/check-openspec-hygiene-selftest.sh` · `.pi/skills/triage-todo/SKILL.md` ·
`.pi/skills/cleanup-todo/SKILL.md` · `.pi/skills/openspec-open-changes/SKILL.md`

## Threading and lifecycle

None: no component is added, no dispatcher, no lifecycle owner. The change touches a YAML block, a Markdown
document, one shell script and three skill documents.

## Verification

- **The red case, offline:** `bash tools/tests/check-openspec-hygiene-selftest.sh` — 11 cases, all passing
  (`evidence/selftest.txt`); the new case carries both refused shapes and the clean tree carries the passing pointer.
- **The revert-check:** mutate the threshold (`if (n > 3)` → `if (n > 99)`), the named case must fail; restore and
  the case must pass (`evidence/revert-check.txt`).
- **The structural checks a delta with no compiled file owes** (`guidelines/Build.md` §2, the scope block):
  `tools/check-doc-routes.sh` (356 section references, 7 skill references), `tools/check-rule-routes.sh` (651 named
  rules), `openspec doctor`, and the artifact-hygiene check itself over the repository (`evidence/structural-checks.txt`).
  No Gradle build and no test suite runs: no compiled file changes, and the exclusion is named here.
- **The read-back:** `openspec instructions apply --change a-served-context-is-a-pointer --json | jq -r .context`
  prints the pointer (`evidence/context-payload.txt`).
- **Nothing to measure on device:** the change has no phone or car surface, so no `pixel-check` and no emulator run
  apply.
