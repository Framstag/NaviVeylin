---
name: process-failure-log
description: Processes NaviVeylin's `ki_processing_failures.log` — extracts every failed approach into an actionable guardrail (guideline / skill / AGENTS.md / CI / build-file item) appended to the guardrail section of `TODO.md`, then clears the processed entries from the log. Use when asked to "process the failure log", "turn the failure log into actions/guardrails", "clean up ki_processing_failures.log", or as a periodic hygiene pass after several changes shipped.
---

# Process the KI failure log

Turns `ki_processing_failures.log` (raw incident notes, gitignored) into durable guardrails:
the log holds *what went wrong*, `TODO.md` §40 holds *what prevents it*.

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
| `TODO.md` | output — guardrail section (currently `## 40. Guardrails extracted from …`), appended, never rewritten wholesale |
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
- **Deduplicate against what already exists.** If `TODO.md` already tracks the defect (§17 test-results
  masking, §33 `:auto` heap, §38 stylesheet crash, §15 Kover/Robolectric, …), add a *cross-reference*,
  not a second copy.
- **Verify every claim about the current tree** before writing it down (see step 3), so the guardrail
  states the tree as it is, not as the log remembered it.

### 3. Verify before you assert or delete

Two verifications, both required:

- **"already implemented"** — never delete an entry (or write a guardrail that says "is now handled")
  on the strength of the entry's own `✅`/`Fixed:` sentence. Check the tree: `grep -n` the function,
  read the file, confirm the build file sets the property. Cite `file:line` in the report.
- **Existing numbering / references** — before renumbering or deleting a `TODO.md` section:

  ```bash
  grep -n '^## ' TODO.md                        # inventory + duplicate numbers
  grep -rn 'TODO\.md.*§[0-9]*\|§[0-9]*.*TODO' openspec guidelines *.md | grep -v '^TODO.md'
  ```

  Keep the number that external artifacts reference (e.g. `aa-entry-zoom-animation` references
  `TODO.md` §29/§33); renumber only the later duplicate of a collision, and report any reference that
  a deletion leaves dangling.

### 4. Append to the guardrail section of `TODO.md`

- Add one dated section (new highest number) with a one-paragraph preamble: date, that the processed
  entries were removed, the meaning of the **[open]** marker, and that items cross-referencing an
  existing section are already tracked there.
- Keep stable group headings (A. Harness/shell/Gradle, B. Editing, C. Kotlin/Compose, D. Test/verification,
  E. On-device/emulator, F. Native/libosmscout, G. Design/scope).
- **Append-only numbering**: continue the existing item numbers, never renumber existing items —
  the section is referenced from sessions and change artifacts.
- Mark `**[open]**` when the named artifact does not carry the rule yet; drop the marker when the item
  is only a cross-reference to an existing section.
- One action per item, imperative, with the concrete command/API/value where one exists.

### 5. Clear the log

Rewrite `ki_processing_failures.log` with the header plus a processing note — the entries themselves
must not survive in both places:

```markdown
# ki_processing_failurs.log

Failed solution approaches and problems, with timestamps, to avoid repeating them.

<date>: every entry up to this date was processed — the actions derived from them now live in
`TODO.md` §<n> and the entries were removed from this file. Add new entries below; keep the format
`<timestamp> — <change>: <problem> → <cause> → <fix> → Lesson:` so the next processing pass can
extract an action from each one.
```

### 6. Report

- entry count processed, guardrail items added, groups touched
- items verified as *already implemented* (with `file:line`) that were deleted from `TODO.md`, and why
- items kept because still open, with the check that says so
- cross-references used instead of new items
- dangling references created by a deletion (report; the user decides)
- which `[open]` items are pure text additions to `guidelines/*` or `AGENTS.md` and can be written next

## Environment constraints (do not re-learn these)

- `python3`, `perl` and `tesseract` are blocked by the lean-ctx shell allowlist. Use `jq`, `grep`,
  `sed -i -E`, `awk`, and tool-native `--json` output.
- `ctx_read` compression can silently drop lines of a long log — read raw ranges for the log and for
  any file whose *complete* sentence matters.
- Multi-edit is atomic: one ambiguous `oldText` rejects the whole call and nothing is applied.
  Copy anchors from a read, keep insertion blocks whole, and re-read the changed regions afterwards.
- `TODO.md` is a working file, not a spec: it may carry duplicate section numbers and stale entries.
  Fixing numbering and deleting verified-resolved entries is in scope; rewriting prose or dropping
  open items is not.
