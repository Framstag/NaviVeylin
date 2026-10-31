# Regulatory.recipes.md — the source list, the checklists and the review commands

Companion to `Regulatory.md`. Every `## §N` anchor names a section there; read it beside the
section its anchor names. Nothing here binds the app — the named rules are in `Regulatory.md`.

## §10 Review cadence and sources — primary sources

Primary sources used for this revision: `Directive (EU) 2024/2853`; `Regulation (EU)
2024/2847`; `Directive (EU) 2019/882`; `Regulation (EU) 2019/2144` with `2021/1958`,
`2023/2590`, `2024/1721`; UN R155/R156/R183 texts; EU Data Act `2023/2854` and the
vehicle-data guidance `OJ C/2025/5026`; Commission Recommendation `2008/653/EC`;
ISO 15008 / ISO 15007-1; NHTSA Phase 1 (2013) and Phase 2 (2016 proposal); GDPR +
ePrivacy `2002/58`; India DPDP Act 2023 + DPDP Rules 2025 (G.S.R. 846(E)); DST
Geospatial Guidelines 2021; China 测绘法 / PIPL / MNR notice 2024 / MIIT app filing
notice 2023 / automotive data-export guidelines 2026; Korea MOLIT map-export decision
(2026-02-27); Conseil constitutionnel 2021-948 QPC; C. route L. 130-11/130-12,
R. 413-15, L. 413-2; Art. 98a SVG; § 23 StVO; Google Play Developer Program Policies
and Play Console Help.

## §2 Publisher duties — the licence checklist

Keep the licence gate green; extend the curated inputs when a dependency changes. The machinery is
`licenses/native-license-map.json`, `licenses/license-policy.json`, the task group `license` and
`checkLicensePolicy` (`Build.md` §9).

- NaviVeylin code — GPL-3.0-or-later: complete corresponding source offer, licence + copyright
  notices, no additional restrictions.
- OSM-derived map data in the APK — ODbL 1.0: "© OpenStreetMap contributors" attribution;
  share-alike on a derived database.
- libosmscout (submodule) — LGPL: relink/replacement ability, notice.
- Cairo/Pango/harfbuzz/fribidi/protobuf/vcpkg deps — see `licenses/`: attribution + notices.

## §6 Platform contracts — the review checklist

- Re-check the Play "Permissions and APIs that Access Sensitive Information" page and the
  2026-04-15 location announcement, and confirm the §6 no-change findings still hold.
- Confirm `app/src/main/res/xml/network_security_config.xml` still permits exactly the cleartext
  transport the app needs, and that `checkCleartextTransportPolicy` still asserts it.
- Confirm the manifest still declares only `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, with
  no `ACCESS_BACKGROUND_LOCATION`, and that both foreground services still carry a justified type.

## §9 Repo inventory and open items — the verification recipe

- A source-level search for a coordinate in a diagnostics line uses the bare grep
  `[0-9]{1,3}\.[0-9]{4,}`. That grep is deliberately *stricter* than the rule: a hit that names a
  magnification or another identity value (a raw `Double` can print at that precision) is an
  identity line, not a regression.
- To read a follow/surface diagnosis without a position, use `frameMag`/`frameAng`,
  `pendingMag`/`pendingAng`, `dMag`/`dAng`, the clamped pixel offset (`off=`, `clamped=`) and the
  render/blit counters — the placement rule itself is unchanged.
