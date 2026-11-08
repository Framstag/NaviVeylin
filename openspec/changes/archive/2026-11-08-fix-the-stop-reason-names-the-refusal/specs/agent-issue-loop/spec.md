# agent-issue-loop delta

## ADDED Requirements

### Requirement: The run's stop reason names the refusal it stopped on

A run that stops because a child could not start SHALL name, beside the status and the answer's size it already
names, the refusal the child's own event stream recorded — the provider and the model of the last refused request
and the refusal's own text — so a person reading the stop tells a provider that refused every call from a child that
broke. A stream that is absent, unreadable or carries no refusal SHALL leave the reason as the base wrote it.

#### Scenario: A refused provider is named in the stop

- **WHEN** a child ends with no verdict line and leaves the item's tree at the run's base commit, and its event
  stream carries a refused request — a `turn_end` on `stopReason: error` or an `auto_retry_end` with a `finalError`
- **THEN** the run's recorded stop reason names the provider and the model of the last refused request and quotes the
  refusal's own text, beside the status and the answer's size it already names, and a stream that carries no refusal
  leaves the reason exactly as the base wrote it
- **Case**: `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`
