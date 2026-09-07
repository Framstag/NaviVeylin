## 1. Manifest fix

- [x] 1.1 Remove the `<provider android:name="androidx.car.app.connection.provider" android:authorities="androidx.car.app.connection" />` element from `<queries>` in `app/src/main/AndroidManifest.xml` (design D1). Keep the three live entries (CarAppService intent, gearhead, templates host).

## 2. Verify

- [x] 2.1 Run `:app:lintMobileDebug` (build-app skill) and confirm the `MissingClass` error is gone; error count drops from 3 to 2 (remaining: `NotificationPermission`, `AppLinkUrlError` — pre-existing, out of scope). Confirm no new warnings.
- [x] 2.2 Verify the app still builds: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` compiles without errors (manifest merge + resource processing unaffected).

## 3. Documentation

- [x] 3.1 Update `TODO.md` §9 lint entry: correct the commit attribution (initial import `6b13586`, not `447049b` — that commit only added `READ_CONTACTS`), note the fix, and mark the entry resolved.
