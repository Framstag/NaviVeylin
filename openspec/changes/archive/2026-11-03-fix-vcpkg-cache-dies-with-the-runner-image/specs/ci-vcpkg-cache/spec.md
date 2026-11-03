# Spec Delta — ci-vcpkg-cache

## MODIFIED Requirements

### Requirement: Cache self-heals after toolchain or runner-image changes

The dependency cache SHALL recover after a runner-image or toolchain change that alters vcpkg ABI hashes: exactly
one rebuild, after which subsequent runs use the cache again. Runs SHALL NOT rebuild on every execution while a
stale cache is present. Because the cache's client runs on the runner's image, the job SHALL run on a pinned image
rather than a moving `-latest` label, and the run SHALL probe the binary cache before the install: when the probe
fails, the run SHALL build without the cache and report the failure in one line naming the measured cost, instead
of attempting a store per package.

#### Scenario: Runner image update invalidates ABI hashes

- **WHEN** a runner image update changes any vcpkg ABI hash and vcpkg rejects previously cached packages
- **THEN** the affected packages compile once, are re-cached under their new ABI versions, and the next run restores
  them
- **Case**: none — this states the ABI-version design of the feed, which is not exercised by a suite; the evidence
  is the restore count of each run, read per run in `evidence/runner-image-measurement.txt`

#### Scenario: Stale cache never blocks recovery

- **WHEN** vcpkg has restored cached packages that it subsequently rejects
- **THEN** the new build output is stored so the rejected stale packages are not restored again on later runs
- **Case**: none — a store that succeeds is read from the run's own log, not asserted by a suite; the transport
  this change adds a probe for is what a store depends on, and that probe has cases

#### Scenario: A run names the image it is given

- **WHEN** the build job's `runs-on:` names a hosted image
- **THEN** it names a released one (`ubuntu-<major>.<minor>`), and a `-latest` label is reported by the check that
  reads the workflow's pins, because a label's contents change without this repository's knowledge
- **Case**: `runtimes#aHostedRunnerImageIsPinned` — a fixture whose `runs-on:` is `ubuntu-latest` must be reported
  with its file and line, and one pinned to `ubuntu-24.04` must pass

#### Scenario: An unreachable cache costs the build only

- **WHEN** the binary cache's transport cannot be reached when the job starts
- **THEN** the probe reports it in one line naming the measured cost, the install runs without the cache, and no
  package is pushed — measured on `ubuntu-26.04`: 99 failed pushes over 62 minutes in a step that costs 1–2 minutes
  with the cache working
- **Case**: `vcpkg-cache#aRefusingFeedIsReportedAndDisablesTheCache`, `vcpkg-cache#aMissingClientIsReported`
