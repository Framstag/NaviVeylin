#!/usr/bin/env python3
"""jev_probe_scenarios.py — variant B: is Jev a cheap pre-filter for "should a reader look at this change"?

Recorded in `docs/2026-11-08-jev-decision-model-analysis.md`. Variant A (`jev_probe.py`) asked a `noul` about
one file's hunks and failed (AUC 0.430); this variant asks per change, in the shape the primitive is designed
for, and also found nothing (AUC 0.510):

  * one request per **change**, not per hunk — the whole `review-N.diff` is the state;
  * **one question per scenario** the change's own delta spec adds, all answered in parallel;
  * each answer is a `noul` with the scenario text as its criteria: "does the change establish this
    scenario, including the case that proves it?"

The signal is `1 - min(P(established))`: "the change fails at least one of its own scenarios". That is the
finding the archived reviewer recorded for 179 of 190 round-1 rounds, and the router's job is to send only
those to the expensive reader.

Cases
  positive       item + `review-1.diff`, whose round-1 report recorded `LOOP-BLOCKING`
  negative       item + `review-2.diff`, whose round-2 report recorded none — a real fixed tree
  ablation       a positive's diff with the target file's `diff --git` section deleted — the defect gone,
                 which must read as clean. This is the `revert-check` analogue.

Usage: python3 tools/bench/jev_probe_scenarios.py <extract|plan|run|grade> [--limit N] [--model ID]
"""
import hashlib
import json
import os
import re
import sys
import uuid
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import jev_probe as A  # noqa: E402 — the sibling harness carries the shared paths and the AUC helper

CASES = os.path.join(A.OUT, "cases-b.jsonl")
REPORT = os.path.join(A.OUT, "report-b.md")
DEFAULT_MODEL = "typesafe/jev-1.14-20261014"
MAX_DIFF_CHARS = 700_000      # ~175 k tokens, inside jev-1.14's 262 k
MAX_SCENARIOS = 12
SCENARIO = re.compile(r"^[+-]#### Scenario:\s*(.+?)\s*$")
WHEN_THEN = re.compile(r"^([+-])(- \*\*(?:WHEN|THEN|Case)\*\*.*)$")


def read_text(path):
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def scenarios_of(diff):
    """The delta spec's added scenarios: name + WHEN/THEN/Case lines, deduplicated by name."""
    out, cur = {}, None
    for line in diff.splitlines():
        m = SCENARIO.match(line)
        if m:
            cur = m.group(1)
            out.setdefault(cur, [])
            continue
        if cur is None:
            continue
        if line.startswith("+#### ") or line.startswith("+## "):
            cur = None
            continue
        m = WHEN_THEN.match(line)
        if m:
            out[cur].append(m.group(2))
    seen, ordered = set(), []
    for name, body in out.items():
        if not body or name in seen:
            continue
        seen.add(name)
        text = ("%s — " % name) + " ".join(body)
        ordered.append(re.sub(r"\s+", " ", text)[:800])
    return ordered[:MAX_SCENARIOS]


def drop_sections(diff, paths):
    """A diff without the sections of the named files — the ablation."""
    keep, drop = [], False
    for line in diff.splitlines():
        m = re.match(r"^diff --git a/(\S+) b/(\S+)\s*$", line)
        if m:
            drop = m.group(2) in paths
        if not drop:
            keep.append(line)
    return "\n".join(keep)


def extract(root, limit=None):
    os.makedirs(A.RESPONSES, exist_ok=True)
    items = sorted((d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d))),
                   key=lambda d: (len(d), d))
    cases, skipped = [], []
    for name in items:
        item = os.path.join(root, name)
        d1 = read_text(os.path.join(item, "review-1.diff"))
        r1 = read_text(os.path.join(item, "reviewer-report-1.md"))
        if not d1 or not r1:
            continue
        scen = scenarios_of(d1)
        if not scen:
            skipped.append((name, "no added scenario in the delta spec"))
            continue
        claim = A.claim_of(item) or name
        blocking1 = "LOOP-BLOCKING: " in r1 and "LOOP-BLOCKING: 0" not in r1
        if blocking1 and len(d1) <= MAX_DIFF_CHARS:
            cases.append(_case(name, "1", "positive", 1, claim, scen, d1, "round-1 report records a blocking finding"))
            rec, why = A.read_item(root, name)
            if rec:
                A.classify([rec])
                if rec["target"]:
                    cut = drop_sections(d1, set(rec["target"]))
                    if cut.strip() and len(cut) <= MAX_DIFF_CHARS:
                        cases.append(_case(name, "1-ablation", "ablation", 0, claim, scen, cut,
                                           "target file's section removed: %s" % ", ".join(rec["target"])))
        d2 = read_text(os.path.join(item, "review-2.diff"))
        r2 = read_text(os.path.join(item, "reviewer-report-2.md"))
        if d2 and r2 and "LOOP-BLOCKING: " in r2 and "LOOP-BLOCKING: 0" not in r2:
            continue
        if d2 and r2 and len(d2) <= MAX_DIFF_CHARS:
            cases.append(_case(name, "2", "negative", 0, claim, scen, d2, "round-2 report records no blocking finding"))
        if limit and len(cases) >= limit:
            break
    with open(CASES, "w", encoding="utf-8") as fh:
        for c in cases:
            fh.write(json.dumps(c, ensure_ascii=False) + "\n")
    return cases, skipped


def _case(item, round_no, kind, label, claim, scenarios, diff, why):
    qs = {}
    for i, s in enumerate(scenarios):
        qs["s%d" % i] = {
            "type": "noul",
            "instructions": ("`change` is a full diff of one change. `scenario` is one scenario that change's "
                             "own specification adds. Does `change` establish `scenario` — its behaviour "
                             "implemented AND the case that proves it exercised? Answer true only if a reader "
                             "could point at the lines that do both."),
            "criteria": {
                "true": "The diff implements the behaviour and contains a case that exercises it: %s" % s,
                "false": "The behaviour, the case, or the exercise of it is missing from the diff: %s" % s,
            },
        }
    return {"id": "%s|%s|%s" % (item, round_no, kind), "item": item, "round": round_no, "kind": kind,
            "label": label, "why": why, "qids": list(qs), "scenarios": scenarios,
            "state": {"claim": claim, "change": diff}, "questions": qs}


def body_for(case, model):
    return {"model": model, "state": case["state"], "questions": case["questions"]}


def load():
    with open(CASES, encoding="utf-8") as fh:
        return [json.loads(l) for l in fh if l.strip()]


def request(case, model):
    raw = json.dumps(body_for(case, model), ensure_ascii=False).encode()
    key = hashlib.sha1(raw).hexdigest()
    path = os.path.join(A.RESPONSES, "b-" + key + ".json")
    if os.path.isfile(path):
        try:
            with open(path, encoding="utf-8") as fh:
                return json.load(fh), True
        except ValueError:
            pass
    req = urllib.request.Request(
        "https://openrouter.ai/api/alpha/decisions", data=raw,
        headers={"Authorization": "Bearer " + os.environ.get("OPENROUTER_API_KEY", ""),
                 "Content-Type": "application/json"})
    import time
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            out = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        out = {"error": {"code": e.code, "message": e.read().decode()[:2000]}}
    except Exception as e:  # noqa: BLE001
        out = {"error": {"code": "transport", "message": repr(e)}}
    out["_latency_s"] = round(time.time() - t0, 3)
    out["_case"] = case["id"]
    tmp = "%s.%s.tmp" % (path, uuid.uuid4().hex[:8])
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(out, fh)
    os.replace(tmp, path)
    return out, False


def p_unestablished(out, qids):
    """1 - min over scenarios: the change fails its weakest scenario."""
    answers = out.get("answers") or {}
    ps = [answers.get(q, {}).get("noul") for q in qids]
    ps = [float(p) for p in ps if p is not None]
    if not ps:
        return None
    return 1.0 - min(ps)


def cmd_run(cases, model, limit):
    from concurrent.futures import ThreadPoolExecutor
    todo = cases[:limit] if limit else cases
    workers = int(os.environ.get("JEVPAR", "6"))
    ok = failed = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, (case, (out, hit)) in enumerate(zip(todo, pool.map(lambda c: request(c, model), todo)), 1):
            if "error" in out:
                failed += 1
                if failed <= 3:
                    print("  ERR %s: %s" % (case["id"], json.dumps(out["error"])[:300]), flush=True)
                if ok == 0 and failed >= 4:
                    print("ABORT: every request refused — access, not the model.")
                    return
            else:
                ok += 1
            if i % 25 == 0:
                print("  %d/%d (%d ok, %d failed)" % (i, len(todo), ok, failed), flush=True)
    print("run-b: %d requests, %d ok, %d failed" % (len(todo), ok, failed))


def grade(cases, model):
    rows, cost, tokens, lat, missing = [], 0.0, 0, [], 0
    for case in cases:
        raw = json.dumps(body_for(case, model), ensure_ascii=False).encode()
        path = os.path.join(A.RESPONSES, "b-" + hashlib.sha1(raw).hexdigest() + ".json")
        if not os.path.isfile(path):
            missing += 1
            continue
        try:
            with open(path, encoding="utf-8") as fh:
                out = json.load(fh)
        except ValueError:
            missing += 1
            continue
        p = p_unestablished(out, case["qids"])
        if p is None:
            missing += 1
            continue
        u = out.get("usage") or {}
        cost += float(u.get("cost") or 0)
        tokens += int(u.get("input_tokens") or 0)
        lat.append(out.get("_latency_s"))
        rows.append((case, p))
    g = {}
    for c, p in rows:
        g.setdefault(c["kind"], []).append(p)
    pos, neg, abl = g.get("positive", []), g.get("negative", []), g.get("ablation", [])
    a = A.auc(pos, neg)
    lines = ["# Jev probe, variant B — report", "",
             "Model: `%s`  ·  cases graded: %d  ·  without a response: %d" % (model, len(rows), missing), "",
             "Predictor: `1 - min(P(established))` over the change's own added scenarios.", "",
             "| kind | n | p50 | min | max |", "|---|---|---|---|---|"]
    for kind in ("positive", "negative", "ablation"):
        vals = g.get(kind, [])
        if vals:
            lines.append("| %s | %d | %.2f | %.2f | %.2f |" % (
                kind, len(vals), sorted(vals)[len(vals) // 2], min(vals), max(vals)))
    lines += ["", "## Verdict", ""]
    if a is None:
        lines.append("- not enough of both classes to compute an AUC.")
    else:
        lines.append("- **AUC (blocking change vs a change whose round-2 report found none): %.3f**" % a)
    if abl:
        lines.append("- ablation (the defect's file removed): p50 %.2f, and %.0f%% still read as failing a "
                     "scenario — a pre-filter that keeps flagging a repaired tree is not a filter."
                     % (sorted(abl)[len(abl) // 2], 100.0 * sum(1 for p in abl if p >= 0.5) / len(abl)))
    if lat:
        lat = [x for x in lat if x]
        if lat:
            lines.append("- latency s: p50 %.1f, p95 %.1f" % (
                sorted(lat)[len(lat) // 2], sorted(lat)[max(0, int(len(lat) * 0.95) - 1)]))
    lines.append("- input tokens: %d  ·  cost: $%.4f" % (tokens, cost))
    with open(REPORT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "plan"
    argv = sys.argv[2:]
    model, root, limit = DEFAULT_MODEL, A.DEFAULT_ROOT, None
    for i, a in enumerate(argv):
        if a == "--model":
            model = argv[i + 1]
        elif a == "--root":
            root = argv[i + 1]
        elif a == "--limit":
            limit = int(argv[i + 1])
    if cmd == "extract":
        cases, skipped = extract(root, limit)
        kinds = {}
        for c in cases:
            kinds[c["kind"]] = kinds.get(c["kind"], 0) + 1
        print("cases-b: %d %s" % (len(cases), kinds))
        print("skipped: %d" % len(skipped))
        for n, why in skipped[:10]:
            print("  - %s: %s" % (n, why))
        for c in cases[:3]:
            print("  e.g. %s  scenarios=%d  diff=%d chars" % (c["id"], len(c["qids"]), len(c["state"]["change"])))
    elif cmd == "plan":
        cases = load()
        chars = sum(len(c["state"]["change"]) + sum(len(s) for s in c["scenarios"]) for c in cases)
        qs = sum(len(c["qids"]) for c in cases)
        print("cases-b: %d  ·  questions: %d  ·  state chars: %d (~%d tokens)"
              % (len(cases), qs, chars, chars // 4))
        print("projected cost at $0.042/M input: $%.3f" % (chars / 4 * 0.042 / 1_000_000))
    elif cmd == "run":
        cmd_run(load(), model, limit)
    elif cmd == "grade":
        grade(load(), model)
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
