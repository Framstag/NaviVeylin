#!/usr/bin/env python3
"""thinking_probe.py — what a probe child spent at its thinking level, and whether it read its own answer key.

Why this exists: a study of `--thinking` levels (`docs/2026-11-11-thinking-level-model-analysis.md`) needs three
numbers per run that no single tool prints — the usage a child's own stream records (`input`, `cacheRead`,
`output`, and the `thinkingLevel` the provider actually applied), the tool mix that produced it (a lower level can
read *more*), and the leak check that decides whether the run may be graded at all (a child that opened the
archived round's report or a change directory looks capable at every level). The streams are the ones
`tools/pi-child-events.sh` keeps, so nothing here starts a model or spends a token.

Usage:
  thinking_probe.py runs   <stream> [<stream> ...]        one block per run
  thinking_probe.py table  <probe> <level> <stream> ...   one row per run, plus the delta against `high`

  `table` takes one argument per run, three fields space-separated: the probe's name, the level, the stream.
  Rates for the cost column: $0.30 per Mtok input, $1.20 per Mtok output (ollama-cloud `deepseek-v4.1-flash`,
  measured 2026-11-07), cache read billed as input — the provider's own `cost` field reports 0 on this plan, so it
  cannot be quoted. `THINKING_PROBE_IN_RATE` / `THINKING_PROBE_OUT_RATE` (dollars per Mtok) override them for
  another model — e.g. `minimax-m3.1-flash` is $0.15/$0.60.

Exit: 0 measured; 2 a stream holds no `turn_end` record (the child never ran a turn).
"""
import json
import os
import sys

IN_RATE = float(os.environ.get("THINKING_PROBE_IN_RATE", "0.30")) / 1_000_000
OUT_RATE = float(os.environ.get("THINKING_PROBE_OUT_RATE", "1.20")) / 1_000_000

# A run that opened one of these is disqualified, not graded: they are the answer keys of the probe shapes this
# tool is used with (the archived round's own products, and the probe's own key file).
KEY_MARKERS = ("reviewer-report", "-key.md", "/evidence/", "worker-report", "gate.log", "review-1.diff", "review-2.diff")


def scan(path):
    turns = tools_total = 0
    tools = {}
    inp = out = cache = reason = 0
    max_ctx = 0
    levels, models, reads = set(), set(), []
    model_ms = 0
    times = []
    for line in open(path, encoding="utf-8", errors="replace"):
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("type") != "turn_end":
            continue
        m = e.get("message") or {}
        u = m.get("usage") or {}
        turns += 1
        inp += u.get("input", 0) or 0
        out += u.get("output", 0) or 0
        cache += u.get("cacheRead", 0) or 0
        reason += u.get("reasoning", 0) or 0
        max_ctx = max(max_ctx, u.get("totalTokens", 0) or 0)
        model_ms += m.get("durationMs", 0) or 0
        if isinstance(m.get("timestamp"), (int, float)):
            times.append(m["timestamp"])
        if m.get("thinkingLevel"):
            levels.add(m["thinkingLevel"])
        if m.get("model"):
            models.add(m["model"])
        for c in m.get("content") or []:
            if c.get("type") != "toolCall":
                continue
            tools_total += 1
            name = c.get("name") or "?"
            tools[name] = tools.get(name, 0) + 1
            args = c.get("arguments") or {}
            for field in ("path", "file_path", "file", "pattern", "command"):
                v = args.get(field)
                if isinstance(v, str):
                    reads.append(v)
    return dict(path=path, turns=turns, tools_total=tools_total, tools=tools, inp=inp, out=out,
                cache=cache, reason=reason, total=inp + out + cache, max_ctx=max_ctx,
                levels=sorted(levels), models=sorted(models), reads=reads, model_ms=model_ms,
                wall_s=(max(times) - min(times)) / 1000.0 if len(times) > 1 else None,
                cost=(inp + cache) * IN_RATE + out * OUT_RATE)


def leaks(s):
    return sorted({r for r in s["reads"] if any(k in r for k in KEY_MARKERS)})


def cmd_runs(paths):
    for p in paths:
        s = scan(p)
        if not s["turns"]:
            print("== %s: no turn_end record — nothing to measure" % p)
            continue
        print("== %s" % p)
        print("   levels=%s models=%s turns=%d tool_calls=%d" %
              (s["levels"], s["models"], s["turns"], s["tools_total"]))
        print("   input=%d cacheRead=%d output=%d reasoning=%d total=%d max_ctx=%d" %
              (s["inp"], s["cache"], s["out"], s["reason"], s["total"], s["max_ctx"]))
        print("   wall=%ss model_time=%ss output_per_turn=%.0f" %
              (round(s["wall_s"], 1) if s["wall_s"] else "?", round(s["model_ms"] / 1000.0, 1),
               s["out"] / max(1, s["turns"])))
        print("   tools=%s" % dict(sorted(s["tools"].items(), key=lambda kv: -kv[1])))
        found = leaks(s)
        print("   answer-key reads: %s" % (found if found else "none"))


def cmd_table(args):
    rows = []
    for arg in args:
        probe, level, path = arg.split(" ", 2)
        s = scan(path)
        s["probe"], s["level"] = probe, level.strip()
        rows.append(s)
    print("%-9s %-5s %5s %5s %10s %10s %8s %11s %7s %7s %7s %6s" %
          ("probe", "level", "turns", "tools", "input", "cacheRead", "output", "total", "maxCtx",
           "wall_s", "cost$", "out/t"))
    for s in rows:
        print("%-9s %-5s %5d %5d %10d %10d %8d %11d %7d %7s %7.2f %6.0f" %
              (s["probe"], s["level"], s["turns"], s["tools_total"], s["inp"], s["cache"], s["out"],
               s["total"], s["max_ctx"], round(s["wall_s"], 1) if s["wall_s"] else "?", s["cost"],
               s["out"] / max(1, s["turns"])))
    for probe in dict.fromkeys(s["probe"] for s in rows):
        sub = [s for s in rows if s["probe"] == probe]
        base = next((s for s in sub if s["level"] == "high"), None)
        if base is None:
            continue
        print("\n%s against high:" % probe)
        for s in sub:
            print("   %-5s total %+6.1f%%   cost %+6.1f%%" %
                  (s["level"], 100.0 * (s["total"] - base["total"]) / base["total"],
                   100.0 * (s["cost"] - base["cost"]) / base["cost"]))


def main(argv):
    if len(argv) < 2 or argv[0] not in ("runs", "table"):
        print(__doc__.strip())
        return 2
    if argv[0] == "runs":
        cmd_runs(argv[1:])
    else:
        cmd_table(argv[1:])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
