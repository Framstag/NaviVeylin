# Tasks

Delta under implementation: `specs/documentation-ownership/spec.md` — one new capability, 4 requirements,
13 scenarios. The scenario → check mapping is enumerated in §4.0; every scenario is named there, and the
three that are inspection rather than machine-checked say so.

This change edits documents, one shell script pair, one CI step and `TODO.md`. It compiles no code and
changes no test, so no Gradle gate is owed; §5.1 proves that mechanically rather than asserting it.

## 1. The routing table (`AGENTS.md`)

- [x] 1.1 Replace the Documentation Map (lines 9-11) with the routing table: one row per topic a change
      routinely touches, each row naming the document, the section number **and** the heading text.
      Open the table with the recovery line for sections it does not route (`grep -n '^## ' <file>` gives
      the complete section list). **Spec**: "A change is routed to the sections that own its conventions"
      / "A phone UI change is routed without reading a document", "Sections outside the map stay
      reachable". **Verify**: read the row for a phone UI change and follow it to the named sections
      without opening a whole guideline; the recovery line is present.
- [x] 1.2 Route every topic that more than one document governs to **all** its owners in one row — car
      surface and host rules (`guidelines/Design.md` §8 "Android Auto & cross-variant",
      `guidelines/MapRendering.md` §14 "Android Auto renderer", `guidelines/Build.md` §10),
      parity/deviation (`guidelines/UI.md`), diagnostics gates (`AGENTS.md` logging section +
      `guidelines/Build.md` §10). **Spec**: "A topic is owned in more than one document". **Verify**:
      inspection — pick the car rows and confirm each names every document that owns that topic; record
      the row text. (Not machine-checked: the route check enforces resolution, not coverage — design D2.)
- [x] 1.3 Give every route row whose section number is ambiguous or absent the heading text as well —
      in particular the two `## 14.` sections of `guidelines/MapRendering.md` (line 553 rotation-gesture
      handoff, line 630 Android Auto renderer), `## Appendix A`/`## Appendix B` in `guidelines/Design.md`,
      `## Keeping this document honest` in `guidelines/UI.md`, `## Known Pitfalls` and
      `## Parameter Overview` in `guidelines/MapRendering.md`. **Spec**: "A heading number is ambiguous or
      absent". **Verify**: every such row carries text that appears verbatim in that document's heading
      (`grep -n '<heading text>' <file>`); the route check resolves each (3.1).

## 2. The change-artifact guidance reads by section (`openspec/config.yaml`)

- [x] 2.1 `context` (line 17): keep the five-document inventory, state the cost (each guideline is 15-27k
      tokens) and point at the routing table in `AGENTS.md`'s Documentation Map; drop the implication that
      all four named documents are read before design/apply. Keep the entry a quoted YAML string.
      **Spec**: "The change-artifact guidance reads by section" / "The served context instruction is
      section-scoped". **Verify**: `openspec instructions proposal --change <any> --json | jq -r .context`
      shows the scoped instruction and still lists the five documents.
- [x] 2.2 `rules.apply` (line 83): scope "verify all new/modified code against every document in
      guidelines/" to the sections that own the conventions the change touches (and keep the obligation to
      verify against them). **Spec**: "The apply instruction is section-scoped". **Verify**:
      `openspec instructions apply --change <any> --json | jq -r '.operationGuidance[]'` contains the
      scoped bullet.
- [x] 2.3 `rules.archive` (line 111): same scoping for "verify design decisions follow the guidelines/
      documents". **Spec**: "The guidance survives the edit". **Verify**: `openspec doctor` exits 0 and
      both guidance arrays are non-empty — the quoting rule that silently drops an unquoted entry
      (`AGENTS.md` OpenSpec Workflow; `.github/workflows/build.yml:115`).

## 3. The route check (`tools/`, CI)

- [x] 3.1 Add `tools/check-doc-routes.sh`: extract `^## ` headings per guideline, ignoring lines inside
      fenced code blocks, and resolve every route row in `AGENTS.md`'s routing table — a row with heading
      text must match a heading carrying that number; a bare ambiguous number is reported as ambiguous.
      Exit 0 only when every reference resolves; on failure name the document and the offending reference.
      **Spec**: "A route resolves to a section that exists" / "The references are checked without a build
      or a device", "A dangling reference fails the check". **Verify**: run it on the clean tree — exit 0,
      and confirm no Gradle, device or network call is made (the script uses shell text tools only).
- [x] 3.2 Add `tools/check-doc-routes-selftest.sh` with its own fixture (one resolving row, one dangling
      row, one document containing two identically numbered sections). **Spec**: "The check is
      self-tested" and "A heading number is ambiguous or absent". **Verify**: the self-test accepts the
      resolving row, rejects the dangling row, and reports the duplicated number as ambiguous; it reads no
      project document; exit 0.
- [x] 3.3 Add the CI step "Check documentation routes" to `.github/workflows/build.yml`, modelled on
      "Check OpenSpec artifact hygiene" (line 100) — its own step so the failure message names the
      documentation route rather than OpenSpec artifacts (design D3). **Spec**: "A route resolves to a
      section that exists" / "The references are checked without a build or a device". **Verify**: run the
      step's command verbatim under `bash -euo pipefail` locally; exit 0. Re-run it with a deliberately
      broken row and confirm the workflow's failure line carries the document and reference.

## 4. Verify the requirements and falsify the new invariant

- [x] 4.0 Scenario → check table, completed with the evidence of each row (no scenario unnamed):

      | requirement | scenario | check |
      |---|---|---|
      | routed to owners | phone UI change routed | 1.1 + 4.1's phone-UI change |
      | routed to owners | topic owned in more than one document | 1.2 (inspection) |
      | routed to owners | ambiguous/absent heading | 1.3 + 3.2 (fixture duplicate) |
      | routed to owners | sections outside the map reachable | 1.1 recovery line |
      | route resolves | checked without build/device | 3.1 |
      | route resolves | dangling reference fails | 3.1 + 4.2 |
      | route resolves | removing a row is detected | 4.2 |
      | route resolves | check is self-tested | 3.2 |
      | guidance by section | context instruction | 2.1 |
      | guidance by section | apply instruction | 2.2 |
      | guidance by section | guidance survives the edit | 2.3 |
      | cost measured | numbers for representative changes | 4.1 |
      | cost measured | unmeasured saving not claimed | 4.1 (no claim except its numbers) |

      **Spec**: all four requirements. **Verify**: every row has a result pasted into the change report.
- [x] 4.1 Measure routed-versus-full reading for three in-flight changes — one phone UI
      (`fix-phone-map-layer-stack`), one car (`fix-car-follow-reengage-render`), one rendering
      (`fix-client-dpi-surface-leak`): take the sections the router names for that change and the four
      documents the old instruction named, and quote both costs in tokens (`wc -c` ÷ 3.6). **Spec**:
      "The routing cost is measured" / "Numbers for representative changes", "An unmeasured saving is not
      claimed". **Verify**: the three pairs of numbers appear in the change report; no other saving claim
      is made.
- [x] 4.2 **Revert-check** for the route-resolution invariant: mutate by deleting one route row from
      `AGENTS.md`'s Documentation Map (or changing its section number to a non-existent one);
      `bash tools/check-doc-routes.sh` MUST exit non-zero naming that reference; restore the row;
      re-run the same command and it MUST exit 0. One mutation only. **Spec**: "A route resolves to a
      section that exists" / "Removing a route is detected". **Verify**: both exit codes quoted in the
      change report, failure first. This check reads text files only, so "forced green" is the re-run of
      the command — no build cache is involved.
- [x] 4.3 Confirm the routed read reaches a real convention: for one of the three changes, follow the
      route and quote the sentence in the owning section that governs that change. **Spec**: "A phone UI
      change is routed without reading a document". **Verify**: the quoted sentence and its
      `file:line`; if the route does not reach it, the row is wrong and 1.1-1.3 are re-done.

## 5. Close out

- [x] 5.1 Prove mechanically that no Gradle gate is owed: `git diff --name-only` plus
      `git status --porcelain` for the new files list only `*.md`, `*.yaml`, `*.yml`, `tools/*.sh` and the
      change directory — no source, test, build script, manifest, resource, Gradle or native file.
      **Spec**: all four requirements (the change alters no behaviour). **Verify**: paste the file list;
      if any other kind of path appears, a compile/test gate becomes owed.
- [x] 5.2 State the two things this change does not owe, so no reader infers them: no on-device or
      emulator evidence (no UI, rendering, car-surface, template or lifecycle behaviour changes), and no
      new unit test (the check is a shell script with its own device-free self-test, per 3.2).
      **Spec**: all four requirements. **Verify**: both sentences exist in the change report.
- [x] 5.3 Add the `TODO.md` entry for the duplicated `## 14.` numbering in `guidelines/MapRendering.md`
      (category `specs-and-process`) with the measured reference pressure — `§14` cited 11 times outside
      the document, 10 of them meaning the AA renderer at line 630 — and the reason it was not renumbered
      here (design D4). **Spec**: not derived from a scenario; recorded because `AGENTS.md` requires a
      detected but unowned problem to be filed. **Verify**: the entry carries id/category/class/status
      metadata and cites the counts.
- [x] 5.4 Run `openspec validate scope-guideline-reads --strict` and `openspec doctor`; quote both
      outputs. **Verify**: both exit 0.
- [x] 5.5 Do not start `slim-agents-md` on this tree: it edits `AGENTS.md` too. Leave the sibling change
      scaffolded and hand it the routing table this change produces. **Verify**: `git status --porcelain`
      shows no edit outside this change's declared files.
