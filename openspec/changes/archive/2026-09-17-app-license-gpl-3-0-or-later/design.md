# Design: app-license-gpl-3-0-or-later

## Context

See `proposal.md` — Why. Constraints that shape the approach:

- First-party components today resolve to a hard-coded `FIRST_PARTY_LICENSE_REF =
  "LicenseRef-NaviVeylin"` (`app/build.gradle.kts` ~line 663), declared in
  `licenses/license-policy.json` `licenseRefs` with `textDistributed: false` (the
  license screen links to `LICENSE`). The SBOM root component
  (`metadata.component`, the application itself) carries **no license field**.
- The gate (`com.naviveylin.build.licensing`, data-driven buildSrc): a
  `LicenseRef-` identifier passes if declared in the policy; an SPDX identifier
  must appear in `permitted.shipped` (or `permitted.buildTimeOnly`) for its
  scope, otherwise the gate fails.
- Texts catalogue: each distributed license's full text is resolved from
  `textSources` declarations (`licenses/texts/<id>.txt` for `kind: file`) and
  gated by `noticeRequired`; `LicenseRef-NaviVeylin` deliberately has no text.
- GPL §5: "give all recipients a copy of this License along with the Program" —
  a link is not sufficient; the full GPLv3 text must be distributed (embedded in
  the app's license inventory) for both flavors.

## Goals / Non-Goals

**Goals:**
- First-party code carries a settled, machine-verifiable SPDX license
  (`GPL-3.0-or-later`) end to end: LICENSE, policy, SBOM root, SBOM components,
  in-app inventory.
- GPLv3 text is embedded in the distributed license inventory (offline, per
  `about-dialog`).
- The gate's `reviewRequired` list empties (first-party decision made;
  LGPL-2.1-or-later covered by the GPLv3-compatible shipped set) and the stale
  libosmscout caveat (GPLv2-text claim no longer true upstream) is reworded in
  `native-license-map.json`, where the gate actually prints it.

**Non-Goals:**
- Re-licensing third-party dependencies or changing elections
  (cairo/MPL-1.1, freetype/FTL, marisa-trie/BSD-2-Clause stay as-is — all
  GPLv3-compatible, see proposal).
- Bulk SPDX headers on every first-party source file (documented deviation:
  LICENSE + docs carry the identifier; per-file headers are optional follow-up
  and do not affect the specs).
- Changing the gate engine's semantics or adding a new policy concept: D7 only
  honors the per-component scoping already declared in `reviewRequired` entries —
  no new policy surface, no SBOM pipeline shape change, AA about screen
  unchanged.

## Decisions

### D1 — First-party license becomes the SPDX identifier, LicenseRef retires

Chosen: flip `FIRST_PARTY_LICENSE_REF` to `"GPL-3.0-or-later"` and remove the
`LicenseRef-NaviVeylin` declaration from `license-policy.json` `licenseRefs`.
First-party components then resolve through the ordinary SPDX path (same code
path third-party components use), and the SBOM/inventory report the real
identifier.

- Alternatives: (a) keep `LicenseRef-NaviVeylin` forever and only change its
  `source`/name — rejected: the SBOM and inventory would keep hiding the actual
  license behind a placeholder, the root component would remain unlicensed, and
  `reviewRequired` would stay permanently populated; (b) exempt first-party
  components from the gate — rejected: contradicts the baseline policy intent
  ("first-party code follows the same rules") and creates a second inventory
  class for no benefit.
- Consequence: `GPL-3.0-or-later` MUST be added to `permitted.shipped` (D2),
  or the gate fails first-party components as outside the policy.

### D2 — Add `GPL-3.0-or-later` to `permitted.shipped`

Chosen: extend `permitted.shipped` with `GPL-3.0-or-later`, with the policy
description noting the application itself is now a shipped component under this
identifier. The gate engine and permitted/build-time split stay untouched.

- Alternatives: (a) exempt first-party from scope checks — rejected (D1);
  (b) a separate `permitted.firstParty` list — rejected: new policy surface for
  one identifier with no added safety, and drifts away from "one rule for all".
- This is the single data change that keeps the gate green after D1.

### D3 — Embed the GPLv3 text; `textDistributed` flips to full text

Chosen: add `licenses/texts/GPL-3.0-or-later.txt` (canonical GNU GPL v3 text),
declare `textSources["GPL-3.0-or-later"] = { kind: file, path: GPL-3.0-or-later.txt }`,
add `GPL-3.0-or-later` to `noticeRequired`, and delete the `LicenseRef-NaviVeylin`
entry (whose `textDistributed: false` linked to LICENSE instead). The license
screen then renders the app entry with full text offline, satisfying GPL §5
(requirements: `build-sbom` "First-party components resolve to the application
license", `about-dialog` "About dialog shows the application's own license").

- Alternatives: (a) keep link-only distribution — rejected: GPL §5 requires a
  copy of the license with the program; (b) resolve the text from an external
  URL at build time — rejected: deterministic-build convention
  (`guidelines/Design.md` §10), and the text is stable public data that belongs
  in the repository.
- `LICENSE` is replaced by the same GPLv3 text with an SPDX header declaring
  `GPL-3.0-or-later`; the file and the asset both source from the canonical
  `licenses/texts/GPL-3.0-or-later.txt` content (copy verified by a gate or
  task 7 check).

### D4 — SBOM root component gets the license from the same constant

Chosen: stamp the merged SBOM's root `metadata.component` license from the same
`FIRST_PARTY_LICENSE_REF` value the components use, in the merge/enrichment step
of the existing per-variant SBOM task — one source of truth, no second
configuration to drift.

- Alternatives: (a) configure the `org.cyclonedx.bom` plugin's root/license
  metadata — rejected: the plugin path does not currently emit a root license
  for this project shape (root today has none) and its metadata API varies
  across plugin versions; (b) leave the root unlicensed — rejected: spec
  `build-sbom` "SBOM root component carries the application license" fails.
- Post-processing the already-produced root object is additive: the SBOM remains
  valid CycloneDX (validated by the existing schema-validation requirement).

### D5 — Refresh the stale libosmscout caveat where the gate prints it

Chosen: the gate emits warnings from two places that duplicated the stale
"LICENSE file contains the GNU GPL v2 text / must be confirmed by the owner of
the application license decision" claim — the `reviewRequired` note in
`license-policy.json` and the `caveat` in `native-license-map.json`. Task 2.3
reworded the policy note; the gate prints the **map caveat** (Licensing.kt emits
`component.caveat`), which was never touched and still flows into the SBOM
`naviveylin:license:caveat` property and the in-app inventory `note`.

Chosen: reword the **map caveat** to describe the verified current state — the
submodule LICENSE is a 21-line LGPL + app-store-exceptions document (upstream
since 2026-03, commit f4a9dabe7, no GPL text), README.md states LGPL, no source
file carries a version clause → `LGPL-2.1-or-later` remains the conservative
mapping — keep the standing duty (re-confirm the mapping when the submodule is
bumped) and drop the stale "application license decision still owed" claim
(superseded by D6).

- Alternatives: (a) change the mapping to `LGPL-2.1-only` — rejected: no
  upstream source supports pinning to 2.1-only; (b) chase upstream for a
  versioned statement — Open Questions, not blocking.
- Lesson of task 2.3: the claim lived in two files; reword both together and
  verify the gate's emitted text, not just the JSON files.

### D6 — `reviewRequired` empties; the application license decision is complete

Chosen: remove the `LGPL-2.1-or-later` entry from `license-policy.json`
`reviewRequired`. The application license decision is made (D1:
GPL-3.0-or-later) and the proposal verified every shipped third-party license is
GPLv3-compatible with LGPL-2.1-or-later in `permitted.shipped` — the gate
message "needs the application license decision before distribution" is no longer
true for any component and today fires five times (fribidi, gettext-libintl,
glib, libiconv — all build-time-only — plus libosmscout). The residual duty
(re-confirm the version-less LGPL mapping against the submodule) is an evidence
duty, carried by the D5 caveat, which the gate surfaces independently.

- Alternatives: (a) keep the entry scoped to libosmscout (D7) — rejected: the
  entry's documented semantics ("needs the application license decision")
  contradict the completed decision; it would print a false "decision owed"
  warning on every build; (b) keep the entry and reword the gate message to
  "evidence re-review due" — rejected: conflates two mechanisms — caveats already
  report evidence duties while `reviewRequired` is documented as the decision
  gate.
- Consequence: with the entry removed the gate stops warning "needs the
  application license decision" entirely; the mechanism stays in code for a
  license that genuinely needs the decision later.

### D7 — `reviewRequired` honors its declared `components` field

Chosen: the decoder collapses the policy's `reviewRequired` entries
(`{license, components, note}`) into a bare `Set<String>` of licenses
(`LicenseData.kt`), so the gate and the NOTICE `[REVIEW]` marker warn **every**
component carrying the license — the curated `components: ["libosmscout"]`
scoping is silently dead. Change the parsed shape to keep the component set per
entry; gate and NOTICE report only listed components, and an entry without a
`components` field keeps today's applies-to-all behavior. With D6 the parsed set
is empty, so this is a latent-bug fix: the data shape promises scoping the code
ignores, and it guards a future scoped entry from re-creating the four-warning
leak this analysis found.

- Alternatives: (a) leave the decoder as-is and rely on D6's empty set —
  rejected: the schema still declares per-component scoping; the next license
  added to `reviewRequired` would silently re-leak to every carrier; (b) drop the
  `components` key from the schema — rejected: contradicts the file's documented
  per-component intent and loses the scoping information.

## Risks / Trade-offs

- [Gate fails after D1 if permitted list edit is missed] → D2 is a two-line
  data edit bundled in the same task as D1; task 7 runs `checkLicensePolicy` for
  both flavors before completion.
- [LicenseRef removal breaks buildSrc tests referencing it] → the three affected
  test files are updated in the same change; task 6 owns both.
- [Two canonical copies of GPLv3 text (LICENSE + asset) drift] → both sourced
  from `licenses/texts/GPL-3.0-or-later.txt`; a verification step compares them.
- [GPL-3.0-or-later surprises downstream consumers who read the old bare-GPLv2
  LICENSE] → `README.md` §License and `AGENTS.md` document the decision;
  `LICENSE` header states the SPDX id and "or (at your option) any later
  version" semantics.
- [libosmscout caveat text lives in shipped build output of prior releases] →
  released APKs are immutable; the corrected caveat applies to builds from this
  change onward (documented in `guidelines/Build.md`).
- [D6's removal drops the standing "confirm on bump" memory] → the duty is
  re-homed in the refreshed libosmscout map caveat (D5), which the gate still
  reports and which still travels into the SBOM/inventory note.
- [A future `reviewRequired` entry re-leaks to every carrier] → D7 honors the
  declared `components` scoping; the fribidi/gettext-libintl/glib/libiconv leak
  of this analysis is regression-test-covered (task 8.4).

## Migration Plan

1. Land data + constant flip + root stamping + tests in one change
   (tasks 1–6); LICENSE/README/policy change atomically with the build logic so
   no intermediate commit distributes a mismatched claim.
2. Verify: `./gradlew :app:checkLicensePolicy :app:generateLicenseAssetsMobileRelease
   :app:generateLicenseAssetsAutomotiveRelease` and SBOM generation; inspect the
   regenerated `dependencies.json` first-party entries (SPDX id + textFile) and
   `bom.json` root.
3. Rollback: revert the change commit — LICENSE/policy/constant return to the TBD
   baseline; generated assets and SBOMs rebuild accordingly. No runtime schema
   changes, no data migration on devices.

## Open Questions

- Upstream libosmscout versioned license statement: worth raising with Framstag
  (LGPL 2.1 vs 3 explicit). Does not block this change — the conservative
  `LGPL-2.1-or-later` mapping and the refreshed caveat carry it; if upstream
  answers, the caveat line updates without spec or design change.
