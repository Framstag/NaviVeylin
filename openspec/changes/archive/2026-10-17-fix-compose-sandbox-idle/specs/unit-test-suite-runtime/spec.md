# Spec Delta — unit-test-suite-runtime

## ADDED Requirements

### Requirement: A Compose case starts from a repaired sandbox

A `:app` Compose test class SHALL repair the Robolectric sandbox's Compose dispatcher and its snapshot-apply
notifications before its Compose rule runs, because both are sandbox-wide state that outlives a test while the
callbacks that drain them do not.
A Compose case SHALL NOT fail because a previously finished case in the same sandbox left that dispatcher
believing a dispatch is in flight, or left the sandbox's `GlobalSnapshotManager` unable to send an apply
notification, and the repository SHALL fail a run that adds a Compose test class without that repair.

#### Scenario: A Compose case follows a plain case that wrote Compose state

- **WHEN** a non-Compose case writes a Compose snapshot state in a sandbox where a Compose case has already run
- **AND** that case ends without the paused main looper draining the dispatch the write posted
- **THEN** the next Compose case in that sandbox still reaches idle, and its `setContent` does not spend an
  idling budget it cannot satisfy
- **AND** the state the repair leaves behind is the state a fresh sandbox is in, so the repair cannot be
  observed by the case it precedes

#### Scenario: A Compose case follows a sandbox that can no longer send apply notifications

- **WHEN** the sandbox's `GlobalSnapshotManager` has lost the consumer coroutine that carries out
  `Snapshot.sendApplyNotifications()`, so its once-per-write latch is stuck and it never sends again
- **AND** a Compose case writes a snapshot state while it attaches its view, which is the write the sandbox
  cannot notify about
- **THEN** the case still reaches idle, because the repair applies what is pending and replaces that
  notification with a kick per global write that carries no latch of its own, so a kick the per-test reset
  drops is re-posted by the next write

#### Scenario: The stranded work is dropped, not run

- **WHEN** the repair finds the sandbox's dispatcher armed
- **THEN** it drops the work stranded by the finished case and clears the dispatcher's scheduled-dispatch flags
- **AND** it does not resume that work, because resuming a finished case's continuation touches that case's
  torn-down objects and fails the case it is meant to protect

#### Scenario: A class without the repair fails the run that adds it

- **WHEN** a Compose test class is added to `:app` without declaring the sandbox repair before its Compose rule
- **THEN** the run's own text check names the file, so the invariant does not depend on a reviewer noticing it
- **AND** no test needs to be executed for that verdict

#### Scenario: The repair does not hide a genuinely stuck composition

- **WHEN** a Compose case cannot idle for a reason of its own — an infinite composition loop, or a further
  sandbox-wide condition the repair does not cover
- **THEN** the case fails with its own idling diagnosis, and the repair is not read as evidence that the suite
  cannot produce that failure
