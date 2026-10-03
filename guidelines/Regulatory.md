# Regulatory Guidelines — What Binds NaviVeylin and Its Developer

Legal and regulatory constraints that apply to **this app and its publisher**, as opposed
to the vehicle manufacturer, the car-app host or the OEM channel. Use it when a change
touches location, logging, telemetry, feature scope in a country, map data, or release
distribution.

**Maintenance rule** — when a change alters what the app collects, logs, ships in a
country, or requests from the platform, update this document in the same change.
**Not legal advice**: dates and readings below are researched, not reviewed by counsel;
anything shipped into an OEM/Play car channel gets the partner's compliance team as
gatekeeper anyway.

Status as of **2026-09-22** (see §10 for the review cadence).

---

## 1. Scope

**In scope — binds the app/developer directly:**

- Licence obligations of the code and data the APK ships (GPL-3.0-or-later, ODbL, LGPL).
- Product liability for software (`Directive (EU) 2024/2853`, from 2026-12-09).
- Cyber-resilience duties **if** the app is commercialised (`Regulation (EU) 2024/2847`).
- Consumer/digital-content duties when selling.
- Export control and sanctions (crypto in the app).
- Data protection: the developer is the **controller** (GDPR/ePrivacy, India DPDP,
  China PIPL, US state laws).
- Legality of specific features per country (speed-camera / police-checkpoint warnings).
- Google Play Developer Program Policies and the car-app quality gate (contract, not
  statute, but they gate distribution).

**Out of scope — binds the OEM/host, not the app:**

- UN Regulations R155 (cybersecurity CSMS) / R156 (software update) as vehicle approval.
- EU GSR `2019/2144` + ISA/ADDW delegated and implementing acts (`2021/1958`,
  `2023/2590`, `2024/1721`) as *vehicle* type approval.
- UN R183 (Advanced Driver Distraction Warning) as a vehicle system.
- eCall (`2015/758`), UN R10 (EMC), RED `2014/53/EU` — device/host level.

**Conditional** (becomes the developer's problem only in the listed situation): see §7.

---

## 2. Publisher duties

### 2.1 Licence obligations (always on)

| Component | Licence | Duty |
|---|---|---|
| NaviVeylin code | GPL-3.0-or-later | complete corresponding source offer (§6), licence + copyright notices, no additional restrictions |
| OSM-derived map data in the APK | ODbL 1.0 | "© OpenStreetMap contributors" attribution; share-alike on a derived database |
| libosmscout (submodule) | LGPL | relink/replacement ability, notice |
| Cairo/Pango/harfbuzz/fribidi/protobuf/vcpkg deps | see `licenses/` | attribution + notices |

Kept green by the build's license machinery: `licenses/native-license-map.json`,
`license-policy.json`, task group `license`, `checkLicensePolicy` (see `Build.md` §9).
No new obligation is introduced by the other sections below; keep that gate passing and
extend the curated inputs when a dependency changes.

### 2.2 Product liability — `Directive (EU) 2024/2853` (biggest exposure)

- Software (including apps) is a **product**; liability is **no-fault** for defects.
- Member states transpose by **2026-12-09**; applies to products placed on the market
  after that date.
- Consequences for us: a defective route, a missing/misleading instruction or a wrong
  map state that contributes to damage is compensable without proving negligence.
  Presumptions and a court-ordered evidence-disclosure duty apply to the producer —
  which makes *diagnostics that can prove what the app displayed* valuable, and makes
  *undisclosed data retention* risky.
- Practical stance: keep the "obey signs over the app" disclaimer, keep the diagnostics
  trail (§9), do not advertise accuracy the data cannot support.

### 2.3 Cyber Resilience Act — `Regulation (EU) 2024/2847`

- Applies to products with digital elements placed on the EU market; reporting duties
  from **2026-09-11**, main obligations from **2027-12-11**.
- Free/open-source software developed or supplied **outside the course of a commercial
  activity** is out of scope; a commercialised FOSS product or an "open-source steward"
  carries lighter but real duties. Overlap with sectoral rules (motor vehicles) limits
  or excludes application where equivalent requirements exist.
- If NaviVeylin is monetised, expect: vulnerability handling + coordinated disclosure,
  security updates for a declared support period, SBOM (an SBOM already exists for the
  license gate — reuse it), CE marking and technical documentation.

### 2.4 Consumer and digital-content law

- `Directive (EU) 2019/770` (supply of digital content) and the UCPD: conformity,
  updates, no misleading marketing claims (e.g. speed-limit or ETA accuracy).
- On Play, Google is merchant of record; for the **sideloaded** APK/AAB the duties are
  ours.

### 2.5 Export control and sanctions

- Standard OS TLS / platform crypto is treated as the mass-market case (US EAR ENC
  `740.17(b)(1)`; self-classification reporting for mass-market products was removed in
  2021). It matters if we ever ship **our own** crypto: EU dual-use `2021/821`
  Annex I `5A002`; France LCEN Art. 30 declaration for supplying encryption means.
- Do not distribute to or serve sanctioned regions; keep the distribution channels
  (Play, sideload) as the compliance boundary.

### 2.6 Accessibility — `Directive (EU) 2019/882`

- Applies from **2025-06-28**; Annex I covers consumer general-purpose computers,
  smartphones/tablets and their operating systems, and specific transport services.
- A standalone car-navigation app is not squarely in the product list, so this is
  **marginal** for us — but it becomes relevant if the app is preinstalled or becomes an
  OEM-delivered component. Compose semantics/labels remain good practice regardless.

---

## 3. Personal data — the developer is the controller

Location is personal data. The app collects position fixes, favourites, search history
and diagnostics.

| Regime | Status (2026-09-22) | Duties that concern the app |
|---|---|---|
| **GDPR + ePrivacy Art. 5(3)** | live | lawful basis + transparency for location; on-device storage beyond strictly necessary needs consent; DSAR/erasure; Art. 30 records where applicable; no telemetry beyond the declared purpose |
| **India DPDP Act 2023 + DPDP Rules 2025** | Rules notified 2025-11-13; substantive rules in force **2027-05-13** (consent managers 2026-11-13) | itemised, plain-language consent notice; withdrawal as easy as consent; grievance response ≤ 90 days; published contact; breach intimation "without delay" + 72 h report to the Board; **minimum 1-year retention of logs** then erasure; rights + nomination |
| **China PIPL + automotive data rules + MIIT app filing (2023-07)** | live | separate consent for sensitive PI (location); anonymisation by default in the cabin; no export of "important data"; serving internet information services in mainland China requires an **app filing (APP备案)** by a Chinese entity. Combined with §4 this is effectively a no-go market |
| **US state privacy laws (CCPA/CPRA et al.)** | live | precise-geolocation consent, opt-out, no sale of location |

Diagnostics count as personal data when they contain coordinates — see §9. They no longer do:
no coordinates are logged, the on-device copy is kept at most 7 days, and the export carries the
disclosure (implemented 2026-09-26, change `fix-diagnostics-coordinate-redaction`).

---

## 4. Map data and geodata sovereignty

The app ships OSM-derived data and renders its boundaries verbatim; several jurisdictions
regulate that directly.

| Jurisdiction | Rule | Effect on us |
|---|---|---|
| **China** | 测绘法 — collecting/processing geodata is "surveying and mapping activity"; navigation electronic maps need a 导航电子地图制作 qualification held by a Chinese entity; GCJ-02 offset mandatory; MNR notice (Jul 2024) extends this to intelligent-connected-vehicle perception data; 8-ministry automotive data-export guidelines (2026 edition) require localisation and block export of "important data"; boundaries must follow the state position | **Do not ship OSM data for China**; no qualification, wrong datum, wrong boundaries |
| **India** | DST Geospatial Guidelines 2021 (liberalised, self-certification portal); onshore storage for the finest-resolution data, restricted export, mandatory masking of military/atomic sites, maps must honour India's official boundaries | usable only with the onshore/security conditions met; verify before any India-specific work |
| **South Korea** | export ban on 1:5,000 map data, **reversed 2026-02-27** with security conditions (national coordinate system, blurring of military/security facilities) | detailed Korean maps now possible *only* under those conditions |
| **Disputed borders** | India, China, Israel/Palestine, Crimea | rendering OSM boundaries verbatim can be unlawful or lead to blocked distribution; commercial vendors ship per-country boundary views |
| **ODbL** | share-alike on a derived database + attribution | see §2.1 |

---

## 5. Feature legality by jurisdiction: speed cameras and police checkpoints

This is the one feature class where the **supplier** can be liable, not just the driver.
Design requirement: a country-keyed feature gate, default **off** for the layers below.

| Country | Law | Who is punished | Consequence for the app |
|---|---|---|---|
| **France** | C. route **R. 413-15** (possess/transport/use a device or product specific to detecting radars = contravention 1500 €, 6 points) and **L. 413-2** (make/import/export/**offer/sell/rent/advertise** such a device = délit, 2 y + 30 k €); the 2011 AFFTAC protocol keeps providers to "zones de danger"; **L. 130-11 / L. 130-12** lets the administration order suppression of user reports near police checkpoints, penalising the **operator** with 2 y + 30 k € | driver; the **operator** for L. 130-12 | no radar-detection/jamming function; if user reports are carried at all, the L. 130-11 blocking channel + the checkpoint confidentiality rules apply; keep to danger zones (CE 2013 / Cass. crim. 2016 read R. 413-15 narrowly: pure information sharing is not punished). QPC **2021-948** struck only the words that limited the road-safety-information carve-out to the national network — the order and the penalty regime **stand** |
| **Switzerland** | **Art. 98a SVG** — fined for importing, **advertising, passing on, selling**, otherwise handing over, installing, carrying, attaching or using devices/means meant to impede official traffic control (ASTRA reads this broadly) | driver **and supplier** | the feature must not exist for CH at all |
| **Germany** | § 23 StVO / BKatV — using a radar warning device while driving = 75 € + 1 point | driver | feature unusable in DE; offering it is not itself banned |
| Austria, Ireland, Italy (partial), Belgium, Cyprus, others | national bans with varying scope | varies | verify per market before enabling the layer; when in doubt, disable |

---

## 6. Platform contracts (not law, but distribution gates)

Google Play Developer Program Policies bind the developer directly.

- **Data safety form + privacy policy + prominent disclosure** must match what the app
  actually does (and what its SDKs do). Misdeclaration → removal.
- **Location policy** (updated 2026-04-15, effective **2026-10-28**): precise location is
  expected at minimum scope (the **location button** is the recommended minimum for
  precise/one-time requests); background location requires a Permissions Declaration
  form, a ≤ 30 s demo video, prominent in-app disclosure and a privacy policy in-app and
  on the listing; foreground-service location must be a continuation of a user-initiated
  action and must stop once it completes; **geofencing was removed** as an approved FGS
  use case (use the Geofence API).
  - **Audited 2026-09-26** (change `fix-location-permission-scope`). The request asks for
    coarse **and** fine in one request — a fine-only request is ignored by the platform on
    API 31+, which was a real defect (`MapCanvasScreen` asked for fine alone, and
    `LocationService` was fine-only, so an approximate grant left the map dead). The granted
    accuracy class now governs the provider request: high accuracy only with the precise
    grant, a balanced request with the approximate one, and the non-Fused fallback requests
    no GPS provider without the precise grant. Starting a route requires the precise grant
    and is refused with an actionable explanation (phone: re-request, or app settings when
    the platform will not ask again; car: a non-blocking notice, never a settings launch).
  - **No change needed**: `ACCESS_BACKGROUND_LOCATION` is not declared and not needed, so no
    Permissions Declaration form, no demo video and no background-location disclosure apply;
    geofencing is not used; the location-button mandate binds apps targeting **API 37+**
    whose features are session-based only — this app targets 36 and its access is a
    continuous, user-initiated navigation session.
- **Foreground service types** must stay justified (`dataSync`, `location` — see §9):
  `NavigationNotificationService` (`location`) starts as the continuation of the user's
  navigation/free-driving action and stops with it (arrival, stop action, driving state
  cleared); `MapDownloadService` (`dataSync`) starts from the download the user requested,
  stops when no download is active, and handles the platform's foreground-service timeout
  (Android 15+ caps `dataSync` at 6 h per 24 h) by ending cleanly, releasing its wake lock
  and leaving the download resumable instead of reporting completion.
- **Car app quality / driver distraction** (`DD-*` tiers, `distractionOptimized`) is
  mandatory for the AAOS listing; see `Build.md` §10 and `UI.md` for the car rules.
- Target API level must be bumped annually.

---

## 7. Conditional obligations

| Situation | Obligation |
|---|---|
| The app reads vehicle signals (`CarPropertyManager`) or is installed in a connected vehicle | **EU Data Act `2023/2854`** (in application since 2025-09-12): as a third party you may only use the data for the agreed purpose, must not coerce the user, must not use it to develop a competing product, must delete it when no longer needed, must protect trade secrets |
| Our map/speed-limit data feeds an OEM's ISA or an ADDW separate technical unit | the OEM's GSR/UN obligations and its CSMS (ISO/SAE 21434 in practice) cascade down contractually |
| Navigation output ever influences vehicle control (ADAS path preview, ISA arbitration) | ISO 26262 / ISO 21448 (SOTIF) become relevant — not statutory for a phone nav app |
| Preinstalled or OTA-updated by an OEM | you sit inside that OEM's R155/R156 process |

---

## 8. Not law, but the yardstick others apply

These are voluntary and non-binding, yet they are what a court, insurer or Play review
measures the UI against after an incident — which is why `UI.md` already encodes the
practical outcome.

- EU **ESoP**, Commission Recommendation `2008/653/EC` — HMI principles for in-vehicle
  systems.
- **ISO 15008** (display legibility, revision in progress), **ISO 15007-1/2020** (glance
  measurement) — source of the 2 s single-glance / 12 s total rule.
- **NHTSA** Phase 1 (2013, OE devices) and Phase 2 (proposed 2016, portable and
  aftermarket devices incl. nav apps).
- Google's **driver distraction guidelines / `DD-*` criteria**, which encode the above
  for the car path.

---

## 9. Repo inventory and open items

**Location permissions in use** (`app/src/main/AndroidManifest.xml`):

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (lines 6–7); **no**
  `ACCESS_BACKGROUND_LOCATION`.
- `MapDownloadService` with `foregroundServiceType="dataSync"` (line 102).
- `NavigationNotificationService` with `foregroundServiceType="location"` (line 111) —
  navigation is the canonical location FGS, but the 2026-10-28 policy still expects the
  user-initiated-action/termination shape and minimum scope for precise location.
- `distractionOptimized` metadata present (line 147) — car app quality gate applies.

**Diagnostic logs carry no coordinates** (implemented 2026-09-26, change
`fix-diagnostics-coordinate-redaction`; spec `auto-diagnostics`):

- Log and diagnostics lines carry precision-free identity instead of a position — object label/id,
  map database or map file name, magnification, screen pixel, accuracy, bearing. No latitude/longitude
  pair reaches logcat or the file, enforced by the build gate `checkNoCoordinatesInLogs`
  (buildSrc `CoordinateLogScanner`).
- The gate resolves **position-carrying locals**, not just coordinate names: a position copied into a
  local whose name the identifier list does not know (`val frameLat = viewportLat`, `val a = fix.lat`,
  or an alias of one) is a finding too. The rule is name/initializer based within one file — a position
  laundered through two hops in different files, or through a function return, is not resolved, so the
  enumerated identifier list stays authoritative for the names this repo uses.
- `filesDir/diagnostics/app.log` (+ `app.log.1`) is pruned by age: `DiagnosticsLog.RETENTION_MS`
  (7 days) is applied by the logging worker on the first flush of a process and once a day after that;
  the byte cap remains as the size backstop. A line whose timestamp cannot be parsed is removed too.
- **The rule covers the file's history, not only new writes** (change `fix-diagnostics-stale-coordinate-purge`,
  TODO §88): the same retention pass removes a line that carries a coordinate **whatever its age**, so the
  entries an earlier build wrote are gone within one pass of installing instead of surviving the rest of
  the window. The rule recognises two shapes (`:core` `LogLineCoordinates`) — a delimited coordinate field
  name together with a number at coordinate precision, and an unnamed comma-separated pair, which is the
  pre-redaction car render shape — and the pass reports those drops as a count in its single retention
  line (`… , N coordinate-carrying`), never repeating the removed position. Residual: a position written
  without a coordinate field name and with fewer than four fraction digits is not recognised — the age
  bound still removes it, and new writes are the build gate's concern. The verification recipe's bare
  grep (`[0-9]{1,3}\.[0-9]{4,}`) is deliberately *stricter* than the rule: a hit that names a
  magnification or another identity value (a raw `Double` can print at that precision) is an identity
  line, not a regression.
- The exported/shared text and both diagnostics viewers lead with the disclosure
  (`diagnostics_disclosure`, de + en), naming what the file holds and the retention window.
  The wording needs no change for the purge: it already claims the file carries no coordinates, which
  the rule above now makes true for the file's history as well.
- **Impact on an earlier recipe**: the `overlay-projects-against-displayed-frame` frame-vs-pending
  comparison (TODO §29) used the geo centre of the displayed frame and of the pending render target;
  that pair is no longer logged. Use `frameMag`/`frameAng`, `pendingMag`/`pendingAng`, `dMag`/`dAng`,
  the clamped pixel offset (`off=`, `clamped=`) and the render/blit counters — the placement rule
  itself is unchanged.
- **The car render entry (`Diag/MAP`)** carries the committed frame's magnification, the projection DPI
  and the rendered bitmap's size (`render mag=… dpi=… -> bitmap WxH`) and **no centre** (change
  `fix-car-render-coordinate-redaction`): the entry is the throttled "a frame was committed"
  marker, and the frame-vs-pending evidence above is where a follow/surface diagnosis reads position-free
  state. A car session's exported log therefore contains no render position either.

**Location and foreground-service surface** — audited 2026-09-26 (change
`fix-location-permission-scope`); the behaviour and the no-change findings are in §6.

**Owner-side items (no code change can satisfy them)**:

- Privacy policy URL in the Play listing (required for a location-using app), plus the in-app
  statement the About dialog now carries (`about_privacy_statement`).
- Data safety form: location used on-device for navigation, not shared, no background location;
  diagnostics stored on-device with no coordinates, 7-day retention, export only when the user asks.
- Track declarations stay as they are: same applicationId, two flavor AABs (mobile track, dedicated
  AAOS track).

Tracked as TODO §68 (diagnostics), §88 (coordinate purge of the file's history — implemented by
`fix-diagnostics-stale-coordinate-purge`, awaiting archive) and §69 (location policy).

---

## 10. Review cadence and sources

Re-check this document when any of these move, and at least twice a year:

| Topic | Watch |
|---|---|
| Product liability | `Directive (EU) 2024/2853` (national transposition, 2026-12-09) |
| Cyber resilience | `Regulation (EU) 2024/2847` (reporting 2026-09-11, main 2027-12-11) |
| Play location/FGS | "Permissions and APIs that Access Sensitive Information" + the 2026-04-15 announcement (effective 2026-10-28) — audited 2026-09-26, re-check the no-change findings (§6) at the next review |
| Car app quality | Google "Car app quality" / driver distraction guidelines |
| India | DPDP Rules 2025 phases (2026-11-13, 2027-05-13) |
| China | MNR/automotive data notices, app filing |
| Feature bans | national road-traffic law changes (FR, CH, DE, …) |
| HMI standards | ISO 15008 revision, NHTSA Phase 2 status |

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
