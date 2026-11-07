# Design procedure — the apply checklist and the stack recipes

Companion to `guidelines/Design.md`: the recipes and the verification checklist a reader *does*, kept out of the
normative document. The rules the recipes implement are `guidelines/DesignStack.md` §2 and §9.

## §2 Adding to the stack — the recipes

Five shapes recur. Each names the files it touches; the rules above say what the code must look like, and
the band a surface composes in belongs to `guidelines/UIPhoneMap.md` §11.

- **Adding a dependency**: `app/build.gradle.kts` (or the module's own build file); a native one
  additionally needs `CMakeLists.txt` and the vcpkg dependency list (`guidelines/Build.md` §12).
- **Adding a screen**: define the route in `NavGraph.kt`, add the composable under `ui/`, add its ViewModel
  under `ui/` or `data/`, and register a Hilt module when it needs one.
- **Adding a full-screen sheet** (e.g. `FavoritesSheet`): the composable under `ui/<feature>/`, a
  `@HiltViewModel` beside it, wired into `MapCanvasScreen` by a boolean state flag with conditional
  composition. Compose it in the **modal band** (`MapLayer.MODAL`, `guidelines/UIPhoneMap.md` §11) — never as a
  bare sibling after the chrome — so no on-map element paints over it.
- **Adding a details sheet after a search**: a `ModalBottomSheet`, a `showDetailsSheet` flag on
  `MapCanvasUiState`, and the selection wired to set the flag and move the centre; its favourite action
  comes from `FavoriteRepository`.
- **Adding a native function**: the `native` method in the **override**
  `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — the submodule's copy of that file is one of the
  five the Gradle module supersedes and is never compiled, so a declaration added there does
  nothing — the JNI wrapper under the submodule's `libosmscout-client-java/src/`, and a CMake target
  link when a new library is involved. The `native-bridge-signature-change` skill holds the
  procedure, the audit script and the one-commit rule for the gitlink and the declaration.

## Appendix A — Quick checklist (apply phase)

- [ ] JNI/native calls off the main thread, with timeouts + loading UI?
- [ ] Native callbacks marshaled to the main thread?
- [ ] Displayed frame emitted atomically (bitmap + viewport + marker)?
- [ ] Overlays never baked into buffers or tiles?
- [ ] Epoch bumped only on meaningful changes?
- [ ] Mode/style switch invalidates cache + forces full render?
- [ ] Marker on raw fix; only the camera smoothed?
- [ ] AA surface released on stop/destroy; renderer paused/resumed?
- [ ] Same labels in both variants where platform allows?
- [ ] New settings fields defaulted + lenient decode?
- [ ] Shared logic in `:core`, not duplicated?
- [ ] Change additive; rollback = simple revert?
- [ ] Pure logic JVM-testable; tests added?
- [ ] Spec ↔ code in sync (`openspec validate`)?
