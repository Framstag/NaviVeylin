## Why

Standalone Android Automotive OS (AAOS) head units cannot discover NaviVeylin as a templated car app: the manifest only carries the Android Auto (projection) declarations (`com.google.android.gms.car.application`) and lacks the AAOS-specific `com.android.automotive` metadata and the `android.hardware.type.automotive` feature declaration. Without them the app never appears as a car app on automotive devices.

## What Changes

- Add `<meta-data android:name="com.android.automotive" android:resource="@xml/automotive_app_desc" />` to the app manifest so AAOS (Android 11+) can discover NaviVeylin as a template host app. The existing `automotive_app_desc.xml` (`<uses name="template" />`) is reused.
- Add `<uses-feature android:name="android.hardware.type.automotive" android:required="false" />` — dual-mode packaging: the app stays installable on phones (a `required="true"` shared-manifest declaration would fail phone installs) while still surfacing on automotive devices.
- Manifest changes are already applied to `app/src/main/AndroidManifest.xml` (uncommitted); this change pins the behavior in specs and verifies the merged/APK manifests.

## Capabilities

### New Capabilities
- `android-automotive-os`: Android Automotive OS discovery — the manifest declarations that make NaviVeylin recognizable as a templated car app on AAOS head units, without breaking phone distribution.

### Modified Capabilities
<!-- No existing requirement changes: the auto spec's Android Auto (projection) requirements
     remain true; AAOS declarations are additive. -->

## Impact

- `app/src/main/AndroidManifest.xml` — AAOS metadata + automotive uses-feature (already edited)
- `app/src/main/res/xml/automotive_app_desc.xml` — reused, unchanged (`<uses name="template" />`)
- Merged manifest output (`:app:processDebugMainManifest`) and packaged APK manifest
- AGENTS.md — manifest conventions section could mention the AAOS declarations (documentation task)
- No code changes; no dependency changes
