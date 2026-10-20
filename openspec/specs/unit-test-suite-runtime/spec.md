# unit-test-suite-runtime Specification

## Purpose

The JVM unit-test suites of the project's modules run to completion in a single invocation inside a
declared, bounded fork budget, and nothing a test creates — background work, retained bitmaps, native
surfaces — outlives that test, so a suite's result is attributable per class and reproducible from a
fresh checkout.

## Requirements

### Requirement: Test-created components release their background work

Every unit test that constructs a component owning long-lived background work — its own coroutine
scope, timers, render loops, a native surface or a large retained bitmap — SHALL release that
component before the test method returns. No background work, retained bitmap or native surface
created by a finished test SHALL remain alive for the remainder of the JVM run.

#### Scenario: Component released when the test returns

- **WHEN** a unit test constructs such a component and the test method returns
- **THEN** the component has been shut down and none of its background jobs is still active

#### Scenario: Every instance of a test class is released

- **WHEN** a test class constructs such a component in its per-test setup for many test methods
- **THEN** every instance that class created is released, including instances whose test method never
  exercised a shutdown path itself

#### Scenario: Shutdown is idempotent

- **WHEN** a test method shuts such a component down itself and the test teardown shuts the same
  instance down again
- **THEN** no exception is raised and the test result is unaffected

#### Scenario: A leak fails the test instead of staying silent

- **WHEN** a component constructed by a test still has active background work after the teardown
  attempt
- **THEN** the test fails, so the leak is attributable to that test class rather than surfacing later
  as an out-of-memory failure of unrelated classes

#### Scenario: Retained render buffers become reclaimable

- **WHEN** a component that retained a rendered bitmap is shut down
- **THEN** the retained bitmap is released and no longer reported by the component's own buffer state

### Requirement: Module unit-test suite completes in a single invocation

The unit-test suite of a module SHALL complete in a single Gradle test invocation within the module's
declared fork budget, writing one result XML per executed test class. A run SHALL NOT require
splitting test classes across multiple invocations, and the suite SHALL also complete in a single
invocation when the coverage instrumentation agent is attached.

#### Scenario: Auto module suite in one invocation

- **WHEN** `:auto:testDebugUnitTest` is executed as one invocation at the module's declared fork budget
- **THEN** all of the module's test classes execute, one result XML exists per executed class, and the
  run completes without `OutOfMemoryError`

#### Scenario: App module suites in one invocation per flavor

- **WHEN** `:app:testMobileDebugUnitTest` or `:app:testAutomotiveDebugUnitTest` is executed as one
  invocation at the module's declared fork budget
- **THEN** all of that flavor's test classes execute, one result XML exists per executed class, and no
  test fails because the JVM ran out of headroom mid-suite

#### Scenario: Coverage report generated in one invocation

- **WHEN** the aggregated coverage report tasks are executed for the Kotlin modules
- **THEN** the same test suite completes in that single invocation and the module's coverage report is
  produced

#### Scenario: Documented procedure needs no batching

- **WHEN** the documented test procedure in `guidelines/Build.md` is followed for a module
- **THEN** it prescribes a single invocation and does not require an operator to split test classes
  into batches

#### Scenario: Failures stay attributable

- **WHEN** a test class in the suite fails
- **THEN** the failure is reported for that class with its own result XML, and the run does not end in
  a memory failure that leaves the suite without per-class results

### Requirement: Unit-test fork budget is declared and bounded

A module whose unit-test suite cannot complete within the build plugin's default fork heap SHALL
declare its fork budget in that module's build script: a heap ceiling and, when a single fork cannot
hold the suite, a fork cadence that bounds the peak. The declared values SHALL apply to a fresh
checkout without local configuration and SHALL be accompanied by the measurement that justifies them.

#### Scenario: Declared budget is applied to the fork

- **WHEN** a module's unit tests run
- **THEN** the test JVM is launched with the declared heap ceiling and a fresh JVM is started at the
  declared fork cadence

#### Scenario: Peak memory stays inside the declared bound

- **WHEN** the declared fork cadence is reached during a suite run
- **THEN** the remaining test classes continue in a fresh JVM and the heap of a single fork stays
  within the declared ceiling

#### Scenario: Budget is reproducible from a fresh checkout

- **WHEN** a fresh checkout runs the module's unit tests without any local Gradle configuration
- **THEN** the same fork budget applies

#### Scenario: Budget values are justified by a measurement

- **WHEN** a module's declared fork budget is introduced or changed
- **THEN** `guidelines/Build.md` records the suite, the class and test counts, and the measured heap
  headroom that justify the declared values

### Requirement: Unit-test parallelism is declared and result-preserving

A module SHALL declare, together with its fork budget, how many test JVMs may execute concurrently, and
that declaration SHALL apply to a fresh checkout without local configuration. A suite executed with the
declared number of concurrent JVMs SHALL write one result XML per executed class and SHALL report the
same failing classes as the same suite executed in a single JVM.

#### Scenario: Declared concurrency applies to a fresh checkout

- **WHEN** a module's unit tests run
- **THEN** at most the declared number of test JVMs run concurrently and each is launched with the
  declared heap ceiling

#### Scenario: The failure set is unchanged by concurrency

- **WHEN** a module's suite runs with the declared number of concurrent JVMs
- **THEN** its failing classes are exactly the failing classes of the same suite run in a single JVM

#### Scenario: Per-class results survive concurrency

- **WHEN** test classes execute in concurrent JVMs
- **THEN** every executed class has its own result XML and the run ends with a verdict rather than a
  memory failure or a lost result file

#### Scenario: Declared concurrency is justified by a measurement

- **WHEN** a module's declared concurrency is introduced or changed
- **THEN** the module's budget note and `guidelines/Build.md` record the concurrency, the wall time and
  peak memory measured at it, and the failure set observed at that concurrency

### Requirement: A unit test controls the time its subject waits on

A unit test SHALL decide its outcome from the behaviour under test and from time it controls, never from
wall-clock elapsed time or a real-thread wait. A case whose subject decides from a time window SHALL
advance an injected time source or the test scheduler instead of sleeping; a case that awaits a state
change SHALL await the observable state on that scheduler instead of a deadline computed from the system
clock.

#### Scenario: Injected time instead of a real clock

- **WHEN** a case exercises behaviour that depends on a staleness or throttle window
- **THEN** the case advances the injected time source past that window and the behaviour is observed
  without the case waiting for real time to elapse

#### Scenario: Awaiting state, not a deadline

- **WHEN** a case awaits a state change published by a component that works off the test thread
- **THEN** the case drives the work on the test scheduler and awaits the observable condition
- **AND** no test helper computes a deadline from the system clock or paces itself with a sleep

### Requirement: A module's failure set is independent of host load and concurrency

The module's failing classes SHALL be the same whether its suite runs on an idle host or a loaded one, at
the declared concurrency or in a single JVM.

#### Scenario: Load does not change the failure set

- **WHEN** the module's suite runs at the declared concurrency while the host is otherwise loaded
- **THEN** the failing classes are exactly those of the same suite run on an idle host in a single JVM

#### Scenario: A formerly flaky case is decided by its subject

- **WHEN** the previously load-dependent cases run inside the full module suite
- **THEN** each is green in a forced repeated run whose tallies are recorded, and none of them passes
  or fails on the strength of elapsed real time

### Requirement: Wall-clock waits in test sources are refused by a build check

A unit test or test helper SHALL NOT pace itself with a fixed sleep, SHALL NOT compute an await
deadline from the system clock, and SHALL NOT block a thread on a condition an injected time source or
the test scheduler can drive. A build check SHALL refuse those patterns in every test-bearing module,
naming the file and line. The check has no exception list: a wait that cannot be converted SHALL be
replaced by driving the seam, never declared as an allowed wait.

#### Scenario: A new fixed sleep fails the build

- **WHEN** a test source paces a case with a fixed sleep
- **THEN** the check fails the build and names the offending file and line

#### Scenario: A system-clock deadline loop fails the build

- **WHEN** a test helper awaits a condition until a deadline computed from the system clock
- **THEN** the check fails the build and names the offending helper

#### Scenario: Converting a wait keeps the behaviour covered

- **WHEN** a waiting case is converted to advance an injected time source or the test scheduler
- **THEN** the case exercises the same behaviour as before, with its result decided by that controlled
  time rather than by elapsed real time

#### Scenario: The refused patterns stay refused

- **WHEN** the check is run against the test sources of the test-bearing modules
- **THEN** it names every remaining wait it finds and passes only for the sources that contain none

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

### Requirement: A published work state is true when it is read

A component that publishes a state for a test to await SHALL publish a state that is true of the moment the
case reads it: work handed to the component SHALL take it out of the state a case treats as idle, the
transition SHALL be made under the lock the reader takes, and a case that must know a piece of work is
*finished* SHALL await the finished work's own state rather than the component's idleness.

#### Scenario: Handed work is not an idle worker

- **WHEN** a case hands the component work that does not signal the worker, after parking it, and then asks
  whether it is parked
- **THEN** the answer is not parked, so a case that reads the component's output on that answer reads it
  after the work was taken up rather than before

#### Scenario: A reader and the transition share one lock

- **WHEN** the transition out of the state and the reader's question are inspected
- **THEN** both happen under the same lock, so no reader observes the state of a moment the worker has
  already left

#### Scenario: A flush is awaited as a flush

- **WHEN** a case must know that a flush has happened before it reads the file
- **THEN** it awaits the flush's own published state — a count of finished batches, which only grows — rather
  than the worker being idle, which is also false while the flush's tail is still pending and which a stale
  flag used to answer before this requirement

### Requirement: A diagnostics assertion is attributed to the lines its own case wrote

A case that asserts on the diagnostics log SHALL decide its result from the lines its own subject wrote, and
SHALL NOT decide it from the whole contents of the configured file: the target `DiagnosticsLog.initForTest`
installs is process-wide, so work another class left running logs into it while the case holds it, and a count,
a `single()` or a whole-file substring measures those lines too. A case identifies its own lines by the tag or
marker only its subject's message carries.

#### Scenario: A foreign line does not decide the fallback case

- **WHEN** a line another class logged is in the file while the unusable-stored-source case asserts the
  fallback line's text and the absence of a coordinate
- **THEN** the case passes on its own line; an own line that carried a coordinate, that was duplicated or
  missing, or that named a value other than the stored one would fail it
- **Case**: `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn`

#### Scenario: A foreign line is not counted as a post

- **WHEN** a line another class logged is in the file while the host-send case asserts that one post was
  recorded
- **THEN** exactly the one `HOST NOTIF post` entry is counted
- **Case**: `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp`

#### Scenario: A sequence of posts is counted as posts

- **WHEN** a line another class logged is in the file while the case asserts that two posts produced two
  entries
- **THEN** exactly the two `HOST NOTIF post` entries are counted
- **Case**: `NotificationHostSendLoggingTest.everyPostAddsExactlyOneEntry`

#### Scenario: A foreign line is not a fallback line

- **WHEN** a line another class logged is in the file while the repository-without-a-URL case asserts its own
  fallback line
- **THEN** exactly the one `repository source without a base URL` entry is asserted on
- **Case**: `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn`

#### Scenario: A foreign line is not a fault record

- **WHEN** a line another class logged is in the file while the confined-fault case reads the record it
  expects
- **THEN** the single `ENGINE_FAULT_TAG` record is the one asserted on
- **Case**: `EngineFaultHandlerTest.aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce`

#### Scenario: A foreign line is not a second fault record

- **WHEN** a line another class logged is in the file while the case asserts that a throwing publish still
  left exactly one record
- **THEN** exactly the one `ENGINE_FAULT_TAG` record is counted
- **Case**: `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands`

#### Scenario: A foreign line is not a fault record for the file assertion

- **WHEN** a line another class logged is in the file while the logcat-and-file case asserts the record
  reached the file
- **THEN** exactly the one `ENGINE_FAULT_TAG` entry is counted in the file
- **Case**: `EngineFaultHandlerTest.theHandlerMirrorsToLogcatAndTheRecordReachesTheFile`

#### Scenario: A foreign line is not a surface entry

- **WHEN** a line another class logged is in the file while the surface-lifetime case asserts one adopt and
  one release
- **THEN** exactly the two `HOST surface` entries are counted, in that order
- **Case**: `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis`

#### Scenario: A foreign line is not the calculation record

- **WHEN** a line another class logged is in the file while the route-calculation case asserts its own entry
  reached the file
- **THEN** exactly the one `ROUTE calc done:` entry is counted in the file
- **Case**: `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`

#### Residual paths outside this change

The rule binds the scenarios above and every case added or converted after them. Four shapes that predate the
change still decide from the whole file and keep their shape here; naming them keeps the rule's scope honest:

- `app/src/test/java/com/naviveylin/ui/route/RouteGeometryTest.kt:110` — `.last { }` on the shared `ROUTE` tag
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt:486` — `single { }` on the shared
  `REROUTE` tag, which a foreign line makes throw
- `auto/src/test/java/com/naviveylin/auto/CarHostGuardsTest.kt:69` — a whole-sink negative,
  `entries().none { it.contains("rejected") }`
- `auto/src/test/java/com/naviveylin/auto/SessionLogTest.kt:70` — the same negative shape on `geo:1.0,2.0`

Scenarios 5–7 attribute at tag level (`ENGINE_FAULT_TAG`), the weakest form the rule allows: a foreign record
carrying that tag would still be counted.

### Requirement: A test class that loads the JNI stub declares no sandbox pin

A test source file in a module that ships the host JNI stub (`src/test/jniLibs/libosmscout_client_java.so`) that
loads that stub SHALL NOT pin a Robolectric sandbox: no line-anchored `@Config(… sdk …)` and no
`@GraphicsMode(…)` on such a file. A run that adds such a pin SHALL fail, naming the file and the line.

#### Scenario: A stub-loading class that pins a sandbox fails the case

- **WHEN** the test sources of the stub-shipping modules are read and one file names the `com.framstag.libosmscout.client`
  package (whose `OSMScoutClient` static initializer calls `System.loadLibrary`) while declaring a line-anchored
  `@Config(… sdk …)` or `@GraphicsMode(…)`
- **THEN** the case fails and its message names the file, the line and the annotation it found
- **Case**: `JniStubSandboxRuleTest.noTestClassThatLoadsTheStubPinsTheSandbox`

#### Scenario: Both stub modules are read

- **WHEN** the case scans the `:app` and `:auto` test sources
- **THEN** each module root contributes at least one file, so neither root can go unread and leave the first
  scenario passing on an empty list
- **Case**: `JniStubSandboxRuleTest.everyStubModuleIsScanned`

#### Scenario: The detector reports a pinned stub-loading class

- **WHEN** the detector is given a fixture that imports the client package and declares a line-anchored
  `@Config(sdk = …)`
- **THEN** it reports exactly one finding and that finding names the fixture's line
- **Case**: `JniStubSandboxRuleTest.aPinnedStubLoadingClassIsReported`

#### Scenario: The detector reports a stub-loading class that pins a graphics mode

- **WHEN** the detector is given a fixture that imports the client package and declares a line-anchored
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and no `@Config(… sdk …)`
- **THEN** it reports exactly one finding and that finding names the fixture's line, so the second refused form
  is measured rather than stated only
- **Case**: `JniStubSandboxRuleTest.aGraphicsModeOnAStubLoadingClassIsReported`

#### Scenario: A pinned class that loads no stub is allowed

- **WHEN** the detector is given a fixture that declares a line-anchored `@Config(sdk = …)` and names no client
  package
- **THEN** it reports no finding, so the rule does not forbid a sandbox pin as such
- **Case**: `JniStubSandboxRuleTest.aPinnedClassThatLoadsNoStubIsAllowed`

#### Scenario: A qualifier on a stub-loading class is allowed

- **WHEN** the detector is given a fixture that imports the client package and declares
  `@Config(qualifiers = "de")` with no sdk argument
- **THEN** it reports no finding, because a qualifier is applied per test and starts no second sandbox, so the
  locale cases stay legal
- **Case**: `JniStubSandboxRuleTest.aQualifierOnAStubLoadingClassIsAllowed`

#### Scenario: The unpinned engine class keeps its cases in the default sandbox

- **WHEN** the engine class that used to pin SDK 34 runs in the suite with no sandbox annotation on the class
- **THEN** its six cases still pass, so removing the pin changed no measured behaviour; which SDK level the
  default sandbox resolves to is not asserted here
- **Case**: `NavigationEngineAcquisitionTest.acquire_withRoute_startsNavigation`

Scope of the case, so the claim is not wider than the evidence: the scan reads `*.kt` files under each
stub-shipping module's `src/test/java`, so a module-wide sandbox pinned in `src/test/resources/robolectric.properties`
(`:auto` carries `sdk=34` there) is outside this requirement, and so is a file that reaches the stub without
naming the client package. The case lives in `:app`, so a `:auto`-only test run enforces nothing on `:auto`
sources; the gate runs both modules.

### Requirement: A test that builds a time-hopped repository wires the hop

Every unit test or test helper that constructs a component whose persisted state is published inside a
dispatcher hop SHALL point that hop at a dispatcher the test drives. The component SHALL keep its production
value and its production ordering: the repair is confined to test sources.

#### Scenario: Every construction in the module's test sources is wired

- **WHEN** the test sources of every test-bearing module are read and the repository's construction is found in
  them
- **THEN** every construction names a dispatcher the test drives and none is reported, with the number of roots
  and files read printed before the verdict
- **Case**: `SearchHistoryDispatcherRuleTest.noTestLeavesTheSearchHistoryRepositoryIoHopOnAProductionDispatcher`

#### Scenario: A chained wiring is accepted

- **WHEN** a construction points the hop at the test's dispatcher through a chained `.also { … }` or
  `.apply { … }` on the construction itself
- **THEN** the check accepts it, so the form the repair writes is the form the check reads
- **Case**: `SearchHistoryDispatcherRuleTest.aChainedWiringIsAllowed`

#### Scenario: A property wired later in the same file is accepted

- **WHEN** a test assigns the repository to a property and points that property's hop at the test's dispatcher
  later in the same file — the form the class setup methods already use
- **THEN** the check accepts it, so the rule does not force one spelling on every test class
- **Case**: `SearchHistoryDispatcherRuleTest.aNameWiredLaterInTheFileIsAllowed`

### Requirement: An unwired hop is refused from the sources alone

A construction that leaves the hop at the component's production value SHALL be refused by a host check that
names the file and the line and that decides its verdict from the sources, without executing a test.

#### Scenario: A construction that leaves the hop at the production value is refused

- **WHEN** a test source constructs the repository and never names the hop's dispatcher
- **THEN** the check fails and its finding carries the file, the line and the construction, so the site can be
  repaired without running the suite
- **Case**: `SearchHistoryDispatcherRuleTest.anUnwiredConstructionIsReported`

#### Scenario: A module whose sources disappear fails the check

- **WHEN** a declared test-source root no longer exists — a module was renamed or removed
- **THEN** the check fails instead of scanning nothing, so the invariant cannot be lost silently
- **Case**: `SearchHistoryDispatcherRuleTest.everyTestBearingModuleIsScanned`
