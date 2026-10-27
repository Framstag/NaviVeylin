## ADDED Requirements

### Requirement: A reused worktree is the tree the new base records

A kept worktree that `create` switches to a new base SHALL be left with the configured submodule checked out at the gitlink that base records, so the tree the next item starts on is the tree `git status` reports clean. The path SHALL be the project's configuration, never one an engine names.

#### Scenario: The pin the new base records replaces the previous branch's

- **WHEN** a kept worktree whose submodule sits on the previous branch's pin is reused for a new base that records another pin
- **THEN** the module is checked out at the new base's gitlink, the tree is clean, and the item starts on it
- **Case**: `worktree#checksOutTheNewPinWhenItReusesTheWorktree`
