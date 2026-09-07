## Context

`app/src/main/AndroidManifest.xml` `<queries>` element contains a `<provider>` entry referencing `androidx.car.app.connection.provider` — a class that does not exist in `androidx.car.app:app:1.7.0` (the package only contains `CarConnection`, `CarConnectionTypeLiveData`, `AutomotiveCarConnectionTypeLiveData`). Lint's `MissingClass` check flags it as an error, keeping `:app:lintMobileDebug` red. The authority `androidx.car.app.connection` is real (`CAR_CONNECTION_AUTHORITY` in `CarConnectionTypeLiveData`), but nothing in the app (app/auto/core modules) references `CarConnection` or `CarConnectionTypeLiveData`, so the visibility grant is dead. The element predates all recent work — it came in with the initial import (`6b13586`), not with the address-book commit (`447049b`) as `TODO.md` §9 currently claims.

## Goals / Non-Goals

**Goals:**
- Make `:app:lintMobileDebug` pass (MissingClass error gone)
- Keep the three live `<queries>` entries (CarAppService intent, gearhead, templates host) untouched — they are required by `openspec/specs/auto/spec.md`
- Correct the `TODO.md` §9 record (attribution + resolution)

**Non-Goals:**
- Fixing the other two pre-existing lint errors (`NotificationPermission`, `AppLinkUrlError`) — separate issues, tracked separately
- Adding `CarConnectionTypeLiveData` usage — nothing needs it today
- Touching the automotive flavor manifest (`app/src/automotive/AndroidManifest.xml`) — it has no such entry

## Decisions

### D1: Remove the `<provider>` element entirely (chosen)

The `<queries>` element becomes:

```xml
<queries>
    <intent>
        <action android:name="androidx.car.app.CarAppService" />
    </intent>
    <package android:name="com.google.android.projection.gearhead" />
    <package android:name="com.google.android.apps.automotive.templates.host" />
</queries>
```

**Alternatives considered:**

- **A1 — Remove entirely (chosen):** Dead declaration, zero references in code. Cleanest end state; matches what `auto/spec.md` already describes. If future work needs car connection state, re-add with the correct form (`<provider android:authorities="androidx.car.app.connection" />` — `android:name` is optional for provider visibility queries).
- **A2 — Keep authority, drop `android:name`:** Preserves the visibility grant for hypothetical future `CarConnectionTypeLiveData` use. Rejected: YAGNI — nothing queries the provider today, and the grant is only needed when code actually binds to it. Keeping dead intent invites the same confusion later.
- **A3 — Add the missing class / dependency:** No such class exists in any car-app artifact; inventing one is nonsense. Rejected.

### D2: `skip_specs: true`

No spec-level behavior change: `auto/spec.md` already specifies the correct `<queries>` content and does not mention the provider. Removing an un-spec'd dead element is build hygiene, not a requirement change. `skip_specs: true` set in `.openspec.yaml`.

## Risks / Trade-offs

- **Low:** Removing a manifest element cannot break runtime — nothing references the class or the authority. The three remaining `<queries>` entries (which the app genuinely needs for Android Auto package visibility) are untouched.
- **Verification:** lint must be re-run to confirm the error count drops 3 → 2 and no new warnings appear. The two remaining errors are pre-existing and unrelated; they stay red until their own fixes land.
- **TODO.md accuracy:** the entry's commit attribution is wrong (`447049b` added only `READ_CONTACTS`); the fix task corrects it to `6b13586` so future readers aren't misled.
