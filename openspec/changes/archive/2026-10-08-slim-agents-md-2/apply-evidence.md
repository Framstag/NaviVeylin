# Apply evidence — `slim-agents-md-2`

Session 2026-10-07, after the archive of `slim-agents-md`. Delta:
`specs/documentation-ownership/spec.md` — 3 added requirements, 12 scenarios. Design: `design.md` (D1–D6).

**Shared-tree note.** A peer session is applying `fix-free-drive-background-liveness` in this tree —
`git status` shows `guidelines/UI.md` modified by it and its change directory is minutes old. This change
touched `AGENTS.md`, `guidelines/Build.md`, `guidelines/Design.md`, `guidelines/MapRendering.md`,
`openspec/config.yaml` and `tools/`; it did not touch `guidelines/UI.md`, `.github/` or `TODO.md` (all of
which are dirty from the peer, from the previous change, or both).

## 2.1 — the relocation inventory (built before the edits)

| # | block removed from `AGENTS.md` | statement found in |
|---|---|---|
| 1 | `### Logging` (3 subsections, 3 466 B) | `guidelines/Logging.md` (new) §1-§3, verbatim |
| 2 | `### JNI stub for unit tests` (1 596 B) | `guidelines/Build.md` §6, last bullet of the test constraints |
| 3 | `### Native Integration` rules (2 326 B) | `guidelines/Design.md` §5, new subsection |
| 4 | `### Stylesheets` runtime rules (1 661 B) | `guidelines/MapRendering.md` §16b (new) |
| 5 | the same block's packaging facts | `guidelines/Build.md` §12, new subsection |
| 6 | AA manifest/distribution detail (~2 500 B) | already in spec `android-automotive-os` §"Automotive template host metadata", §"Automotive hardware feature declaration", §"Distribution as separate bundle" — no move, a pointer |
| 7 | the two harness paragraphs (~2 000 B) | `guidelines/Build.md` §1, the harness subsection |
| 8 | `## Constraints` (617 B) | already in `guidelines/Design.md` §2 — no move, a pointer |

Plus the routing table's heading text: dropped from rows whose section number is unique and unsuffixed, kept
in the rows that name several documents and where a number is ambiguous (`MapRendering.md` §14). That is a
trim, not a relocation, so it is not counted below.

**Deviation from the task text.** Task 2.4 said the AA detail moves to the specs `auto` and
`android-automotive-os`. It turned out to be **already there, verbatim** (metadata, the `AAR` merge and
`tools:node="remove"`, `required="true"`, the two AABs and both Play tracks) — and those two files are *main*
specs, which a change may only update through its own delta. So the block became a pointer. The packaging
facts of §5 went to `Build.md` §12 rather than to a spec, for the same reason: they are implementation facts,
which the specs rules keep out of specs.

## New owners created

- `guidelines/Logging.md` (4 325 B) — the only recurring concern that had no document.
- `guidelines/Build.md` §2's "Agent iteration protocol" subsection and §12 "Native build and vcpkg" existed
  already; this change added the JNI-stub facts to §6, the harness section to §1, and the stylesheet/icon
  packaging facts to §12.
- `guidelines/MapRendering.md` §16b "Stylesheet source and the on-device copy".
- `guidelines/Design.md` §5's "The bridge's two sides, the database-open asymmetry and the submodule
  discipline".

## 4.0 — scenario → check

| requirement | scenario | check | result |
|---|---|---|---|
| a concern has one owning document | a rule is added to its concern's document | 1.2 / 2.1-2.6 | pass — each moved block lands in the document that owns its concern |
| a concern has one owning document | a recurring concern without a document gets one | 1.2 | pass — `guidelines/Logging.md`, holding the concern's existing rules verbatim |
| a concern has one owning document | a new document is reachable | 3.1 / 3.4 | pass — the routing table and the config inventory both name it |
| the entry document is bounded | a fact needed before any other read stays | 4.3 | pass — read-back below |
| the entry document is bounded | a fact needed only inside one area moves | 2.1-2.6 | pass — 8 blocks |
| the entry document is bounded | relocating keeps the entry document readable | 4.3 | pass — see the read-back |
| every guideline is covered by the check | the document set is derived | 3.2 | pass — the list comes from `guidelines/*.md` |
| every guideline is covered by the check | a new guideline is covered without a code change | 3.2 / 3.3 | pass — `Logging.md` is checked; the self-test's extra-guideline cases pass |
| every guideline is covered by the check | an unrouted document is reported | 3.2 | pass — a throwaway `guidelines/Tmp.md` was reported, then removed |
| every guideline is covered by the check | the derivation is self-tested | 3.3 | pass — 10 cases, 0 failed |

## 4.1 / 4.2 — the relocation count

```
blocks removed from AGENTS.md ............ 8
statements found in an owning document ... 8      (six by movement, two already present and now referenced)
homeless blocks .......................... 0      (nothing filed; nothing deleted without an owner)
```

Each row of the inventory above was produced by the `grep` it names, in this session, on this tree.

## 4.3 — the entry-document read-back

`AGENTS.md` is now 16 835 B / 242 lines. Section by section, against the boundary rule ("needed before the
first read"):

```
## Documentation Map            3783   the routes themselves                        stays
## Agent iteration loop         2039   the loop's pointer, the skills, the harness  stays (see residual 2)
### Android Auto / AAOS         1475   two invariants + the two flavors + routes    stays
### Architecture               1030   app architecture facts (+ one rule, see 1)   stays, residual 1
## OpenSpec Workflow             912   how to work in this repo                     stays
### Logging                     742   two facts + route                            stays
## Build & Test                 739   pointer + routes                             stays
## Backlog maintenance          722   how TODO.md is maintained                    stays
### Native Integration          701   one rule + route                             stays
## Module Structure             689   module map                                   stays
### License compliance          626   the SPDX fact + route                        stays
## Constraints                  584   pointer + the two distribution facts         stays
## Tech Stack                   549   stack table                                  stays
### Stylesheets                 537   one fact + route                             stays
### Release versioning          529   two facts + route                            stays
## Project Overview             313   what the app is                              stays
### Code Style                  120   style conventions                            stays
```

One block fails the test and stayed, which the task requires be named with its reason:

1. **`### Architecture` carries a rule with no owner.** "Favorite writes are serialised on both sides and
   must stay that way: `osmscout::FavoriteStore` … while `FavoriteRepository` holds one write lock across
   `mutate + refreshState + persist`" is a rule for a reader working in the data layer, and
   `guidelines/Design.md` §9 does **not** state it (checked). Per the "a homeless rule is filed, not dropped"
   clause it was left in place rather than deleted, and it is reported here for a third pass: move it to
   `Design.md` §9 (or a `fav-service` section) and keep one sentence in `AGENTS.md`.
2. **`## Agent iteration loop` keeps two bullets that restate routes** ("measuring a pixel symptom →
   `Build.md` §10/§11 and the `pixel-check` skill"; "the device loop → the `device-check`,
   `provision-phone-emulator` and `compose-geometry` skills") which the routing table now states. Harmless
   duplication rather than a missing owner; tightening it is a candidate for a third pass, not a
   requirement of this delta.

## 4.4 — the measurement

```
                             before        after        delta
AGENTS.md (always read)      28 374 B      16 835 B     -11 539 B  (-40.7 %)
  tokens (÷3.6)               ~7 882        ~4 677     -3 205 tokens per session
guidelines/Build.md         105 084 B     108 724 B     +3 640
guidelines/Design.md         54 580 B      56 681 B     +2 101
guidelines/MapRendering.md   81 714 B      82 504 B       +790
guidelines/Logging.md              —        4 325 B     +4 325   (new owner)
guidelines/UI.md             78 188 B      78 188 B          0
guidelines/Regulatory.md     23 941 B      23 941 B          0
                           ---------     ---------    --------
guideline total             343 507 B     354 363 B    +10 856 B  (+3 015 tokens, per section read)
openspec/config.yaml         11 109 B      11 137 B         +28   (served when writing an artifact)
```

Cumulative across the three changes of this family: `AGENTS.md` 38 817 B (~10.8k tokens) at the start of the
session → 16 835 B (~4.7k), a **57 % reduction of the always-injected document**, while the guideline corpus
grew to hold the owners (read by section, 10-14 % of a whole-document read).

## 4.5 / 4.6 — what is not owed, and why

Files touched: `AGENTS.md`, `guidelines/Build.md`, `guidelines/Design.md`, `guidelines/MapRendering.md`,
`guidelines/Logging.md` (new), `openspec/config.yaml`, and the two `tools/check-doc-routes*.sh` — markdown,
one YAML, two shell scripts. No source, test, Gradle, manifest, resource, workflow, native or JNI file.

Stated explicitly so no reader infers otherwise: **(a)** no on-device or emulator evidence is owed — no UI,
rendering or car-surface behaviour changed; **(b)** no new unit test is owed — the artifact is documentation
plus a script whose verification is its 10-case self-test.

## 4.7 — revert-check (failure first, then green)

```
$ sed -i 's|for path in "$gdir"/*.md; do|for path in "$gdir"/Build.md …"$gdir"/Regulatory.md; do|' tools/check-doc-routes.sh
  # a maintained five-document list again, plus a throwaway unrouted guidelines/Tmp2.md
$ bash tools/check-doc-routes.sh
::error file=AGENTS.md::Logging.md — the routing table references it, but guidelines/Logging.md does not exist
check-doc-routes: 0 of 68 references do not resolve, 1 referenced document(s) missing
mutated exit=1                      <- the maintained list is detected, and 68 < 71 references are even seen
$ bash tools/check-doc-routes.sh 2>&1 | grep -c Tmp2
0                                   <- the unrouted document is INVISIBLE to a maintained list: the hole
$ cp <backup> tools/check-doc-routes.sh; rm guidelines/Tmp2.md
$ md5sum tools/check-doc-routes.sh <backup>
5d245b6867a1556f1bb2aa70c260f974  tools/check-doc-routes.sh
5d245b6867a1556f1bb2aa70c260f974  <backup>
$ bash tools/check-doc-routes.sh          -> all 71 section references resolve, exit 0
$ bash tools/check-doc-routes-selftest.sh -> 10 passed, 0 failed
```

One mutation only. The check reads text files, so "forced green" is the re-run of the command.

## 5.1 / 5.2 / 5.4 — gates

```
$ openspec validate slim-agents-md-2 --strict     -> valid, exit 0
$ openspec doctor                                 -> OpenSpec root ok, exit 0
$ bash tools/check-doc-routes.sh                  -> all 71 section references resolve
$ git status --porcelain .github/                 -> no edit by this change (the CI step is the earlier change's)
$ git diff --stat TODO.md                         -> the peer session's entries only; this change did not touch it
```
