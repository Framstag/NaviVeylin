# Design — five pointer sentences, no new claim

## The root cause

The normative layer states the rule and `guidelines/Design.why.md` §13 states the reversal that produced it; nothing
in either layer joins the two. `Design.md:17` states the relation only for a *future* supersession ("the supersession
itself is recorded in `guidelines/Design.why.md` §13"), and `AGENTS.md:51` routes a reader who is asking why a decision
was reversed to the companion — so the record is reachable from the routing table, and unreachable from the rule
whose own section a reader is already reading. A pointer that no rule carries is invisible to every check in the
tree: `grep -rn 'Design\.why' tools/` is 0, so no case and no check reads the relation at all.

This is the state of the **five rule sites the item names**, not of every rule born from a reversal: §13 records
nine reversals (`sed -n '7,71p' guidelines/Design.why.md | grep -c '^- '` = 9), and the four this delta leaves alone
are listed as residual under "Residual sites" below. `revision 2, review round 1, item 2` narrowed the claim to the
sites the delta edits.

## The fix

One sentence inside each of the five rule blocks that §13 explains — "The reversal this rule exists for, and why, is
recorded in `guidelines/Design.why.md` §13." — in the two documents that now carry those rules:

| document | rule | what it explains |
|---|---|---|
| `DesignCrossVariant.md` section 8a | "Every car guidance surface reads the step model" | instruction prose built in native and parsed back |
| `DesignCrossVariant.md` section 8a | the host-constraint bullet ("Prefer host-rendered panels") | AA instruction hints drawn app-side |
| `DesignCrossVariant.md` section 8b | "Rule: phone and AAOS are separate build flavors under one applicationId" | dual-mode single APK |
| `MapFollow.md` section 7 | "Bearing smoothing lives in `LocationService` (`BearingFilter`), NOT in the ViewModel" | bearing smoothing in the ViewModel |
| `MapFollow.md` section 8 | "Rule: Render target" (never written into cached tiles or a buffer) | GPS markers baked into the native render |

The sentence sits inside the rule's block, so it has no rule name of its own, states no new obligation, and adds no
`### Why` heading or figure — the two forms `documentation-ownership` "A normative document carries no rationale and
no procedure" (`spec.md:642`) reports. It is a pointer, the same shape `Design.md:17` and `Design.md:356` already use,
and the section it names is the one the companion carries (`guidelines/Design.why.md:7`). `tools/check-doc-routes.sh`
resolves section references into routed documents only, so a companion named inside a body is text, not a resolved
reference; the pointer's correctness is that anchor line, checked by reading it.

No permanent case is added, deliberately. The item records the fix as *additive, no new claim and no case*, and it
records why the rule form was rejected: which rule was born from a reversal is a judgement no text decides, so a guard
over "every rule born from a reversal names the record" would be undecidable. The delta's evidence is a probe over the
five sites it edits (`evidence/reversal-pointer-probe.sh`), which is red on the base (`tests=5 failures=5`) and green
after the change — the number the delta requires, without turning a judgement into a declared rule.

## Residual sites

Four of §13's nine reversals keep rules that carry no pointer after this delta, and none of them is this change's:

| §13 reversal | rule site left unpointed |
|---|---|
| single render path with `blitSubRegion` → TILES/DIRECT split | `guidelines/MapRendering.md:95` "Rule: TILES mode — tile path", `guidelines/MapRendering.md:101` "Rule: DIRECT mode — full native path" |
| the route list parsed from native description lines → derived from `RouteEntry.steps` | `guidelines/UIPhoneMap.md:124` "Rule: the route list is derived from the route's structured steps, never from description text" |
| the readiness flag deferred as API surface → published as its own signal | `guidelines/UICar.md:106`, the text naming `AutoFavoritesProvider.storeReady` |
| "No new GPS load" → an invisible, non-navigating FREE_DRIVE holds no location lease | no guideline rule site; the guarantee is `openspec/specs/location-updates-lease/spec.md` |

The item named five rules and this delta points exactly those five. Widening to the four above is a different change
with its own probe rows and gate round; it is recorded, not taken, here.

## Blast radius

| path | what it can break |
|---|---|
| `guidelines/DesignCrossVariant.md` | the section bounds and the routing row's figures are unchanged by the plan but not by the text: section 8a measured 10 525 B and section 8b 3 038 B (`evidence/section-sizes.log`, `tools/check-doc-routes.sh --sizes`), so `AGENTS.md`'s two rows were realigned to 10.5 kB and 3.0 kB (`tools/check-doc-routes.sh`); no rule name is added or moved |
| `AGENTS.md` | two routing rows' figures for `DesignCrossVariant.md` section 8a/section 8b: 10.3/2.9 kB -> 10.5/3.0 kB; no route added or removed |
| `guidelines/MapFollow.md` | same: 2 added lines, no rule name added or moved, no measurement touched |

No compiled file, no Gradle, no device, no network. The documentation suites `tools/check-doc-routes.sh`,
`tools/check-doc-routes-selftest.sh`, `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh`,
`tools/check-openspec-hygiene-selftest.sh` and `tools/gen-feature-list.sh --check-classification` are the gate this
pathspec owes.

## Rollback

Delete the five added sentences. Nothing references them: no check, no case, no rule name and no spec scenario is
added, and the companion §13 is unchanged, so the reversal of this delta is the deletion of the pointer lines alone.

## Alternatives the evidence eliminated

- **A rule and a declared case** ("a rule that exists because a decision was reversed names the record"): the item
  records this as rejected, and the reason is measurable — the probe has to name the five sites by hand because the
  text does not say which rule came from a reversal. A case would encode that hand-written list as a permanent guard
  over judgement, which is the fifth rule no case can assert.
- **Restating the reversal inside the rule.** That would put rationale into the normative layer, which
  "A normative document carries no rationale and no procedure" refuses, and it would give §13's record a second home.
- **Editing only `Design.md`.** The reader who needs the pointer is reading `DesignCrossVariant.md` or `MapFollow.md`;
  a pointer in the entry document's maintenance line is the hop the item measured as already present and insufficient.
- **Leaving the four documents to their landed changes.** The 2026-10-31 finding deferred the fix because those
  documents belonged to changes that had landed. Measured today, the five rules live in two documents and the pointer
  is a five-line addition, so the objection no longer holds.
- **Pointing all nine reversals §13 records.** That is the wider claim the first round of this change made and the
  review refused (`revision 2`): the item named five rules, so the honest scope is five pointers plus the residual
  list, and the two rules `MapRendering.md` §1b and `UIPhoneMap.md` §124 carry would need their own probe rows.

## Open decisions

none
