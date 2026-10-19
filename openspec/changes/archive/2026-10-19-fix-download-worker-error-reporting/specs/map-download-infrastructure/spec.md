# Spec Delta — map-download-infrastructure

## ADDED Requirements

### Requirement: A download worker that dies reports the failure

A provider download runs on its own worker thread. The system SHALL report a worker that terminates with
any throwable — an `Exception` or an `Error` — through the download listener it was started with, so a
download that cannot run becomes the entry's error state instead of an uncaught throwable on a thread the
caller does not observe.

#### Scenario: A worker that dies of an Error is reported

- **WHEN** a provider download's worker terminates with an `Error` — a native call whose symbol is missing, or a stack overflow
- **THEN** the listener receives `onError` and the message names the failure's class
- **AND** no uncaught throwable escapes the worker thread
- **AND** the case `MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnErrorIsReportedThroughTheListener` exercises this

#### Scenario: A worker that dies of an Exception keeps its message

- **WHEN** a provider download's worker terminates with an `Exception` that carries a message
- **THEN** the listener receives `onError` with that message, as before
- **AND** the case `MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnExceptionKeepsItsMessage` exercises this
