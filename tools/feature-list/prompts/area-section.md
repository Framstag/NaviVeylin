# Write one section of the NaviVeylin feature list

You are writing one section of a **marketing** feature list for **NaviVeylin**, an offline OpenStreetMap
navigation app for Android (phone, tablet) with Android Auto and Android Automotive OS support. It is read like a
landing page: someone skims the keywords, stops at the ones that matter to them, and reads those paragraphs.

You receive one JSON bundle on stdin:

```
{
  "area":     { "id", "heading", "order", "carOnly", "surfaces": ["phone","car"] },
  "promptFile": "<absolute path to this file>",
  "promptVersion": "<digest>",
  "highlights": [
    { "id": "lane", "keyword": "Know which lane to be in", "why": "<a note for the reviewer>",
      "capabilities": [ { "key", "spec", "name", "surfaces", "text" } ] }
  ]
}
```

**The shortlist has already decided what is worth saying.** Each highlight in the bundle is one selling point and
gets exactly one entry. Do not add a selling point, do not drop one, and do not write about a capability that is
not in the bundle: a bundle is deliberately short, and the basics are absent from it because they are not the
reason anyone installs this app.

Write **only the entries** and print them to stdout. Do not print the area heading or its surface line — the tool
generates those.

## The shape of one entry

```
- **Know which lane to be in** — Lane arrows at complex junctions show the lane to take, roundabouts are drawn as
  roundabouts with the exit highlighted, and the signs are drawn rather than typed, so they look the same on every
  phone.
  <!-- cap: lane-guidance#Lane arrows shown at complex junctions -->
  <!-- cap: roundabout-renderer#Roundabout exit highlighted -->
```

- `keyword` is what the reader scans for. Keep the bundle's suggestion if it works, sharpen it if you can. At most
  six words, and every word of it must appear in the specs the entry cites.
- The paragraph is **at most 320 characters** — one or two short sentences, no more. Count them. The run fails
  above 320, and a paragraph that runs longer than that is the mistake this document was rewritten to remove.
- Then one `<!-- cap: ... -->` line per capability, one key per line.

## Write like a pitch, not a manual

The paragraph answers *why should I care*, not *what does the control do*.

| do not write | write |
|---|---|
| the map manager screen shows basemap status with size and version, and a Download or Update control starts the download | the map lives on your phone, so it is there when the signal is not |
| the overlay collapses to a compact status line while the download runs | updates install in the background and a failure leaves your maps intact |
| lane arrows are rendered as explicit Canvas graphics | arrows are drawn, not typed, so every phone looks the same |

Rules that follow from that:

- **No control names, no screen names, no layout talk.** Not "the map manager screen", not "a bottom sheet", not
  "top-right". The reader has not opened the app yet.
- **No implementation talk.** Not "the epoch is incremented", not "tiles are cached", not "the database is
  opened". If a capability is plumbing, say what it buys the reader or leave it out of the sentence — but every
  capability in the bundle must still be cited, so cite it in a sentence about what it enables.
- **Lead with the benefit**, then at most one supporting detail.
- **Concrete beats abstract**: name the vehicle profiles, the group colours, the offline maps, the car screen.
- State only what the cited capabilities' `text` states. No figure, limit, count or version number that does not
  appear there, and never a feature that is not in the bundle.
- Second person, present tense. No emoji, no exclamation marks, no bold inside the paragraph.
- American English and the product's own nouns — *favorite*, *color*, *map manager* — because the rest of the
  product uses them. Read `tone.md` next to this file for the register and the vocabulary.

## Output

One entry per highlight in the bundle, in the bundle's order: a `- **Keyword** — paragraph` line, wrapped if you
like, followed by its `<!-- cap: ... -->` lines. A blank line between entries. Nothing else — no heading, no
preamble, no closing remark, no code fence.
