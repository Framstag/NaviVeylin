# Traceability

One row per delta scenario: the spec clause, the task that implements it, and the case or device step
that exercises it.

| Spec requirement | Scenario | Task | Enforced by |
|---|---|---|---|
| A frame from the previous orientation yields no offset instead of an error | The phone is rotated while follow mode is active | 1.1, 1.2, 2.1, 4.3 | `FollowPredictionTest` swapped-orientation case (no throw, margin-less axis `0.0`, `clamped == true`); `FollowAnchorFramingTest` render-request case; device rotation mid-follow (4.3) |
| A frame from the previous orientation yields no offset instead of an error | Both orientation transitions are safe | 1.2 | `FollowPredictionTest` portrait→landscape and landscape→portrait cases (finite offsets, no throw) |
| A frame from the previous orientation yields no offset instead of an error | A frame with overrun margin keeps today's clamp | 1.2 | `FollowPredictionTest` control case reproducing the existing margin clamp and the unclamped in-margin drift |

Every task above maps back to the one modified capability (`smooth-follow`); the change adds no new
capability and touches no native/JNI artifact.

Revert-check (one mutation, one named case): task 1.3 — restore the bare margin, the
swapped-orientation case must fail with `IllegalArgumentException: Cannot coerce value to an empty
range`.
