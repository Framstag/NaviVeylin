# Delta — the shell refusal policy's reading arm

## ADDED Requirements

### Requirement: A range read of the tree is refused and a log's is not

The shell refusal policy SHALL refuse a range read of a file in the tree and SHALL leave the range read the exemption
was written for standing. A `sed` whose quiet arm (`-n`, `--quiet`) carries a line range as its script SHALL be
refused when a path it names is one the policy classifies as the tree, with a reason naming
`ctx_read path=<path> offset=<N> limit=<M>` and stating the cap that call stops at, so a child whose range is wider
than the cap keeps the shell. The policy SHALL leave standing: a range read of a log outside the tree, a range read of
a file named from inside an item directory, a `sed -i` and a `sed -e`, a quiet `sed` whose script is a substitution,
and a `sed` that names no path because it reads the pipe it stands in.

#### Scenario: A range read of a tracked file is refused and a log's is not

- **WHEN** the policy is handed `sed -n '100,130p' guidelines/Build.md`, and then a range read of a log under `/tmp`, a
  range read of a file named from inside an item directory, a `sed -i`, a quiet `sed` whose script is a substitution,
  a `sed -e`, and a `sed` reading a pipe
- **THEN** the first is refused with a reason naming `ctx_read path=<path> offset=<N> limit=<M>` and the 2 000-line cap
  that call stops at, and each of the others is allowed unchanged
- **Case**: `policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot`
