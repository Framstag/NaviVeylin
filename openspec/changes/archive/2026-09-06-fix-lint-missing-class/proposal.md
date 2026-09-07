## Why

`:app:lintMobileDebug` fails with a `MissingClass` error: the manifest's `<queries>` element references `androidx.car.app.connection.provider`, a class that does not exist in the car-app library (1.7.0) or anywhere in the project. The declaration is a leftover from the initial import (`6b13586`) and the app never uses the car connection state it would grant visibility to. The lint gate cannot go green until it is removed.

## What Changes

- Remove the `<provider android:name="androidx.car.app.connection.provider" android:authorities="androidx.car.app.connection" />` element from `<queries>` in `app/src/main/AndroidManifest.xml`.
- The remaining three `<queries>` entries (CarAppService intent, gearhead package, templates host package) stay — they are actively used and already covered by `openspec/specs/auto/spec.md`.
- Update `TODO.md` §9: correct the commit attribution (initial import `6b13586`, not `447049b`) and close the entry once lint is green.
- Additive change, no breaking behavior. Rollback: re-add the element.

## Capabilities

No spec-level behavior change. The `auto` spec already describes the correct `<queries>` content (CarAppService, gearhead, templates host — no provider). This is build/lint hygiene, so `skip_specs: true` is set in `.openspec.yaml`.

## Impact

- `app/src/main/AndroidManifest.xml` — remove one `<provider>` element (3 lines)
- `TODO.md` — §9 lint entry: fix attribution, mark resolved
- Lint: `:app:lintMobileDebug` errors drop from 3 to 2 (remaining: `NotificationPermission`, `AppLinkUrlError` — separate pre-existing issues, out of scope)
- No runtime impact: nothing in app/auto/core references `CarConnection`/`CarConnectionTypeLiveData`
