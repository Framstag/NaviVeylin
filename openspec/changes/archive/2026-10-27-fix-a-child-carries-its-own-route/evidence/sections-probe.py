#!/usr/bin/env python3
"""sections-probe.py — what one child's system prompt is made of, read from its own event stream.

Usage: python3 sections-probe.py <project root>

Reads the newest `.pi/issue-loop/items/*/events.jsonl` under the root it is given (the loop's own item
directory, which is gitignored and machine-local) and prints the `sections` block of the first
`message_start` event — the one the agent sends on every turn — with each section's size in characters, the
total, and the share the project's context files take of it.

This is the probe the change `2026-10-27-fix-a-child-carries-its-own-route` cites for the 25 624-character
`project_context` section (issue #661). It is a measurement tool and runs nothing: no model, no token, no
network.
"""
import glob
import json
import os
import sys


def main(argv):
    root = argv[1] if len(argv) > 1 else "."
    streams = sorted(
        glob.glob(os.path.join(root, ".pi/issue-loop/items/*/events.jsonl")),
        key=os.path.getmtime,
    )
    print("sections-probe: streams=%d root=%s" % (len(streams), os.path.abspath(root)))
    if not streams:
        print("sections-probe: no event stream under %s" % os.path.join(root, ".pi/issue-loop/items"))
        return 1
    path = streams[-1]
    print("sections-probe: stream=%s bytes=%d" % (path, os.path.getsize(path)))
    with open(path) as handle:
        for line in handle:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") != "message_start":
                continue
            sections = event.get("message", {}).get("sections")
            if not isinstance(sections, dict):
                continue
            total = 0
            for name, body in sections.items():
                size = len(body) if isinstance(body, str) else len(json.dumps(body))
                total += size
                print("sections-probe: section %-16s %6d chars" % (name, size))
            print("sections-probe: total system prompt %d chars" % total)
            context = sections.get("project_context")
            if isinstance(context, str) and total:
                print(
                    "sections-probe: project_context share %d chars = %.1f %% of the system prompt,"
                    " %.1f tokens at %s chars/token"
                    % (len(context), 100.0 * len(context) / total, len(context) / 2.8, "2.8")
                )
            return 0
    print("sections-probe: no message_start carrying a sections block in %s" % path)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
