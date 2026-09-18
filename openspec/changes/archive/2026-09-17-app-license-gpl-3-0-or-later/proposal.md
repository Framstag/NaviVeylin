# Proposal: app-license-gpl-3-0-or-later

## Why

The application's own source is distributed without a settled license:
`README.md` §License says "License information TBD" while the repository `LICENSE`
file contains the bare GNU GPL v2 text (June 1991) without an "or later" clause
and no SPDX identifier anywhere. Distributing unlicensed code grants no rights to
redistributors, and the ambiguous GPLv2 text is not actually applicable to the
project. All third-party shipped licenses are GPLv3-compatible (Apache-2.0,
MPL-1.1, LGPL-2.1-or-later, FTL, BSD, MIT, Zlib — verified against the mobile
Release SBOM: 160 components), so placing the application under
`GPL-3.0-or-later` resolves the unlicensed state with zero dependency conflicts.
GPL-2.0-only would have been a conflict: 123 Apache-2.0 JVM components are
incompatible with GPLv2.

This decision is deliberately what `license-compliance-baseline` (in flight) kept
out of scope ("Deciding the application's own license"); that change built the
evidence this decision now consumes. Its `reviewRequired` entries
(`LicenseRef-NaviVeylin`, libosmscout LGPL) are resolved or refreshed here.

## What Changes

- **`LICENSE`**: replace the bare GPLv2 text with the GNU GPL version 3 text plus
  an SPDX header declaring `GPL-3.0-or-later`.
- **`README.md` §License**: replace "[License information TBD]" with the
  GPL-3.0-or-later statement.
- **`licenses/license-policy.json`**:
  - `licenseRefs`: remove the `LicenseRef-NaviVeylin` declaration; first-party
    components now resolve to the SPDX identifier `GPL-3.0-or-later`.
  - `permitted.shipped`: add `GPL-3.0-or-later` (the application itself is a
    shipped component; the gate evaluates it like any other).
  - `textSources`: add `GPL-3.0-or-later` → `{ kind: file, path: GPL-3.0-or-later.txt }`.
  - `noticeRequired`: add `GPL-3.0-or-later` — GPL §5 requires that a copy of the
    license accompany distribution, so the text must be embedded, not linked.
  - `reviewRequired`: remove the `LicenseRef-NaviVeylin` entry (first-party now
    resolves to an SPDX identifier) **and** the `LGPL-2.1-or-later` entry — the
    application license decision (GPL-3.0-or-later, D1) covers the verified
    GPLv3-compatible shipped set, so the gate message "needs the application
    license decision before distribution" is false for every current component;
    the standing re-confirm-on-bump duty for the version-less libosmscout
    mapping moves to the map caveat below (the gate reports caveats
    independently).
- **`licenses/native-license-map.json`**: reword the libosmscout `caveat` — the
  stale "submodule LICENSE contains GNU GPL v2 text" / "must be confirmed by the
  owner of the application license decision" claim is the text the gate prints
  and the inventory `note` shows in-app; task 2.3 reworded the policy `note` but
  the gate emits the map `caveat`, which was never touched. The caveat becomes:
  LICENSE is a 21-line LGPL + app-store-exceptions document (upstream commit
  f4a9dabe7, 2026-03, no GPL text), README states LGPL, no version clause →
  `LGPL-2.1-or-later` conservative; application license decision made
  (GPL-3.0-or-later); re-confirm the mapping when the submodule is bumped.
- **buildSrc gate scoping**: `reviewRequired` entries declare a `components`
  list but the decoder drops the field, leaking one "needs the application license
  decision" warning per component carrying the license (fribidi, gettext-libintl,
  glib, libiconv — all build-time-only — plus libosmscout). The decoder and gate
  honor the declared scoping (an entry without `components` keeps today's
  applies-to-all behavior). Latent-bug fix: with D6 the set is empty, this guards
  a future scoped entry from re-creating the leak.
- **`licenses/texts/GPL-3.0-or-later.txt`** (new): canonical GNU GPL v3 text.
- **`app/build.gradle.kts`**: `FIRST_PARTY_LICENSE_REF` constant flips from
  `LicenseRef-NaviVeylin` to `GPL-3.0-or-later`; the SBOM root
  (`metadata.component`) gains the application license (currently absent).
- **buildSrc tests**: update `LicenseResolverTest` / `LicenseGateTest` /
  `LicenseDataTest` fixtures that reference `LicenseRef-NaviVeylin`.
- **Guidelines/documentation**: `guidelines/Build.md` §8/§9 (root license, resolved
  reviewRequired, refreshed libosmscout caveat), `AGENTS.md` (project license
  fact), `README.md`.

Additive/breaking: this is **additive** for distribution (it grants rights that
were unclear) but changes the licensing contract of every first-party module —
treated as **BREAKING** for anyone relying on the previous ambiguous state.
Rollback: revert the commit; LICENSE/policy return to the TBD baseline. No
dependency, native/JNI, or submodule changes — no submodule patch.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-sbom`: the merged SBOM's root component SHALL carry the application's
  SPDX license identifier; first-party components SHALL resolve to the same SPDX
  identifier instead of a `LicenseRef-` — the SBOM becomes truthful about the
  application's own license, which it currently omits (root has none) or models
  as `LicenseRef-NaviVeylin`.
- `about-dialog`: the bundled license list SHALL include the application's own
  license entry with its SPDX identifier and the **full license text** reachable
  offline (GPL §5 requires the text to accompany the program; today first-party
  entries link to the LICENSE file instead of embedding text).

## Impact

Affected files and modules:

- `LICENSE` — replaced content.
- `README.md` — §License paragraph.
- `licenses/license-policy.json` — licenseRefs/permitted/textSources/
  noticeRequired/reviewRequired sections (reviewRequired empties).
- `licenses/native-license-map.json` — libosmscout caveat reworded (new).
- `licenses/texts/GPL-3.0-or-later.txt` — new canonical text.
- `app/build.gradle.kts` — `FIRST_PARTY_LICENSE_REF` (~line 663), SBOM license
  wiring in the bom task section (~lines 370–520).
- `buildSrc/src/main/kotlin/com/naviveylin/build/licensing/` — decoder keeps
  the already-declared `components` scoping of `reviewRequired` (no new policy
  surface, no semantic change to the gate); `buildSrc/src/test/.../licensing/*Test.kt`
  — fixture updates plus a scoping regression test.
- `guidelines/Build.md` §8 (SBOM) / §9 (license data) — updated descriptions.
- `AGENTS.md` — license fact.
- No change: `app/src/main/java/.../LicensesScreen.kt`, `AboutDialog.kt` (the
  screen is data-driven; the new license flows through generated assets).

Generated artifacts that change (not committed): each flavor's
`licenses/dependencies.json` (first-party entries: identifier
`GPL-3.0-or-later`, embedded text file), `licenses/texts/*`, and the SBOM
root `metadata.component.licenses`.

Scope: phone/Android Auto and Android Automotive OS alike (both flavors share the
same license decision); the AA about screen keeps its current identity/OSM rows
and is unchanged.

Relationship to `license-compliance-baseline`: this change fulfills the decision
that change deliberately left open; the two are independent and both apply to the
pending `license-compliance` capability.

Guidelines affected:
- `guidelines/Build.md` §8/§9 — SBOM root license and resolved reviewRequired.
- No changes to `guidelines/Design.md` or `guidelines/UI.md`.
