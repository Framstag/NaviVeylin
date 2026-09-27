# Spec Delta — osmscout-jni

## ADDED Requirements

### Requirement: Favorite calls are safe while the favorite store is replaced

The JNI bridge SHALL replace the native favorite store atomically with respect to favorite calls. The store is replaced whenever the favorites file is loaded or saved, which today recreates the underlying service instance. A favorite call SHALL NEVER run against a service instance that has been destroyed or replaced, and replacing the store SHALL NEVER take place while a favorite call is in flight on it. Concurrent favorite calls SHALL be serialised. Under concurrency the bridge SHALL NOT fault.

#### Scenario: Store replacement during an in-flight favorite call does not fault

- **WHEN** the favorites file is loaded or saved while another thread is inside a favorite call
- **THEN** no favorite call SHALL operate on a destroyed service instance
- **AND** no native fault (SIGSEGV, abort, use-after-free) SHALL occur

#### Scenario: A store replacement is never observed half-applied

- **WHEN** a favorite read runs while the store is being replaced (loaded or rebuilt from the caller's data)
- **THEN** the read SHALL return either the state before the replacement or the fully replaced store
- **AND** SHALL NOT return an empty or partially rebuilt store

#### Scenario: Concurrent mutation and persist do not discard each other

- **WHEN** a persist that rebuilds the store from caller-supplied data runs concurrently with a favorite mutation
- **THEN** the bridge SHALL NOT fault and SHALL NOT leave the store partially rebuilt
- **AND** a mutation that completed before the persist took its input data SHALL still be present afterwards

#### Scenario: Sequential favorite calls are unchanged

- **WHEN** favorite calls are issued one after another without overlapping
- **THEN** they SHALL behave exactly as before this change
- **AND** the favorites file SHALL contain the same content as before
