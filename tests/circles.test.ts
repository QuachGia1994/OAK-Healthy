import { test } from "node:test";
import assert from "node:assert/strict";
import { newSyncKey, open, seal } from "../lib/crypto.ts";
import {
  contentKey, joinAuth, joinProof, newContentKey, newJoinSecret, newMemberKeys, openMemberKey, pinsAfterRemoval, trustedMembers, unwrapFor, verifyProof, wrapFor,
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
