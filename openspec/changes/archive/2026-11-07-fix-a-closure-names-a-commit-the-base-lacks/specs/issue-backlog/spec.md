## ADDED Requirements

### Requirement: A closure's evidence is a claim about the base branch

The tooling SHALL refuse a completed closure whose stated evidence is a commit the base branch does not carry, and
SHALL report the base branch and what landing the item still needs instead of closing it.

#### Scenario: A closure naming a commit the base does not carry is refused

- **WHEN** a completed closure names the commit its claim rests on (`--landed`) and `git merge-base --is-ancestor`
  does not find that commit on the base branch the profile names
- **THEN** the call exits 3 with the reason `not-on-base`, naming the ref, the base branch and the landing step the
  item needs, and nothing is written: no comment, no state, no close
- **AND** the same call naming a commit the base carries writes the closure, and a closure naming no commit is
  unchanged — the refusal is the claim, not the path
- **Case**: `close#refusesAClosureTheBaseDoesNotCarry`
