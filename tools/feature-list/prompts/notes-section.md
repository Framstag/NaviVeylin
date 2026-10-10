# Compose the release-note section for one feature area

You are writing the changed part of NaviVeylin's release notes for one feature area. NaviVeylin is an offline
OpenStreetMap navigation app for Android (phone, tablet) with Android Auto and Android Automotive OS support.

You receive one JSON bundle on stdin:

```
{
  "role": "release-note",
  "version": "2026-10-08-1",
  "area": { "id", "heading", "order", "carOnly" },
  "promptFile": "<absolute path to this file>",
  "capabilities": [
    { "key": "spec-id#Requirement name", "spec": "spec-id", "name": "Requirement name",
      "text": "<the requirement's own text, including its scenarios>" }
  ],
  "removed": [ { "key": "spec-id#Requirement name", "spec": "spec-id", "name": "Requirement name" } ]
}
```

Write only the bullets for this area and print them to stdout. Do not print a heading, do not print the version —
the tool generates both from the classification and the release version.

## What this is

A release note answers one question for someone who already has the app: *what is different for me since the last
version?* It is not the catalogue. A capability that did not change does not belong here, even if it is important.

`capabilities` are the ones that changed; `removed` are the ones that are gone. Every entry must be reported —
this is the one place where the run's coverage obligation is a *report* rather than a gate, so a capability the
bundle lists and your text omits is a silent loss.

## The hard rule

Every bullet carries the capability key it was written from, as an HTML comment line right after it, one key per
line:

```
- The street name now updates as you move onto a new road, not only when the map redraws.
  <!-- cap: current-road-info#Street name updates on road change -->
```

A bullet whose key does not resolve, or whose key names a spec that is not user-visible, fails the run. A figure
that appears in none of the cited specs fails the run too.

## Shape

- One bullet per changed capability, or merge two capabilities that are one change to the user.
- `removed` entries go in as "No longer available: <what it was>" — say what stopped working, not the
  requirement's name.
- Past tense only where it is genuinely past ("the map no longer freezes"); otherwise present tense: the reader
  is reading about the app they now have.
- No version numbers, no issue numbers, no "internal improvements", no thanks, no roadmap.
- One sentence each. If a change needs two sentences to be understood, it is two bullets.
- Under about 120 characters per bullet.

## Output

Markdown bullets only, each followed by its `<!-- cap: ... -->` line or lines. Nothing else: no heading, no
version line, no preamble, no code fence.
