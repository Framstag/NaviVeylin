#!/usr/bin/env node
// pi-trim-result.mjs — one bound on what a single tool result may put into a child's context (issue #660).
//
// Why this file exists: a child's tool result is injected whole and stays in its context for the rest of the
// session, so every later turn re-reads it, and nothing between the tool and the context bounds it. Measured over
// the archive the loop reads (`python3 tools/loop-spend.py`): 59.2 M characters of tool output injected once,
// 876.4 M tokens of it re-read — 53 % of everything the archive spent — with a 116.6 k `ctx_read` and 51.3 k
// `ctx_shell` at the top, while the median result is 825 characters (`evidence/result-sizes.py` in the change
// `2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed`). The spend is the tail, so the bound is a rule about
// the tail: a result above it is cut and named, a result under it is handed over byte for byte.
//
// Prose does not do this job. The worker prompt has asked a child to read the section it needs rather than the
// whole document, and to cite a file rather than read it again, since #246 — and the archive's repeated-read share
// is 45 % before and after that change, with a 116.6 k single result standing beside it.
//
// Two entry points, one rule:
//
//   * as a pi extension (`pi --extension tools/pi-trim-result.mjs`, which the adapter every loop child is started
//     through does), it registers the `tool_result` hook and replaces a text block above the bound. The hook never
//     throws: an error leaves the result exactly as the tool returned it, because a trim that could fail a tool
//     mid-item would be worse than the spend it saves.
//   * as a CLI (`node tools/pi-trim-result.mjs --bound <n> [--tool <name>] [--record <file>]`), it applies the same
//     rule to stdin, which is what the cases in `tools/loop-cases.sh` drive — so the rule they prove is the rule
//     the hook applies, and there is no second implementation of it.
//
// Where the values come from: the bound is `LOOP_TOOL_RESULT_MAX_CHARS` (`tools/loop/loop.conf`, a recorded value;
// `0` or absent means no bound), and the record is `LOOP_TOOL_RESULT_TRIM_RECORD`, which the adapter names beside
// the child's own event stream so the trim is a fact of the item rather than of the child's memory.
import { appendFileSync, readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

/** The bound as the recorded key states it: a positive whole number of characters, or 0 for "no bound". */
export function recordedBound(raw) {
  const n = Number.parseInt(String(raw ?? "").trim(), 10);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** The line that tells the child what it is reading, and how to read the rest of it. */
export function trimNotice(tool, total, bound) {
  return `\n[LOOP-TRIMMED: ${tool} returned ${total} characters; the first ${bound} are shown — read the rest in`
    + ` ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]\n`;
}

/** One line of the record, appended where the item's own evidence stands. */
export function trimRecordLine(tool, total, bound) {
  return `LOOP-TOOL-RESULT-TRIM: ${tool} chars=${total} kept=${bound}\n`;
}

/**
 * The rule: a result at or under the bound is returned untouched; one above it is cut to its first `bound`
 * characters — never in the middle of a surrogate pair — and carries one line naming the tool, the size and the
 * bound, and the trim is appended to `record` when a path names one.
 */
export function trimResult(text, bound, tool, record) {
  if (typeof text !== "string" || !(bound > 0) || text.length <= bound) return text;
  const total = text.length;
  let cut = text.slice(0, bound);
  const last = cut.charCodeAt(cut.length - 1);
  if (last >= 0xd800 && last <= 0xdbff) cut = cut.slice(0, -1);
  if (record) {
    try {
      appendFileSync(record, trimRecordLine(tool, total, bound));
    } catch {
      // A record that cannot be written is not a reason to hand the child a result it cannot afford to re-read.
    }
  }
  return cut + trimNotice(tool, total, bound);
}

/**
 * The content array the hook returns, with every oversized text block cut. A changed result drops
 * `structuredContent`: it no longer describes what the child is reading, and the agent's own contract says so.
 */
export function trimContent(content, bound, tool, record) {
  let changed = false;
  const out = (content || []).map((block) => {
    if (!block || block.type !== "text" || typeof block.text !== "string") return block;
    const text = trimResult(block.text, bound, tool, record);
    if (text !== block.text) changed = true;
    return text === block.text ? block : { ...block, text };
  });
  return changed ? out : null;
}

/** The extension factory: one hook, and the bound the run recorded. */
export default function (pi) {
  pi.on("tool_result", async (event, ctx) => {
    try {
      const bound = recordedBound(process.env.LOOP_TOOL_RESULT_MAX_CHARS);
      if (!bound) return undefined;
      const record = process.env.LOOP_TOOL_RESULT_TRIM_RECORD || "";
      const content = trimContent(event.content, bound, event.toolName || "?", record);
      return content ? { content } : undefined;
    } catch {
      return undefined; // the result stands as the tool returned it
    }
  });
}

/** The CLI: the same rule over stdin, for the cases and for a reader who wants to see what it would do. */
function cli(argv) {
  let bound = recordedBound(process.env.LOOP_TOOL_RESULT_MAX_CHARS);
  let tool = "?";
  let record = process.env.LOOP_TOOL_RESULT_TRIM_RECORD || "";
  for (let i = 0; i < argv.length; i += 1) {
    const name = argv[i];
    const value = argv[i + 1];
    if (name === "--bound") {
      bound = recordedBound(value);
      i += 1;
    } else if (name === "--tool") {
      tool = value || tool;
      i += 1;
    } else if (name === "--record") {
      record = value || record;
      i += 1;
    } else if (name === "-h" || name === "--help") {
      process.stdout.write("usage: pi-trim-result.mjs --bound <chars> [--tool <name>] [--record <file>] < result\n");
      return 0;
    } else {
      process.stderr.write(`pi-trim-result: unknown argument ${name}\n`);
      return 2;
    }
  }
  const text = readFileSync(0, "utf8");
  process.stdout.write(trimResult(text, bound, tool, record));
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = cli(process.argv.slice(2));
}
