# Proposal

## Why

`AGENTS.md` is the one document every session is given whether it asks or not. `slim-agents-md` (archived
2026-10-07) cut it from 42 745 B to **28 374 B** (~11.9k → ~7.9k tokens) by relocating eight blocks to the
documents that already owned them. What is left still contains material that no session needs *before it
reads anything else*:

```
block (AGENTS.md, measured 2026-10-07)          bytes   who owns it today
### Logging (3 subsections)                      3 466   nobody — the only concern with no document
  #### Native log integration                   1 391   `native_log_bridge` appears in AGENTS.md alone
  #### Kotlin (app) logging                     1 317   `DiagnosticsLog` is mentioned in four guidelines,
                                                        none of which states a logging rule
  #### Native code (libosmscout submodule)        758   the rule is enforced by a CI step, stated in AGENTS.md
### JNI stub for unit tests                      1 596   the rule is `Build.md` §6; the stub facts are nowhere else
### Native Integration                           2 326   the rules are `Design.md` §5; the file list is here
### Stylesheets                                  1 661   the contracts are `MapRendering.md` §16 and §16a
### Android Auto / AAOS                         3 762   ~2.5k of it is manifest/distribution fact, and the
                                                        specs `auto` and `android-automotive-os` exist
harness paragraphs inside the iteration loop    2 000   `Build.md` §1 owns the screenshot prerequisite; the
                                                        libtui pinning note has no home
## Constraints                                    617   `Design.md` §2 states all three verbatim
```

Two of these have no owner at all, which is also why they accumulated here: logging is the only recurring
concern in this repository that never got a document, and the harness notes have nowhere to live. The rest
are rules and facts that already have owners and were simply never moved.

The cost is per session, not per read: 28 374 B are paid by every session, including one that only asks
about a route calculation. Relocating the blocks above brings the file to roughly 17 KB (~4.7k tokens) —
another 41 % off — while every rule keeps one normative home.

This is a second pass, not a repeat of the first: the first moved what *had* an owner, and this one creates
the two owners that are missing and finishes the job.

## What Changes

- **`guidelines/Logging.md` is created** as the document that owns observability: `osmscout::log` in
  libosmscout and the Android-free rule outside its frozen `Android/` directory (CI-gated), the app-owned
  NDK bridge (`native_log_bridge.cpp`, `NativeLogBridge`, the `NaviVeylin` tag, the level mapping), the
  condensed per-file stylesheet-type report line, the Kotlin side (per-class `TAG`, `DiagnosticsLog`'s
  buffered writer with asynchronous readers and its retention), the prohibition on coordinates in a log or
  diagnostics line (spec `auto-diagnostics`) and the build gate that enforces it, and the disclosure
  statement the exported text and both viewers lead with (`Regulatory.md` §9). The three subsections move
  out of `AGENTS.md`; what stays there is the two facts a session needs at start — the one tag and the one
  bridge.
- **The remaining blocks move to owners that exist**, each as a relocation rather than a rewrite:
  - the JNI-stub facts (the two test source sets, the committed host `.so`, the `libjavad` fallback name,
    `java.library.path`, the fakes) → `guidelines/Build.md` §6, which already states the rule;
  - the Native Integration rules ("patch in one, never both", the two database-open entries, the submodule
    push discipline) → `guidelines/Design.md` §5, which owns the native boundary;
  - the stylesheet rules (single source of truth, the two sync tasks, `AssetCopier`, the icon leaf) →
    `guidelines/MapRendering.md` §16 and §16a, with the packaging facts joining `Build.md` §12;
  - the manifest and distribution detail → the specs `auto` and `android-automotive-os` plus `UI.md` §3,
    leaving the two facts a session needs (two flavors, one applicationId; the AAOS overlay) in place;
  - the two harness paragraphs → `guidelines/Build.md` §1, which is the harness section;
  - `## Constraints` → a pointer at `guidelines/Design.md` §2, which already states all three constraints.
- **`AGENTS.md`'s routing table gains the logging row**, and its rows keep heading text only where a
  number is ambiguous (`MapRendering.md` §14) or absent — a unique number resolves without it, so the table
  loses roughly 1.8 KB while the check keeps its teeth.
- **`tools/check-doc-routes.sh` derives its document list from `guidelines/*.md`** instead of the hardcoded
  `files="Build Design UI MapRendering Regulatory"`, so the sixth guideline cannot escape the check. Its
  self-test gains the case: a fixture with an extra guideline whose route does not resolve must fail.
- **Not changing:** no rule is deleted; no guideline body is shortened; no measurement moves.

**Classification:** additive in behaviour, a partial rewrite of `AGENTS.md`. **Rollback:** `git checkout`
the two documents and delete the new file and the check's derivation; nothing at runtime observes them.

## Capabilities

### New Capabilities

None. Logging is a concern with an owner document, not a new system behaviour: its rules are already
enforced (a CI gate, a build gate, a disclosure statement), and `guidelines/Logging.md` is where they are
stated. A spec for logging would be a third copy.

### Modified Capabilities

- `documentation-ownership`: three added requirements.
  1. **A concern has one owning document** — a rule is added to the document that owns its concern; a
     recurring concern without a document gets one, and the routing table names it.
  2. **The entry document is bounded by what a session needs before it reads anything else** — facts and
     routes stay; anything a reader only needs while working inside one area moves to that area's
     document, however useful the fact is.
  3. **Every guideline document is covered by the route check** — the documents the check resolves against
     are derived from the repository, so a document added to `guidelines/` cannot be silently unchecked.

### Capabilities considered and not changed

- `auto-diagnostics` — the coordinate prohibition is referenced, not restated; its home stays the spec.
- `build-test-gate`, `unit-test-suite-runtime` — the JNI-stub move adds no rule to `Build.md` §6; it moves
  facts there.

## Impact

| File | Change |
|---|---|
| `guidelines/Logging.md` | new: the observability rules and facts, moved from `AGENTS.md` |
| `AGENTS.md` | logging subsections, JNI-stub facts, Native Integration rules, Stylesheets rules, AA manifest detail, the two harness paragraphs and `## Constraints` relocated; the logging row added to the routing table; table rows trimmed to heading text where it disambiguates |
| `guidelines/Build.md` | §6 gains the stub facts; §12 gains the stylesheet packaging facts; §1 gains the harness notes |
| `guidelines/Design.md` | §5 gains the Native Integration rules; §2 keeps the constraints and is pointed at |
| `guidelines/MapRendering.md` | §16/§16a gain the stylesheet rules |
| `openspec/specs/auto/spec.md`, `openspec/specs/android-automotive-os/spec.md` | the manifest/distribution detail |
| `openspec/config.yaml` | `context`'s document inventory names the sixth guideline |
| `tools/check-doc-routes.sh` + `-selftest.sh` | the document list is derived from `guidelines/*.md`; a self-test case for an unrouted extra guideline |

**Not affected.** `guidelines/UI.md`, `guidelines/Regulatory.md` (pointed at, not edited), `todo.md`,
and every source, test, Gradle, manifest, native and JNI file. No CI change: the existing
"Check documentation routes" step runs the derived check unchanged.

**Guidelines referenced.** All six are named by the routing table; this change edits four of them
(`Build.md`, `Design.md`, `MapRendering.md` receive relocations) and creates the sixth.

**Scope.** Repository-wide developer documentation plus one shell script. Nothing phone-, tablet- or
car-specific.

**Sequencing.** This change edits `AGENTS.md` again, and a peer session is still active in the tree
(`guidelines/UI.md` and `AGENTS.md` carry its uncommitted lines as of 2026-10-07). Apply it alone, with the
tree otherwise quiet; it does not interact with the archived three beyond building on the state they left.

**Verification (planned).** Measured, not asserted: the relocation count closes again (blocks removed =
statements found in owners, with a `grep` hit quoted per block); `wc -c`/token counts for `AGENTS.md` and
every guideline before and after; the route check exits 0 with the derived document list and its self-test
covers the extra-guideline case; and one revert-check on the new invariant — remove the derived list so the
check falls back to nothing, and the named case must fail.
