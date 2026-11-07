## ADDED Requirements

### Requirement: The spend report charges a session to the role its prompt names

`tools/loop-spend.py` SHALL read a session's role the way the engine starts that child — from the name of the prompt
file that is the session's first user message, when that message is the path to a file whose name ends `.md`, the only
form the run renders (`run_child` writes `*-prompt.md`, and every one of the archive's 730 sessions carries that form),
applying the arms in the order `child_role` applies them (`tools/loop/run.sh`) — and SHALL charge the session's turns,
tokens and cost to that role and to no other, so its per-role table and its `--json` `byRole` name the roles the engine
starts: `worker`, `worker-rework`, `worker-gate`, `worker-tail`, `reviewer` and `reviewer-pre-gate`. A first user
message in any other form — prompt text, or a path whose name does not end `.md` — SHALL keep the reading the words
give it (a `reviewer` word, else `worker`); a `.md` prompt name no arm of `child_role` matches SHALL be charged to the
name it carries without its extension rather than folded into the worker; and a role with no session SHALL be absent
from the table rather than printed as a zero.

#### Scenario: Each role the engine starts is charged to its own name

- **WHEN** a stream holds one session started from each prompt the run renders — `worker-prompt.md`,
  `worker-prompt-round2.md`, `worker-gate-prompt.md`, `worker-tail-prompt.md`, `reviewer-prompt-1.md` and
  `reviewer-pre-gate-prompt.md` — each with one turn of its own known size
- **THEN** `--json`'s `byRole` carries those six keys and no seventh, each holding that session's one turn, its one
  session and its own token total
- **Case**: `spend#eachRoleTheEngineStartsIsChargedToItsOwnName`

#### Scenario: The round before the gate is charged to neither the reviewer nor the worker

- **WHEN** a session's first user message is a `reviewer-pre-gate-prompt.md` path, beside a reviewer session and a
  worker session in the same stream
- **THEN** its turn and its tokens are counted under `reviewer-pre-gate`, and the `reviewer` and `worker` entries carry
  only their own sessions' turns and tokens
- **Case**: `spend#theRoundBeforeTheGateIsChargedApart`

#### Scenario: The per-role table prints the roles the JSON names

- **WHEN** the report prints its per-role table over a stream that holds a session of each of the six roles
- **THEN** the table carries one row per role, each of them a key of `--json`'s `byRole`, and no row for a role the
  stream holds no session of
- **Case**: `spend#thePerRoleTableNamesWhatTheJsonCarries`

#### Scenario: A prompt outside the engine's arms keeps the reading it had

- **WHEN** a session's first user message is prompt text rather than a path, or a prompt file whose name no arm of
  `child_role` matches
- **THEN** the text form is charged to `reviewer` where its words name a reviewer and to `worker` otherwise, and the
  unmatched name is charged to the name it carries rather than to the worker
- **Case**: `spend#anUnmatchedPromptKeepsItsOwnReading`
