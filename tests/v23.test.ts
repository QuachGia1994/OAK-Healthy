import { test } from "node:test";
import assert from "node:assert/strict";
import { okEndpoint, okSnooze, remindable, utcMinutes } from "../lib/domain/remind.ts";
import { fromProduct, validGtin } from "../lib/domain/label.ts";

const base = { deletedAt: null, paused: false };

test("per-routine reminder toggle: missing means on, false means off", () => {
  assert.equal(remindable({ ...base }), true);
  assert.equal(remindable({ ...base, remind: true }), true);
  assert.equal(remindable({ ...base, remind: false }), false);
  assert.equal(remindable({ ...base, paused: true }), false);
  assert.equal(remindable({ ...base, deletedAt: 1 }), false);
});

test("reminder minutes skip muted routines, dedupe and wrap around UTC midnight", () => {
  const rs = [{ ...base, time: "08:00" }, { ...base, time: "08:00" }, { ...base, time: "21:00", remind: false }, { ...base, time: "06:30" }];
  // UTC+7 (Vietnam): getTimezoneOffset() = -420.
  assert.deepEqual(utcMinutes(rs, -420), [60, 23 * 60 + 30]);
  assert.deepEqual(utcMinutes([{ ...base, time: "bad" }], 0), []);
});

test("snooze length is bounded", () => {
  assert.equal(okSnooze(10), true);
  assert.equal(okSnooze(60), true);
  assert.equal(okSnooze(4), false);
  assert.equal(okSnooze(181), false);
  assert.equal(okSnooze(10.5), false);
  assert.equal(okSnooze("10"), false);
});

test("push endpoints: browser push services only, including Chrome's *.google.com hosts", () => {
  assert.equal(okEndpoint("https://fcm.googleapis.com/fcm/send/abc:APA91b"), true);
  assert.equal(okEndpoint("https://fcm.googleapis.com/wp/abc"), true);
  assert.equal(okEndpoint("https://jmt17.google.com/fcm/send/abc:APA91b"), true);
  assert.equal(okEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc"), true);
  assert.equal(okEndpoint("https://web.push.apple.com/abc"), true);
  assert.equal(okEndpoint("https://evilgoogle.com/x"), false);
  assert.equal(okEndpoint("https://google.com.evil.io/x"), false);
  assert.equal(okEndpoint("http://fcm.googleapis.com/fcm/send/abc"), false);
  assert.equal(okEndpoint("https://fcm.googleapis.com:8443/x"), false);
  assert.equal(okEndpoint("https://fcm.googleapis.com/" + "a".repeat(1000)), false);
  assert.equal(okEndpoint(42), false);
});

test("GTIN check digits", () => {
  assert.equal(validGtin("4006381333931"), true); // EAN-13
  assert.equal(validGtin("036000291452"), true); // UPC-A
  assert.equal(validGtin("96385074"), true); // EAN-8
  assert.equal(validGtin("4006381333932"), false);
  assert.equal(validGtin("12345"), false);
  assert.equal(validGtin("40063813339a1"), false);
});

test("barcode product record prefills a routine", () => {
  const g = fromProduct({ product_name: "Vitamin D3 2000 IU", brands: "Acme, Acme Labs", quantity: "120 softgels" }, "4006381333931");
  assert.match(g.name ?? "", /Vitamin D/);
  assert.equal(g.dose, "2000 IU");
  assert.equal(g.stock, 120);
  assert.equal(g.barcode, "4006381333931");
  const u = fromProduct({ product_name: "Mystery Blend" });
  assert.equal(u.name, "Mystery Blend");
  assert.equal(fromProduct({ brands: "Acme" }).name, "Acme");
});
