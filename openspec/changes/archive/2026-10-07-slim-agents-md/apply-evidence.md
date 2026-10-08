# Apply evidence — `slim-agents-md`

Session 2026-10-07, after the archives of `dedupe-test-rule-ownership` and `scope-guideline-reads`.
Delta: `specs/documentation-ownership/spec.md` — 3 added requirements, 9 scenarios. Design: `design.md`
(D1–D7); this change keeps D4's discipline: **relocation, not rewriting**, and a do-not-move list.

**Shared-tree note.** The peer session applying `fix-phone-map-layer-stack` was still active
(`git status` shows `guidelines/UI.md` modified by it, and its emulator screenshots are stamped
throughout this session). This change touched `AGENTS.md`, `guidelines/Build.md` and
`guidelines/Design.md` — it did not touch `guidelines/UI.md` or `.github/workflows/build.yml`, both of
which are dirty from the peer and from the previous change respectively.

## 2.1 — the relocation inventory (built before the edits)

| # | block removed from `AGENTS.md` | statement found in |
|---|---|---|
| 1 | AA rule bullets (`SessionCarSurfaceHost`, nothing-native, no-fault-escapes, guarded seam, `invalidate`, observations) + the diagnosis paragraph | `guidelines/Design.md:376` "the car-app surface lifecycle is strict and **session-owned**"; `guidelines/UI.md:257` "## 3a. Map renderer startup (never on the host thread)"; `guidelines/Build.md:1032` the `HOST` tags; specs `car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer`, `auto-smooth-follow` |
| 2 | iteration rules 1-4 + the "Skills for this" paragraph | `guidelines/Build.md:71` "### Agent iteration protocol" (new), with §4/§6/§7 for the levers |
| 3 | view_image prerequisite paragraph | `guidelines/Build.md:35` "### Screenshot reading (harness prerequisite)" |
| 4 | Release versioning (9 bullets) | `guidelines/Build.md:505` "Version state lives in `app/release-version.properties`" |
| 5 | Build & Test command block | `guidelines/Build.md:263` "| Build debug APK (all 3 ABIs, both flavors) |" |
| 6 | License compliance (6 bullets) | `guidelines/Build.md` §9 "License compliance" (`naviveylin:license:*` properties, the policy gate) |
| 7 | Common Patterns (5 recipes) | `guidelines/Design.md:84` "**Adding a full-screen sheet** (e.g. `FavoritesSheet`)" |
| 8 | Native Build Details (architecture, dependencies, rebuild, vcpkg usage) | `guidelines/Build.md:1412` "the dependency list is hardcoded in `setup-vcpkg.sh`" (moved verbatim into §12) |

New owners created by this change (nothing was deleted for want of one): `guidelines/Build.md` §2's
"Agent iteration protocol" subsection, `guidelines/Build.md` §12 "Native build and vcpkg", and
`guidelines/Design.md` §2's "Adding to the stack" subsection.

## 4.0 — scenario → check

| requirement | scenario | check | result |
|---|---|---|---|
| facts and references | a rule mention names its owning section | 2.2 / 2.4 | pass — the AA invariants, the release facts and the licence facts each name their section |
| facts and references | a rule summary carries no measurement | 2.3 | pass — `grep -nE '[0-9]+ (classes\|tests)\|[0-9]+m[0-9]+s\|~[0-9]+ min' AGENTS.md` → no output, exit 1 |
| facts and references | a fact is stated where it is used | 2.2 / 2.6 | pass — the checklist below |
| one normative home | editing a rule touches one document | 4.1 | pass — 8 blocks, 8 owner statements |
| one normative home | a duplicate is removed rather than synchronised | 2.2-2.5 | pass — the AA block's rules now live once, in `Design.md` §8 / `UI.md` §3a-§3c / the specs, with the entry document carrying one line each |
| one normative home | a rule that spans documents names one home | 2.2 / 3.1 | pass — the car topic is routed to all four owners, and the routing row names each |
| relocated rule keeps its statement | the removal is verified by finding the statement | 4.1 / 4.6 | pass |
| relocated rule keeps its statement | the relocation count closes | 4.1 | pass — 8 / 8 |
| relocated rule keeps its statement | a homeless rule is filed, not dropped | 4.2 | pass — none was homeless (see below) |

**2.6 do-not-move checklist** (each present exactly once in `AGENTS.md`): "One surface, one owner",
"look then measure", "coordinate-free" (the diagnostics rule), "Two distribution flavors",
`openDatabase`/`openDatabases` (the asymmetry), "one builder per working tree".

## 4.1 / 4.2 — the relocation count

```
blocks removed from AGENTS.md ............ 8
statements found in an owning document ... 8      (block 1 in three owners, which the route names)
homeless blocks .......................... 0      (nothing filed; nothing deleted without an owner)
```

Verification is the owner-hit table above: every row was produced by the `grep` it names, in this session, on this tree.

## 4.3 — the measurement

```
                        before      after      delta
AGENTS.md (always read) 42 745 B    28 374 B   -14 371 B  (-33.6 %)
  tokens (÷3.6)         ~11 874     ~7 882     -3 992 tokens per session
guidelines/Build.md     99 308 B   105 084 B   +5 776 B  (+1.6k tokens, paid per section read, not per session)
guidelines/Design.md    53 158 B    54 580 B   +1 422 B  (+0.4k tokens, same)
```

The always-injected document lost a third of its size; the two guidelines gained the two owners the
relocated rules needed. Because the previous change made guideline reads section-scoped, the additions
cost only when their sections are read, while the removal is paid once per session.

## 4.4 / 4.5 — what is not owed, and why

Files this change touched: `AGENTS.md`, `guidelines/Build.md`, `guidelines/Design.md` — three markdown
files, plus this change's own directory. No source, test, Gradle, manifest, resource, workflow, native or
JNI file.

Stated explicitly so no later reader infers otherwise: **(a)** no on-device or emulator evidence is owed —
no UI, rendering, car-surface, template or lifecycle behaviour changed; **(b)** no new unit test is owed —
the artifact under test would be documentation, and the delta's scenarios are `grep`-checkable.

## 4.6 — revert-check (failure first, then green)

```
$ sed -i '/^### Agent iteration protocol$/,/^### Liveness guard and shell recipes$/{…!d}' guidelines/Build.md
$ grep -n '^### Agent iteration protocol' guidelines/Build.md
(no output) exit=1                     <- the statement is gone while AGENTS.md still points at it (2 hits)
$ cp <backup> guidelines/Build.md; md5sum guidelines/Build.md <backup>
6e74e0e2ee439b7e86cbdf3d18da8728  guidelines/Build.md
6e74e0e2ee439b7e86cbdf3d18da8728  <backup>
$ grep -n '^### Agent iteration protocol' guidelines/Build.md
71:### Agent iteration protocol         <- green again
$ bash tools/check-doc-routes.sh
check-doc-routes: all 65 section references resolve
```

One mutation only. **Limitation recorded:** the route check verifies that a `## ` section exists, so it
does *not* catch a deleted `###` subsection — the named check for this invariant is the heading `grep`
above, and a future guard would need a subsection-aware variant.

## 3.1 / 3.2 — the new sections are routable

The routing table gained a native-build row (`Build.md` §12 "Native build and vcpkg") and now names
`Build.md` §2 "Common behavior" (its "Agent iteration protocol" subsection) and `Design.md` §2's recipes in
its existing rows. The check resolves **65** references (was 63), and the self-test is unchanged: 7 passed,
0 failed. References name `## ` sections only, because that is what the check understands; a subsection is
named in prose beside its section.

## 5.1 / 5.2 / 5.3 — gates

```
$ openspec validate slim-agents-md --strict      -> valid, exit 0
$ openspec doctor                                -> OpenSpec root ok, exit 0
$ git status --porcelain -- AGENTS.md guidelines/ openspec/changes/slim-agents-md
 M AGENTS.md
 M guidelines/Build.md
 M guidelines/Design.md
 M guidelines/UI.md          <- the peer session's edit, not this change
$ git status --porcelain .github/                -> no output for this change (the CI step is the
                                                    previous change's edit)
```

## Notes

1. **Two references in the routing table name a `## ` section, not the subsection that holds the rule**
   (`Build.md` §2 for the protocol, `Design.md` §2 for the recipes): the check resolves top-level sections,
   so the subsection is named in the row's prose. Worth knowing before adding a row.
2. **The AA block was the one topic whose owner is spread over three documents** — `Design.md` §8, `UI.md`
   §3a-§3c and the four specs. The route names all of them, which is the "a topic is owned in more than one
   document" scenario of the capability's first requirement.
3. **Nothing was moved between guideline sections, and no measurement was relocated** — the one
   measurement-bearing sentence removed earlier (`-PforceTests --no-build-cache`, the suite sizes) had
   already been dealt with by `dedupe-test-rule-ownership`; this change re-quotes none of them.
