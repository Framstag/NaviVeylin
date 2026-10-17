---
name: process-failure-log
description: Processes NaviVeylin's `ki_processing_failures.log` — extracts every failed approach into an actionable guardrail (guideline / skill / AGENTS.md / CI / build-file item), filed as one issue through `tools/issues/create.sh` (fingerprint-deduped), then clears the processed entries from the log. Use when asked to "process the failure log", "turn the failure log into actions/guardrails", "clean up ki_processing_failures.log", or as a periodic hygiene pass after several changes shipped.
---

# Process the KI failure log

Turns `ki_processing_failures.log` (raw incident notes, gitignored) into durable guardrails:
the log holds *what went wrong*; the guardrail issues of the tracker hold *what prevents it*.

## When to use

- User asks to process / mine / clean `ki_processing_failures.log`
- Several changes shipped and the log has grown (periodic hygiene pass)
- A repeated failure class (same mistake twice) should become an enforceable rule

Sibling pass: `openspec-proof-gap-audit` harvests unproven requirements from the archived bugfix
changes the same way this skill harvests unproven approaches from the log.

## Inputs / outputs

| Path | Role |
|---|---|
| `ki_processing_failures.log` | input — one entry per failure; cleared at the end |
| the tracker | output — one issue per extracted action, filed with `tools/issues/create.sh --json`; an existing finding is refused by fingerprint (`exit 4`, naming the issue) rather than duplicated |
| `guidelines/Build.md`, `guidelines/Design.md`, `guidelines/MapRendering.md`, `guidelines/UI.md`, `AGENTS.md`, `.pi/skills/*` | the artifacts the extracted actions *name* as their home (this skill records the target, it does not edit them unless the user asks) |

## Procedure

### 1. Read the whole log, uncompressed

`ctx_read` compresses windows and can drop lines — for this file read raw ranges and do not
summarise from a compressed excerpt:

```bash
wc -l ki_processing_failures.log
sed -n '1,180p' ki_processing_failures.log      # then 180-300, 300-470, 470-667, …
```

Every entry must be seen at least once; a missed entry is a lost guardrail.

### 2. Classify each entry

Assign one action per entry, and record it as `action → target artifact`:

| Class | Where the action belongs |
|---|---|
| Shell / harness / tooling limits (blocked interpreters, output caps, `pkill -f`, detached runs) | `guidelines/Build.md` |
| Editing / patch tooling (atomic multi-edit, anchors copied not retyped, re-read after failure) | `guidelines/Design.md` or a skill note |
| Kotlin / Compose / car-app API traps | `guidelines/Design.md`, `guidelines/UI.md` |
| Test + evidence discipline (no sleep-paced tests, one mutation per revert check, XML counts) | `guidelines/Build.md` |
| On-device / emulator preconditions (ABI filtering, no injectable bearing, DNS, IME) | `guidelines/Build.md` |
| Native / libosmscout (both `#ifdef` configurations, ctest, `.so` names, submodule ownership) | `guidelines/Build.md`, `AGENTS.md` |
| Design / scope (verify premise, log both sides of a seam, decide pacing first) | `guidelines/Design.md`, `guidelines/MapRendering.md` |
| Cross-session / repo workflow (single writer per tree, `git ls-remote` before push) | `AGENTS.md` |
| Enforceable automatically | CI step, `build.gradle.kts` (`maxHeapSize`), gate test |

Rules:

- **An action, not a story.** "Never install an ABI-filtered APK — verify with `unzip -l … | grep <abi>`",
  not "once we installed the wrong APK and it crashed".
- **Deduplicate against what already exists.** The dedup is the tool's, not a habit: `create.sh` compares
  the finding's fingerprint against the live tracker and refuses with `exit 4`, naming the issue that
  already carries it. An action the tracker already holds gets a *cross-reference in the report*, never a
  second issue; `tools/issues/list.sh --class bug --category build-and-harness` (and the neighbouring
  filters) is how you look before you file.
- **Verify every claim about the current tree** before writing it down (see step 3), so the guardrail
  states the tree as it is, not as the log remembered it.

### 3. Verify before you assert or delete

Two verifications, both required:

- **"already implemented"** — never delete an entry (or write a guardrail that says "is now handled")
  on the strength of the entry's own `✅`/`Fixed:` sentence. Check the tree: `grep -n` the function,
  read the file, confirm the build file sets the property. Cite `file:line` in the report.
- **Ids and citations** — an id is identity and is never renumbered; a guardrail that quotes one must
  quote an id that resolves:

  ```bash
  tools/issues/list.sh --id 29                     # does this migrated entry exist?
  bash tools/check-todo-references.sh              # does every live citation still resolve?
  ```

  A guardrail filed *now* carries no `§N` — its identity is its issue number, and its labels are its
  facts. Report any citation the check flags rather than editing it yourself: a citation is provenance
  for somebody else's rule.

### 4. File the extracted guardrails as issues

- One issue per action, through the tool rather than by hand, so the dedup and the label vocabulary are
  the tool's job and not yours:

  ```bash
  jq -n '{sourceId: "", title: "…", class: "improvement", category: "build-and-harness",
          claim: "…", anchor: "guidelines/Build.md:§…",
          labels: ["class/improvement", "category/build-and-harness", "status/open", "source/agent"],
          body: "…"}' | bash tools/issues/create.sh --json
  ```

- `class` and `category` come from step 2's classification, which is the same mapping the migration
  used: the action's kind is `class/improvement` (or `class/bug` when the log records a defect the tree
  still has), and its home is the category that owns the artifact it names — `build-and-harness`,
  `specs-and-process`, `ui`, `map-rendering`, `native-jni`.
- `source/agent` is the provenance: the action came from the log, not from a person.
- One action per issue, imperative, with the concrete command/API/value where one exists; the body names
  the artifact that must carry the rule and the log entry it came from.
- **Never file `agent/eligible`.** It is the reserved authorisation label: filing an issue records a
  finding, and only a human authorises the loop to act on it.
- An action that is only a cross-reference to something the tracker already holds is reported, not filed.

### 5. Clear the log

Rewrite `ki_processing_failures.log` with the header plus a processing note — the entries themselves
must not survive in both places:

```markdown
# ki_processing_failurs.log

Failed solution approaches and problems, with timestamps, to avoid repeating them.

<date>: every entry up to this date was processed — the actions derived from them now live as
issues in the tracker (`bash tools/issues/list.sh --status open`) and the entries were removed from this
file. Add new entries below; keep the format
`<timestamp> — <change>: <problem> → <cause> → <fix> → Lesson:` so the next processing pass can
extract an action from each one.
```

### 6. Report

- entry count processed, issues created (with their numbers), categories touched
- items verified as *already implemented* (with `file:line`) that were closed and removed from the
  backlog, and why
- items kept because still open, with the check that says so
- cross-references used instead of new issues, naming the issue that already carries each one
- a `§N` the check flags as unresolvable (report; the user decides)
- the dedup refusals (`exit 4`) the run hit, with the issue each one named
- which `[open]` items are pure text additions to `guidelines/*` or `AGENTS.md` and can be written next

## Environment constraints (do not re-learn these)

- `python3`, `perl` and `tesseract` are blocked by the lean-ctx shell allowlist. Use `jq`, `grep`,
  `sed -i -E`, `awk`, and tool-native `--json` output.
- `ctx_read` compression can silently drop lines of a long log — read raw ranges for the log and for
  any file whose *complete* sentence matters.
- Multi-edit is atomic: one ambiguous `oldText` rejects the whole call and nothing is applied.
  Copy anchors from a read, keep insertion blocks whole, and re-read the changed regions afterwards.
- The tracker is the record of the backlog, not a spec: an imported item may carry text the tree has
  moved past, and its `§N` names the entry it came from. Repairing a label or a status, and closing an
  item with evidence, are in scope; rewriting an item's prose or dropping open work is not.
