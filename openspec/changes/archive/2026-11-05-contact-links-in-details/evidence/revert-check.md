# Revert-check — the website scheme allow-list

One mutation, one invariant, per the `revert-check` skill (issue #768, change `contact-links-in-details`,
task 6.1). Production code only; the test file was not touched.

## The mutation

`core/src/main/java/com/naviveylin/core/details/ContactLinks.kt`, `webTarget` — the allow-list removed, so any
scheme a value carries is accepted (the design's rejected option 2):

```diff
-                if (SCHEME_PREFIX.containsMatchIn(part)) {
-                    return part.takeIf { WEB_SCHEME.containsMatchIn(it) }
+                // REVERT-CHECK MUTATION
+                if (SCHEME_PREFIX.containsMatchIn(part)) {
+                    return part
                 }
```

Command, identical for both runs:

```
./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.details.ContactLinksTest" -PforceTests --no-build-cache
```

## Run 1 — mutated (it must fail)

- `BUILD FAILED`, exit 1 — `ContactLinksTest > aNonWebSchemeResolvesToNoTarget FAILED`
- XML `tests="7" failures="1" errors="0"`, `timestamp="2026-11-05T09:48:12.598Z"`
- Failure quoted: `java.lang.AssertionError: javascript:alert(1) must resolve to no target`
  — the assertion the guard exists for, not a compile error and not an unrelated premise.
- Transcript: `revert-check-mutated.log`

## Restore

The same edit reversed; `grep -c 'REVERT-CHECK MUTATION' core/src/main/java/com/naviveylin/core/details/ContactLinks.kt`
→ `0`, and `webTarget`'s allow-list line is back:

```
            return part.takeIf { WEB_SCHEME.containsMatchIn(it) }
```

## Run 2 — restored, forced (it must pass again)

- `BUILD SUCCESSFUL`, exit 0
- XML `tests="7" failures="0" errors="0"`, `timestamp="2026-11-05T09:48:18.235Z"` — **after** the restore
  (the mutated run's timestamp is `09:48:12.598Z`)
- Transcript: `revert-check-restored.log`

Verdict: the allow-list case can fail, and it fails for the reason it claims. The mutation is gone from the tree.
