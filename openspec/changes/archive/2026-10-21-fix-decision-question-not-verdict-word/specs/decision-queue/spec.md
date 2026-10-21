# decision-queue — deltas

## ADDED Requirements

### Requirement: The question is the sentence the escalation asks, not its verdict word

The question an item carries SHALL be read as the first line the escalation marks `**The decision:**`, and only a
comment without that marker SHALL fall back to its first non-empty line. The run writes its verdict word above
the sentence, so a reader that takes the first non-empty line answers — and binds an answer to — a verdict.

#### Scenario: The queue prints the sentence

- **WHEN** the queue is asked for an item whose escalation comment opens with its verdict word
- **THEN** the question it prints is the `**The decision:**` sentence, not the verdict word above it
- **Case**: `decisions#theQuestionIsNotTheVerdictWord`

#### Scenario: An answer binds to the sentence

- **WHEN** an answer is recorded on such an item
- **THEN** the question the answer names is that sentence, so a bare option number is readable beside the list it
  answered
- **Case**: `answer#theAnswerQuotesTheQuestionNotTheVerdictWord`

#### Scenario: The package pairs an older answer with the sentence

- **WHEN** a work package pairs an answer written before the answer path recorded its question with the comment
  that stood as that question
- **THEN** the question it carries is that comment's sentence, not its verdict word
- **Case**: `WorkPackage#questionIsNotTheVerdictWord`
