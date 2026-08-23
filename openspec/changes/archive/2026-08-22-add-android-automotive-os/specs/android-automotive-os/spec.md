## Purpose

Makes NaviVeylin discoverable as a templated car app on standalone Android Automotive OS (AAOS) head units while keeping phone distribution intact — dual-mode packaging via `required="false"`.

## ADDED Requirements

### Requirement: Automotive template host metadata

The system SHALL declare the `com.android.automotive` metadata in the app manifest, referencing an `automotive_app_desc.xml` resource that declares template support, so Android Automotive OS (Android 11+) can discover the app as a template host.

#### Scenario: AAOS discovers the app as a template host

- **WHEN** an Android Automotive OS head unit scans for template host apps
- **THEN** it finds the app via the `com.android.automotive` metadata pointing to `automotive_app_desc.xml`
- **AND** `automotive_app_desc.xml` declares `<uses name="template" />`

#### Scenario: Single automotive description resource shared with Android Auto

- **WHEN** the manifest is built
- **THEN** both the `com.google.android.gms.car.application` (Android Auto projection) and `com.android.automotive` (Android Automotive OS) metadata reference the same `automotive_app_desc.xml` resource

### Requirement: Automotive hardware feature declaration

The system SHALL declare the `android.hardware.type.automotive` feature in the app manifest with `required="false"`, so the app remains installable on phones and tablets while also being available on automotive devices.

#### Scenario: Phone installs unaffected

- **WHEN** the APK is installed on a phone or tablet (sideload)
- **THEN** installation succeeds despite the automotive feature declaration

#### Scenario: Available on automotive devices

- **WHEN** the APK is installed on an Android Automotive OS device
- **THEN** the app is presented as available for automotive use

### Requirement: Android Auto projection declarations remain intact

The system SHALL keep the existing Android Auto (projection) manifest declarations unchanged: the `com.google.android.gms.car.application` metadata, the `NaviVeylinCarAppService` service with the `androidx.car.app.CarAppService` action and `androidx.car.app.category.NAVIGATION` category, and the template/access-surface permissions.

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
