# Design Native boundary rationale — why the boundary is shaped this way

Companion to `guidelines/DesignNative.md`: why the two database-open entries differ, what each cost, and the
issue behind the bridge's two sides. Read it beside the section its `## §N` anchor names.

## §5b The bridge's two sides, the database-open asymmetry and the submodule discipline

- **Why the two database-open entries differ.** `openDatabase(path)` is the single-database entry a screen calls
  with a path it just resolved: a rejected path reports `false`, registers nothing and publishes no
  database-set change, because the caller must see the rejection. `openDatabases(paths[])` registers a *batch* —
  a directory that disappears between the app's scan and the call must not fail the whole batch — so it registers
  what it is handed and only reports the missing one, by directory name. "Unifying" them would make one of the
  two callers wrong (spec `native-database-open`).
- **What the dex provenance cost** (issue #691): AGP dexes a project dependency from `main`'s `classesDirs`
  collection, so a split that left the vendored directory outside it gave the APK no
  `com.framstag.libosmscout.client` class and the app died at startup with `ClassNotFoundException: MapProvider`.
- **What the drift class cost** (issue #174, the class `TODO.md` §119 fixed by hand): for the five overridden
  files the override is the only Java copy the APK sees, so a native declared on one side only reaches the other
  as a `NoSuchMethodError` at the first call — no compiler error, no failing test.
