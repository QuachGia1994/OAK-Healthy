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
