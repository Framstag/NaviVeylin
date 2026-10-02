# Spec Delta — map-render

## ADDED Requirements

### Requirement: Map initialization keeps native and file work off the main thread

Initializing the map screen SHALL NOT perform JNI or filesystem work on the main thread. Opening the
selected map database, registering the other installed databases, configuring the native tile data
cache for the opened database set, initializing the favorites store, refreshing the bundled
stylesheet and icon assets onto device storage, and resolving a database's bounding box for the
initial viewport SHALL all run on a background dispatcher, and the main thread SHALL NOT block on any
of them — a cold start with a slow or contended filesystem SHALL leave the UI responsive and the
loading state visible.

This is the phone-side counterpart of the car requirement "Renderer initialization off the car-app
main thread": the same rule on both surfaces, with only the platform constraint differing (on the car
the blocked thread is the car-app host-callback thread).

- The loading state SHALL be published before the off-main work begins and SHALL be cleared when
  initialization completes, so the user sees the loading indication for the whole initialization
- All updates to the screen's observable state (loading, error, ready) SHALL be published on the main
  thread; the off-main work SHALL NOT publish state directly
- The user-visible state sequence and the error text for a database that cannot be opened SHALL be
  unchanged by running the work off the main thread
- The initialization's own sequencing SHALL be preserved: the persisted-viewport restore (or its
  bounding-box fallback) SHALL complete before a renderer exists, so no render submitted during
  initialization can replace the restored viewport with a default one
- The renderer SHALL keep performing its render work on its own background scope; this change does not move renderer construction (it performs no native work)
- Initialization SHALL remain cancellable and re-entrant-safe: a new initialization SHALL supersede an
  in-flight one, which SHALL be cancelled before it can apply a stale viewport restore, register a
  second database set, or install a second renderer

#### Scenario: Cold start with a slow database open

- **WHEN** the map screen initializes and the map database open takes long enough that a frame lands while it runs
- **THEN** the main thread is not blocked on the open, the asset refresh, the favorites-store initialization or the bounding-box lookup
- **THEN** the loading state is observable before the work starts and cleared when initialization finishes

#### Scenario: No native call of initialization on the main thread

- **WHEN** the map screen initializes, for any combination of an openable database, a rejected database path, and additional installed databases
- **THEN** every native client call made during initialization is observed on a background thread, never on the main thread

#### Scenario: Rejected database path still reaches the UI

- **WHEN** the selected map database path is rejected while initialization runs off the main thread
- **THEN** the system reports the failure state with the text "Could not open map database"
- **THEN** the initialization completes and the screen leaves its loading state

#### Scenario: Viewport restore wins over an early render request

- **WHEN** a render is requested while initialization is still running off the main thread
- **THEN** the render is submitted with the restored (or bounding-box) viewport, not with the default viewport, because no renderer existed while the restore was being applied

#### Scenario: Second initialization supersedes the first

- **WHEN** the map screen initializes twice in succession (map re-entry after a download) while the first initialization is still running
- **THEN** the first initialization is cancelled before it can apply a stale viewport, register a second database set, or install a second renderer
- **THEN** only the second initialization's results are observable

#### Scenario: Additional databases registered in one batch

- **WHEN** initialization registers the other installed map databases alongside the selected one
- **THEN** the whole set is registered in a single coordinated registration, unchanged by running it off the main thread
