# Tasks

Delta under implementation: `specs/documentation-ownership/spec.md` — 3 added requirements, 12 scenarios,
on top of the capability's existing 7 requirements. Design: `design.md` (D1–D6).

Documents only, plus one shell script. No Gradle gate is owed; §5.1 proves that by file kind and §5.2 states
the two evidences it deliberately does not produce.

**Dependency note.** Written against the state left by `dedupe-test-rule-ownership`, `scope-guideline-reads`
and `slim-agents-md` (all archived 2026-10-07). Anchors are phrases, not line numbers — every prior change
moved lines below the routing table.

## 1. Inventory and the missing owner

- [x] 1.1 Build the relocation inventory before editing: one line per block to move — the seven in
      `proposal.md` "What Changes" — naming a distinctive phrase to `grep` and the document that must contain
      it. **Spec**: "A relocated rule keeps its statement" / "The removal is verified by finding the
      statement". **Verify**: the inventory is in the change record, one line per block with its owner.
- [x] 1.2 Create `guidelines/Logging.md` and move the three logging subsections into it, verbatim: logging
      through `osmscout::log` and the Android-free rule outside libosmscout's frozen `Android/` directory
      (naming the CI step that enforces it), the app-owned bridge (`native_log_bridge.cpp`,
      `NativeLogBridge`, the `NaviVeylin` tag, the level mapping), the condensed per-file stylesheet-type
      report line, the Kotlin side (per-class `TAG`, `DiagnosticsLog`'s buffered writer with asynchronous
      readers and its retention window), the coordinate prohibition with its build gate, and the
      disclosure statement. Give it a title, a purpose line and a `## Requirements`-style structure
      consistent with the other guidelines. **Spec**: "A concern has one owning document" / "A recurring
      concern without a document gets one". **Verify**: `grep -c` for two distinctive phrases (e.g.
      "Unknown types in", "DiagnosticsLog") is ≥1 in the new file and 0 in `AGENTS.md` for the moved text.
- [x] 1.3 `AGENTS.md` keeps the two facts a session needs at start about logging — the one tag and the one
      bridge — and a pointer to the new document. **Spec**: "The entry document is bounded…" / "A fact needed
      before any other read stays". **Verify**: read back; the two facts are present, the rules are not.

## 2. The blocks with existing owners

- [x] 2.1 `guidelines/Build.md` §6 gains the JNI-stub facts: the two test source sets, the committed host
      `.so` named for both the plain and the `_javad` fallback, why it is committed, `java.library.path`, and
      the fakes that override the native methods. **Spec**: "A rule has one normative home" (the rule is
      already there) / "A fact needed only inside one area moves". **Verify**: `grep -n 'libosmscout_client_javad' guidelines/Build.md` hits §6, and the block is gone from `AGENTS.md`.
- [x] 2.2 `guidelines/Design.md` §5 gains the Native Integration rules: patch the JNI bridge in one place
      and never both, the two database-open entries and why they differ, the submodule-SHA bump and the
      clean-submodule rule, and the one-session-owns-the-push rule with the `git ls-remote` check. Keep the
      5-override file list with them as the fact it is. **Spec**: "A fact needed only inside one area moves".
      **Verify**: `grep -n 'openDatabases' guidelines/Design.md` hits §5; `AGENTS.md` no longer states the
      asymmetry.
- [x] 2.3 `guidelines/MapRendering.md` §16/§16a gain the stylesheet rules (the submodule as the single
      source of truth, the two sync tasks with their `preBuild` wiring, `AssetCopier`'s per-file refresh, the
      icon leaf and the trailing separator), and `guidelines/Build.md` §12 gains the packaging facts.
      **Spec**: "A fact needed only inside one area moves". **Verify**: `grep -n 'syncSubmoduleStylesheets\|AssetCopier' guidelines/MapRendering.md guidelines/Build.md`;
      `AGENTS.md` keeps one line naming the stylesheet source and the route.
- [x] 2.4 The AA manifest and distribution detail moves to the specs `auto` and
      `android-automotive-os` (and the platform knowledge stays reachable via `UI.md` §3); `AGENTS.md` keeps
      two facts: two flavors under one applicationId, and the AAOS overlay that re-declares the automotive
      feature. **Spec**: "A fact needed only inside one area moves" / "A new document is reachable" (the
      specs are reachable through the routing table's car row). **Verify**: the moved detail is found in the
      two specs by `grep`; the two facts remain in `AGENTS.md`.
- [x] 2.5 `guidelines/Build.md` §1 gains the two harness paragraphs, collapsed: `view_image` as the
      screenshot-reading prerequisite (joining the subsection already there) and the libtui pinning note
      with its drift check. **Spec**: "A fact needed only inside one area moves". **Verify**:
      `grep -n 'libtui' guidelines/Build.md` hits; `AGENTS.md` keeps no more than the one-line route.
- [x] 2.6 `## Constraints` reduces to a pointer at `guidelines/Design.md` §2, which already states the three
      constraints; keep the two distribution facts (the two AABs and their tracks). **Spec**: "A duplicate is
      removed rather than synchronised". **Verify**: `grep -c 'Google Play Services' AGENTS.md` is 0 and the
      pointer names `Design.md` §2.

## 3. Routes and the check

- [x] 3.1 Add the logging row to `AGENTS.md`'s routing table (the new document plus the recipe sections a
      logging change also needs), and trim heading text from rows whose section number is unique and
      unsuffixed, keeping it where a number is duplicated or absent and in every row that names several
      documents. **Spec**: "A new document is reachable" / "A change is routed to the sections that own its
      conventions". **Verify**: `bash tools/check-doc-routes.sh` resolves every remaining reference; the
      `MapRendering.md` §14 row still carries its text.
- [x] 3.2 `tools/check-doc-routes.sh`: derive the document list from `guidelines/*.md` instead of the
      hardcoded list, and report a document the routing table does not name. **Spec**: "Every guideline
      document is covered by the route check" / "The document set is derived", "An unrouted document is
      reported". **Verify**: run it on the tree — exit 0 for the routed set; then add a throwaway
      `guidelines/Tmp.md` and confirm it is reported, then remove it.
- [x] 3.3 `tools/check-doc-routes-selftest.sh`: add the two extra-guideline cases (an unresolvable route in
      an extra guideline fails; an extra guideline named by the table passes) and keep every existing case.
      **Spec**: "The derivation is self-tested". **Verify**: the self-test prints its cases and exits 0.
- [x] 3.4 `openspec/config.yaml`: the `context` inventory names the sixth guideline. **Spec**: "A new
      document is reachable". **Verify**: `openspec instructions proposal --change <any> --json | jq -r .context` lists it; `openspec doctor` exits 0.

## 4. Verify the relocation and the entry document

- [x] 4.0 Scenario → check table for the delta's 12 scenarios, each with the check that exercises it and
      its result, no scenario unnamed. **Spec**: all three requirements. **Verify**: the table is in the
      change record with a result per row.
- [x] 4.1 Walk §1.1's inventory: blocks removed versus statements found in their owners, reported as one
      pair of numbers. **Spec**: "The relocation count closes". **Verify**: both numbers quoted, equal; a
      block with no owner statement is restored or filed.
- [x] 4.2 If any block's statement exists nowhere else: do not delete it, record it in `TODO.md`
      (category `specs-and-process`) and leave it in place. **Spec**: "A homeless rule is filed, not
      dropped". **Verify**: either the list is empty, or each entry names the block and its intended owner.
- [x] 4.3 Check the entry document against the boundary rule: read `AGENTS.md` end to end and confirm every
      remaining block passes the "needed before the first read" test, with the do-not-move set of design D2
      intact. **Spec**: "The entry document is bounded…" / "Relocating keeps the entry document readable",
      "A fact needed before any other read stays". **Verify**: the read-back lists each section and its
      verdict; any block that fails the test but stayed is named with its reason.
- [x] 4.4 Measure: `wc -c` and tokens (`÷3.6`) for `AGENTS.md` and each guideline before and after, with the
      per-session figure for the entry document and the per-section-read note for the guidelines.
      **Spec**: "The entry document is bounded…". **Verify**: both numbers quoted; no saving claimed that the
      numbers do not support.
- [x] 4.5 Prove no Gradle gate is owed: `git status --porcelain` and `git diff --name-only` list only
      `*.md`, `*.yaml` and `tools/*.sh`. **Spec**: all three requirements. **Verify**: paste the list; any
      source, test, Gradle, manifest, native or workflow path means a gate becomes owed.
- [x] 4.6 State the two evidences this change does not owe: (a) no on-device or emulator evidence — no UI,
      rendering or car-surface behaviour changes; (b) no new unit test — the artifact under test is
      documentation, and the script's verification is its self-test. **Spec**: all three requirements.
      **Verify**: both sentences are in the change record.
- [x] 4.7 **Revert-check** for the new check invariant: mutate by replacing the derived document list in
      `tools/check-doc-routes.sh` with the old hardcoded five-file list, and add a throwaway
      `guidelines/Tmp.md` naming a section that does not resolve; the named check — running the check and
      grepping its output for `Tmp` — MUST report nothing (the document is not covered); then run it against
      the real tree: the unrouted document must be reported only when the derivation is in place. Restore
      the derived list, remove the throwaway file, and both the route check and its self-test MUST be green
      again. One mutation only. **Spec**: "Every guideline document is covered by the route check" / "The
      document set is derived", "A new guideline is covered without a code change". **Verify**: the outputs
      of both runs quoted, the uncovered case first, then the green.

## 5. Close out

- [x] 5.1 Run `openspec validate slim-agents-md-2 --strict` and `openspec doctor`; quote both outputs.
      **Verify**: both exit 0.
- [x] 5.2 Confirm no CI change was needed and the existing step still passes: `bash tools/check-doc-routes.sh`
      from a clean shell, and `git status --porcelain .github/`. **Verify**: the check exits 0 and
      `.github/` is unchanged.
- [x] 5.3 Write the change record (`apply-evidence.md`) with the inventory, the relocation count, the size
      measurement, the scenario table, the entry-document read-back and the revert-check outputs.
      **Verify**: each of those six is present.
- [x] 5.4 Hand the result to the follow-up judgement: `TODO.md` stays untouched, and design Open Question 1
      (whether a harness section deserves its own document) is where a later change picks up.
      **Verify**: `git status --porcelain TODO.md` shows no edit from this change.
