## Context

See proposal.md - Why. Current state: `app/src/main/AndroidManifest.xml` carries Android Auto (projection) declarations only — `com.google.android.gms.car.application` metadata + `automotive_app_desc.xml` (`<uses name="template" />`), `NaviVeylinCarAppService`, template permissions. The manifest edits this change pins are already applied (uncommitted); `:app:processDebugMainManifest` merge verified manually.

## Goals / Non-Goals

**Goals:**
- AAOS (Android 11+) template-host discovery via `com.android.automotive`.
- Dual-mode packaging: automotive devices see the app; phones keep installing.
- Pin the behavior in specs and verify merged/packaged manifests.

**Non-Goals:**
- No dedicated automotive module / car-only APK (see D3).
- No AAOS launcher/phone-UI changes (head unit launcher behaviour is host-controlled).
- No change to the existing Android Auto projection declarations.

## Decisions

### D1. `android.hardware.type.automotive` with `required="false"`

Dual-mode single APK: the app is phone-primary (sideloaded, phone/foldable/tablet targets). `required="true"` on the shared manifest fails phone installs (`INSTALL_FAILED_MISSING_FEATURE`); `required="false"` keeps both worlds.

Alternative considered: separate automotive module with `required="true"` (Google's documented AAOS pattern) — rejected for now: needs a second APK/packaging flow the sideload distribution doesn't have. Deferred, see Open Questions.

### D2. Reuse `automotive_app_desc.xml` for both metas

Both the Android Auto (`com.google.android.gms.car.application`) and AAOS (`com.android.automotive`) metadata reference the same `automotive_app_desc.xml` with `<uses name="template" />`. The resource already exists and declares template support, which is exactly what the AAOS template host needs; no duplicated resource.

### D3. Manifest declarations live in the shared app manifest

The `:auto` library module's manifest stays lean (surface permission only); the AAOS declarations live with the other car-app metadata in `app/src/main/AndroidManifest.xml`, next to the CarAppService they describe.

### D4. AAOS launch entry: CarAppActivity trampoline (single APK)

On AAOS the launcher must start `androidx.car.app.activity.CarAppActivity` (from `androidx.car.app:app-automotive`, minSdk 29), which binds the template host and renders the CarAppService. The phone `MainActivity` is the launcher and, on automotive hardware (`android.hardware.type.automotive` present), redirects to `CarAppActivity` then finishes — the official docs' dedicated-automotive-track pattern adapted to the single sideloaded APK. `CarAppActivity` is declared exported + `singleTask` + `distractionOptimized`, with NO launcher filter (avoids two launcher entries). `android.software.car.templates_host` is declared required=false.

Alternative considered: dedicated automotive module/APK with its own launcher manifest (Google's documented pattern) — rejected for now: requires a second packaging track the sideload distribution doesn't have; the trampoline delivers the same behavior in one APK.

Consequence: app minSdk 28 → 29 (app-automotive requirement; Android 9 dropped).

## Risks / Trade-offs

- [`required="false"` may hide the app from some AAOS launchers vs `required="true"`] → Documented AAOS dual-mode approach; on-device verification recommended.
- [AAOS launcher opens the phone `MainActivity` (Compose UI) rather than the car template] → Host/launcher behaviour; template-host discovery works via the metadata. Out of scope; revisit with a dedicated automotive module if real head-unit testing demands it.
- [Single shared APK means AAOS users get the full phone app] → Accepted for now; matches sideload distribution. The trampoline (D4) ensures the car experience, not the phone UI, starts on automotive hardware.
- [Trampoline flicker (MainActivity briefly starts before redirect)] → `finish()` immediately after launching `CarAppActivity`; negligible and standard for single-APK car apps.

## Migration Plan

- Already applied: manifest edits in the working tree. No data or code migration.
- Rollback: revert `app/src/main/AndroidManifest.xml` (two small hunks).

## Open Questions

- Whether to later split a dedicated automotive module / car-only APK (`required="true"`, template-launcher entry) when AAOS distribution is actually targeted — deferrable, no spec impact.
