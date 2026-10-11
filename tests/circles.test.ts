import { test } from "node:test";
import assert from "node:assert/strict";
import { newSyncKey, open, seal } from "../lib/crypto.ts";
import {
  applyPins, canSign, checkSnapshot, commitSigners, contentKey, joinAuth, joinProof, mergeCircles, newContentKey, newJoinSecret, newMemberKeys, newSigner, openMemberKey,
  pinsAfterRemoval, signSnapshot, TAMPERED, trustedMembers, UNSIGNED, unwrapFor, verifyProof, wrapFor, type OwnerCircle,
} from "../lib/member-keys.ts";

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
