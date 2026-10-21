# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The mutation harness leaves the tree exactly as it found it

The harness SHALL keep the backups of the files it mutates beside the tree it mutates, on the tree's own
filesystem, unless the caller names another place. A backup it could not take SHALL stop the harness before
anything is mutated, and a restore SHALL never write a backup that is empty or missing.

#### Scenario: The backups are not the host's tmpfs

- **WHEN** the harness runs with no `TMPDIR` set
- **THEN** its scratch tree lies under the project's gitignored scratch root, it is named once, and it is
  removed when the harness exits; `TMPDIR` still moves it
- **Case**: `tools/loop-mutations-selftest.sh` (`the scratch tree is the project's, not the host's tmpfs`)

#### Scenario: A backup that did not land stops the harness instead of emptying the file

- **WHEN** a backup cannot be taken, or the restore is offered a backup that is empty or missing
- **THEN** the harness exits 2 with the reason, the file it protects still holds its content, and nothing is
  mutated after that point
- **Case**: `tools/loop-mutations-selftest.sh` (`a restore from an empty backup exits 2`, via
  `tools/loop-mutations.sh --probe empty-backup`)
