## ADDED Requirements

### Requirement: The stale-citation repair walks the whole backlog

`tools/issues/resolve-stale.sh` SHALL read the whole open backlog — the count of open issues plus one, refused
when the read comes back that full — so that a citation which broke in an item older than any window is named,
nominated and reported like any other, because the capability's contract is stated over the backlog and not over
its newest page.

#### Scenario: An old item's broken citation is still nominated

- **WHEN** the backlog holds 405 open items and the oldest carries a citation the tree does not hold
- **THEN** the resolver reads all 405, reports the citation unresolved and nominates that item — case
  `resolver#anOldItemsCitationIsStillNominated`

LOOP-ROUTE: guidelines/BacklogTooling.md §9
