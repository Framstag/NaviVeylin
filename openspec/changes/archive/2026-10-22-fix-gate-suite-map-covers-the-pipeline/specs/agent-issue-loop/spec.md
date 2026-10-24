# Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The gate's suite map covers every script command the pipeline's check steps run

The profile SHALL name the pipeline its suite map is pinned to, and `LOOP_GATE_SUITES` SHALL carry every
`bash`/`sh` command that pipeline's `Check …` steps run, so that a landing the pipeline would refuse is
refused by the run instead of by `main`. The pin closes the command side only: a path a check reads but the
map does not name, and a step whose check is inline shell or a Gradle task, are residuals named beside the rule
in `guidelines/Build.md` §3.

#### Scenario: A pipeline step the map does not carry fails the loop's suite

- **WHEN** the map names none of the `bash`/`sh` commands a `Check …` step of the pipeline runs
- **THEN** the loop's suite SHALL fail, naming each missing command
- **AND** a profile that records no pipeline key at all SHALL fail, because its map is pinned to nothing
- **AND** a profile that records the key empty SHALL pass, having said it has no pipeline
- **Case**: `gate#theMapCoversEveryPipelineStep`

#### Scenario: A path the pipeline checks owes that step's command set

- **WHEN** a change's delta touches a path the map names for a pipeline check step
- **THEN** the gate's plan SHALL name every command the map carries for that step, the check and its
  self-test among them
- **AND** it SHALL NOT name a command of another step
- **AND** a path a check reads but the map does not name — `tools/loop/loop.conf`, `tools/tooling.json` and
  `tools/sensors/sensors.json` under `Check portability` — stays a residual the map cannot close
- **Case**: `gate#aPipelineCheckIsOwedByItsPaths`
