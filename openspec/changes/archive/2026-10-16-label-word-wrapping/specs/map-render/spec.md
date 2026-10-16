# Spec Delta — map-render

## ADDED Requirements

### Requirement: Labels wrap at word boundaries within the configured bounds
The renderer SHALL break a label into lines at word boundaries while the label is wider than the
line-wrapping map parameters allow, SHALL draw the lines stacked inside the label's rectangle, and
SHALL state those parameters in the rendering call instead of relying on the library defaults. A word
SHALL NOT be broken. A wrapped label SHALL report the ink width of its widest line and the height of
its stacked lines to the label layouter.

#### Scenario: A multi-word label longer than the bound wraps
- **WHEN** a label with several words is measured with wrapping enabled and a line width below the
  width of the whole text
- **THEN** its glyphs SHALL sit on more than one baseline
- **THEN** its reported width SHALL be smaller than the width of the same label measured unwrapped

#### Scenario: A one-word label is never broken
- **WHEN** a one-word label is measured with wrapping enabled and a line width below the width of that
  word
- **THEN** all its glyphs SHALL sit on a single baseline
- **THEN** its reported width and height SHALL equal those of the same label measured unwrapped

#### Scenario: A label below the minimum character count stays on one line
- **WHEN** a label is measured with wrapping enabled, a line width below the width of the text and a
  minimum character count above the length of that text
- **THEN** all its glyphs SHALL sit on a single baseline
- **THEN** its reported width and height SHALL equal those of the same label measured unwrapped

#### Scenario: A wrapped label reports the size it draws
- **WHEN** a label wraps into more than one line
- **THEN** its reported width SHALL be the ink width of its widest line
- **THEN** its reported height SHALL be larger than the height of the same label measured unwrapped

#### Scenario: Every glyph is drawn once
- **WHEN** a label wraps into lines
- **THEN** the glyphs it yields SHALL be one per character of the text, less one per line break, whose
  whitespace is not drawn

### Requirement: The wrapping is effective in the backend the Android build uses
The wrapping SHALL be effective in the Cairo FreeType/cairo variant, which is the variant the Android
build compiles, SHALL be configured by the label-line map parameters the render call states rather
than by the library defaults, and SHALL hold for both surfaces, which render through the same native
entry point.

#### Scenario: Both surfaces receive wrapped labels
- **WHEN** the phone canvas and the car surface render the same viewport with the wrapping parameters
  in effect
- **THEN** both SHALL draw the same wrapped label, because both reach the same native render entry
  point with the same parameters
- **THEN** the recorded on-device check SHALL name, per surface, a multi-word place name drawn on more
  than one line and a one-word name drawn on one line
