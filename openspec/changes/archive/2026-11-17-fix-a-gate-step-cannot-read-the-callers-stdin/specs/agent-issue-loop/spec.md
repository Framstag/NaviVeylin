# agent-issue-loop delta

## ADDED Requirements

### Requirement: A gate step is given a stdin of its own

A gate SHALL start every step with its stdin read from the empty device, so no command a step runs can read the stdin
the gate inherited. The gate starts a step in a process group of its own, which a terminal treats as a background
group, and a read from that group stops the child with `SIGTTIN` — a stop no `SIGCONT` undoes while the read stands,
leaving the gate silent for the step's whole bound.

#### Scenario: A step that reads stdin completes

- **WHEN** the gate runs a suite whose first act is a read of its stdin, started under a caller whose stdin delivers
  nothing
- **THEN** that read returns end-of-file at once, the suite prints its own summary, and the gate's verdict is read from
  that summary — the step is neither ended at its bound nor stopped by the terminal, and its command has not consumed
  anything the gate's caller handed the gate
- **Case**: `gate#aStepCannotReadTheCallersStdin`
