#!/usr/bin/env python3
"""The measurement behind issue #796's two numbers: how often a loop child's `ctx_grep` failed with
`command not found: rg`, over the items this run archive holds.

    python3 openspec/changes/fix-a-child-has-a-working-search/evidence/ctx-grep-census.py [<dir>]

`<dir>` defaults to `.pi/issue-loop/items`. One line is printed, in the shape the loop's other censuses use, so a
reader can quote it and re-take it. A `tool_execution_end` is the only event that says how a call ended; an item's
own directory is counted once whether it holds one child stream or several.
"""
import json
import os
import sys

root = sys.argv[1] if len(sys.argv) > 1 else ".pi/issue-loop/items"
calls = failed = 0
items = items_failed = 0
for name in sorted(os.listdir(root)):
    path = os.path.join(root, name, "events.jsonl")
    if not os.path.isfile(path):
        continue
    items += 1
    item_failed = False
    with open(path, encoding="utf-8", errors="replace") as stream:
        for line in stream:
            if '"tool_execution_end"' not in line or '"ctx_grep"' not in line:
                continue
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") != "tool_execution_end" or event.get("toolName") != "ctx_grep":
                continue
            calls += 1
            if event.get("isError") and "command not found: rg" in json.dumps(event):
                failed += 1
                item_failed = True
    if item_failed:
        items_failed += 1
print(f"CTX-GREP-CENSUS at={os.popen('date -u +%Y-%m-%dT%H:%M:%SZ').read().strip()} "
      f"items={items} items_failed={items_failed} calls={calls} failed={failed}")
