# Spec Delta — osmscout-jni

## ADDED Requirements

### Requirement: The Android override declares the submodule's favorite file-format natives

The Android bridge override of `OSMScoutClient` SHALL declare the favorite file-format natives that the
submodule's `OSMScoutClient` source declares and the linked JNI implements, with matching signatures, so a
caller on Android reaches the same favorites API as the submodule. Without the declaration the natives are
unreachable through the class the app compiles against, because the override shadows the submodule source.

#### Scenario: The favorite file-format natives are declared on the override

- **WHEN** the Android bridge's `OSMScoutClient` class is inspected for its favorite file-format natives
- **THEN** `getFavoriteFileFormatVersion` SHALL be declared as a native method taking no arguments and
  returning an `int`
- **AND** `isFavoriteFileFormatSupported` SHALL be declared as a native method taking no arguments and
  returning a `boolean`
