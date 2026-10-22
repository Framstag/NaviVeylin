# Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The gate's suite map covers every command the pipeline's check steps run

The profile SHALL name the pipeline its suite map is pinned to, and `LOOP_GATE_SUITES` SHALL carry every shell
command that pipeline's `Check …` steps run, so that a landing the pipeline would refuse is refused by the run
instead of by `main`.

#### Scenario: A pipeline step the map does not carry fails the loop's suite

- **WHEN** the map names none of the commands a `Check …` step of the pipeline runs
- **THEN** the loop's suite SHALL fail, naming each missing command
- **AND** a profile that records no pipeline key SHALL fail, because its map is pinned to nothing
- **AND** a profile that records the key empty SHALL pass, having said it has no pipeline
- **Case**: `gate#theMapCoversEveryPipelineStep`

#### Scenario: A path the pipeline checks owes that step's command set

- **WHEN** a change's delta touches a path inside a pipeline check step's own surface
- **THEN** the gate's plan SHALL name every command that step runs, the check and its self-test among them
- **AND** it SHALL NOT name a command of another step
- **Case**: `gate#aPipelineCheckIsOwedByItsPaths`
