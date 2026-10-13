import { test } from "node:test";
import assert from "node:assert/strict";

import {
    hashDeviceId,
    isHashedDeviceId,
    normalizeDeviceId,
} from "../src/utils/deviceId.js";

const PEPPER = "unit-test-pepper-value-at-least-32-chars";

test("normalizeDeviceId accepts lowercase 16-hex (padding-tolerant)", () => {
    assert.equal(normalizeDeviceId("abcdef0123456789"), "abcdef0123456789");
    assert.equal(normalizeDeviceId("  abcdef0123456789  "), "abcdef0123456789");
});

test("normalizeDeviceId rejects malformed, uppercase, zero, and non-strings", () => {
    assert.equal(normalizeDeviceId("ABCDEF0123456789"), null);
    assert.equal(normalizeDeviceId("0000000000000000"), null);
    assert.equal(normalizeDeviceId("not-hex-and-too-long"), null);
    assert.equal(normalizeDeviceId(""), null);
    assert.equal(normalizeDeviceId(null), null);
    assert.equal(normalizeDeviceId(123), null);
});

test("isHashedDeviceId recognizes only v1:<64 hex>", () => {
    assert.equal(isHashedDeviceId(`v1:${"a".repeat(64)}`), true);
    assert.equal(isHashedDeviceId(`v1:${"a".repeat(63)}`), false);
    assert.equal(isHashedDeviceId("abcdef0123456789"), false);
    assert.equal(isHashedDeviceId(null), false);
});

test("hashDeviceId is deterministic and version-prefixed", () => {
    process.env.DEVICE_ID_PEPPER = PEPPER;
    const first = hashDeviceId("abcdef0123456789");
    const second = hashDeviceId("abcdef0123456789");
    assert.equal(first, second);
    assert.match(first, /^v1:[0-9a-f]{64}$/);
    assert.equal(isHashedDeviceId(first), true);
});

test("hashDeviceId fails closed without a strong pepper", () => {
    delete process.env.DEVICE_ID_PEPPER;
    assert.throws(() => hashDeviceId("abcdef0123456789"));
    process.env.DEVICE_ID_PEPPER = "too-short";
    assert.throws(() => hashDeviceId("abcdef0123456789"));
    process.env.DEVICE_ID_PEPPER = PEPPER;
});
