// Vault circle state, one set of cases for both pushers: the app (lib/sync.ts = pushCircleState(pullCircleState(meta,
// vault)), tests/circles.test.ts) and the service worker mirror (public/sw.js vaultCircles, tests/pwa.test.ts). Each
// case has one expected push for both. The SW passes pulled circles through where the app merges them, so no case
// holds the same circle on both sides (owner-state merging is tested in tests/circles.test.ts).
import type { OwnerCircle, VaultCircles } from "../../lib/member-keys.ts";

type C = OwnerCircle & { name?: string };
export interface VaultCirclesCase { name: string; remote: Partial<VaultCircles<C>>; local: VaultCircles<C>; want: VaultCircles<C> }

/** Every circle field a vault push must carry. */
export const VAULT_CIRCLE_FIELDS = ["circlePins", "circleSeen", "circles"];

export const circle: C = { id: "c1", name: "Family", createdAt: 1, updatedAt: 3 };
export const committed = { pub: "spub", priv: { kty: "EC", d: "x" }, createdAt: 2 };
export const pendingCircle: C = { ...circle, sign: { ...committed, pending: true } };
const signed: C = { ...circle, id: "c2", sign: committed };
const none = { circles: [], circlePins: {}, circleSeen: {} };

export const vaultCircleCases: VaultCirclesCase[] = [
  { name: "no remote vault: local circle state", remote: {}, local: { circles: [circle], circlePins: { c1: "a" }, circleSeen: { c1: 4 } },
    want: { circles: [circle], circlePins: { c1: "a" }, circleSeen: { c1: 4 } } },
  { name: "every field from the vault: signer pushed committed, pins this device wins, floors the higher per circle",
    remote: { circles: [pendingCircle], circlePins: { c1: "r", c2: "b" }, circleSeen: { c1: 9, c2: 2 } },
    local: { circles: [], circlePins: { c1: "a" }, circleSeen: { c1: 5, c3: 1 } },
    want: { circles: [{ ...pendingCircle, sign: committed }], circlePins: { c1: "a", c2: "b" }, circleSeen: { c1: 9, c2: 2, c3: 1 } } },
  { name: "a 2.6 vault without circleSeen keeps this device's floor", remote: { circlePins: { c2: "b" } },
    local: { circles: [signed], circlePins: {}, circleSeen: { c1: 5 } }, want: { circles: [signed], circlePins: { c2: "b" }, circleSeen: { c1: 5 } } },
  { name: "a lower or malformed floor in the vault lowers nothing", remote: { circleSeen: { c1: 2, c2: -1, c3: "9" as unknown as number, c4: 1.5 } },
    local: { ...none, circleSeen: { c1: 5 } }, want: { ...none, circleSeen: { c1: 5 } } },
  { name: "empty remote circles keep local ones", remote: { circles: [] }, local: { ...none, circles: [circle] }, want: { ...none, circles: [circle] } },
  { name: "nothing anywhere", remote: {}, local: none, want: none },
  { name: "a pending local signer is pushed committed", remote: {}, local: { ...none, circles: [pendingCircle, signed] },
    want: { ...none, circles: [{ ...pendingCircle, sign: committed }, signed] } },
];

// Vault share list (meta `shares`, contract C1) and its tombstones (meta `sharesGone`, C3), one clock for both:
// lib/sync.ts mergeShareLists through syncNow (tests/sync.test.ts: pushed and written to meta) and public/sw.js
// vaultShares (tests/pwa.test.ts: pushed). An entry is kept until 30 days after expiresAt and needs the worker's id shape
// (8-64 of A-Za-z0-9_-), a token of at least 20 characters and a finite expiresAt; title (at most 120 characters) and
// createdAt are optional. A tombstone { id, at } is kept 400 days.
type ShareLists = { shares?: unknown; sharesGone?: unknown };
export interface VaultSharesCase { name: string; remote: ShareLists; local: ShareLists; want: { shares: unknown[]; sharesGone: unknown[] } }
const DAY = 864e5;
export const SHARES_NOW = 1000 * DAY;
const tok = (c: string) => c.repeat(24);
const s1 = { id: "share001", token: tok("a"), expiresAt: SHARES_NOW + 300 * DAY }, s2 = { id: "share002", token: tok("b"), expiresAt: SHARES_NOW + 200 * DAY };
const s3 = { id: "share003", token: tok("c"), expiresAt: SHARES_NOW + 100 * DAY };
const gone = (id: string, at: number) => ({ id, at });
const g1 = gone(s1.id, SHARES_NOW - DAY);
const just = (...shares: unknown[]) => ({ shares, sharesGone: [] });
/** 120 code points, 240 UTF-16 units: the title limit counts characters. */
const leaf120 = "\u{1F33F}".repeat(120);

export const vaultShareCases: VaultSharesCase[] = [
  { name: "union by id of this device's and the vault's lists", remote: { shares: [s2] }, local: { shares: [s1] }, want: just(s1, s2) },
  { name: "same id: this device's token, the later expiry", remote: { shares: [{ ...s1, token: tok("o"), expiresAt: s1.expiresAt + DAY }] }, local: { shares: [s1] },
    want: just({ ...s1, expiresAt: s1.expiresAt + DAY }) },
  { name: "dropped only 30 days after expiry, on either side", local: { shares: [{ ...s1, expiresAt: SHARES_NOW - 31 * DAY }, { ...s2, expiresAt: SHARES_NOW - 29 * DAY }] },
    remote: { shares: [{ id: "share003", token: tok("c"), expiresAt: SHARES_NOW - 31 * DAY }] }, want: just({ ...s2, expiresAt: SHARES_NOW - 29 * DAY }) },
  { name: "a 2.7 vault without shares keeps this device's list", remote: {}, local: { shares: [s1, s2] }, want: just(s1, s2) },
  { name: "deleted on device A does not return from device B's vault", local: { shares: [s2], sharesGone: [g1] },
    remote: { shares: [s1, s2] }, want: { shares: [s2], sharesGone: [g1] } },
  { name: "a tombstone in the vault drops this device's entry", local: { shares: [s1, s2] },
    remote: { shares: [s2], sharesGone: [g1] }, want: { shares: [s2], sharesGone: [g1] } },
  { name: "a 2.8 push without tombstones or titles keeps this device's tombstones and titles",
    local: { shares: [{ ...s2, title: "Morning stack", createdAt: SHARES_NOW - 5 * DAY }], sharesGone: [g1] },
    remote: { shares: [s1, s2] }, want: { shares: [{ ...s2, title: "Morning stack", createdAt: SHARES_NOW - 5 * DAY }], sharesGone: [g1] } },
  { name: "tombstones: union by id, the earliest at", local: { shares: [], sharesGone: [gone("share005", SHARES_NOW - 5 * DAY)] },
    remote: { sharesGone: [gone("share005", SHARES_NOW - 9 * DAY), gone("share006", SHARES_NOW - DAY)] },
    want: { shares: [], sharesGone: [gone("share005", SHARES_NOW - 9 * DAY), gone("share006", SHARES_NOW - DAY)] } },
  { name: "a tombstone is dropped 400 days after at; a future at counts from now",
    local: { shares: [], sharesGone: [gone("share005", SHARES_NOW - 401 * DAY), gone("share006", SHARES_NOW - 400 * DAY)] },
    remote: { sharesGone: [gone("share007", SHARES_NOW + 900 * DAY)] },
    want: { shares: [], sharesGone: [gone("share006", SHARES_NOW - 400 * DAY), gone("share007", SHARES_NOW)] } },
  { name: "malformed tombstones are ignored on both sides", local: { shares: [s1], sharesGone: [null, gone("s1", 1), { id: "share002", at: "9" }, gone("share003", Infinity)] },
    remote: { shares: [s2], sharesGone: ["share001", { id: 9, at: 1 }, gone("share004", NaN), { at: 1 }] }, want: just(s1, s2) },
  { name: "a vault sharesGone field that is not a list", local: { shares: [s1], sharesGone: [g1] }, remote: { shares: [s2], sharesGone: { g1 } },
    want: { shares: [s2], sharesGone: [g1] } },
  { name: "title and createdAt: this device's field, else the vault's, also for a vault-only entry",
    local: { shares: [{ ...s1, title: "Mine" }] },
    remote: { shares: [{ ...s1, title: "Theirs", createdAt: SHARES_NOW - 3 * DAY }, { ...s2, title: "Evening", createdAt: SHARES_NOW - 2 * DAY }] },
    want: just({ ...s1, title: "Mine", createdAt: SHARES_NOW - 3 * DAY }, { ...s2, title: "Evening", createdAt: SHARES_NOW - 2 * DAY }) },
  { name: "a bad title or createdAt drops that field, not the entry",
    local: { shares: [{ ...s1, title: "x".repeat(121), createdAt: "9" }] },
    remote: { shares: [{ ...s2, title: leaf120, createdAt: Infinity }, { ...s3, title: "", createdAt: SHARES_NOW }] },
    want: just(s1, { ...s2, title: leaf120 }, { ...s3, createdAt: SHARES_NOW }) },
  { name: "malformed vault entries are ignored", local: { shares: [s1] },
    remote: { shares: [null, "share009", { id: 9, token: tok("k"), expiresAt: 1 }, { id: "share008", token: tok("k") },
      { id: "share007", token: tok("k"), expiresAt: "9" }, { id: "s6", token: tok("k"), expiresAt: SHARES_NOW }, { id: "share/05", token: tok("k"), expiresAt: SHARES_NOW },
      { id: "share004", token: "short", expiresAt: SHARES_NOW }, { id: "share010", token: tok("k"), expiresAt: Infinity },
      { id: "share011", token: tok("k"), expiresAt: NaN }, s2] }, want: just(s1, s2) },
  { name: "a malformed entry on this device is dropped too", remote: { shares: [s2] },
    local: { shares: [s1, { id: "s1", token: "k1", expiresAt: SHARES_NOW }, { id: "share012", token: tok("k"), expiresAt: Infinity }] }, want: just(s1, s2) },
  { name: "a vault shares field that is not a list", remote: { shares: { s2 } }, local: { shares: [s1] }, want: just(s1) },
  { name: "nothing anywhere", remote: {}, local: {}, want: just() },
];
