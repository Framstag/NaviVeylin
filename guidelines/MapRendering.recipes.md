# MapRendering — the recipes

This is the procedure layer of `guidelines/MapRendering.md`: the number tables and the measurement
recipes, read against the section the base document states the rule in.

## Parameter Overview

| Parameter | Value | Purpose |
|---|---|---|
| `GPS_DEDUPE_MS` | 100 ms | Duplicate time window (coordinate + timestamp) |
| `GPS_FOLLOW_RENDER_INTERVAL_MS` | 200 ms | Follow-render throttle |
| `centerSmoothMaxJumpM` | 500 m | Teleport reset center |
| `COURSE_HISTORY_SIZE` | 10 | Course ring buffer |
| `MIN_COURSE_DISTANCE_M` | 40 m | Stable course base |
| `MIN_COURSE_DISTANCE_FAST_M` | 10 m | Fast base after reset/start |
| `MIN_SEGMENT_FOR_TURN_M` | 8 m | Min. segment for turn detection |
| `COURSE_TURN_RESET_DEG` | 45° | Turn-reset threshold |
| `COURSE_LOW_PASS_ALPHA` | 0.3 | Stable course (≥ 40 m) |
| `COURSE_LOW_PASS_ALPHA_FAST` | 0.7 | Fast course (< 40 m) |
| `MIN_BEARING_DELTA_DEG` | 2° | Rotation deadband |
| `MAX_ANGLE_RATE_DEG_PER_RENDER` | 90° | Rotation rate limit per render |
| `ANGLE_EPSILON_RAD` | (removed) | Former angle tolerance — do not reintroduce |
