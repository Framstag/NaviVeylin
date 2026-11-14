# Design — the bound case asks a phase the bound leaves room in

## Root cause

`tools/loop/phase.sh:109-196` builds one body — phase line, item line, the phase's own checklist section, the tree,
the gate, the paths and then the work package's delimited text — and cuts it by bytes at `:199-208`: the head is kept
to `bound − notice_bytes − 2` and the notice names where the rest is. The rule the case asserts is that the phase
state *and the package's head* survive that cut.

At phase A the body's head already reaches the package's delimiter at 3 696 bytes: the checklist section is 2 630,
and the ten state lines before the package carry the fixture's own absolute paths. The window is 3 736 bytes, so it
ends 4 bytes into `<<<ISSUE_TEXT`, and `PKG-LINE-001` — the first package line and the token the case asserts — begins
27 bytes past the end of the window. The case could not pass at this phase for any package, because the package is
never reached.

## The single fix

`tools/loop/tests/loop-cases.sh`, the case only: its three legs ask `prescreen`, the state assertion names that phase,
and the package's opening delimiter is asserted beside its first line. `prescreen` is a phase of the same run
(`tools/loop/state.sh` records a step against it) whose prompt carries no section, so the body prints one line —
"the prompt carries no section for phase prescreen — its checklist is the recorded one in …" — instead of a 2 630-byte
checklist. The same rule is asserted with 2 630 bytes more room: the answer is at or under the recorded bound, says it
was cut, carries the state and the tree, holds the delimiter and `PKG-LINE-001`, and does not hold `PKG-LINE-300`.
The comment above the case states that arithmetic, and `evidence/window.txt` carries the commands that took it.

## Why not the tool

A guarantee that a cut at 4 000 characters keeps the package's head at *every* phase has to decide what gives way
instead — the checklist, which is the child's own prompt section printed as it stands, or the state lines, or the
bound. Each answer changes what a phase boundary prints for every item, which is a trade-off and not a repair; the
item names the number as unchanged, so the fixture is what moves.

## Blast radius

One case and one guideline paragraph. `tools/loop/phase.sh`, `tools/loop/loop.conf` and the prompts are untouched. The
case's `# files:` declaration is unchanged and still accurate — `prescreen` reads the same prompt file — so
`driver#everyCaseDeclaresTheFilesItCanReach` is not owed a change. No mutation record names the case or `phase.sh`.

## Rollback

Revert the two hunks (the case and the guideline paragraph). The tool is unchanged, so no behaviour moves; the case
returns to red on this
base and the suite's selection is exactly what it was.

## Alternatives the evidence eliminated

- **A shorter fixture package.** The window's end is fixed by the state, not by the package: 3 696 bytes are spent
  before any package byte, so no package length reaches the assertion.
- **A shorter fixture path.** The state lines carry the fixture's absolute paths three times; a temp root about 30
  bytes shorter would turn the case green and a longer one would redden it again. That is a case that passes or fails on its
  harness's directory name — the flake this suite has already paid for once (#861), not a fix.
- **A mid-package token.** A window that reaches no package line holds no package token, middle or head.
- **A larger `LOOP_TOOL_RESULT_MAX_CHARS`.** The item states the number as unchanged; it is the project's one home for
  the bound, and raising it to fit a case would have the case dictate the bound.
