# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A review round is non-interactive, and a question is a cut-short answer

The reviewer prompt SHALL state, beside the output contract, that the round runs with nobody to ask: never ask a
question, never offer to proceed or to confirm, and never offer a summary instead of the round. It SHALL
name an answer that is a question a cut-short answer — no verdict is read, the retry spends the byte-identical
prompt, and the round is recorded `unanswered` — and leave a refusal's
`LOOP-DECISION` line to the contract it points at. The clause SHALL stand in both rounds' text.

#### Scenario: Both rounds are told the round is non-interactive

- **WHEN** the run renders the reviewer prompt, once for the round before the gate and once for the round after it
- **THEN** each carries the clause beside its output contract, the round before the gate carrying it because that
  round is the same prompt with its own section substituted rather than a prompt of its own
- **Case**: `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle`

#### Scenario: A question is named a cut-short answer, and the cost is stated

- **WHEN** the reviewer prompt is rendered
- **THEN** it names an answer that is a question a cut-short answer and states what such an answer costs — the one
  retry spends a byte-identical prompt that asks the same question again, and the round is recorded as unanswered
- **Case**: `prompt#reviewerCarriesTheEightItems`

#### Scenario: A refusal's decision line is not the question the clause forbids

- **WHEN** the reviewer prompt is rendered
- **THEN** the exception beside the clause says the `LOOP-DECISION` line a refusal owes is part of that contract
- **Case**: `prompt#reviewerCarriesTheEightItems`
