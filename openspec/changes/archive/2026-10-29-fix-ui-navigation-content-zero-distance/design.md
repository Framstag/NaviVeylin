# Design — fix-ui-navigation-content-zero-distance

## Root cause

The NAVIGATION content bullet in `guidelines/UI.md` §1 enumerates the fields of the notification contract but
never gained the zero-versus-unknown distance rule. The requirement that distinguishes the two facts shipped in
`2026-10-28-fix-zero-distance-reads-zero-not-unknown` (`openspec/specs/navigation-ongoing-notification/spec.md`
— *A known zero distance is not an unknown distance*), and the guideline that `AGENTS.md` routes a
notification-content change to still lists the field without the rule.

## The single fix

Append one clause to the existing bullet (`guidelines/UI.md:231`–`:236`), in the wording the shipped requirement
uses:

> The distance-to-turn renders a **known zero** as `0 m`, while a distance that is absent or `NaN` renders
> `--` — the phone notification's stats line and the car hint's secondary text follow the same rule (spec
> `navigation-ongoing-notification` — A known zero distance is not an unknown distance).

The clause is added to the content bullet rather than as a second bullet or a new table row: the bullet already
names the distance-to-turn, the phone stats line and the car hint's secondary text, so the rule sits beside the
fields it governs and a reader of the route sees both. It states a rule for those two surfaces rather than
repeating the requirement's rationale, its `remainingDistance` field name or its implementation note ("one
formatter produces both"), so the guideline adds the reader-facing rendering rule and keeps the contract as the
normative home.

## Blast radius

- **`guidelines/UI.md` only**, one bullet. No section number, route row, parity-table row or glyph-table row
  changes, so `AGENTS.md` and `tools/check-doc-routes.sh` need no edit.
- **The change's markdown and `openspec/**` paths pull the project's documentation suites** (`tools/loop/loop.conf`,
  `LOOP_GATE_SUITES`: `*.md` → `check-doc-routes.sh`, its self-test and mutation list, `check-todo-references.sh`;
  `openspec/*` → `check-doc-routes.sh`, `check-openspec-hygiene.sh`, `gen-feature-list.sh
  --check-classification`). No compiled path is touched, so no Gradle task is owed.
- **The shipped Gradle case and spec scenario are unchanged** — the change only makes the guideline restate the
  rule they already assert. The evidence probe is the red/green case for the documentation delta and lives under
  the change's `evidence/`, not in the module's test source.

## Alternatives the evidence eliminated

- **Add a generic `documentation-ownership` requirement ("a restatement of a value rule names both states")** —
  eliminated: it would create a new normative rule for a single missing sentence, duplicate the contract's
  concern and violate the artifact guidance's "do not invent a requirement just to satisfy validation". The
  existing *A rule has one normative home* and *Keeping this document honest* already govern the edit.
- **Add a separate bullet below the content bullet** — eliminated: the issue asks for one clause in the
  NAVIGATION content bullet, and a separate bullet separates the rule from the fields it governs.
- **Restate the requirement wholesale (including `remainingDistance` and the formatter mechanism)** — eliminated
  by `guidelines/UI.md` "Keeping this document honest": the guideline is the condensed knowledge base, so it
  states the rendered values and surfaces and cites the owning requirement instead of copying its normative text.

## Rollback

Revert the commit. The clause disappears from `guidelines/UI.md`, the probe's single case reports the missing
tokens again (`tests=1 failures=1`), and the shipped requirement, test case and spec are untouched either way.

## Verification shape

The change touches no production invariant and adds no guard test, so no build or revert-check is owed
(`revert-check` skill, "When NOT to use": pure docs/spec/tooling changes with no production invariant). Its
red case is `evidence/red-probe.sh` on HEAD and its green case is the same probe after the edit, followed by the
documentation suites the project's gate runs for `*.md` and `openspec/**`.
