# fix-loop-harness-first — design

Three decisions. Each names the alternatives the evidence eliminated and why.

## D1 — Where the priority order lives: a profile role, not the engine

The loop's engine (`tools/loop/**`) may name no project value (`tools/check-portability.sh`, `PORTING.md`); every
decision about a vocabulary value is a role in `tools/tooling.json` → `roles`, read through `loop_role`
(`tools/loop/lib.sh:134`). The order is exactly such a decision.

| alternative | why not |
|---|---|
| a category list spelled in `prescreen.sh` | the portability gate forbids it by construction: `build-and-harness` is a `category` value, and `check-portability.sh:172` derives its forbidden set from the profile it is checking — the engine would be red against a foreign project, which is the defect this repository's `tools/check-portability.sh` exists to catch |
| rank by the existing `prio/*` band | the loop never reads `prio/*` today (only `tools/issues/prioritise.sh` writes it), most open harness items carry no band at all, and mixing two orderings in one key makes neither falsifiable: a case could pass on the band and say nothing about the category |
| a new `priorityCategories` **mapping** (category → weight) | more expressive than the requirement; a weight is a number nobody can defend for two categories, and the profile then needs a rule for an unlisted category. The chosen shape has one: unlisted means "after the listed ones" |
| **chosen: `roles.firstCategories`, a list** | one declared value per profile, read through the existing `loop_role` path, empty means "no priority", and `check-portability.sh` picks it up with no change of its own because it derives its value set from `roles` |

## D2 — The key's position: before the class, not after it

| alternative | why not |
|---|---|
| `[class, category, issue]` — a harness item leads only inside its class | this is the conservative reading, and it was rejected on the directive it implements: the owner asked that harness **bugs and then improvements** come first, and under this key a harness improvement still sits behind every app bug — the **15** `improvement/build-and-harness` items the tracker's queue holds on 2026-10-19 would be starved by app bugs that no tooling defect blocks |
| `[category, class, issue]` with the class compared first and the category as a tiebreak | identical to the alternative above by another spelling (the stable sort applies the first key first); it is listed separately because it is the shape a reader is most likely to write by mistake |
| **chosen: `[category leads, class order, issue]`** | a harness bug, then a harness improvement, then an app bug, then an app improvement, oldest first inside each group. It keeps the class order that `prescreen#ranksBugsFirstAndDropsFeatures` already asserts, because that case's fixture carries no priority category — the class key is added *before* nothing, it stays the second key |

Consequence, stated rather than hidden: with the harness pile drained, the queue's head becomes app bugs again
with no further change, because the category key only ever separates the listed categories from the rest.

## D3 — Which categories lead

| alternative | why not |
|---|---|
| `build-and-harness` only | a defect in `category/verification` (#130's device suites bound to a dataset the reference AVD lacks, #132's four open verification tasks) blocks the *proof* of every later item, which is the same compounding as a broken script, only one step removed |
| `build-and-harness` + `verification` | closer, and rejected only because the third category has the same shape: #194 — open when this change was written, since `settled` in the tracker — was a `specs-and-process` defect ("there is no way to answer a decision … the loop's own cap refuses every run until the pile is drained") that stopped **every** run, and it would still have queued behind app defects |
| **chosen: `build-and-harness`, `verification`, `specs-and-process`** | the three categories whose items are the tooling, the process and the proof rather than the shipped app. The boundary is stated positively — an item whose defect is repaired *outside* `app/`, `auto/`, `core/`, `osmscout-client-java/` and `app/src/main/cpp/` — so a future category needs one line in the profile, not a debate about severity |

## Component, threading and lifecycle

No new component: the change adds one `jq` key to an existing expression in `prescreen.sh`, which is a
short-lived process the wrapper starts before any child. No dispatcher, no `StateFlow`, no lifecycle — the sort
runs once per run over the records the pre-screen already built in memory. `loop_role` is read after
`loop_roles_load`, which is already called before the sort, so no new ordering constraint is introduced.

## Verification

- Shell gate `tools/loop-selftest.sh` (the loop's own suite, 85 cases after the review round's two additions) and
  `tools/check-portability.sh` (**42** engines and 2 templates, the foreign fixture): the portability check proves
  no engine spells a profile value; that the leading list is *read* is proven by
  `prescreen#aRenamedLeadingCategoryLeads`, which leads on a value this project never names.
- The three cases named in the proposal, the red one first with its HEAD output recorded.
- Mutation 22.1 — drop the category key from the sort — must fail `prescreen#harnessItemsLeadTheQueue`, and
  `prescreen#aRenamedLeadingCategoryLeads` with it, because both assert the same key from different inputs
  (`prescreen#ranksBugsFirstAndDropsFeatures` must stay green: it carries no leading category). Its restore must
  be green (`.pi/skills/revert-check`). Both red lines and the byte-exact restore are in
  `evidence/revert-check.txt`, because the runner binds one case per mutation.
- A `--dry-run` of `tools/loop/run.sh` against the real tracker, whose printed queue must name a
  `build-and-harness` item at rank 1. Measured 2026-10-19 (19:14:16Z..19:15:14Z, 58 s, 54 eligible / 39 excluded):
  `#126`, `#189`, `#190`, `#198` at ranks 1–4, the oldest app bug `#131` at rank 37 and the app improvement `#7` at
  rank 38 — 19 eligible `build-and-harness` items in all, all of them above every app item — while no label is
  written and nothing is created.
- No device verification: nothing here reaches a device, a pixel or a rendered surface.
