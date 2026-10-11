import { test } from "node:test";
import assert from "node:assert/strict";
import { safeNext } from "../lib/domain/safe-next.ts";

const O = "https://healthy.oakgatekeeper.uk";

test("safeNext keeps same-origin paths with query and fragment", () => {
  assert.equal(safeNext("/c/abc#j=K&o=P", O, "/settings/"), "/c/abc#j=K&o=P");
  assert.equal(safeNext("/reset/?x=1#t=T", O, "/settings/"), "/reset/?x=1#t=T");
});

test("safeNext refuses other origins and parser tricks", () => {
  for (const p of ["//evil.com", "/\\evil.com", "\\\\evil.com", "/\t/evil.com", "\t//evil.com", "https://evil.com/", "javascript:alert(1)", "http://healthy.oakgatekeeper.uk/"]) {
    assert.equal(safeNext(p, O, "/settings/"), null, p);
  }
});

test("safeNext skips empty and the current page", () => {
  assert.equal(safeNext("", O, "/settings/"), null);
  assert.equal(safeNext("/settings/", O, "/settings/"), null);
});
