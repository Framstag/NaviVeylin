// hook-probe.mjs — does the extension's own `tool_result` hook apply the bound, through the factory pi calls?
//
// Why this exists: the cases in `tools/loop-cases.sh` drive the CLI entry point, because the loop's self-test
// cannot start an agent. The hook is the entry point a child actually goes through, so this probe calls the
// extension's factory with a stand-in for pi's API, hands it a `tool_result` event and reads what it returns — the
// same object pi would put in the child's context. It is not a substitute for the gate; it is the evidence that the
// two entry points are one rule.
//
// Usage: node openspec/changes/fix-a-tool-result-over-its-bound-is-trimmed/evidence/hook-probe.mjs
//        (from the change root; the extension is resolved beside the project's tools)
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const extension = new URL("../../../../tools/pi-trim-result.mjs", import.meta.url);
const mod = await import(extension.href);

const record = join(mkdtempSync(join(tmpdir(), "hook-probe.")), "tool-result-trims.log");
process.env.LOOP_TOOL_RESULT_MAX_CHARS = "8000";
process.env.LOOP_TOOL_RESULT_TRIM_RECORD = record;

const handlers = {};
mod.default({ on: (name, fn) => { handlers[name] = fn; } });
if (typeof handlers.tool_result !== "function") {
  console.log("PROBE-HOOK: no tool_result handler was registered");
  process.exit(1);
}

const call = (text, toolName) =>
  handlers.tool_result({ type: "tool_result", toolCallId: "c1", toolName, input: {}, isError: false,
    content: [{ type: "text", text }] }, {});

const big = "a".repeat(20000) + "TAIL\n";
const trimmed = await call(big, "ctx_read");
const text = trimmed && trimmed.content && trimmed.content[0] && trimmed.content[0].text;

console.log(`PROBE-HOOK chars=${big.length} kept=${text ? text.length : "none"}`);
console.log(`PROBE-HOOK prefix=${text && text.slice(0, 8000) === big.slice(0, 8000) ? "unchanged first 8000" : "CHANGED"}`);
console.log(`PROBE-HOOK tail=${text && text.includes("TAIL") ? "LEAKED" : "cut"}`);
console.log(`PROBE-HOOK notice=${text && text.includes("LOOP-TRIMMED") ? "named" : "MISSING"}`);
console.log(`PROBE-HOOK record=${readFileSync(record, "utf8").trim()}`);

const small = await call("b".repeat(1000) + "END\n", "ctx_read");
console.log(`PROBE-HOOK undersized=${small === undefined ? "untouched (nothing returned)" : "CHANGED"}`);

const image = await call("c".repeat(10), "ctx_read");
console.log(`PROBE-HOOK at-the-bound=${image === undefined ? "untouched (nothing returned)" : "CHANGED"}`);

process.env.LOOP_TOOL_RESULT_MAX_CHARS = "0";
const off = await call(big, "ctx_read");
console.log(`PROBE-HOOK bound-0=${off === undefined ? "no bound, nothing returned" : "CHANGED"}`);
