# GPS Render Coalescing

## Why

In follow-mode the map receives one GPS fix per second from `LocationService`. When both `FusedLocationProviderClient` and `LocationManager` are active, the same fix can be delivered twice within milliseconds. Each fix triggers `MapRenderer`, which performs a heavy native render. On slow devices the render takes longer than the GPS interval, so the pipeline discards the running frame because the epoch advances (`stale epoch ... discarding`) or suppresses the finished frame because the bearing changed slightly during the render (`front buffer NOT emitted`). The visible result is a map that lags behind the GPS marker and roads that no longer line up with the marker at the end of the trace.

## What Changes

- Deduplicate GPS fixes in `LocationService` by fix timestamp so the same satellite fix is not emitted twice.
- Coalesce follow-mode renders in `MapCanvasViewModel` so multiple ticks within a short window do not flood the render queue.
- Ignore bearing changes smaller than 3° for map angle updates to reduce jitter.
- Stop incrementing the renderer epoch for pure GPS position updates so a running frame is no longer discarded; let the conflated render queue pick up the latest position on the next job.
- Tolerate up to 5° angle drift when deciding whether to emit a completed rotated render to the front buffer.
- Document the angle epsilon and throttle constants in the change spec.

## Capabilities

### New Capabilities
- `gps-render-coalescing`: Throttle and coalesce GPS-triggered map renders while preserving navigation-engine updates.

### Modified Capabilities
- `map-render`: Allow small angle drift between render start and front-buffer emission so GPS bearing jitter does not drop frames.

## Impact

- `LocationService.kt`
- `MapCanvasViewModel.kt`
- `MapRenderer.kt`
- `MapRendererGpsMarkerTest.kt`
