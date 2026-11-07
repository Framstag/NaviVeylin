# Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: The gate's suite map covers every script command the pipeline's check steps run

The profile SHALL name the pipeline its suite map is pinned to. `LOOP_GATE_SUITES` SHALL carry every
`bash`/`sh` command that pipeline's `Check …` steps run, and every path those steps' tools read, so that a
landing the pipeline would refuse is refused by the run instead of by `main`. A step whose check is inline
shell or a Gradle task, and a reader edge a later step adds, are residuals named beside the rule in
`guidelines/Build.md` §3.

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
- **Case**: `gate#aPipelineCheckIsOwedByItsPaths`

#### Scenario: A path a check reads owes that step's commands

- **WHEN** a change's delta touches a path one of the pipeline's `Check …` steps reads — a tool's configuration
  or an engine that step scans
- **THEN** the gate's plan SHALL name every command of the step that reads it, so the run refuses what the
  step would refuse
- **AND** touching `tools/loop/loop.conf` SHALL owe `Check portability`'s check, its self-test and its mutation
  list among them, and touching `tools/issues/create.sh` SHALL owe the same step's check
- **AND** the three configurations `tools/check-portability.sh` opens and the three engine globs it scans
  SHALL each owe that step's commands, not only a tool's own glob
- **AND** an engine another step's case greps SHALL owe that step as well — a delta touching
  `tools/loop/worktree.sh` owes `Check issue tooling`'s self-test and its mutation sets
- **Case**: `gate#aPathACheckReadsOwesThatStepsCommandSet`
