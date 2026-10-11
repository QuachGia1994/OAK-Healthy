import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { REVOKE_AFTER_MS, saveBlob, type DownloadEnv } from "../lib/download.ts";

/** Fake page: logs every DOM/URL call; timers wait until the test runs them. */
function fakeEnv() {
  const log: string[] = [], timers: { run: () => void; ms: number }[] = [];
  const a = { href: "", download: "", click: () => { log.push(`click ${a.download}`); }, remove: () => { log.push("remove"); } };
  const env = {
    document: { createElement: (tag: string) => { log.push(`element ${tag}`); return a; }, body: { appendChild: (x: unknown) => { log.push("append"); return x; } } },
    URL: { createObjectURL: () => { log.push("url"); return "blob:oak-1"; }, revokeObjectURL: (u: string) => { log.push(`revoke ${u}`); } },
    setTimeout: (run: () => void, ms: number) => { timers.push({ run, ms }); },
  };
  return { log, timers, a, env: env as unknown as DownloadEnv };
}

test("saveBlob clicks an anchor in the page and frees the blob URL only later, not in the same tick", () => {
  const { log, timers, a, env } = fakeEnv();
  saveBlob(new Blob(["x"]), "oak-healthy-history-2026-10-11.csv", env);
  assert.deepEqual(log, ["url", "element a", "append", "click oak-healthy-history-2026-10-11.csv"], "no revoke right after click()");
  assert.equal(a.href, "blob:oak-1");
  assert.equal(timers.length, 1);
  assert.equal(timers[0].ms, REVOKE_AFTER_MS);
  assert.ok(REVOKE_AFTER_MS >= 10_000, "long enough for a slow save dialog");
  timers[0].run();
  assert.deepEqual(log.slice(4), ["remove", "revoke blob:oak-1"]);
});

test("Settings saves the JSON backup and the history CSV through saveBlob, with no revoke of its own", () => {
  const src = readFileSync(new URL("../components/settings.tsx", import.meta.url), "utf8");
  assert.equal(src.match(/saveBlob\(/g)?.length, 2);
  assert.equal(src.includes("revokeObjectURL"), false);
  assert.equal(src.includes("createObjectURL"), false);
});
