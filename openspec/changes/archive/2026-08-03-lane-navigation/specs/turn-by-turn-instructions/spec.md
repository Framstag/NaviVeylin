# Turn-by-Turn Instructions

## MODIFIED REQUIREMENTS

### Requirement: Left-aligned layout

The next-turn overlay SHALL be aligned to the left edge of the screen (was centered).

### Requirement: Text wrapping

Next-turn description and next-next-turn description SHALL support line wrapping (`maxLines=2`, `TextOverflow.Ellipsis`).

### Requirement: No gap when lanes absent

When lane guidance is not shown, there SHALL be no visual gap between the next-turn row and the next-next-turn row. Spacers SHALL only be inserted when lane data is present.

### Requirement: Next-next turn smaller

Next-next turn text SHALL use `bodySmall` typography (smaller than next-turn's `bodyLarge`/`titleLarge`). This requirement is already implemented.
