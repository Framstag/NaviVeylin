# Metrics recipes — the invocations and the report sections the rules are read against

Procedure for `guidelines/Metrics.md`. Each anchor names a section of that document; the rules stay there.

## §1 Invocation and exit status

```bash
tools/project-metrics-report.sh                # whole repository
tools/project-metrics-report.sh app core       # one or more scopes
tools/project-metrics-report.sh --help         # the usage block, from the script's own header
```

Output is a complete text report on stdout. `--help` is the only option beside scope paths.

## §2 What one run measures, and what it does not

- The report's header states the run's date, its scope, how many files were measured, and every excluded class
  with the paths it removed.
- Each excluded class is listed under the report's `EXCLUDED CLASSES` section with the paths it removed.
- Files of types the tool does not count are counted in the header as `not counted  N files of other types (…)`,
  with the most frequent types named.

## §7 Ordering and completeness

- Each figure column carries a header; documents and OpenSpec store files are aggregated with their own file
  and line counts printed in place of the rows that were left out.
- The tool's counting rules are covered without a device by `tools/project-metrics-report-selftest.sh`.
