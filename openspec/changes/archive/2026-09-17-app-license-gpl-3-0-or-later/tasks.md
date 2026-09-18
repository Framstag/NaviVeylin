# Tasks

## 1. License text and repository documents

- [x] 1.1 Replace `LICENSE` with the canonical GNU GPL version 3 text (copied from `licenses/texts/GPL-3.0-or-later.txt` after 2.1) plus an SPDX header declaring `GPL-3.0-or-later` with "or (at your option) any later version" semantics; verify the file contains the full GPLv3 text and the SPDX identifier line (`build-sbom` "SBOM root component carries the application license")
- [x] 1.2 Update `README.md` §License: replace "[License information TBD]" with a GPL-3.0-or-later statement (SPDX id, short link to LICENSE); verify the section renders the new text
- [x] 1.3 Update `AGENTS.md` project facts with the application license (GPL-3.0-or-later) and note that first-party code no longer resolves through a LicenseRef; verify the relevant section mentions the license

## 2. Policy and text data

- [x] 2.1 Add `licenses/texts/GPL-3.0-or-later.txt` with the canonical GNU GPL v3 text; verify it matches the FSF-published text (e.g. byte-compare with the official GPLv3 document)
- [x] 2.2 Edit `licenses/license-policy.json`: remove the `LicenseRef-NaviVeylin` entry from `licenseRefs`; add `GPL-3.0-or-later` to `permitted.shipped`; add `textSources["GPL-3.0-or-later"] = { kind: "file", path: "GPL-3.0-or-later.txt" }`; add `GPL-3.0-or-later` to `noticeRequired`; update the description paragraph (first-party license decided); verify `jq` on the file: `permitted.shipped` contains `GPL-3.0-or-later`, `licenseRefs` has no `LicenseRef-NaviVeylin` key (`build-sbom` "First-party components resolve to the application license")
- [x] 2.3 Edit `licenses/license-policy.json` `reviewRequired`: remove the `LicenseRef-NaviVeylin` entry; reword the libosmscout entry's note/caveat to describe the current 21-line LGPL + app-store-exceptions LICENSE (upstream commit f4a9dabe7, 2026-03-06, no GPL text) and keep the `LGPL-2.1-or-later` mapping; verify the file no longer contains the stale GPLv2-text claim

## 3. Build wiring

- [x] 3.1 Flip `FIRST_PARTY_LICENSE_REF` in `app/build.gradle.kts` (~line 663) from `LicenseRef-NaviVeylin` to `"GPL-3.0-or-later"`; verify no other `LicenseRef-NaviVeylin` reference remains in the file
- [x] 3.2 Stamp the SBOM root component (`metadata.component`) with the same `FIRST_PARTY_LICENSE_REF` value in the per-variant SBOM merge/enrichment task (single source of truth, per design D4); verify the generated `bom.json` root carries `licenses` with the SPDX id while remaining valid CycloneDX (`build-sbom` "SBOM root component carries the application license")

## 4. Tests

- [x] 4.1 Update buildSrc licensing tests (`LicenseResolverTest`, `LicenseGateTest`, `LicenseDataTest`) that reference `LicenseRef-NaviVeylin` to use `GPL-3.0-or-later` (permitted-shipped pass, missing-permitted fail, text resolution, reviewRequired refresh); verify `./gradlew -p buildSrc test` passes
- [x] 4.2 Add/adjust a gate test asserting `GPL-3.0-or-later` first-party components pass once in `permitted.shipped` and fail when absent; verify the new case fails before the policy edit and passes after (red/green evidence)

## 5. App-side verification

- [x] 5.1 Verify the license inventory assets for both flavors regenerate with first-party entries carrying identifier `GPL-3.0-or-later` and a `textFile` (`GPL-3.0-or-later.txt`) instead of `licenseUrl: LICENSE`; run `:app:generateLicenseAssetsMobileRelease` and `:app:generateLicenseAssetsAutomotiveRelease` and inspect `build/generated/assets-licenses/*/licenses/dependencies.json`
- [x] 5.2 Verify `./gradlew :app:checkLicensePolicy` passes for both flavors (gate green with the new policy); also run once before the policy edits to confirm the expected failure is gone only after 2.2

## 6. Docs and guidelines

- [x] 6.1 Update `guidelines/Build.md` §8/§9: root component license, application license decision, resolved `reviewRequired` entry, refreshed libosmscout caveat (LGPL text, no GPLv2), note that released older APKs keep the old caveat; verify the sections describe the new state
- [x] 6.2 Update `README.md`/`AGENTS.md` cross-references to the license (if any) and confirm no doc still says "License information TBD"

## 7. Full build verification

- [x] 7.1 Run `./gradlew -p buildSrc test :app:testMobileDebugUnitTest :app:checkLicensePolicy :app:generateLicenseAssetsMobileRelease :app:generateLicenseAssetsAutomotiveRelease :app:bundleMobileRelease` (or the build-app skill) and verify the build compiles without errors, all unit tests pass, the gate is green, and the SBOM root + in-app inventory reflect `GPL-3.0-or-later`
- [x] 7.2 On-device check (phone/emulator): open About → license list → application entry shows GPL name + SPDX id, open it and confirm the full GPLv3 text displays with airplane mode on (offline, `about-dialog` "About dialog shows the application's own license with full text"); verify both flavors conceptually share the same generated data (7.1 artifacts) — **verified on-device 2026-09-17**: the application entry shows GPL-3.0-or-later (name + SPDX id), the full GPLv3 text opens and displays in airplane mode (offline), and both flavors share the same generated data (5.1/7.1 artifacts)
- [x] 7.3 Verify `git grep LicenseRef-NaviVeylin` returns nothing (code, policy, tests, docs) and `bom.json` components for `NaviVeylin`/`auto`/`core`/`osmscout-client-java` carry `GPL-3.0-or-later`

## 8. Gate warning cleanup — stale caveat, decision completion, scoped reviewRequired

Follows from the post-change analysis: `checkLicensePolicyMobileRelease` still emitted 11 warnings — four were the decoder dropping the `reviewRequired[...].components` field, five were "needs the application license decision" (decision complete after D1), one was the libosmscout map caveat still claiming the submodule LICENSE contains GNU GPL v2 text (task 2.3 reworded the policy note, but the gate prints the map caveat). The five honest evidence caveats (dirent, libiconv, liblzma, pthread, pthreads) stay as-is (verified: those ports install no copyright file / declare no license field).

- [x] 8.1 Reword the libosmscout `caveat` in `licenses/native-license-map.json` (D5): submodule LICENSE is a 21-line LGPL + app-store-exceptions document (upstream commit f4a9dabe7, no GPL text), README states LGPL, no version clause → `LGPL-2.1-or-later` conservative; application license decision made (GPL-3.0-or-later); re-confirm mapping on submodule bump; drop "must be confirmed by the owner of the application license decision". Verify `./gradlew :app:checkLicensePolicyMobileRelease` output for libosmscout no longer contains "GNU GPL v2 text" or "must be confirmed by the owner" and the regenerated `dependencies.json` `note` matches (build-sbom, about-dialog)
- [x] 8.2 Remove the `LGPL-2.1-or-later` entry from `licenses/license-policy.json` `reviewRequired` (D6) and update the description paragraph if it implies the decision is pending. Verify `jq '.reviewRequired | length' licenses/license-policy.json` prints 0 and the generated NOTICE carries no `[REVIEW]` marker (build-sbom)
- [x] 8.3 Decoder + gate honor the declared `components` scoping (D7): `LicenseData.kt` parses `reviewRequired` entries keeping their component set (empty/missing = all components, today's behavior), `Licensing.kt` gate and `LicenseData.kt` NOTICE `[REVIEW]` check warn only listed components; update `LicensePolicy.reviewRequired` shape and the two `LicenseGateTest` fixtures (lines ~22, ~170). Verify `./gradlew -p buildSrc test` passes (build-sbom)
- [x] 8.4 Add regression tests (red/green): a scoped `reviewRequired` entry warns only the named component, an unscoped entry warns every carrier, an empty `reviewRequired` yields zero "needs the application license decision" warnings (LicenseGateTest/LicenseDataTest). Verify the new cases fail before 8.3 and pass after (build-sbom)
- [x] 8.5 Re-verify both flavors end to end: `./gradlew :app:checkLicensePolicyMobileRelease :app:checkLicensePolicyAutomotiveRelease :app:generateLicenseAssetsMobileRelease :app:generateLicenseAssetsAutomotiveRelease`. Verify the gate passes with only the six honest caveat warnings (dirent, libiconv, liblzma, pthread, pthreads, libosmscout) and zero "needs the application license decision" lines, all violations remain empty (build-sbom, about-dialog)
- [x] 8.6 Update `guidelines/Build.md` §8/§9 and `AGENTS.md` for: `reviewRequired` empty (mechanism retained, decision complete), libosmscout caveat corrected, reviewRequired scoping honored. Verify `git grep -n "GNU GPL v2 text\|must be confirmed by the owner" licenses/ guidelines/ AGENTS.md buildSrc/src/test` returns nothing (build-sbom)
