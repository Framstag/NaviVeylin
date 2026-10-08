# Tasks

Delta under implementation: `specs/documentation-ownership/spec.md` — 3 added requirements, 9 scenarios,
against the capability `scope-guideline-reads` introduced. Design: `design.md` (D1–D7), which D4 requires
this change to keep: relocation rather than rewriting, and a do-not-move list of one-line rules.

This change edits documents only (`AGENTS.md`, `guidelines/Build.md`, `guidelines/Design.md`). It compiles
no code and changes no test, so no Gradle gate is owed; §4.4 proves that by file kind, and §4.5 states the
two evidences it deliberately does not produce.

**Dependency note.** The plan is written against the state left by `dedupe-test-rule-ownership` (2026-10-07,
archived) and `scope-guideline-reads` (archived the same day): the test-rule owner statements, the routing
table in `AGENTS.md`'s Documentation Map, and `tools/check-doc-routes.sh` all exist now. Line numbers below
are anchors, not truth — re-read each block before editing it.

## 1. Create the owners the relocated rules need

- [x] 1.1 `guidelines/Build.md` §2 "Common behavior": add the subsection "Agent iteration protocol" stating
      the four iteration rules normatively — look then measure before changing code; iterate with focused
      suites and one flavor while iterating, gating once; batch independent questions into one call; one
      builder per working tree, with the wrapper probe that makes the detection correct. State no
      measurement in it: point at §4 (the levers and the declared-cases pass), §6 (the gate's forcing rule),
      §10/§11 (the on-device measurement recipes) and the skills. **Spec**: "A rule has one normative home"
      / "A rule that spans documents names one home". **Verify**: `grep -n 'Agent iteration protocol'
      guidelines/Build.md`; the four rules are readable there without `AGENTS.md`.
- [x] 1.2 `guidelines/Build.md`: add §12 "Native build and vcpkg" and move the native-build facts out of
      `AGENTS.md` **verbatim** — the libosmscout CMake sources, the Cairo backend link, the ABI→triplet
      selection, the overlay triplet's API level, the iconv stub, the dependency list per triplet, the
      rebuild recipe, and the six vcpkg usage facts (classic mode/`DEPS`, the CI pin, the NuGet binary
      cache, the removed files-provider design, adding/removing a dependency, refreshing ports). **Spec**:
      "A relocated rule keeps its statement" / "The removal is verified by finding the statement".
      **Verify**: every moved paragraph is found in `Build.md` §12 (`grep` two distinctive phrases: "the
      dependency list is hardcoded in `setup-vcpkg.sh`", "arm-neon-android").
- [x] 1.3 `guidelines/Build.md`: update its own maintenance rule and §1 so the new §2 subsection and §12 are
      covered by the same "this document owns it" statement and the skills' pointer list. **Spec**: "A rule
      has one normative home". **Verify**: read the maintenance rule back; it still names the mirror sites
      and now also covers §12.
- [x] 1.4 `guidelines/Design.md` §2 "Stack & UI patterns": add the recipes `AGENTS.md`'s "Common Patterns"
      carries — adding a dependency, a new screen, a full-screen sheet (keeping the modal-band rule and
      its `guidelines/UI.md` §11 reference), a details sheet after a search, and a native function — so the
      how-to has an owner next to the pattern rules it belongs to. **Spec**: "A relocated rule keeps its
      statement". **Verify**: `grep -n 'Adding a new screen' guidelines/Design.md`; each recipe names the
      files it touches.

## 2. Reduce `AGENTS.md` to facts plus pointers

- [x] 2.1 Build the relocation inventory before editing: one line per block to be removed, naming a
      distinctive phrase to `grep` and the owner section that must contain it (the block list in
      `proposal.md` "What Changes", with §1.1-§1.4's owners added). **Spec**: "The removal is verified by
      finding the statement" / "The relocation count closes". **Verify**: the inventory exists in the
      change record, one line per block, each with its owner.
- [x] 2.2 The Android Auto section: keep the facts it uniquely owns (the `:auto` module and
      `NaviVeylinCarAppService`'s package, the manifest conventions, the two distribution flavors, the
      guard names, the diagnostics tags) and reduce its five rule bullets plus the diagnosis paragraph to
      one line each with a pointer — `guidelines/Design.md` §8, `guidelines/UI.md` §3a/§3b/§3c, the specs
      `car-host-fault-isolation`, `auto/screen-observation`, and `guidelines/Build.md` §10 for the recipe.
      **Spec**: "The always-read document states facts and references rules" / "A rule mention names its
      owning section". **Verify**: each of the four hard invariants is one sentence carrying its section
      reference; `grep -c` for the removed sentences returns 0 in `AGENTS.md` and ≥1 in the owner.
- [x] 2.3 The iteration loop: reduce to the four rules in one sentence each, each naming
      `guidelines/Build.md` §2's protocol, and carry no measurement. **Spec**: "A rule summary carries no
      measurement". **Verify**: `grep -nE '[0-9]+ (classes|tests)|[0-9]+m[0-9]+s|~[0-9]+ min' AGENTS.md`
      returns nothing; each rule's sentence names §2.
- [x] 2.4 Release versioning, Build & Test commands, licence compliance: replace each with the non-obvious
      fact it owns (the version state file is machine-local and gitignored, `release` fails fast without
      it; the two AAB outputs) plus a pointer to `guidelines/Build.md` §5, §3 and §9 respectively.
      **Spec**: "A fact is stated where it is used" / "A duplicate is removed rather than synchronised".
      **Verify**: the three pointers resolve; no command block and no licence detail remains in
      `AGENTS.md`.
- [x] 2.5 Common Patterns and Native Build Details: delete both blocks — the recipes moved in §1.4, the
      native facts in §1.2 — leaving one line each that says where they went. **Spec**: "A relocated rule
      keeps its statement". **Verify**: the inventory lines for both blocks find their statements in the
      new owners.
- [x] 2.6 Check that nothing on `design.md` D4's do-not-move list lost its one-liner: the four car
      invariants, measure-before-changing-code, never log coordinates, the two distribution flavors, the
      two database-open entries, and one builder per working tree. **Spec**: "A fact is stated where it is
      used". **Verify**: read back; each item is present exactly once in `AGENTS.md`.

## 3. Route the new sections

- [x] 3.1 Extend the routing table in `AGENTS.md`'s Documentation Map so the sections this change creates
      are reachable: `guidelines/Build.md` §2 "Agent iteration protocol" (how a session runs the
      build/measure loop) and §12 "Native build and vcpkg" (native build, ABI, CMake, dependency changes),
      plus the recipes in `guidelines/Design.md` §2. **Spec**: "A change is routed to the sections that own
      its conventions". **Verify**: `bash tools/check-doc-routes.sh` resolves every reference including the
      new ones, and following the native-build row reaches §12.
- [x] 3.2 Re-run the route check's self-test to confirm the resolver was not weakened. **Spec**: "A route
      resolves to a section that exists" / "The check is self-tested". **Verify**:
      `bash tools/check-doc-routes-selftest.sh` → 7 passed, 0 failed.

## 4. Verify the relocation, and falsify its invariant

- [x] 4.0 Scenario → check table, every one of the delta's 9 scenarios named with the check that exercises
      it and its result. **Spec**: all three requirements. **Verify**: the table is in the change record
      with a result per row.
- [x] 4.1 Walk the §2.1 inventory and count: blocks removed versus statements found in their owners.
      **Spec**: "The relocation count closes". **Verify**: both numbers quoted together, equal; any block
      whose statement is missing is either restored or filed per §4.2.
- [x] 4.2 For any block whose statement exists nowhere: do **not** delete it; record the homeless rule in
      `TODO.md` (category `specs-and-process`) and leave the block in place. **Spec**: "A homeless rule is
      filed, not dropped". **Verify**: either the list of such blocks is empty, or each has a `TODO.md`
      entry naming the block and the document that should own it.
- [x] 4.3 Measure the outcome: `wc -c AGENTS.md` and `guidelines/Build.md` before and after, with token
      estimates (`bytes ÷ 3.6`), and state the per-session saving against the per-read cost the new
      `Build.md` sections add. **Spec**: "The always-read document states facts and references rules".
      **Verify**: both numbers quoted; no saving is claimed that the numbers do not support.
- [x] 4.4 Prove mechanically that no Gradle gate is owed: `git status --porcelain` plus `git diff
      --name-only` list only `*.md` files (and this change's directory) — no source, test, Gradle,
      manifest, resource, workflow, native or JNI file. **Spec**: all three requirements (the change alters
      no behaviour). **Verify**: paste the file list; any other kind of path means a compile/test gate
      becomes owed.
- [x] 4.5 State the two evidences this change does not owe: (a) no on-device or emulator evidence — no UI,
      rendering, car-surface, template or lifecycle behaviour changes; (b) no new unit test — the artifact
      under test would be documentation, and the delta's scenarios are `grep`-checkable. **Spec**: all three
      requirements. **Verify**: both sentences are in the change record.
- [x] 4.6 **Revert-check** for the relocation invariant: mutate by deleting the "Agent iteration protocol"
      subsection from `guidelines/Build.md` §2; the named check for that inventory line — `grep -n 'Agent
      iteration protocol' guidelines/Build.md` — MUST find nothing while `AGENTS.md` still points at it,
      so the removal is detected; restore the subsection; re-run the same `grep` and the route check
      (`bash tools/check-doc-routes.sh`) and both MUST be green again. One mutation only. **Spec**: "The
      removal is verified by finding the statement". **Verify**: both grep outputs and the check's exit
      code quoted, the failure first. This check reads text files, so "forced green" is the re-run — no
      build cache is involved.

## 5. Close out

- [x] 5.1 Run `openspec validate slim-agents-md --strict` and `openspec doctor`; quote both outputs.
      **Verify**: both exit 0.
- [x] 5.2 Confirm no CI file was needed: this change adds no check (the route check of
      `scope-guideline-reads` already covers the table). **Verify**: `git status --porcelain .github/` is
      empty.
- [x] 5.3 Write the change record for this change (`apply-evidence.md`) with the inventory, the relocation
      count, the size measurement, the scenario table and the revert-check outputs. **Verify**: the file
      exists and contains each of those five.
