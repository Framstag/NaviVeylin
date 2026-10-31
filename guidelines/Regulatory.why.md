# Regulatory.why.md — the inventory, the review record, and why a duty was added

Companion to `Regulatory.md`. Every `## §N` anchor names a section there; read it beside the
section its anchor names. This document is history and provenance: the binding duties are the
named rules in `Regulatory.md`.

## §9 Repo inventory and open items — the inventory

**Location permissions in use** (`app/src/main/AndroidManifest.xml`):

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (lines 6–7); **no**
  `ACCESS_BACKGROUND_LOCATION`.
- `MapDownloadService` with `foregroundServiceType="dataSync"` (line 102).
- `NavigationNotificationService` with `foregroundServiceType="location"` (line 111) —
  navigation is the canonical location FGS, but the 2026-10-28 policy still expects the
  user-initiated-action/termination shape and minimum scope for precise location.
- `distractionOptimized` metadata present (line 147) — car app quality gate applies.

**Location and foreground-service surface** — audited 2026-09-26 (change
`fix-location-permission-scope`); the behaviour and the no-change findings are in `Regulatory.md` §6.

**Owner-side items (no code change can satisfy them)**:

- Privacy policy URL in the Play listing (required for a location-using app), plus the in-app
  statement the About dialog now carries (`about_privacy_statement`).
- Data safety form: location used on-device for navigation, not shared, no background location;
  diagnostics stored on-device with no coordinates, 7-day retention, export only when the user asks.
- Track declarations stay as they are: same applicationId, two flavor AABs (mobile track, dedicated
  AAOS track).

Tracked as TODO §68 (diagnostics), §88 (coordinate purge of the file's history — implemented by
`fix-diagnostics-stale-coordinate-purge`, awaiting archive) and §69 (location policy).

### The diagnostics history

The no-coordinate rule is enforced by the build gate `checkNoCoordinatesInLogs` (buildSrc
`CoordinateLogScanner`), and this is the record of how the rule grew.

- The gate resolves **position-carrying locals**, not just coordinate names: a position copied into a
  local whose name the identifier list does not know (`val frameLat = viewportLat`, `val a = fix.lat`,
  or an alias of one) is a finding too. The rule is name/initializer based within one file — a position
  laundered through two hops in different files, or through a function return, is not resolved, so the
  enumerated identifier list stays authoritative for the names this repo uses.
- **The rule covers the file's history, not only new writes** (change `fix-diagnostics-stale-coordinate-purge`,
  TODO §88): the same retention pass removes a line that carries a coordinate **whatever its age**, so the
  entries an earlier build wrote are gone within one pass of installing instead of surviving the rest of
  the window. The rule recognises two shapes (`:core` `LogLineCoordinates`) — a delimited coordinate field
  name together with a number at coordinate precision, and an unnamed comma-separated pair, which is the
  pre-redaction car render shape — and the pass reports those drops as a count in its single retention
  line (`… , N coordinate-carrying`), never repeating the removed position. Residual: a position written
  without a coordinate field name and with fewer than four fraction digits is not recognised — the age
  bound still removes it, and new writes are the build gate's concern.
- **A source-level privacy gate needs one rule per delivery *shape*, not one per field name** (2026-10-05,
  from the same gate's second pass). The first rule set targeted coordinate *identifiers* (`lat`/`lon`) and
  coordinate-shaped formats, so sites where the value arrives **whole** passed it: an interpolated request
  object (`$request`), a whole-URI hand-over (`${original?.data}`), a destination object, a label string.
  Two practices keep such a gate honest: run the new rule against the **fixed** shape before claiming an
  empty false-positive set (widening to "any `.data` in an interpolation" flagged the fix itself), and
  check `checkNoCoordinatesInLogs` is not vacuous — a brand-new gate that fails on its first run may be the
  wrong gate, and a `Sync` task's build **output** is not a mutation point (the sync restores the file
  before the test runs; the source under `app/src/main/cpp/libosmscout/` is).
- **Impact on an earlier recipe**: the `overlay-projects-against-displayed-frame` frame-vs-pending
  comparison (TODO §29) used the geo centre of the displayed frame and of the pending render target;
  that pair is no longer logged. The `follow` entry's numbers are formatted with a fixed locale on
  **both** variants — the car line by `fix-car-follow-reengage-render` (spec `auto-diagnostics`), the
  phone line by `fix-phone-diagnostic-locale` (spec `i18n-l10n`) — so the grep/parse of that recipe works
  identically on a German install (it printed `off=3,4,-1,5` before, where the decimal comma read as
  a field separator).
- The no-coordinate rule went live in change `fix-diagnostics-coordinate-redaction` (2026-09-26); the
  car render entry got its own change `fix-car-render-coordinate-redaction`.

### Why the duties were added

- **§2 product liability** — the software-as-a-product duty and the shift of the evidentiary burden to
  the producer are why the diagnostics trail is kept and why undisclosed retention is treated as a risk.
- **§6 platform location policy** — the 2026-04-15 announcement (effective 2026-10-28) is why the
  precise-minimum scope and the foreground-service shape became named duties rather than good practice;
  the 2026-09-26 audit found the fine-only request was a real defect.
- **§6 cleartext repository transport** — audited 2026-10-09 (change `allow-lan-http-map-repository`): the
  shipped build permits cleartext HTTP app-wide (`app/src/main/res/xml/network_security_config.xml`,
  asserted by `checkCleartextTransportPolicy`), because a self-hosted libosmscout mapgen repository is
  served over plain HTTP by upstream's own web server and its address is a LAN host the user types —
  Android's static policy cannot name that host in advance, and no narrower form exists. No Play policy
  forbids it; the data safety declaration is unchanged, and the app marks an `http` source as unencrypted
  in its own UI. The consequence to state rather than hide: such a transfer has **no integrity protection**,
  because the download's declared size and CRC-32 arrive over the same connection (`TODO.md` §163).

## §10 Review cadence and sources — the watch list

Re-check `Regulatory.md` when any of these move, and at least twice a year:

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

Status as of **2026-09-22**.
