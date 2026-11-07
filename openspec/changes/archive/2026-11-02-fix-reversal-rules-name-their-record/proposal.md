# Five of the nine reversals §13 records: the rule each produced names it

## Why

`guidelines/Design.why.md` §13 "Superseded decisions" records the project's reversals, and this delta points **five**
of them — the five rules the item names, which exist *because* one of those reversals was decided. None of the five
names the record: a reader meets the rule and never the reason it replaced something.

The five are five of **nine**: `sed -n '7,71p' guidelines/Design.why.md | grep -c '^- '` prints `9`. The other four
reversals' rule sites are recorded as residual below and are not pointed by this delta, so the claim this archive
makes is exactly the five sites it edits — not "the rules born from a reversal", which would be false for those
four (`revision 2, review round 1, item 2`).

Measured on base `e190d8729eda946da6de579151552a5203055c61` (`evidence/surface.txt`):

```
$ grep -rln 'Design\.why\.md' guidelines/*.md
guidelines/Design.md
$ grep -c 'Design\.why' guidelines/Design.md
2
$ grep -rn 'Design\.why' tools/ | wc -l
0
$ grep -c 'Design\.why' guidelines/DesignCrossVariant.md guidelines/MapFollow.md
guidelines/DesignCrossVariant.md:0
guidelines/MapFollow.md:0
```

Only `guidelines/Design.md` names the companion, and only in its two maintenance lines (`Design.md:17` the rule that
records a supersession, `Design.md:356` the §13 pointer that replaced the moved section). The five rules carry no
pointer at all; `AGENTS.md:51` routes "why an earlier decision was reversed" to the companion, so the route exists,
but a reader who arrives at a rule through its own document never crosses it.

**The root cause, in one sentence.** In the two documents this delta edits, five rules state what to do and the
companion states what each replaced, and no pointer joins the two, so the record is reachable only from the routing
table and never from the rule the record explains.

**The five rules, re-derived against the tree today** (the item filed 2026-10-31 named `MapRendering.md` sections 7 and 8,
`Regulatory.md` and `UICar.md` §3; the 2026-10-31 guideline split moved all of them, and the probe below reads the
sites that carry the rule now). These are the five the item names, out of the nine §13 records:

| §13 records the reversal of | the rule born from it lives in |
|---|---|
| live guidance built instruction prose in native, parsed back in the app | `DesignCrossVariant.md` section 8a — "Every car guidance surface reads the step model" |
| AA instruction hints surface-drawn → host-rendered panel | `DesignCrossVariant.md` section 8a — the host-constraint bullet ("Prefer host-rendered panels") |
| dual-mode single APK → two-flavor split | `DesignCrossVariant.md` section 8b — "Rule: phone and AAOS are separate build flavors under one applicationId" |
| bearing smoothing in the ViewModel → location layer | `MapFollow.md` section 7 — "Bearing smoothing lives in `LocationService` (`BearingFilter`), NOT in the ViewModel" |
| GPS markers baked into the native render → display-layer overlays | `MapFollow.md` section 8 — "Rule: Render target" (never written into cached tiles, the back or front buffer) |

**Residual — the four §13 reversals this delta does not point**, each a later change's and none a claim of this
archive (measured after the delta: `grep -rn 'Design\.why\.md' guidelines/MapRendering.md guidelines/UIPhoneMap.md
guidelines/UICar.md` prints nothing):

| §13 records the reversal of | the rule born from it lives in |
|---|---|
| single render path with `blitSubRegion` → TILES/DIRECT split | `guidelines/MapRendering.md:95` "Rule: TILES mode — tile path" and `guidelines/MapRendering.md:101` "Rule: DIRECT mode — full native path" |
| the route list built by parsing the native English description lines → derived from `RouteEntry.steps` | `guidelines/UIPhoneMap.md:124` "Rule: the route list is derived from the route's structured steps, never from description text" |
| the repository readiness flag deferred as API surface → published as its own signal | `guidelines/UICar.md:106`, the text naming `AutoFavoritesProvider.storeReady` |
| "No new GPS load" → an invisible, non-navigating FREE_DRIVE holds no location lease | no guideline rule site: the guarantee is `openspec/specs/location-updates-lease/spec.md` |

The relocation is the change's own input, not a defect of it: the same five rules are the ones the item names, and the
2026-10-31 change `guideline-design-layers` left the fix open because they then lived in four documents that other
landed changes owned (`openspec/changes/archive/2026-10-31-guideline-design-layers/evidence/blocked-2.2-finding.md`).
Today they live in **two** documents, both routed from `AGENTS.md`, so one change owns the whole delta. A reversal
with more than one rule site is pointed at the one site the item named: the step-model reversal's siblings
(`DesignNative.md:21`, `UIPhoneMap.md:136`, and the host-rendered/one-source text at `UI.md:146`) stay unpointed and
are not claims of this archive.

**The red test.** `reversal-pointer-probe`, `evidence/reversal-pointer-probe.sh` — a text probe that reads the
block of each of the five rules (from its statement line to the next blank line, bullet or heading) and requires
`Design.why.md` in it. On base `e190d872` it reports `tests=5 failures=5` (`evidence/red-on-HEAD.xml`,
`evidence/red-on-HEAD.log`, 2026-11-02T04:45:09Z): every one of the five rules carries no pointer. It is text
evidence, not a declared case: the item's recorded `Fix` is *additive, no new claim and no case*, and the item's
`Rejected alternative` records why "a rule that exists because a decision was reversed names the record" cannot be
made a rule — which rule was born from a reversal is a judgement no text decides. The probe therefore counts the
five sites this delta actually edits, and a permanent guard over them would be the rejected rule wearing a case's
name.

**Owning capability and its home.** `documentation-ownership` — "A normative document carries no rationale and no
procedure" (`openspec/specs/documentation-ownership/spec.md:642`) owns where a rule's reason lives: the normative
document states the rule, the companion states why. This change moves no text between the layers and adds no
rationale; it adds the pointer between them, which is the same relation `Design.md:17` already states for a
supersession. `AGENTS.md:63` routes a concern's rationale to its `<Concern>.why.md` companion, and `AGENTS.md:51`
routes a reversal to `Design.why.md` §13 — the two rows this delta makes meet at the rule.

**Surface.** Measured with the commands above: **0 cases added, 0 rewritten, 0 fixtures realigned** — 5 rule sites
in 2 documents gain one pointer line each, and nothing asserts the pointer today (`grep -rn 'Design\.why' tools/`
= 0). The three pointer sentences in `DesignCrossVariant.md` — two on new lines and the section 8b one appended to
an existing line — also move section 8a to 10 540 B and section 8b to 3 038 B, so `AGENTS.md`'s
two routing rows for them state 10.5 kB and 3.0 kB after the delta (`tools/check-doc-routes.sh --sizes`,
`evidence/section-sizes.log`); the two
`MapFollow.md` lines move no routed figure, because its row prices no section.

surface: 5 rule sites in 2 documents gain one pointer line; 0 cases added, 0 rewritten, 0 fixtures realigned —
`grep -c 'Design\.why' guidelines/DesignCrossVariant.md guidelines/MapFollow.md` (0 and 0 before the delta)

## What Changes

- **`guidelines/DesignCrossVariant.md`** — one sentence added inside each of the three rule blocks that §13
  explains (section 8a step model, section 8a host-rendered panels, section 8b two flavors): the reversal, and why, is
  `guidelines/Design.why.md` §13. No rule text, name or bound changes.
- **`guidelines/MapFollow.md`** — the same sentence in section 7's opening statement (bearing smoothing in the location
  layer) and in section 8's "Rule: Render target". No rule text, name or bound changes.

No requirement is added, no case is added, no fixture moves, and no compiled file is touched: the gate this change
owes is the documentation checks `tools/check-doc-routes.sh`, its self-test, `tools/check-todo-references.sh`,
`tools/check-openspec-hygiene.sh` and its self-test, plus `tools/gen-feature-list.sh --check-classification`.

## Capabilities

### New Capabilities

None — the pointer is additive and states no new claim.

### Modified Capabilities

None. The requirement that owns the relation, `documentation-ownership` "A normative document carries no rationale
and no procedure" (`spec.md:642`), already states where a rule's reason lives; this change makes five rules use it
and adds no requirement, in line with the item's recorded rejection of the rule form.

## Impact

| path | what changes |
|---|---|
| `guidelines/DesignCrossVariant.md` | 3 pointer sentences (no rule text, name or bound changes; section 8a 10.5 kB, section 8b 3.0 kB) |
| `guidelines/MapFollow.md` | 2 pointer sentences (no rule text, name or bound changes) |
| `AGENTS.md` | two routing rows realigned to `DesignCrossVariant.md` section 8a 10.5 kB and section 8b 3.0 kB |
| `openspec/changes/fix-reversal-rules-name-their-record/` | the proposal, design, tasks and `evidence/` this change is read by |

No compiled file: `tools/loop/gate.sh` runs the documentation suites and no Gradle build, and its tally is the
change's gate record.
