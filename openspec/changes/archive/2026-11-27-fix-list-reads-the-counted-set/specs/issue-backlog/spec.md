## ADDED Requirements

### Requirement: A read of the tracker covers the set the tracker counts

Every read `tools/issues/list.sh` takes SHALL be over the set the tracker counts rather than over a constant window: the tool SHALL count the items the tracker holds, ask the list for exactly that count plus one, and refuse a read that comes back that full as `issue-set-uncovered` — naming the counted set and the read — instead of answering from it. `--limit <n>` SHALL remain the caller's explicit window, and only then is the read a page.

#### Scenario: An item older than the newest page resolves by its marker

- **WHEN** `--id <n>` names a migrated entry that sits outside the newest page of the tracker
- **THEN** the one issue carrying that marker is answered, and it is not reported as an item that does not exist
- **Case**: `list#readsTheWholeSetNotTheNewestPage`

#### Scenario: A filtered selection is drawn from the whole set

- **WHEN** a selection is asked for by a label every item of the tracker carries, on a tracker larger than the newest page
- **THEN** every item is in the selection, including the oldest one outside that page
- **Case**: `list#readsTheWholeSetNotTheNewestPage`

#### Scenario: A window a caller asks for is the window it gets

- **WHEN** the caller passes `--limit <n>`
- **THEN** the read is that window and the tool answers from it, because a window is now the caller's own word for what it wants read
- **Case**: `list#readsTheWholeSetNotTheNewestPage`
