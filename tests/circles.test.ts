import { test } from "node:test";
import assert from "node:assert/strict";
import { deriveVault, newSyncKey, open, seal, unb64u } from "../lib/crypto.ts";
import {
  applyPins, canSign, checkSeq, checkSnapshot, commitSigners, contentKey, epochGate, REPLAYED, STALE_EPOCH, UPDATE_APP, joinAuth, joinProof, mergeCircles, newContentKey, outOfStep, publishEpoch, newJoinSecret, newMemberKeys, newSigner, openMemberKey,
  pinsAfterRemoval, pullCircleState, pushCircleState, signSnapshot, TAMPERED, trustedMembers, UNSIGNED, unwrapFor, verifyProof, wrapFor, type OwnerCircle, type VaultCircles,
} from "../lib/member-keys.ts";
import { VAULT_CIRCLE_FIELDS, vaultCircleCases } from "./fixtures/vault-circles.ts";

const CID = "circle123abc";

test("member private key opens only with the account's sync key", async () => {
  const sync = newSyncKey();
  const m = await newMemberKeys(sync);
  assert.equal(m.pub.length, 87); // raw uncompressed P-256 point
  assert.ok(!m.sealed.includes("\"d\""), "private JWK is sealed, not plain");
  await openMemberKey(sync.toLowerCase(), m.sealed); // same key, any formatting
  await assert.rejects(openMemberKey(newSyncKey(), m.sealed));
});

test("content key wrapped to a member unwraps for that member only", async () => {
  const a = await newMemberKeys(newSyncKey()), b = await newMemberKeys(newSyncKey());
  const raw = newContentKey();
  const blob = await seal(await contentKey(raw, "encrypt"), { hello: "circle" });
  const wrap = await wrapFor(raw, a.pub, CID);
  assert.equal(wrap.split(".").length, 3);
  assert.deepEqual(await open(await unwrapFor(a.priv, wrap, CID), blob), { hello: "circle" });
  await assert.rejects(unwrapFor(b.priv, wrap, CID));
  await assert.rejects(unwrapFor(a.priv, wrap, "othercircle1"), "wrap is bound to its circle");
});

test("rotated content key: a revoked member's old key cannot read the next publish", async () => {
  const a = await newMemberKeys(newSyncKey());
  const oldKey = await unwrapFor(a.priv, await wrapFor(newContentKey(), a.pub, CID), CID);
  const next = await seal(await contentKey(newContentKey(), "encrypt"), { v: 2 });
  await assert.rejects(open(oldKey, next));
});

test("join proof binds the invite secret, circle and public key", async () => {
  const j = newJoinSecret(), m = await newMemberKeys(newSyncKey()), other = await newMemberKeys(newSyncKey());
  const proof = await joinProof(j, CID, m.pub);
  assert.equal(await verifyProof(j, CID, m.pub, proof), true);
  assert.equal(await verifyProof(newJoinSecret(), CID, m.pub, proof), false);
  assert.equal(await verifyProof(j, "othercircle1", m.pub, proof), false);
  assert.equal(await verifyProof(j, CID, other.pub, proof), false, "server cannot swap in its own key");
  assert.equal(await verifyProof(j, CID, m.pub, "%%%"), false);
  assert.notEqual(await joinAuth(j), j);
  assert.equal(await joinAuth(j), await joinAuth(j));
});

test("trusted members: pinned keys survive invite rotation, forged or unproven keys are dropped", async () => {
  const oldJ = newJoinSecret(), j = newJoinSecret();
  const kept = await newMemberKeys(newSyncKey()), fresh = await newMemberKeys(newSyncKey()), evil = await newMemberKeys(newSyncKey());
  const claims = [
    { userId: "u-kept", pub: kept.pub, proof: await joinProof(oldJ, CID, kept.pub) },
    { userId: "u-fresh", pub: fresh.pub, proof: await joinProof(j, CID, fresh.pub) },
    { userId: "u-kept-swapped", pub: evil.pub, proof: "AAAA" },
    { userId: "u-stale", pub: evil.pub, proof: await joinProof(oldJ, CID, evil.pub) },
  ];
  const pinned = { "u-kept": kept.pub, "u-kept-swapped": kept.pub, "u-gone": fresh.pub };
  assert.deepEqual(await trustedMembers(CID, j, pinned, claims), { "u-kept": kept.pub, "u-fresh": fresh.pub });
});

test("removing one member keeps unpinned and pending members readable after the invite rotates", async () => {
  const oldJ = newJoinSecret(), newJ = newJoinSecret();
  const a = await newMemberKeys(newSyncKey()), b = await newMemberKeys(newSyncKey()), p = await newMemberKeys(newSyncKey());
  const claims = await Promise.all([["u-a", a], ["u-b", b], ["u-pending", p]].map(async ([userId, m]) => {
    const pub = (m as { pub: string }).pub;
    return { userId: userId as string, pub, proof: await joinProof(oldJ, CID, pub) };
  }));
  // Stale owner state: only A was pinned; B was pinned elsewhere, the pending joiner never published-in.
  const pins = await pinsAfterRemoval(CID, oldJ, { "u-a": a.pub }, claims, "u-a");
  assert.deepEqual(pins, { "u-b": b.pub, "u-pending": p.pub });
  const afterRotation = await trustedMembers(CID, newJ, pins, claims.filter((m) => m.userId !== "u-a"));
  assert.deepEqual(afterRotation, { "u-b": b.pub, "u-pending": p.pub }, "others keep access");
  assert.deepEqual(await trustedMembers(CID, newJ, { "u-a": a.pub }, claims.filter((m) => m.userId !== "u-a")), {}, "the old flow dropped them");
});

test("a publish racing a member removal cannot write the old invite secret back", () => {
  const before: OwnerCircle = { id: CID, j: "j-old", epoch: 0, members: { "u-a": "A", "u-b": "B" }, createdAt: 1, updatedAt: 1 };
  // publishCircles took `before`, then revokeMember stored the rotated circle, then the publish finished.
  const revoked: OwnerCircle = { ...before, j: "j-new", epoch: 1, members: { "u-b": "B" }, updatedAt: 2 };
  const pins = { "u-a": "A", "u-b": "B", "u-c": "C" };
  const oldFlow = { ...before, members: pins }; // 2.4: saveCircle({ ...c, members })
  assert.equal(oldFlow.j, "j-old", "the 2.4 write-back restored the revoked invite and the removed member's pin");
  const stored = applyPins(revoked, before.epoch!, pins);
  assert.equal(stored, revoked, "stale publish is dropped");
  assert.equal(stored.j, "j-new");
  assert.deepEqual(stored.members, { "u-b": "B" });
  // Same epoch: new pins land on the stored circle, keeping its j.
  assert.deepEqual(applyPins(revoked, 1, { "u-b": "B", "u-c": "C" }, 9), { ...revoked, members: { "u-b": "B", "u-c": "C" }, updatedAt: 9 });
});

test("owner state merges across devices: rotation only moves forward, pins union, deletes stick", async () => {
  const s1 = await newSigner(), s2 = { ...(await newSigner()), createdAt: s1.createdAt + 1 };
  const base: OwnerCircle = { id: "c1", j: "j0", epoch: 0, members: { a: "A" }, sign: s1, createdAt: 1, updatedAt: 1 };
  // Device B removed a member (epoch 1); device A later only learned a pin under epoch 0 and made its own signer.
  const b: OwnerCircle = { ...base, j: "j1", epoch: 1, members: {}, updatedAt: 5 };
  const a: OwnerCircle = { ...base, members: { a: "A", c: "C" }, sign: s2, updatedAt: 9 };
  for (const [x, y] of [[a, b], [b, a]]) {
    const [m] = mergeCircles([x], [y]);
    assert.equal(m.j, "j1", "newer edit at an older epoch cannot undo the rotation");
    assert.deepEqual(m.members, {}, "old-epoch pins are dropped with the old j");
    assert.equal(m.sign?.pub, y.sign?.pub, "the pulled vault's signer wins");
    assert.equal(m.updatedAt, 9);
  }
  const p1 = { ...base, members: { a: "A", c: "C" }, updatedAt: 3 }, p2 = { ...base, members: { a: "A", d: "D" }, updatedAt: 4 };
  assert.deepEqual(mergeCircles([p1], [p2])[0].members, { a: "A", c: "C", d: "D" }, "same epoch: pins union");
  const gone: OwnerCircle = { id: "c1", createdAt: 1, updatedAt: 2, deletedAt: 2 };
  assert.equal(mergeCircles([a], [gone])[0], gone);
  assert.equal(mergeCircles([gone], [a])[0], gone, "a stale device cannot revive a deleted circle");
  const other: OwnerCircle = { id: "c2", createdAt: 1 };
  assert.deepEqual(mergeCircles([a], [other]).map((c) => c.id), ["c1", "c2"], "circles from the other device arrive");
  const legacy: OwnerCircle = { id: "c1", j: "j0", members: { a: "A" }, createdAt: 1 }; // 2.4: no epoch, no updatedAt
  assert.equal(mergeCircles([legacy], [b])[0].j, "j1");
});

test("pre-2.5 circle on two owner devices: both sign with one key, the one pushed to the vault first", async () => {
  // Linear vault (baseRev): each sync pulls, merges, pushes, then commits what it pushed.
  let vault: OwnerCircle[] = [];
  const sync = (local: OwnerCircle[]) => {
    const merged = mergeCircles(local, vault);
    vault = merged.map((c) => (c.sign?.pending ? { ...c, sign: { pub: c.sign.pub, priv: c.sign.priv, createdAt: c.sign.createdAt } } : c));
    return commitSigners(merged, vault);
  };
  const legacy: OwnerCircle = { id: CID, j: "j0", members: { a: "A" }, createdAt: 1 };
  vault = [legacy];
  const sb = { ...(await newSigner()), createdAt: 1, pending: true as const }; // B's key is older, so the 2.5 "older wins" picked it
  const sa = { ...(await newSigner()), createdAt: 2, pending: true as const };
  // A mints before its push (ensureSigners runs ahead of syncNow); B minted offline, before seeing A's push.
  let a: OwnerCircle[] = [{ ...legacy, sign: sa }], b: OwnerCircle[] = [{ ...legacy, sign: sb }];
  assert.equal(canSign(a[0]), false, "a pending key cannot sign yet");
  assert.equal(canSign(b[0]), false, "B cannot sign with its own key before pushing it");
  a = sync(a);
  assert.ok(canSign(a[0]) && a[0].sign.pub === sa.pub, "A's key is in the vault: A may publish");
  b = sync(b);
  assert.equal(b[0].sign?.pub, sa.pub, "B adopts the key A pushed (2.5 kept B's older one: fork)");
  assert.ok(canSign(b[0]));
  assert.equal(vault[0].sign?.pub, sa.pub);
  assert.equal(vault[0].sign?.pending, undefined, "the vault never carries pending");
  // B's own key never signed anything, so no member could have pinned it.
});

test("member snapshots: only the pinned owner's signature is accepted", async () => {
  const owner = await newSigner(), server = await newSigner();
  const sealed = await seal(await contentKey(newContentKey(), "encrypt"), { v: 1 });
  const blob = await signSnapshot(owner, CID, sealed);
  assert.deepEqual(await checkSnapshot(CID, blob, owner.pub), { sealed, signer: owner.pub });
  assert.deepEqual(await checkSnapshot(CID, blob), { sealed, signer: owner.pub }, "no pin yet (joined before 2.5): first signer returned to pin");
  const refused = (p: Promise<unknown>, msg: string) => assert.rejects(p, (e: Error) => e.name === "SnapshotRefused" && e.message === msg);
  await refused(checkSnapshot(CID, sealed, owner.pub), UNSIGNED);
  await refused(checkSnapshot(CID, sealed), UNSIGNED);
  await refused(checkSnapshot(CID, await signSnapshot(server, CID, sealed), owner.pub), TAMPERED);
  const other = await seal(await contentKey(newContentKey(), "encrypt"), { v: 2 });
  const [, , pub, sig] = blob.split(".");
  await refused(checkSnapshot(CID, `${other}.${pub}.${sig}`, owner.pub), TAMPERED);
  await refused(checkSnapshot("othercircle1", blob, owner.pub), TAMPERED);
  await refused(checkSnapshot(CID, `${sealed}.${pub}.AAAA`), TAMPERED);
});

test("server epoch gate: 2.5 writes only while unset, first 2.6 write adopts its count, forward only after that", () => {
  assert.deepEqual(epochGate(null, undefined), { ok: true, base: null }, "2.5 owner app, epoch never set: accepted");
  assert.deepEqual(epochGate(4, undefined), { ok: false, epoch: 4, error: UPDATE_APP }, "2.5 owner app after a 2.6 write: update the app");
  assert.deepEqual(epochGate(0, undefined), { ok: false, epoch: 0, error: UPDATE_APP }, "epoch 0 counts as set");
  assert.deepEqual(epochGate(null, 3), { ok: true, base: 3 }, "first 2.6 write keeps the circle's own count");
  assert.deepEqual(epochGate(3, 3), { ok: true, base: 3 });
  assert.deepEqual(epochGate(4, 3), { ok: false, epoch: 4, error: STALE_EPOCH }, "stale: re-base on 4");
  assert.deepEqual(epochGate(3, 4), { ok: true, base: 4 }, "ahead of the server: the newer owner state, the server moves up");
});

// A tiny model of worker/circles.ts POST /revoke on top of the shared gate, and of the owner devices around it.
interface Srv { epoch: number | null; j: string; members: Set<string> }
const rotate = (s: Srv, sent: number | undefined, userId: string, j: string) => {
  if (s.j === j) return { ok: true as const, epoch: s.epoch }; // a retry of a rotation that already landed
  const g = epochGate(s.epoch, sent);
  if (!g.ok) return g;
  s.members.delete(userId); s.j = j; s.epoch = g.base === null ? null : g.base + 1;
  return { ok: true as const, epoch: s.epoch };
};
// PUT (publish): same gate, no change to j or member rows; wraps land only on rows that exist.
const publish = (s: Srv & { wraps?: string[] }, sent: number, trusted: string[]) => {
  const g = epochGate(s.epoch, sent);
  if (!g.ok) return g;
  s.epoch = g.base; s.wraps = trusted.filter((u) => s.members.has(u));
  return { ok: true as const, epoch: s.epoch };
};
/** lib/circles.ts publishCircle: try the local epoch, on 409 sync and try again, publishing under the server's epoch
 * if this device is still behind it. Returns the epoch it published under. */
const publishFrom = (s: Srv, local: () => OwnerCircle, sync: () => void, trusted: string[]) => {
  let refused: number | null = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    const epoch = publishEpoch(local().epoch ?? 0, refused), r = publish(s, epoch, trusted);
    if (r.ok) return epoch;
    refused = r.epoch;
    if (attempt === 0) sync();
  }
  return null;
};
const drop = (pins: Record<string, string>, id: string) => Object.fromEntries(Object.entries(pins).filter(([k]) => k !== id));

for (const aPushed of [true, false]) {
  test(`two owner devices remove different members offline and converge (${aPushed ? "A synced" : "A never synced"})`, () => {
    const pins = { m1: "P1", m2: "P2", c: "PC" };
    const s: Srv = { epoch: 0, j: "j0", members: new Set(Object.keys(pins)) };
    let vault: OwnerCircle[] = [{ id: CID, j: "j0", epoch: 0, members: pins, createdAt: 1, updatedAt: 1 }];
    let a = vault[0], b = vault[0];
    // A removes m1.
    const ra = rotate(s, a.epoch, "m1", "jA");
    assert.ok(ra.ok);
    a = { ...a, j: "jA", epoch: ra.epoch!, members: drop(pins, "m1"), updatedAt: 30 };
    if (aPushed) vault = mergeCircles(vault, [a]);
    // B, offline since before that, removes m2: refused, re-bases (lib/circles.ts rebase), retries.
    const stale = rotate(s, b.epoch, "m2", "jB");
    assert.equal(stale.ok, false);
    assert.equal((stale as { epoch: number }).epoch, 1);
    b = { ...mergeCircles([b], vault)[0], epoch: (stale as { epoch: number }).epoch };
    const rb = rotate(s, b.epoch, "m2", "jB");
    assert.ok(rb.ok);
    // pinsAfterRemoval keeps only pins the server still lists, so A's removal of m1 survives even when B never saw it.
    const listed = Object.fromEntries(Object.entries(b.members ?? {}).filter(([k]) => s.members.has(k)));
    b = { ...b, j: "jB", epoch: rb.epoch!, members: listed, updatedAt: 20 };
    assert.deepEqual(b.members, { c: "PC" });
    vault = mergeCircles(vault, [b]);
    // A's next publish carries epoch 1: refused (server 2), A syncs, gets jB at 2, publishes on it.
    assert.equal(publishFrom(s, () => a, () => { a = mergeCircles([a], vault)[0]; }, Object.keys(a.members ?? {})), 2);
    assert.equal(a.j, "jB");
    assert.equal(outOfStep(a.epoch!, 2), false, "no out-of-step note: A holds the server's invite");
    assert.deepEqual([s.j, a.j, b.j], ["jB", "jB", "jB"], "one invite secret everywhere, the one the server holds");
    assert.equal(s.epoch, 2);
    assert.deepEqual([...s.members], ["c"], "both removals applied");
    assert.equal(a.epoch, 2);
  });
}

test("the 2.5 flow left the invite out of step (why the server keeps the epoch)", () => {
  const s = { j: "j0", members: new Set(["m1", "m2", "c"]) };
  const base: OwnerCircle = { id: CID, j: "j0", epoch: 0, createdAt: 1, updatedAt: 1 };
  // 2.5: the server took every rotation; each device stored max(local)+1. A's local edit is newer, B posted last.
  s.members.delete("m1"); s.j = "jA"; const a = { ...base, j: "jA", epoch: 1, updatedAt: 30 };
  s.members.delete("m2"); s.j = "jB"; const b = { ...base, j: "jB", epoch: 1, updatedAt: 20 };
  const merged = mergeCircles([a], [b])[0];
  assert.notEqual(merged.j, s.j, "devices keep jA while the server holds jB: the copied invite no longer works");
  assert.equal(rotate({ epoch: 1, j: "jA", members: new Set() }, 0, "m2", "jB").ok, false, "2.6 refuses B's stale rotation instead");
});

test("rollout: a behind owner device publishes first, the server adopts its lower count, both devices still publish", () => {
  const strict = (server: number | null, sent: number) => server === null || sent === server; // 2.6 review build
  const s: Srv = { epoch: null, j: "j3", members: new Set(["m", "c"]) }; // pre-2.6 circle; A's 2.5 rotation set j3
  let vault: OwnerCircle[] = [{ id: CID, j: "j3", epoch: 3, members: { c: "PC" }, createdAt: 1, updatedAt: 9 }];
  let a = vault[0], b: OwnerCircle = { id: CID, j: "j2", epoch: 2, members: { c: "PC", m: "PM" }, createdAt: 1, updatedAt: 5 };
  assert.equal(publishFrom(s, () => b, () => {}, ["c", "m"]), 2, "B, behind, publishes first");
  assert.equal(s.epoch, 2, "the server adopted the lower count");
  assert.equal(strict(s.epoch, a.epoch!), false, "strict gate: A (epoch 3) refused for good, nothing ever writes 3");
  assert.equal(publishFrom(s, () => a, () => { a = mergeCircles([a], vault)[0]; }, ["c"]), 3, "forward only: A's newer state is accepted");
  assert.equal(s.epoch, 3);
  assert.equal(publishFrom(s, () => b, () => { b = mergeCircles([b], vault)[0]; }, Object.keys(b.members ?? {})), 3, "B: 409, syncs, gets j3, publishes");
  assert.equal(b.j, "j3");
  assert.deepEqual([outOfStep(a.epoch!, 3), outOfStep(b.epoch!, 3)], [false, false]);
});

test("revoke answer lost: the next publish never stalls, the removal holds, the device finishes the rotation", () => {
  const s: Srv & { wraps?: string[] } = { epoch: 0, j: "j0", members: new Set(["m", "c"]) };
  const base: OwnerCircle = { id: CID, j: "j0", epoch: 0, members: { m: "PM", c: "PC" }, createdAt: 1, updatedAt: 1 };
  let vault: OwnerCircle[] = [base], a = base, b = base;
  // A removes m: lib/circles.ts rotate stores { j: jNew, from: 0 } in circleRotating, the server commits, the answer is lost.
  const inFlight = { userId: "m", j: "jNew", from: a.epoch! };
  assert.ok(rotate(s, inFlight.from, inFlight.userId, inFlight.j).ok);
  assert.deepEqual([s.epoch, a.epoch, a.j], [1, 0, "j0"], "server at 1 with jNew; A still holds j0");
  // B (vault has nothing newer) publishes: 409 → sync → still behind → publishes under the server's epoch.
  const strictStall = epochGate(1, 0).ok;
  assert.equal(strictStall, false, "epoch 0 alone is refused (the 2.6 review build skipped from here on, forever)");
  assert.equal(publishFrom(s, () => b, () => { b = mergeCircles([b], vault)[0]; }, ["m", "c"]), 1);
  assert.deepEqual(s.wraps, ["c"], "the removed member gets no wrap: its row is gone");
  assert.equal(s.j, "jNew", "the publish did not touch the invite");
  assert.equal(outOfStep(b.epoch!, 1), true, "B shows the out-of-step note");
  // A's next publish finishes the rotation first: the same request again, the server already holds jNew.
  const again = rotate(s, inFlight.from, inFlight.userId, inFlight.j);
  assert.deepEqual(again, { ok: true, epoch: 1 });
  a = { ...a, j: inFlight.j, epoch: again.epoch!, members: { c: "PC" }, updatedAt: 20 };
  vault = mergeCircles(vault, [a]);
  assert.equal(publishFrom(s, () => a, () => {}, ["c"]), 1);
  b = mergeCircles([b], vault)[0];
  assert.deepEqual([b.j, b.epoch, outOfStep(b.epoch!, 1)], ["jNew", 1, false], "B gets jNew; the note clears");
  assert.deepEqual([...s.members], ["c"], "the removal held throughout");
  // The POST never arrived: the retry applies it now (it is not lost either).
  const s2: Srv = { epoch: 0, j: "j0", members: new Set(["m", "c"]) };
  assert.deepEqual(rotate(s2, 0, "m", "jNew"), { ok: true, epoch: 1 });
  assert.deepEqual([...s2.members], ["c"]);
});

test("replay guard: members keep the highest seq and refuse an older or unnumbered snapshot", () => {
  assert.equal(checkSeq(undefined, 5), 5, "first numbered snapshot");
  assert.equal(checkSeq(undefined, undefined), undefined, "2.5 owner, nothing seen yet: shown, nothing stored");
  assert.equal(checkSeq(5, 6), 6);
  assert.equal(checkSeq(5, 5), 5, "the same snapshot again is fine");
  const refused = (fn: () => unknown) => assert.throws(fn, (e: Error) => e.name === "SnapshotRefused" && e.message === REPLAYED);
  refused(() => checkSeq(5, 4));
  refused(() => checkSeq(5, undefined));
  const x: OwnerCircle = { id: CID, seq: 7, createdAt: 1, updatedAt: 9 }, y: OwnerCircle = { id: CID, seq: 3, createdAt: 1, updatedAt: 10 };
  assert.equal(mergeCircles([x], [y])[0].seq, 7, "owner devices keep the highest publish number");
});

test("a fresh install that pulls the vault refuses a snapshot older than the account's floor", async () => {
  const secret = newSyncKey(), empty: VaultCircles = { circles: [], circlePins: {}, circleSeen: {} };
  // The member's phone accepted seq 7 and pushed (lib/sync.ts: the vault blob is sealed with the sync key).
  const phone: VaultCircles = { ...empty, circleSeen: { [CID]: 7 } };
  const blob = await seal((await deriveVault(secret)).key, pushCircleState(phone));
  assert.ok(!blob.includes("circleSeen") && !blob.includes(CID), "the floor travels sealed, like the rest of the vault");
  // Reinstall: empty meta, same sync key, first pull.
  const pulled = await open<Partial<VaultCircles>>((await deriveVault(secret)).key, blob);
  const floor = pullCircleState(empty, pulled).circleSeen[CID];
  assert.equal(floor, 7);
  assert.throws(() => checkSeq(floor, 5), (e: Error) => e.name === "SnapshotRefused" && e.message === REPLAYED, "the replayed seq 5 is refused");
  assert.equal(checkSeq(floor, 8), 8);
  // A 2.6 vault has no circleSeen: floor 0, as before.
  const from26 = pullCircleState(empty, { circles: [], circlePins: {} }).circleSeen[CID];
  assert.equal(from26, undefined);
  assert.equal(checkSeq(from26, 5), 5);
});

test("a 2.6 device's push drops the floor from the vault until a 2.7 device pulls and pushes again", () => {
  const empty: VaultCircles = { circles: [], circlePins: {}, circleSeen: {} };
  const phone27: VaultCircles = { ...empty, circleSeen: { [CID]: 7 } };
  // A 2.6 device pulls, then pushes its own shape: no circleSeen key (accepted for 2.7).
  const v26: Partial<VaultCircles> = { circles: [], circlePins: {} };
  assert.equal(pullCircleState(empty, v26).circleSeen[CID], undefined, "a reinstall from that vault has floor 0");
  // The 2.7 phone keeps its own floor and its next pull+push writes it back.
  const pushed = pushCircleState(pullCircleState(phone27, v26));
  assert.deepEqual(pushed.circleSeen, { [CID]: 7 }, "restored in the vault");
  assert.equal(pullCircleState(empty, pushed).circleSeen[CID], 7, "a reinstall now gets the floor back");
});

// The shared vault fixture, through what lib/sync.ts runs (tests/pwa.test.ts runs the same cases through public/sw.js).
for (const { name, remote, local, want } of vaultCircleCases) {
  test(`vault circle state (app) ${name}`, () => {
    const pushed = JSON.parse(JSON.stringify(pushCircleState(pullCircleState(local, remote))));
    assert.deepEqual(Object.keys(pushed).sort(), VAULT_CIRCLE_FIELDS, "no circle field dropped");
    assert.deepEqual(pushed, want);
  });
}

test("a 2.5 member verifies a 2.6 snapshot", async () => {
  // The 2.5 member check, frozen: blob iv.ct.pub.sig, ECDSA over oak-circle-sig-v1|circle|pub|iv.ct.
  const verify25 = async (circleId: string, blob: string, pinned: string) => {
    const [iv, ct, pub, sig] = blob.split("."), sealed = `${iv}.${ct}`;
    if (pub !== pinned) return null;
    const k = await crypto.subtle.importKey("raw", unb64u(pub), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const ok = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, k, unb64u(sig), new TextEncoder().encode(`oak-circle-sig-v1|${circleId}|${pub}|${sealed}`));
    return ok ? sealed : null;
  };
  const owner = await newSigner(), raw = newContentKey();
  const view26 = { v: 1, profile: { name: "Mom", color: "#1F6B4D" }, routines: [], intakes: [], publishedAt: 5, seq: 12 };
  const blob = await signSnapshot(owner, CID, await seal(await contentKey(raw, "encrypt"), view26));
  const sealed = await verify25(CID, blob, owner.pub);
  assert.ok(sealed, "signature and blob format unchanged");
  const seen = await open<{ v: number; profile: { name: string }; seq?: number }>(await contentKey(raw, "decrypt"), sealed!);
  assert.equal(seen.profile.name, "Mom", "a 2.5 member reads it; seq is just an extra field");
  assert.equal(seen.seq, 12, "the seq a 2.6 member checks is inside the signed ciphertext");
});
