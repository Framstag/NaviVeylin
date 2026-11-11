# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A gate's tallies belong to the run that wrote them

The gate SHALL make its tally directory one run's for the whole of that run: it SHALL claim the directory before its
first step writes a tally and SHALL refuse, naming the lock and the process that holds it, while another gate of the
same tree holds it; and it SHALL clear every carrier the directory still holds before it writes its own, so a step
file an earlier run wrote is never read as this run's. A tally SHALL carry the run that wrote it, and the gate SHALL
name the directory and that run on its console, so a reader copying the directory as the gate's evidence copies
exactly the steps the gate ran — not a step another run left behind.

#### Scenario: A carrier an earlier run left is not read as this gate's

- **WHEN** a gate starts in a tree whose tally directory still holds a carrier an earlier run wrote, and runs its own
  suite
- **THEN** the directory holds that suite's tally and no other when the gate ends, each carrier carries the run that
  wrote it, and the gate's line carries that suite's numbers only
- **Case**: `gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes`

#### Scenario: A second gate of the tree is refused while one runs

- **WHEN** a gate is started while another gate of the same tree holds the tally directory's lock
- **THEN** it refuses before running any step, names the lock and the process holding it, leaves that lock as it
  found it, and writes no `LOOP-GATE:` line — so a refused gate is never read as a green one
- **Case**: `gate#aSecondGateRefusesWhileOneRuns`
