## ADDED Requirements

### Requirement: A read that misses its drain is retried and recorded

A read of the diagnostic log SHALL NOT treat a drain whose bounded wait has expired as a complete snapshot:
when the worker still holds entries at the end of the read's wait, the read SHALL record the expiry as one
coordinate-free entry on the log's own diagnosis tag and SHALL retry the drain once before it reads the file.

#### Scenario: A missed drain is recorded and retried once

- **WHEN** a read's bounded wait for the worker expires while entries are still buffered
- **THEN** the expiry SHALL be recorded on the log's own diagnosis tag (identity only, no coordinate) and the
  drain SHALL be retried once, so the entries the worker still held are among the lines the read returns, in a
  window that carries the marker of its own miss
- **Case**: `DiagnosticsLogWritePathTest#aMissedDrainIsRetriedAndRecorded`
