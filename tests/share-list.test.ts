import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { cleanShareFields, deleteOne, forgetShare, isShareId, parseSharesGone, shareRows, shareTitle, SHARE_GONE_TTL_MS, type DeleteOneDeps, type ShareGone } from "../lib/domain/shares.ts";
import type { ShareRef } from "../lib/domain/wipe.ts";

const NOW = 1_800_000_000_000, DAY = 864e5;
const A: ShareRef = { id: "aaaaaaaa", token: "t".repeat(43), expiresAt: NOW + 300 * DAY, title: "Mom's stack", createdAt: NOW - 65 * DAY };
const B: ShareRef = { id: "bbbbbbbb", token: "u".repeat(43), expiresAt: NOW + 360 * DAY, title: "Coach", createdAt: NOW - 5 * DAY };
const OLD: ShareRef = { id: "oldentry", token: "v".repeat(43), expiresAt: NOW - DAY }; // a 2.8 entry: no title, no createdAt, expired

test("share list: newest first, a 2.8 entry has no title or created date, expired ones are marked, gone ids are hidden", () => {
  const gone: ShareGone[] = [{ id: "cccccccc", at: NOW - DAY }];
  const C: ShareRef = { id: "cccccccc", token: "w".repeat(43), expiresAt: NOW + DAY, title: "Deleted elsewhere", createdAt: NOW };
  assert.deepEqual(shareRows([OLD, A, C, B], gone, NOW), [
    { id: "bbbbbbbb", title: "Coach", createdAt: NOW - 5 * DAY, expiresAt: NOW + 360 * DAY, expired: false },
    { id: "aaaaaaaa", title: "Mom's stack", createdAt: NOW - 65 * DAY, expiresAt: NOW + 300 * DAY, expired: false },
    { id: "oldentry", title: null, createdAt: null, expiresAt: NOW - DAY, expired: true },
  ]);
  assert.deepEqual(shareRows([], [], NOW), []);
});

test("share list: forgetting a share removes it from shares and records the id (no token) in sharesGone", () => {
  assert.deepEqual(forgetShare([A, B], [], A.id, NOW), { shares: [B], gone: [{ id: "aaaaaaaa", at: NOW }] });
  const earlier = [{ id: "aaaaaaaa", at: NOW - 9 }];
  assert.deepEqual(forgetShare([A, B], earlier, A.id, NOW), { shares: [B], gone: earlier }, "the first deletion time stays");
});

test("share list: the title is trimmed and cut to 120 characters without splitting one", () => {
  assert.equal(shareTitle("  Mom's stack "), "Mom's stack");
  const long = "😀".repeat(130);
  assert.equal(shareTitle(long), "😀".repeat(120));
  assert.equal(shareTitle("   "), undefined);
});

test("share list: sharesGone keeps only share-id-shaped ids with a finite time, clamps a future time, drops ones past 400 days", () => {
  assert.equal(SHARE_GONE_TTL_MS, 400 * DAY);
  for (const ok of ["aaaaaaaa", "A_b-9".repeat(2), "x".repeat(64)]) assert.equal(isShareId(ok), true, ok);
  for (const bad of ["short", "x".repeat(65), "has space", "a/b/cdefgh", 12345678, null]) assert.equal(isShareId(bad), false, String(bad));
  const util = readFileSync(new URL("../worker/util.ts", import.meta.url), "utf8");
  assert.ok(util.includes("[A-Za-z0-9_-]{8,64}"), "same shape as the worker's validId");
  for (const raw of [null, undefined, "x", 5, { id: "aaaaaaaa", at: NOW }]) assert.deepEqual(parseSharesGone(raw, NOW), [], String(raw));
  assert.deepEqual(parseSharesGone([
    { id: "aaaaaaaa", at: NOW - DAY },
    { id: "bbbbbbbb", at: NOW + 9 * DAY },
    { id: "short", at: NOW },
    { id: "has space", at: NOW },
    { id: "x".repeat(65), at: NOW },
    { id: "cccccccc", at: Number.NaN },
    { id: "dddddddd", at: "1" },
    { id: "eeeeeeee", at: NOW - 401 * DAY },
    { id: "ffffffff", at: NOW - 400 * DAY },
    null, 7,
  ], NOW), [{ id: "aaaaaaaa", at: NOW - DAY }, { id: "bbbbbbbb", at: NOW }, { id: "ffffffff", at: NOW - 400 * DAY }]);
});

test("share list: a bad title or created date drops only that field, never the entry", () => {
  const base = { id: "aaaaaaaa", token: "t".repeat(43), expiresAt: NOW };
  assert.deepEqual(cleanShareFields({ ...A }), A);
  for (const title of ["", 5, null, "x".repeat(121)]) assert.deepEqual(cleanShareFields({ ...base, title, createdAt: NOW } as unknown as ShareRef), { ...base, createdAt: NOW }, String(title));
  for (const createdAt of [Number.NaN, Infinity, "1", null]) assert.deepEqual(cleanShareFields({ ...base, title: "Coach", createdAt } as unknown as ShareRef), { ...base, title: "Coach" }, String(createdAt));
  assert.equal(cleanShareFields({ ...base, title: "😀".repeat(120) }).title, "😀".repeat(120), "120 characters, not UTF-16 units");
  assert.deepEqual(shareRows([{ ...base, title: 5, createdAt: "x" } as unknown as ShareRef], [], NOW - 1), [{ id: "aaaaaaaa", title: null, createdAt: null, expiresAt: NOW, expired: false }]);
});

/** status: the server's answer; null: the request fails (offline). */
function deps(status: number | null) {
  const forgot: string[] = [];
  const d: DeleteOneDeps = {
    shares: async () => [A, B],
    request: async (s) => { assert.equal(s.token, A.token); if (status === null) throw new Error("Failed to fetch"); return { status }; },
    forget: async (id) => { forgot.push(id); },
  };
  return { d, forgot };
}

test("share list: deleting one, 200 = deleted and recorded as gone", async () => {
  const { d, forgot } = deps(200);
  assert.deepEqual(await deleteOne(d, A.id), { outcome: "deleted" });
  assert.deepEqual(forgot, ["aaaaaaaa"]);
});

test("share list: a refusal a retry cannot change (400/401/403/405) also leaves the list, shown with its expiry", async () => {
  for (const status of [400, 401, 403, 405]) {
    const { d, forgot } = deps(status);
    assert.deepEqual(await deleteOne(d, A.id), { outcome: "kept", expiresAt: NOW + 300 * DAY }, String(status));
    assert.deepEqual(forgot, ["aaaaaaaa"]);
  }
});

test("share list: offline, 429 or a server error keeps the entry; only the 429 is named, no raw fetch/HTTP text", async () => {
  for (const [status, tooMany] of [[429, true], [500, false], [503, false], [null, false]] as const) {
    const { d, forgot } = deps(status);
    assert.deepEqual(await deleteOne(d, A.id), { outcome: "failed", tooMany }, String(status));
    assert.deepEqual(forgot, [], "still listed");
  }
  const ui = readFileSync(new URL("../components/settings.tsx", import.meta.url), "utf8");
  const card = ui.slice(ui.indexOf("export function ShareLinks"), ui.indexOf("const WIPE_DOING"));
  assert.match(card, /toast\.error\(r\.tooMany \? `\$\{t\("Couldn't delete the share link\."\)\} \$\{t\("Too many attempts\. Try again later\."\)\}` : t\("Couldn't delete the share link\."\)\)/);
  assert.ok(!/r\.error|Failed to fetch|Request failed/.test(card), "no raw error text in the card");
});

test("share list UI: the card renders shareRows, the title only as text, and deletes behind an inline confirm", () => {
  const ui = readFileSync(new URL("../components/settings.tsx", import.meta.url), "utf8");
  const card = ui.slice(ui.indexOf("export function ShareLinks"), ui.indexOf("const WIPE_DOING"));
  assert.match(card, /shareRows\(list, parseSharesGone\(meta\[1\]\?\.value, now\), now\)/);
  assert.match(card, /\{r\.title \?\? t\("Untitled stack"\)\}/);
  assert.ok(!/dangerouslySetInnerHTML|innerHTML/.test(card), "text-only render");
  assert.ok(card.indexOf("setConfirm(r.id)") < card.indexOf("del(r.id)"), "Delete opens the confirm; only Yes, delete sends");
  assert.match(card, /No share links yet\./);
  assert.match(ui, /<Section title="Share links"[^>]*>\s*<ShareLinks \/>/);
  const acc = readFileSync(new URL("../lib/account.ts", import.meta.url), "utf8");
  const req = acc.slice(acc.indexOf("const shareDelete ="), acc.indexOf("export async function deleteOneShare"));
  assert.ok(req.includes("Bearer ${s.token}") && !req.includes("title"), "the title is never in the DELETE");
  assert.match(acc, /db\.transaction\("rw", db\.meta,[\s\S]{0,300}forgetShare\([\s\S]{0,200}bulkPut\(\[\{ key: "shares"[^\]]*key: "sharesGone"/, "C3 in one transaction");
});

test("share list: an id no longer in the list (deleted in another tab) sends nothing", async () => {
  const { d, forgot } = deps(500);
  assert.deepEqual(await deleteOne(d, "zzzzzzzz"), { outcome: "deleted" });
  assert.deepEqual(forgot, []);
});
