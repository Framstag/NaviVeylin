# android-automotive-os Specification

## Purpose

Makes NaviVeylin discoverable as a templated car app on standalone Android Automotive OS (AAOS) head units. AAOS is packaged as a **separate AAB** (`automotive` product flavor, same applicationId) because Google Play rejects any single AAB that declares both `android.hardware.type.automotive` and `com.google.android.gms.car.application` (the Android Auto projection metadata) — a dual-mode single APK is sideload-only and cannot be uploaded to Play. The automotive AAB is distributed via the dedicated "Android Automotive OS" track; the `mobile` flavor AAB covers phones/tablets + Android Auto projection.

## Requirements

### Requirement: Automotive template host metadata

The system SHALL declare the `com.android.automotive` metadata in the **automotive flavor manifest** (`app/src/automotive/AndroidManifest.xml`), referencing an `automotive_app_desc.xml` resource that declares template support, so Android Automotive OS (Android 11+) can discover the app as a template host.

#### Scenario: AAOS discovers the app as a template host

- **WHEN** an Android Automotive OS head unit scans for template host apps
- **THEN** it finds the app via the `com.android.automotive` metadata pointing to `automotive_app_desc.xml`
- **AND** `automotive_app_desc.xml` declares `<uses name="template" />`

#### Scenario: Projection metadata absent in the AAOS build

- **WHEN** the automotive flavor manifest is merged
- **THEN** the `com.google.android.gms.car.application` (Android Auto projection) metadata is removed (`tools:node="remove"`), because Google Play rejects an AAB declaring both it and `android.hardware.type.automotive`

### Requirement: Automotive hardware feature declaration

The system SHALL declare `android.hardware.type.automotive` with `required="true"` in the automotive flavor only. The main manifest strips the feature (including the copy merged in by the `androidx.car.app:app-automotive` AAR) so the mobile/Android Auto AAB never contains it.

#### Scenario: AAOS build declares the hardware feature

- **WHEN** the automotive flavor is built
- **THEN** the merged manifest contains `<uses-feature android:name="android.hardware.type.automotive" android:required="true" />`

#### Scenario: Mobile build declares no automotive hardware

- **WHEN** the mobile flavor is built
- **THEN** the merged manifest contains no `android.hardware.type.automotive` declaration, keeping the app installable on phones and tablets

### Requirement: Android Auto projection declarations intact in the mobile build

The `mobile` flavor SHALL keep the Android Auto (projection) declarations: the `com.google.android.gms.car.application` metadata, the `NaviVeylinCarAppService` service with the `androidx.car.app.CarAppService` action and `androidx.car.app.category.NAVIGATION` category, and the template/access-surface permissions.

#### Scenario: Projection discovery still works

- **WHEN** an Android Auto host (phone projection) scans for navigation apps
- **THEN** it finds `NaviVeylinCarAppService` with the NAVIGATION category as before

### Requirement: AAOS launch entry point

The system SHALL start the car experience on Android Automotive OS, not the phone UI: on automotive hardware the launcher path must redirect to `androidx.car.app.activity.CarAppActivity` (declared with `distractionOptimized` and `singleTask`), which binds the AAOS template host and renders the `CarAppService`. On non-automotive devices the phone `MainActivity` remains the entry point.

#### Scenario: Car app launches on AAOS

- **WHEN** the user opens the app on an Android Automotive OS head unit
- **THEN** the car experience appears (template host rendering the CarAppService) instead of the phone UI

#### Scenario: Phone entry point unchanged on phones

- **WHEN** the user opens the app on a phone or tablet
- **THEN** the phone `MainActivity` UI is shown and no redirect occurs

#### Scenario: Template host declared

- **WHEN** the app runs on AAOS
- **THEN** the manifest declares `android.software.car.templates_host` (required=false) alongside the automotive hardware feature

### Requirement: Distribution as separate bundle

The build SHALL produce two Play-ready AABs from one applicationId: `app-mobile-release.aab` (phones + Android Auto projection) and `app-automotive-release.aab` (AAOS head units), so Play can serve each surface without a forbidden feature/metadata combination.

#### Scenario: Release builds both bundles

- **WHEN** the developer runs `./gradlew release`
- **THEN** both `bundleMobileRelease` and `bundleAutomotiveRelease` complete and both AABs are produced

#### Scenario: Play upload split across tracks

- **WHEN** the developer uploads to Play Console
- **THEN** the mobile AAB goes to the normal tracks and the automotive AAB goes to the dedicated "Android Automotive OS" track, keeping one store listing (same package name)
