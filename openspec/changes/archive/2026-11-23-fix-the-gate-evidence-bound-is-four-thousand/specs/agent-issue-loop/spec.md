# Spec Delta

## ADDED Requirements

### Requirement: The gate's evidence is held at its own bound, head and tail

The class of result `guidelines/BuildEvidence.md` §4d attributes a red run from — a gate log or a suite XML, matched
by its path or by its `LOOP-GATE` line or `<testsuite` root — SHALL be held at `GATE_EVIDENCE_BOUND`, four thousand
characters, or the recorded bound when a project records a wider one. A cut of that class SHALL keep both ends: its
first half and its last half.

#### Scenario: A gate log above the class's bound keeps both ends

- **WHEN** a gate log of 5 000 characters is handed to the bound while the project records 2 000
- **THEN** it arrives cut at 4 000 — its first 2 000 characters and its last 2 000, the verdict line at the head and
  the failed step at the tail — with one line naming both ends and how to read the rest, the middle 1 000 withheld
- **Case**: `adapter#theResultBoundExemptsGateEvidence`
