# Proposal

## Why

`github-issue-tooling` built the tracker surface and verified it with a dry run; the backlog itself is
still `TODO.md` — 3 494 lines, ~384 KB, ~100 k tokens, the file that puts every reader in the dumb
zone and the reason no backlog query is cheap. Concept doc §4 step 1 is explicit: the migration runs
**per script, not per LLM**, one `## N.` section per issue, the metadata line translated to labels, the
`§N` number kept in the title.

This is the change that flips the source of truth, and it is deliberately the one with the irreversible
public step. Its cost is not the issue creation — the tooling already previews all 137 in a dry run
(the run's own measurement; this document said 136 before it, see `import-evidence.md` §1) —
it is the **cross-references**, measured 2026-10-16:

```
324   §N references outside TODO.md (guidelines, AGENTS.md, skills, config.yaml, build files)
292   archived-change artifacts that name TODO.md — immutable history, never rewritten
 12   tracked skills that read the file
  7   guideline documents that name it
  4   live specs that name it as provenance (compass-button, osmscout-jni,
      route-analysis, route-planning-session)
  1   CI gate whose error message quotes a §N (build.yml, the capability-classification step)
```

So the flip is only safe if `§N` keeps resolving after the file is gone, which is why
`github-issue-tooling` put the number in the issue title and an `<!-- nvid: N -->` marker in the body.
This change makes that resolution a command and then deletes the file.

## What Changes

- **The live import runs.** 137 issues, one per `## N.` section: `§N` in the title, the section body
  verbatim, labels from the taxonomy, `source/todo-import`, and `<!-- nvid: N -->` in the body. Executed
  with `import.sh --apply`, throttled, and verified against the recorded dry run before
  `TODO.md` is touched. Every issue arrives **open** with its own `status/*` label and **none** carries
  `agent/eligible`.
- **`§N` resolution becomes a command.** `tools/issues/list.sh` gains `--id N`, matching the `nvid`
  marker, so a `§N` quoted in a guideline, a config rule or an archived change still finds its entry
  after `TODO.md` is gone.
- **`TODO.md` is deleted**, as the last step, once every consumer has been rewired — not before. The
  tracker pointer that replaces it lives in `AGENTS.md` (the place a reader actually looks), which this
  change is editing anyway.
- **All 12 skills stop reading `TODO.md`.** Eleven of them are pure rewiring: `triage-todo` ranks from
  labels and loads bodies only for candidates, `cleanup-todo` becomes a tracker hygiene pass,
  `process-failure-log` files its extracted guardrails as issues instead of appending sections. The
  twelfth, `fix-loop`, gets its **content** flip here — its `TODO.md` bookkeeping in Phase F becomes
  `update.sh`/`close.sh` — while its **mechanism** flip (script entry, worktree and branch ownership,
  two `pi` processes per item, the outer wrapper) is `agent-issue-loop`, which consumes the tracker this
  change creates. The two halves are split this way so that no change leaves a skill reading a deleted
  file.
- **The harness is rewired where it names the file**: `AGENTS.md` (the "Backlog maintenance" section,
  the Documentation Map row, the iteration-loop bullets), `openspec/config.yaml` (every `rules.*` entry
  and every `operations.*.guidance` entry that names `TODO.md`), the seven `guidelines/*.md` documents,
  and the CI step whose error message quotes a `§N`.
- **Historical references are left alone, on purpose.** The 292 archived-change artifacts and the four
  live specs that name `TODO.md` as provenance are history: they are not rewritten, and the rule that
  replaces the file says so, so a reader who follows an old reference knows where to look.
- **The backlog's own accounting moves into the tracker**: the cluster index becomes two saved searches
  (open bugs; open improvements), and the file's `**Clusters**` block is not carried anywhere as a
  maintained list.
- **Explicitly not in this change**: the loop's port (`agent-issue-loop`), the decision queue and
  scheduled runs (concept doc §4 step 4), the hotspot loop (§3), and any change to issue bodies after
  import — the import is a one-way copy, not a synchronisation.

## Capabilities

### New Capabilities

- `backlog-source-of-truth`: the tracker is the project's only live backlog — every instruction that says what to work on names it, every reference to a migrated item's identifier resolves without the record it came from, history keeps its own text, the migrated backlog's shape is recorded and none of it arrives pre-authorised, and the tooling keeps working for a person with no automated actor present.

### Modified Capabilities

None. The four live specs that mention `TODO.md` name it as provenance only; no requirement of theirs
changes.

## Impact

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `tools/issues/list.sh` — the `--id N` filter (the `nvid` lookup)
- `.pi/skills/triage-todo/SKILL.md`, `cleanup-todo/SKILL.md`, `process-failure-log/SKILL.md`,
  `compose-geometry/SKILL.md`, `device-check/SKILL.md`, `openspec-open-changes/SKILL.md`,
  `openspec-proof-gap-audit/SKILL.md`, `pixel-check/SKILL.md`, `provision-phone-emulator/SKILL.md`,
  `revert-check/SKILL.md`, `run-tests/SKILL.md` — the eleven pure rewires
- `.pi/skills/fix-loop/SKILL.md` — the Phase F content flip only
- `AGENTS.md`, `openspec/config.yaml`, `guidelines/Build.md`, `Design.md`, `FeatureList.md`,
  `Logging.md`, `MapRendering.md`, `Regulatory.md`, `UI.md`
- `.github/workflows/build.yml` — the capability-classification step's message

**Only instructions, never citations.** Measured by reading every occurrence: ~300 of the references are
`§N` provenance citations (`TODO.md §113`, `tracked in TODO.md §153`) and stay byte-identical, including
all three in `app/build.gradle.kts` (`:291`, `:401`, `:531`), the four in `tools/*.sh|py`, the two in
`build.yml`, the 41 in the guidelines, the four live specs and the 292 archived artifacts. Roughly 20
sites actually **instruct** a reader or a process to use the file, and only those are rewired:
`openspec/config.yaml:21,92,129` · `AGENTS.md:43,156` · `guidelines/Build.md:107,417,880` ·
`guidelines/UI.md:1003` · the 12 skills. **No compiled file, no build script and no workflow is edited**,
so this change runs no Gradle build and no suite — its gate is a reference check plus the structural
checks (`design.md` D2, D12).

Deleted:
- `TODO.md` — the last step, verified by the structural check below

Added:
- `openspec/changes/migrate-todo-to-github-issues/import-evidence.md` — the live run's counts, the
  per-issue numbers with their `§N`, and the verification that every `§N` resolves

Not affected: no Kotlin/Java/C++ under `app`/`auto`/`core`/`osmscout-client-java`/`buildSrc`, no
manifest, no native or JNI boundary (so no submodule patch and no bridge-module override).
`openspec/changes/archive/**` and the four provenance specs are untouched by design. Neither
distribution flavour changes.

Scope: agent-harness and process only — variant-independent; neither phone-only nor car-only work.
Guidelines referenced: `guidelines/Build.md` §1 (skills and harness prerequisites), §2 (the iteration
protocol and its scope block), and every guideline document that is edited for its `TODO.md`
reference. No behavioural section of `UI.md`, `MapRendering.md`, `Regulatory.md` or `Logging.md`
changes — only the references to the backlog file.

**Additive for the app, breaking for the process.** Nothing the app does changes; what breaks is the
habit of reading `TODO.md`, deliberately and once. **Rollback**: `TODO.md` is recovered from git
(`git checkout <sha> -- TODO.md`) and the rewired references are reverted. The imported issues cannot
be un-created — the tooling has no delete path (`github-issue-tooling`, D6) — so a mistaken import is
corrected by closing the wrong issues with a comment naming the correction. That asymmetry is why the
import is verified against the recorded dry run *before* the file is deleted.

**Verification.** The import's counts are consumed from `import.sh --json` and cross-checked
against `grep -c '^## ' TODO.md` and `grep -c '^\*\*id:\*\*' TODO.md`; every `§N` in the tree is
resolved through `list.sh --id N`; the issue count, the label histogram and the `agent/eligible: 0`
assertion are recorded; the structural check is that no live file in the tree still names `TODO.md`
outside the archive and the four provenance specs. No device measurement applies.
