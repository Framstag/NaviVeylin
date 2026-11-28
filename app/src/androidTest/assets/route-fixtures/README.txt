Committed route fixtures live here: one `<label>.expected.txt` per route, recorded once by
`tools/route-hints.sh --record --routes <file>` and replayed by `--replay <label>`.

The identity of a line is `<kind> <km from the route start>`; node ids, file offsets and database
ids are deliberately absent, because an import renumbers them.
