#!/usr/bin/env python3
"""jev_probe.py — does a System One decision model (Jev) find a recorded defect a reviewer found?

This is the probe behind `docs/2026-11-08-jev-decision-model-analysis.md`, whose result was negative: see that
document for the numbers and for what would justify a re-take. It is a measurement harness, not a check —
no gate runs it, and it reads the loop's archive (`.pi/issue-loop/items`, untracked), so it cannot be one.

Answer key = the archived round-1 reviewer report of `.pi/issue-loop/items/<n>`. Nothing from that report
is ever put in a request: the request carries the *claim* (the issue's own text) and one file's diff hunks.
The report supplies only the grading label (does the recorded finding name this file?) — so the probe
measures "can Jev point at the file a reviewer blocked on", not "can Jev repeat the reviewer's words".

Cases per item: positives = diff files a recorded blocking finding names; hard negatives = the other files
of the same diff; falsification = the same file's hunks in `review-2.diff` (the fixed tree), which must fall
below threshold; null = an empty state, which must never read as a defect.

Usage: python3 tools/bench/jev_probe.py <extract|plan|run|control|grade> [--limit N] [--model ID] [--root DIR]

Commands:
  extract   build cases.jsonl from the archive (offline)
  plan      counts, request count and projected cost (offline)
  run       send the requests, cache every response (network; needs OPENROUTER_API_KEY)
  grade     threshold sweep, calibration, falsification and cost -> report.md (offline)

Usage: python3 jev_probe.py <extract|plan|run|grade> [--limit N] [--model ID] [--root DIR]
"""
import hashlib
import json
import uuid
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_ROOT = os.path.join(REPO, ".pi", "issue-loop", "items")
# Output is scratch, never the tree: the loop's own archive and a response cache are not repository content.
OUT = os.environ.get("JEVP_OUT", os.path.join(REPO, ".pi", "jev-probe"))
CASES = os.path.join(OUT, "cases.jsonl")
RESPONSES = os.path.join(OUT, "responses")
REPORT = os.path.join(OUT, "report.md")
ENDPOINT = "https://openrouter.ai/api/alpha/decisions"
DEFAULT_MODEL = "typesafe/jev-1.14-20261014"
# The control is a cheap, capable chat model that answers the same states — it is not the loop's own
# reviewer (that one is served through ollama-cloud, which the OpenRouter key does not reach).
DEFAULT_CONTROL = "anthropic/claude-haiku-4.5"
# jev-1.13 accepts 32k tokens for state + longest question; ~3.6 chars/token, and the instruction text and
# the claim also count, so a state over this is split out and reported rather than silently truncated.
MAX_STATE_CHARS = 100_000
MAX_POS_PER_ITEM = 3
MAX_NEG_PER_ITEM = 3
TRAILER = re.compile(r"^(LOOP-BLOCKING|VERDICT|LOOP-CORRECTIONS-ONLY|LOOP-DECISION|LOOP-TIMEOUT)\s*:")


def split_diff(text):
    """{b-path: {'lines': n, 'chars': n, 'text': ...}} — one entry per `diff --git` section."""
    files, cur, buf = {}, None, []
    for line in text.splitlines():
        m = re.match(r"^diff --git a/(\S+) b/(\S+)\s*$", line)
        if m:
            if cur:
                files[cur] = buf
            cur, buf = m.group(2), [line]
        elif cur is not None:
            buf.append(line)
    if cur:
        files[cur] = buf
    out = {}
    for path, lines in files.items():
        body = "\n".join(lines)
        out[path] = {"text": body, "lines": len(lines), "chars": len(body)}
    return out


def changed_lines(body):
    return [l for l in body.splitlines() if l[:1] in "+-" and l[:3] not in ("+++", "---")]


def report_body(path):
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8", errors="replace") as fh:
        lines = [l for l in fh.read().splitlines() if not TRAILER.match(l)]
    return "\n".join(lines)


def key_terms(body):
    """Identifiers and paths a reader could point at: backticked tokens plus long CamelCase words."""
    terms = set()
    for raw in re.findall(r"`([^`\n]{2,120})`", body):
        for tok in re.split(r"[\s,;()\[\]]+", raw):
            tok = tok.strip("`.,:;'\"")
            if len(tok) >= 6:
                terms.add(tok)
    for tok in re.findall(r"\b[A-Z][A-Za-z0-9]{7,}\b", body):
        terms.add(tok)
    return terms


MAX_HUNKS_PER_ITEM = 12
# A term that appears in more than this share of all hunks carries no localisation signal and is dropped
# from the answer key: without it, boilerplate (`NaviVeylin`, `ViewModel`) marks hunks as defect-bearing.
DF_CEILING = 0.02
# A symbol hit only localises a defect in a file that can carry one. Docs and config land in the positive set
# only when the recorded finding names their path: item 116's report names `MapTreeDiscovery`, and AGENTS.md's
# diff hunks mention it too, so without this gate a doc hunk is graded as if the defect lived there.
CODE_EXT = (".kt", ".java", ".kts", ".gradle", ".c", ".cc", ".cpp", ".cxx", ".h", ".hpp", ".sh", ".bash",
            ".py", ".rs", ".go", ".ts", ".tsx", ".js")


def split_hunks(body):
    """[{header, text, changed}] — one entry per `@@` block, each carrying the file preamble."""
    if "@@" not in body:
        return []
    pre, hunks, cur = [], [], None
    for line in body.splitlines():
        if line.startswith("@@"):
            if cur:
                hunks.append(cur)
            cur = [line]
        elif cur is None:
            pre.append(line)
        else:
            cur.append(line)
    if cur:
        hunks.append(cur)
    out = []
    for h in hunks:
        text = "\n".join(pre + h)
        out.append({"header": h[0], "text": text, "changed": "\n".join(changed_lines(text))})
    return out


def terms_of(report):
    """(paths, words) — what the recorded finding names: file paths, or identifiers to localise with."""
    terms = key_terms(report)
    paths = [t for t in terms if "/" in t and "." in t.split("/")[-1]]
    words = [t for t in terms if "/" not in t and len(t) >= 8]
    return paths, words


def path_named(path, paths):
    return any(path.endswith(p.lstrip("./")) or p.lstrip("./").endswith(path) for p in paths)


def claim_of(item_dir):
    wp = os.path.join(item_dir, "work-package.md")
    if not os.path.isfile(wp):
        return None
    with open(wp, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    m = re.search(r"<<<ISSUE_TEXT\n(.*?)\nISSUE_TEXT", text, re.S)
    title = re.search(r"^title:\s*(.+)$", text, re.M)
    parts = []
    if title:
        parts.append(title.group(1).strip())
    if m:
        parts.append(re.sub(r"\n{2,}", "\n", m.group(1)).strip())
    claim = "\n".join(parts).strip()
    return claim[:2600] or None


def questions(claim, path):
    return {
        "defect": {
            "type": "noul",
            "instructions": (
                "`claim` is what a change says it fixes. `hunk` is the whole diff of one file in that change. "
                "Is there a defect in `hunk` that would make the change fail to establish `claim`? Answer true "
                "only if a reader could point at a specific line in `hunk` that breaks it."
            ),
            "criteria": {
                "true": "A specific line in the diff breaks what the claim promises.",
                "false": "Nothing in the diff breaks the claim; it is missing, weak or unrelated at most.",
            },
        },
        "established": {
            "type": "score",
            "instructions": "How completely does `hunk` establish what `claim` promises?",
            "criteria": [
                "No evidence for the claim in this file.",
                "Touches the claim but leaves it unproven.",
                "Establishes it but a reader could still point at a hole.",
                "Establishes it and shows the case that proves it.",
            ],
        },
        "failure": {
            "type": "choice",
            "instructions": "If `hunk` does break `claim`, which failure is it? Choose `none` otherwise.",
            "criteria": {
                "none": "No defect in this file against the claim.",
                "silent-path": "A code path drops, hides or ignores something the claim requires.",
                "unproven-claim": "The claim is asserted without the case, test, or log line that proves it.",
                "wrong-scope": "The change lands where the claim does not reach, or misses where it does.",
                "contradiction": "A line directly contradicts another file the change also touches.",
            },
        },
    }


def state_for(claim, path, body):
    return {"claim": claim, "file": path, "hunk": body}


def read_item(root, name):
    """One item's probe inputs, or (None, why-it-does-not-qualify)."""
    item_dir = os.path.join(root, name)
    ro = os.path.join(item_dir, "review-1.diff")
    report = report_body(os.path.join(item_dir, "reviewer-report-1.md"))
    claim = claim_of(item_dir)
    if not (os.path.isfile(ro) and report and claim):
        return None, "no diff, report or claim"
    with open(ro, encoding="utf-8", errors="replace") as fh:
        diff1 = split_diff(fh.read())
    paths_named, words = terms_of(report)
    if not words and not paths_named:
        return None, "the report names nothing a reader could localise"
    all_hunks = []
    for path, info in diff1.items():
        hunks = split_hunks(info["text"])
        if not hunks and len(info["text"]) <= MAX_STATE_CHARS:
            hunks = [{"header": "(whole file)", "text": info["text"], "changed": "\n".join(changed_lines(info["text"]))}]
        for h in hunks:
            if len(h["text"]) <= MAX_STATE_CHARS:
                all_hunks.append((path, h))
    post = {}
    r2 = os.path.join(item_dir, "review-2.diff")
    if os.path.isfile(r2):
        with open(r2, encoding="utf-8", errors="replace") as fh:
            post = split_diff(fh.read())
    return {"name": name, "claim": claim, "post": post, "all": all_hunks,
            "words": words, "paths": paths_named}, None


# A finding names a symbol; the defect lives in the file that *declares* it. Merely mentioning the symbol —
# a call site, a doc, a reading list — does not localise it, which is what made path and word matching flag
# AGENTS.md, build.yml and app/build.gradle.kts for item 116.
DECL = re.compile(
    r"^[+-]\s*(?:public |private |protected |internal |static |final |open |override |suspend |inline |"
    r"external |export |abstract |data |sealed |lateinit |const )*"
    r"(?:fun|class|object|interface|enum|struct|def|void|typealias|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)")


def declared(changed):
    out = set()
    for line in changed.splitlines():
        m = DECL.match(line)
        if m:
            out.add(m.group(1))
    return out


def classify(records):
    """Positive hunks = hunks of the file that declares the symbol the finding names; the rest are negatives."""
    total = sum(len(r["all"]) for r in records) or 1
    df = {}
    for r in records:
        seen = set()
        for _, h in r["all"]:
            for w in r["words"]:
                if w in h["changed"]:
                    seen.add(w)
        for w in seen:
            df[w] = df.get(w, 0) + 1
    ceiling = max(1, int(DF_CEILING * total))
    for r in records:
        r["all"] = [(p, h) for p, h in r["all"] if h["changed"].strip()]
        syms = {w for w in r["words"] if df.get(w, total) <= ceiling}
        per_file = {}
        for path, h in r["all"]:
            if not path.endswith(CODE_EXT):
                continue
            d = declared(h["changed"]) & syms
            if d:
                per_file.setdefault(path, set()).update(d)
        decl_files = set()
        if per_file:
            # The target is the file that declares the most of the finding's named symbols; a file the
            # finding also names by path wins a tie.
            best = max(len(v) for v in per_file.values())
            tied = [p for p, v in per_file.items() if len(v) == best]
            decl_files = set([p for p in tied if path_named(p, r["paths"])] or tied)
        elif syms:
            scored = [(len({w for w in syms if w in h["changed"]}), p)
                      for p, h in r["all"] if p.endswith(CODE_EXT)]
            scored = [s for s in scored if s[0]]
            if scored:
                decl_files = {max(scored)[1]}
        r["target"] = sorted(decl_files)
        r["pos"] = [x for x in r["all"] if x[0] in decl_files]
        r["neg"] = [x for x in r["all"] if x[0] not in decl_files]
    return records


def build(root, limit=None):
    os.makedirs(RESPONSES, exist_ok=True)
    items = sorted((d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d))),
                   key=lambda d: (len(d), d))
    records, skipped = [], []
    for name in items:
        rec, why = read_item(root, name)
        if rec:
            records.append(rec)
        else:
            skipped.append((name, why))
    records = classify(records)
    empty = [r for r in records if not r["pos"]]
    for r in empty:
        skipped.append((r["name"], "the recorded finding names no symbol this diff declares"))
    records = [r for r in records if r["pos"]]
    cases = []
    for i, rec in enumerate(records):
        pos_hunks = [x for x in rec["pos"] if x[1]["changed"].strip()]
        for path, h in pos_hunks[:MAX_POS_PER_ITEM]:
            cases.append(_case(rec, path, h, "positive", 1))
        neg_pool = [x for x in rec["neg"] if x[1]["changed"].strip()]
        for path, h in neg_pool[:MAX_HUNKS_PER_ITEM]:
            cases.append(_case(rec, path, h, "hard-negative", 0))
        # cross-negative: another item's clean hunk — the false-positive rate outside this change's context.
        if records:
            other = records[(i + 1) % len(records)]
            pool = [x for x in other["neg"] if x[0] != (rec["pos"] or [("",)])[0][0]]
            if pool:
                path, h = pool[i % len(pool)]
                c = _case(other, path, h, "cross-negative", 0)
                c["state"]["claim"] = rec["claim"]
                c["id"] = "%s|%s|cross-%s" % (rec["name"], path, other["name"])
                cases.append(c)
        for path, h in pos_hunks[:MAX_POS_PER_ITEM]:
            hunks = split_hunks(rec["post"].get(path, {}).get("text", ""))
            if hunks:
                f = hunks[min(len(hunks) - 1, i % len(hunks))]
                if len(f["text"]) <= MAX_STATE_CHARS:
                    cases.append(_case(rec, path, f, "falsification", 0))
        if limit and len(cases) >= limit:
            break
    cases.append({
        "id": "null|empty", "item": "null", "file": "(none)", "label": 0, "kind": "null",
        "state": state_for("The change must never log a user coordinate.", "(none)", ""),
    })
    with open(CASES, "w", encoding="utf-8") as fh:
        for c in cases:
            fh.write(json.dumps(c, ensure_ascii=False) + "\n")
    return cases, skipped


def _case(rec, path, hunk, kind, label):
    return {"id": "%s|%s|%s|%s" % (rec["name"], path, hunk["header"][:24], kind),
            "item": rec["name"], "file": path, "label": label, "kind": kind,
            "state": state_for(rec["claim"], path, hunk["text"])}


def load_cases():
    with open(CASES, encoding="utf-8") as fh:
        return [json.loads(l) for l in fh if l.strip()]


def request(case, model):
    body = {"model": model, "state": case["state"],
            "questions": questions(case["state"]["claim"], case["file"])}
    raw = json.dumps(body, ensure_ascii=False).encode()
    key = hashlib.sha1(raw).hexdigest()
    cache = os.path.join(RESPONSES, key + ".json")
    if os.path.isfile(cache):
        try:
            with open(cache, encoding="utf-8") as fh:
                return json.load(fh), True
        except ValueError:
            pass  # a cache file two workers interleaved before the write became atomic — refetch it
    req = urllib.request.Request(
        ENDPOINT, data=raw,
        headers={"Authorization": "Bearer " + os.environ.get("OPENROUTER_API_KEY", ""),
                 "Content-Type": "application/json"})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            out = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        out = {"error": {"code": e.code, "message": e.read().decode()[:2000]}}
    except Exception as e:  # noqa: BLE001 — every failure is recorded, none is swallowed
        out = {"error": {"code": "transport", "message": repr(e)}}
    out["_latency_s"] = round(time.time() - t0, 3)
    out["_case"] = case["id"]
    tmp = "%s.%s.tmp" % (cache, uuid.uuid4().hex[:8])
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(out, fh)
    os.replace(tmp, cache)  # two cases can share one state; a reader must never see a half-written file
    return out, False


def cmd_run(cases, model, limit):
    """Send every case, `JEVPAR` at a time. A systematic refusal (a guardrail, a bad key) stops the run
    early: 2 552 requests against a wall is not a measurement, and a cached response is never re-sent."""
    from concurrent.futures import ThreadPoolExecutor
    todo = cases[:limit] if limit else cases
    workers = int(os.environ.get("JEVPAR", "8"))
    sent = cached = failed = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, (case, (out, hit)) in enumerate(zip(todo, pool.map(lambda c: request(c, model), todo)), 1):
            cached += hit
            if "error" in out:
                failed += 1
                if failed <= 3:
                    print("  ERR %s: %s" % (case["id"], json.dumps(out["error"])[:300]), flush=True)
                if sent == 0 and failed >= 5:
                    print("ABORT: every request refused (%s) — access, not the model. %d sent, %d failed."
                          % (json.dumps(out["error"])[:200], sent, failed))
                    return
            else:
                sent += 1
            if i % 100 == 0:
                print("  %d/%d (%d live, %d cached, %d failed)" % (i, len(todo), sent, cached, failed), flush=True)
    print("run: %d requests, %d live, %d cached, %d failed" % (len(todo), sent, cached, failed))


def p_defect(out):
    a = (out.get("answers") or {}).get("defect") or {}
    v = a.get("noul")
    return None if v is None else float(v)


def p_established(out):
    """The score answer, rescaled to 0..1 with 1 = "establishes the claim"; read as a defect signal it is
    inverted. A second predictor for free from the same cached response."""
    a = (out.get("answers") or {}).get("established") or {}
    v = a.get("score")
    return None if v is None else max(0.0, min(1.0, 1.0 - float(v) / 3.0))


def auc(pos, neg):
    """Rank-based AUC (Mann-Whitney, ties averaged). 0.5 = the score carries no signal."""
    if not pos or not neg:
        return None
    tagged = sorted([(v, 1) for v in pos] + [(v, 0) for v in neg])
    ranks, i = [], 0
    while i < len(tagged):
        j = i
        while j + 1 < len(tagged) and tagged[j + 1][0] == tagged[i][0]:
            j += 1
        avg = (i + j) / 2.0 + 1
        ranks.extend([(tagged[k][1], avg) for k in range(i, j + 1)])
        i = j + 1
    r1 = sum(rk for lab, rk in ranks if lab == 1)
    n1, n0 = len(pos), len(neg)
    return (r1 - n1 * (n1 + 1) / 2.0) / (n1 * n0)


def chat_prompt(case):
    s = case["state"]
    return (
        "A change claims to fix the following.\n\nCLAIM:\n%s\n\n"
        "Below is the complete diff of ONE file in that change.\n\nFILE: %s\nHUNK:\n%s\n\n"
        "Question: is there a defect in this file's diff that would make the change fail to establish the "
        "claim? Answer true only if a reader could point at a specific line that breaks it.\n"
        "Reply with ONLY a probability between 0 and 1 (0 = certainly no defect, 1 = certainly a defect). "
        "No words." % (s["claim"], s["file"], s["hunk"])
    )


def chat_body(case, model):
    """One builder for the control request: `grade` must rebuild the identical body or it reads no cache."""
    body = {"model": model, "max_tokens": 600, "temperature": 0,
            "messages": [{"role": "user", "content": chat_prompt(case)}]}
    # A thinking model spends the whole budget in `reasoning` and returns `content: null` unless it is
    # capped. Only sent when asked for: a model that does not take the parameter must not be sent it.
    if os.environ.get("JEJ_REASONING"):
        body["reasoning"] = {"max_tokens": int(os.environ["JEJ_REASONING"])}
    return body


def chat_request(case, model):
    body = chat_body(case, model)
    raw = json.dumps(body, ensure_ascii=False).encode()
    key = hashlib.sha1(raw).hexdigest()
    cache = os.path.join(RESPONSES, key + ".json")
    if os.path.isfile(cache):
        try:
            with open(cache, encoding="utf-8") as fh:
                return json.load(fh), True
        except ValueError:
            pass
    req = urllib.request.Request(
        "https://openrouter.ai/api/v1/chat/completions", data=raw,
        headers={"Authorization": "Bearer " + os.environ.get("OPENROUTER_API_KEY", ""),
                 "Content-Type": "application/json"})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=240) as resp:
            out = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        out = {"error": {"code": e.code, "message": e.read().decode()[:2000]}}
    except Exception as e:  # noqa: BLE001
        out = {"error": {"code": "transport", "message": repr(e)}}
    out["_latency_s"] = round(time.time() - t0, 3)
    out["_case"] = case["id"]
    tmp = "%s.%s.tmp" % (cache, uuid.uuid4().hex[:8])
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(out, fh)
    os.replace(tmp, cache)
    return out, False


def p_chat(out):
    """The last number the model wrote. A thinking model may spend the whole budget in `reasoning` before it
    emits `content`; both are read, content first, and a missing answer is a missing answer."""
    try:
        msg = out["choices"][0]["message"]
    except (KeyError, IndexError, TypeError):
        return None
    content = msg.get("content")
    if content:
        nums = re.findall(r"(?<![\d.])(\d*\.?\d+)(?![\d])", content)
        if nums:
            v = float(nums[0])  # the answer comes first when the model writes an answer
            return v / 100.0 if v > 1 else v
    for txt in (msg.get("reasoning"), msg.get("reasoning_content")):
        if not txt:
            continue
        # No answer emitted: take the last probability-shaped number it reasoned towards, and let the
        # missing-answer count in the report say how often that happened.
        nums = re.findall(r"(?<![\d.])(0\.\d+|1\.0*|0|1)(?![\d.])", txt)
        if nums:
            return float(nums[-1])
    return None


def cached_out(case, model):
    body = {"model": model, "state": case["state"],
            "questions": questions(case["state"]["claim"], case["file"])}
    key = hashlib.sha1(json.dumps(body, ensure_ascii=False).encode()).hexdigest()
    path = os.path.join(RESPONSES, key + ".json")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except ValueError:
        return None


def cmd_control(cases, model, limit):
    """The same states through a chat model: separates "the claim+hunk shape is too hard" from "Jev fails it"."""
    from concurrent.futures import ThreadPoolExecutor
    if limit and limit < len(cases):
        # Balanced: every positive plus as many negatives as the limit leaves room for. Taking the first
        # N cases in file order would grade one end of the archive and call it the archive.
        pos = [c for c in cases if c["label"] == 1]
        neg = [c for c in cases if c["label"] == 0]
        todo = pos + neg[:max(0, limit - len(pos))]
    else:
        todo = cases
    workers = int(os.environ.get("JEVPAR", "8"))
    sent = failed = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, (case, (out, hit)) in enumerate(zip(todo, pool.map(lambda c: chat_request(c, model), todo)), 1):
            if "error" in out:
                failed += 1
                if failed <= 3:
                    print("  ERR %s: %s" % (case["id"], json.dumps(out["error"])[:300]), flush=True)
                if sent == 0 and failed >= 5:
                    print("ABORT: every request refused — access, not the model.")
                    return
            else:
                sent += 1
            if i % 100 == 0:
                print("  %d/%d (%d ok, %d failed)" % (i, len(todo), sent, failed), flush=True)
    print("control: %d requests, %d ok, %d failed" % (len(todo), sent, failed))


def grade(cases, model, control_model=None):
    rows, missing, cost, tokens, lat = [], 0, 0.0, 0, []
    alt = {}
    for case in cases:
        out = cached_out(case, model)
        if out is None:
            missing += 1
            continue
        s = p_established(out)
        if s is not None:
            alt.setdefault(case["kind"], []).append(s)
        p = p_defect(out)
        if p is None:
            missing += 1
            continue
        u = out.get("usage") or {}
        cost += float(u.get("cost") or 0)
        tokens += int(u.get("input_tokens") or 0)
        lat.append(out.get("_latency_s"))
        rows.append((case, p))

    def group(kind):
        return [p for c, p in rows if c["kind"] == kind]

    pos, neg, fals = group("positive"), group("hard-negative"), group("falsification")
    cross, nulls = group("cross-negative"), group("null")
    a = auc(pos, neg + cross)
    a_alt = auc(alt.get("positive", []), alt.get("hard-negative", []) + alt.get("cross-negative", []))
    prevalence = sum(c["label"] for c, _ in rows) / max(1, len(rows))
    constant_brier = prevalence * (1 - prevalence)
    sweep = []
    for t in [round(x / 100, 2) for x in range(5, 100, 5)]:
        r = sum(1 for p in pos if p >= t) / len(pos) if pos else 0.0
        f = sum(1 for p in neg + cross if p >= t) / (len(neg) + len(cross)) if (neg or cross) else 0.0
        sweep.append((t, r, f))
    full = [s for s in sweep if s[1] >= 1.0]
    chosen = min(full, key=lambda s: s[2])[0] if full else None
    brier = statistics.fmean([(p - c["label"]) ** 2 for c, p in rows]) if rows else None
    lat = [x for x in lat if x]
    lines = [
        "# Jev probe — report", "",
        "Model: `%s`  ·  cases graded: %d  ·  without a response: %d" % (model, len(rows), missing), "",
        "| group | n | p50 P(defect) | min | max |", "|---|---|---|---|---|",
    ]
    for kind, vals in (("positive", pos), ("hard-negative", neg), ("cross-negative", cross),
                       ("falsification", fals), ("null", nulls)):
        if vals:
            lines.append("| %s | %d | %.2f | %.2f | %.2f |" % (
                kind, len(vals), statistics.median(vals), min(vals), max(vals)))
    lines += ["", "## Threshold sweep", "", "| t | recall | false-positive rate |", "|---|---|---|"]
    lines += ["| %.2f | %.2f | %.2f |" % s for s in sweep]
    lines += ["", "## Verdict", ""]
    if chosen is None:
        lines.append("- **No threshold reaches recall 1.0** — a recorded defect reads as clean. Disqualified as a router.")
    else:
        fpr = [s for s in sweep if s[0] == chosen][0][2]
        fp = sum(1 for p in fals if p >= chosen) / len(fals) if fals else 0.0
        ph = sum(1 for p in nulls if p >= chosen) / len(nulls) if nulls else 0.0
        lines.append("- recall 1.0 at t=%.2f, false-positive rate %.2f on hard + cross negatives." % (chosen, fpr))
        lines.append("- falsification (fixed tree reads as a defect): %.2f of %d cases." % (fp, len(fals)))
        lines.append("- null state reads as a defect: %.2f of %d." % (ph, len(nulls)))
        lines.append("- usable as a router" + (
            " **only if** falsification and null are 0.0." if (fp or ph) else " at t=%.2f." % chosen))
    if brier is not None:
        lines.append("- Brier score: %.3f  (a constant guess at the base rate %.2f scores %.3f — lower is better)"
                     % (brier, prevalence, constant_brier))
    if a is not None:
        lines.append("- **AUC (positive vs hard+cross negative): %.3f** — 0.5 is no signal." % a)
    if a_alt is not None:
        ap = alt.get("positive", [])
        an = alt.get("hard-negative", []) + alt.get("cross-negative", [])
        lines.append("- AUC of the `score` answer instead of the `noul`: %.3f (n=%d/%d)"
                     % (a_alt, len(ap), len(an)))
        best = min(((t, sum(1 for p in an if p >= t) / len(an)) for t in [i / 20 for i in range(1, 19)]
                    if sum(1 for p in ap if p >= t) / len(ap) >= 0.9), key=lambda x: x[1], default=None)
        lines.append("  - best `score` operating point at recall>=0.90: %s"
                     % ("none" if best is None else "t=%.2f, false-positive rate %.2f" % best))
    if control_model:
        cp, cn, cmissing = [], [], 0
        for case in cases:
            body = chat_body(case, control_model)
            key = hashlib.sha1(json.dumps(body, ensure_ascii=False).encode()).hexdigest()
            path = os.path.join(RESPONSES, key + ".json")
            if not os.path.isfile(path):
                cmissing += 1
                continue
            try:
                with open(path, encoding="utf-8") as fh:
                    v = p_chat(json.load(fh))
            except ValueError:
                v = None
            if v is None:
                cmissing += 1
                continue
            if case["kind"] in ("hard-negative", "cross-negative"):
                cn.append(v)
            elif case["kind"] == "positive":
                cp.append(v)
        if cp and cn:
            lines.append("- control `%s` on the same states: AUC %.3f (n=%d pos, %d neg, %d without answer)"
                         % (control_model, auc(cp, cn), len(cp), len(cn), cmissing))
    if lat:
        lines.append("- latency s: p50 %.2f, p95 %.2f" % (
            statistics.median(lat), sorted(lat)[int(len(lat) * 0.95) - 1] if len(lat) > 1 else lat[0]))
    lines.append("- input tokens: %d  ·  cost: $%.4f" % (tokens, cost))
    with open(REPORT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "plan"
    argv = sys.argv[2:]
    model = DEFAULT_MODEL
    root = DEFAULT_ROOT
    control_model = None
    limit = None
    for i, a in enumerate(argv):
        if a == "--model":
            model = argv[i + 1]
        elif a == "--root":
            root = argv[i + 1]
        elif a == "--limit":
            limit = int(argv[i + 1])
        elif a == "--control-model":
            control_model = argv[i + 1]
    if cmd == "extract":
        cases, skipped = build(root, limit)
        kinds = {}
        for c in cases:
            kinds[c["kind"]] = kinds.get(c["kind"], 0) + 1
        print("cases: %d %s" % (len(cases), kinds))
        print("skipped: %d" % len(skipped))
        for name, why in skipped[:15]:
            print("  - %s: %s" % (name, why))
    elif cmd == "plan":
        cases = load_cases()
        chars = sum(len(c["state"]["hunk"]) + len(c["state"]["claim"]) for c in cases)
        print("cases: %d  ·  state chars: %d (~%d tokens)" % (len(cases), chars, chars // 4))
        print("projected cost at $0.042/M input: $%.3f" % (chars / 4 * 0.042 / 1_000_000))
        print("requests: 1 per case (3 questions answered in parallel)")
    elif cmd == "run":
        cmd_run(load_cases(), model, limit)
    elif cmd == "grade":
        grade(load_cases(), model, control_model)
    elif cmd == "control":
        cmd_control(load_cases(), control_model or DEFAULT_CONTROL, limit)
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
