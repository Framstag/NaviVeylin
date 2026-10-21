# revert-check — `aTickDoesNotDispatchOnTheMainDispatcher` — 2026-10-21

## Red on HEAD (base `2e732f0`)

The unrelated main-dispatcher traffic the item observed was supplied deterministically (one dispatch per
window from a `Dispatchers.IO` coroutine), assertion unchanged:

```
$ ./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache -PnoCoverage \
    --tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest"
BUILD FAILED  (2026-10-21T09:37:19Z)
tests="9" skipped="0" failures="1" errors="0"
MapCanvasViewModelFixQualityTest > aTickDoesNotDispatchOnTheMainDispatcher FAILED
java.lang.AssertionError: the fix-quality tick dispatched on the main dispatcher from another thread 1 times
in the second 300ms window while nothing changed (first window: 1) — it must stay off the main dispatcher;
first dispatch:
	at …MapCanvasViewModelFixQualityTest$CountingMainDispatcher.dispatch(MapCanvasViewModelFixQualityTest.kt:379)
	at kotlinx.coroutines.test.internal.TestMainDispatcher.dispatch(TestMainDispatcher.kt:25)
	…
```

The printed stack is the case's own noise coroutine — not the re-evaluation — which is the defect.
XML: `red-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`.

## Falsification of the fix's guard

Mutation: the pre-fix per-tick hop reintroduced inside the re-evaluation loop of
`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (one line after
`fixQualityTickCount.value = fixQualityTickCount.value + 1L`):

```kotlin
kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { }
```

```
$ ./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache -PnoCoverage \
    --tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher"
BUILD FAILED  (2026-10-21T09:38Z)
tests="1" failures="1"
java.lang.AssertionError: the fix-quality tick dispatched on the main dispatcher from another thread 18
times over 3 windows of 6 ticks (20ms each) while nothing changed ([window 1: 6, window 2: 6, window 3: 6])
— it must stay off the main dispatcher; first dispatch from the ViewModel: …
```

The attribution puts every one of the 18 dispatches on the re-evaluation, one per tick, in every window
(`tests="1" failures="1"`, 2026-10-21T09:42:21Z).
Line restored; `git status --porcelain` shows only the test file modified.
XML: `falsification-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`.

## Green after the restore

```
$ ./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache -PnoCoverage \
    --tests "com.naviveylin.ui.map.MapCanvasViewModel*"
BUILD SUCCESSFUL in 40s  (2026-10-21T09:42:42Z)
slice: tests=321 skipped=0 failures=0 errors=0
MapCanvasViewModelFixQualityTest: tests="9" skipped="0" failures="0" errors="0"
```

Five forced runs of the affected class before it (`tests="9" failures="0"` each) and this slice run after
the restore. XML: `green-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`.

No full `./gradlew test` gate was run: this delta touches test sources only, so `guidelines/Build.md` §2's
scope block names the red case plus the affected cases instead — and no compiled production file changed.
