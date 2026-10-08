# Apply evidence — `scope-guideline-reads`

Session 2026-10-07, 22:52–23:00. Delta: `specs/documentation-ownership/spec.md` — one new capability,
4 requirements, 13 scenarios. Design: `design.md` (D1–D6).

**Shared-tree note.** The peer session applying `fix-phone-map-layer-stack` was still active during this
pass (its emulator screenshots under `.pi/logs/fix-phone-map-layer-stack/` are stamped after this pass
began). `AGENTS.md` and `TODO.md` therefore carry its uncommitted lines beside this change's edits — the
`git diff --stat` figures for those two files include them. This change started no Gradle run and touched
no source, so the rule-4 build-corruption case does not arise.

## Edits

| File | Change |
|---|---|
| `AGENTS.md` | "Documentation Map" (3 lines) → a topic→section routing table, 22 rows, each reference `§<number> "<heading text>"`, with a recovery line for unrouted sections |
| `openspec/config.yaml` | `context` states the whole-document cost and points at the routing table; the apply bullet "verify against every document" and the archive bullet "follow the guidelines/ documents" are scoped to the owning sections |
| `tools/check-doc-routes.sh` | new: resolves every §-reference in the Documentation Map against the named guideline (fenced blocks are not headings; text must match a heading with that number; a bare duplicated number is ambiguous) |
| `tools/check-doc-routes-selftest.sh` | new: 7 cases against throwaway fixtures |
| `.github/workflows/build.yml` | new step "Check documentation routes" (its own step, `bash tools/check-doc-routes.sh`), after "Check OpenSpec artifact hygiene" |
| `TODO.md` | new entry §153 (duplicated `## 14.` in `MapRendering.md`), cluster line `specs-and-process` gains §153 |

## 4.0 — scenario → check, with results

| requirement | scenario | check | result |
|---|---|---|---|
| routed to owners | phone UI change routed | 1.1 row + 4.3 | pass — the phone-UI row names `UI.md` §7, §7a, §8, §8a, §9, §10, §11 and `Design.md` §2; 4.3 follows it to the governing sentence |
| routed to owners | topic owned in more than one document | 1.2 (inspection) | pass — see below |
| routed to owners | ambiguous/absent heading | 1.3 + 3.2 | pass — the check reported `UI.md §14` as ambiguous before the heading text was read correctly (63 refs, 1 ambiguous); the self-test's duplicate-number fixture covers it |
| routed to owners | sections outside the map reachable | 1.1 recovery line | pass — the map's first paragraph states `grep -n '^## ' guidelines/<file>.md` |
| route resolves | checked without build/device | 3.1 | pass — `bash tools/check-doc-routes.sh` → `all 63 section references resolve`, exit 0; tmp files only, no network |
| route resolves | dangling reference fails | 3.1 + 4.2 | pass — 4.2 |
| route resolves | removing a row is detected | 4.2 | pass |
| route resolves | check is self-tested | 3.2 | pass — 7 passed, 0 failed |
| guidance by section | context instruction | 2.1 | pass |
| guidance by section | apply instruction | 2.2 | pass |
| guidance by section | guidance survives the edit | 2.3 | pass — doctor exit 0, apply 22 / archive 17 entries |
| cost measured | numbers for representative changes | 4.1 | pass — below |
| cost measured | unmeasured saving not claimed | 4.1 | pass — the numbers below are the only saving claim |

**1.2 inspection.** The car row names all four owners: `Design.md` §8 "Android Auto & cross-variant",
`MapRendering.md` §14 "Android Auto renderer", `UI.md` §3a "Map renderer startup", and the specs
`car-host-fault-isolation`, `auto/screen-observation`, `auto-map-renderer`, `auto-smooth-follow`. The
parity row names `UI.md` §1 and §6; the diagnostics topic is routed to `Build.md` §10 and this file's own
logging sections. Coverage is not machine-checked (design D2) — the check enforces resolution only.

## 4.1 — measurement: routed read vs the whole-document read

Whole-document set the old instruction named (`wc -c` ÷ 3.6):

```
Build.md        99 308 B  ~27 586 tok
MapRendering.md 81 714 B  ~22 699 tok
UI.md           78 188 B  ~21 719 tok
Design.md       53 158 B  ~14 766 tok
                -------   --------
                312 368 B  ~86 770 tok        (+ Regulatory.md 23 941 B ~6 650 tok when it applies)
```

The sections the routing table names for each in-flight change (`/tmp/section-sizes.tsv`, one row per
`## ` section, fenced blocks excluded):

```
change                                 routed sections        tokens   of ~86.8k
fix-phone-map-layer-stack   (phone UI) Design §2, UI §7/7a/8/8a/9/10/11      ~11 708   13.5 %
fix-car-follow-reengage-render (car)   Design §8, MapRendering §14, UI §3a,
                                       MapRendering §3/6/7/8/9/10/11        ~10 817   12.5 %
fix-client-dpi-surface-leak  (render)  Design §8, MapRendering §14, UI §3a,
                                       MapRendering §18, UI §10a            ~8 833   10.2 %
```

A routed read is 10–14 % of the document read, i.e. roughly a 7–8× reduction, for the three changes
measured. The table adds ~2.8 KB (~0.8k tokens) to the always-injected `AGENTS.md`, so the break-even is
one guideline section read.

## 4.2 — revert-check (failure first, then green)

```
$ cp AGENTS.md <backup>; sed -i 's/§7a "Phone map re-center button"/§99 "Phone map re-center button"/' AGENTS.md
$ bash tools/check-doc-routes.sh
::error file=AGENTS.md::UI.md §99 — no such section
check-doc-routes: 1 of 63 references do not resolve
mutated exit=1                       <- the dangling reference is named, file and section
$ cp <backup> AGENTS.md; md5sum AGENTS.md <backup>
ddd8c887b8d2184dcfaa24d63e1f4c0e  AGENTS.md
ddd8c887b8d2184dcfaa24d63e1f4c0e  <backup>
$ bash tools/check-doc-routes.sh
check-doc-routes: all 63 section references resolve
restored exit=0
```

One mutation only. The check reads text files, so "forced green" is the re-run of the command — no build
cache is involved.

## 4.3 — the route reaches a real convention

Following the phone-UI row for `fix-phone-map-layer-stack` lands on `guidelines/UI.md` §11 "Phone overlay
layering (the band stack)", which governs that change:

```
guidelines/UI.md:1050  The phone map screen composes everything into **five named bands**, and the band an
guidelines/UI.md:1051  element is composed in is the only thing that decides the stacking (`MapLayer` in
guidelines/UI.md:1052  `MapCanvasScreen.kt`, spec `map-canvas-screen` — Phone map overlay layer stack).
```

Corroboration from the other direction: that change's own uncommitted `AGENTS.md` note cites
`guidelines/UI.md` §11 for the modal band, i.e. the route names the section its author already used.

## 3.3 — the CI step

Run verbatim, `bash tools/check-doc-routes.sh` → exit 0; the script sets `set -uo pipefail` itself, so the
step needs no wrapper. The failure path is 4.2's mutation: `::error file=AGENTS.md::UI.md §99 — no such
section`, which is the line the workflow would annotate.

## 5.1 / 5.2 — nothing is owed, and why

```
$ git status --porcelain -- AGENTS.md openspec/config.yaml TODO.md tools .github/workflows/build.yml
 M .github/workflows/build.yml
 M AGENTS.md
 M TODO.md
 M openspec/config.yaml
?? tools/check-doc-routes-selftest.sh
?? tools/check-doc-routes.sh

$ git diff --stat -- AGENTS.md openspec/config.yaml TODO.md .github/workflows/build.yml
 .github/workflows/build.yml |   9 +
 AGENTS.md                   |  87 ++++++---      (includes the peer's 18 lines)
 TODO.md                     | 455 ++++++++---   (includes the peer's 427 lines)
 openspec/config.yaml        |   8 +-
```

Four text files, one YAML, one YAML workflow, two shell scripts — no source, test, Gradle, manifest,
resource, native or JNI file. The `.pi/logs/fix-phone-map-layer-stack/*.png` files that a whole-tree mtime
scan also shows belong to the concurrent peer session.

Stated explicitly so no later reader infers otherwise: **(a)** no on-device or emulator evidence is owed —
no UI, rendering, car-surface, template or lifecycle behaviour changes, so no `pixel-check`/`device-check`
step applies; **(b)** no new unit test is owed — the new artifact is a shell script and it carries its own
device-free self-test (3.2), while the delta's other scenarios are `grep`- and check-verifiable.

## Notes, deviations and added scope

1. **The task labels for 2.2 / 2.3 name the wrong YAML key.** Lines 83 and 111 are
   `operations.apply.guidance` and `operations.archive.guidance`, not `rules.apply` / `rules.archive`. The
   line numbers were right, so the edits landed where the tasks meant; the effect is what task 2.2/2.3
   describe (the served guidance, verified through `openspec instructions apply|archive`).
2. **The check had a real bug, found by running it on the real table:** heading text separated from the
   number by a space was read as absent, so `MapRendering.md §14 "Android Auto renderer"` was reported as an
   ambiguous bare number. The parser now skips whitespace before the opening quote. Without the run this
   would have shipped as a false positive on a resolvable reference.
3. **Extra edit, same requirement:** `guidelines/Build.md` was *not* touched by this change (its §4/§6 were
   edited by the previous change). The routing table's intro deliberately carries no token numbers; the
   cost is stated once, in `openspec/config.yaml`'s `context` (task 2.1), so the "one documented home" rule
   of `build-test-gate` holds for this measurement too.
4. **Residual (not this change's):** the peer's uncommitted `AGENTS.md`/`TODO.md` lines will ride along in
   whatever commit lands first, and `TODO.md`'s `§153` sits in a file the peer is editing.
5. The check reports `63` references today; a row added later without a resolvable section fails the CI
   step immediately.

## 5.4 / 5.5 — gates

```
$ openspec validate scope-guideline-reads --strict   -> valid, exit 0
$ openspec doctor                                    -> OpenSpec root ok, exit 0
$ bash tools/check-doc-routes-selftest.sh            -> 7 passed, 0 failed, exit 0
```

`slim-agents-md` was not started on this tree (`git status` shows no edit outside this change's declared
files); it can now lean on the routing table this change produced.
