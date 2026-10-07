import { test } from "node:test";
import assert from "node:assert/strict";

import { decideAuthorization, evaluateGate } from "../src/services/AuthorizationService.js";
import type { AccessStore } from "../src/services/AuthorizationService.js";

process.env.DEVICE_ID_PEPPER = "unit-test-pepper-value-at-least-32-chars";

const NOW = Date.now();
const FUTURE = NOW + 3_600_000;
const PAST = NOW - 3_600_000;
const DEVICE_A = "abcdef0123456789";
const DEVICE_B = "ffffffffffffffff";

/**
 * In-memory store that also enforces transaction semantics: the first
 * transaction to mutate the value wins; concurrent mutators see the
 * committed value (mirrors Firebase RTDB transaction retry behavior).
 */
class MemoryStore implements AccessStore {
    private value: unknown;

    constructor(value: unknown) {
        this.value = value;
    }

    async read(): Promise<unknown> {
        return this.value;
    }

    async transact(updater: (current: unknown) => unknown): Promise<unknown> {
        const next = updater(this.value);
        if (next !== undefined) {
            this.value = next;
        }
        return this.value;
    }
}

test("node absent -> ACCESS_DENIED (not USER_DISABLED)", async () => {
    const decision = await decideAuthorization(new MemoryStore(null), DEVICE_A, "enforce");
    assert.deepEqual(decision, { authorized: false, status: "ACCESS_DENIED" });
});

test("node present but active !== true -> USER_DISABLED", async () => {
    const store = new MemoryStore({ active: false, expiresAt: FUTURE });
    const decision = await decideAuthorization(store, DEVICE_A, "enforce");
    assert.deepEqual(decision, { authorized: false, status: "USER_DISABLED" });
});

test("active true but expiresAt invalid/expired -> USER_EXPIRED", async () => {
    for (const expiresAt of [undefined, "x", 0, PAST]) {
        const store = new MemoryStore({ active: true, expiresAt });
        const decision = await decideAuthorization(store, DEVICE_A, "enforce");
        assert.deepEqual(decision, { authorized: false, status: "USER_EXPIRED" });
    }
});

test("mode off ignores device input entirely", async () => {
    const store = new MemoryStore({ active: true, expiresAt: FUTURE });
    const decision = await decideAuthorization(store, null, "off");
    assert.deepEqual(decision, { authorized: true });
});

test("UNBOUND + device -> binds hash and authorizes", async () => {
    const store = new MemoryStore({ active: true, expiresAt: FUTURE, deviceBound: false });
    const decision = await decideAuthorization(store, DEVICE_A, "enforce");
    assert.deepEqual(decision, { authorized: true });

    const persisted = await store.read() as Record<string, unknown>;
    assert.equal(persisted.deviceBound, true);
    assert.match(String(persisted.deviceId), /^v1:[0-9a-f]{64}$/);
    assert.equal(typeof persisted.deviceBoundAt, "number");
});

test("BOUND_HASH same device -> authorized; different -> DEVICE_REJECTED", async () => {
    const { hashDeviceId } = await import("../src/utils/deviceId.js");
    const store = new MemoryStore({
        active: true,
        expiresAt: FUTURE,
        deviceBound: true,
        deviceId: hashDeviceId(DEVICE_A),
    });

    assert.deepEqual(
        await decideAuthorization(store, DEVICE_A, "enforce"),
        { authorized: true },
    );
    assert.deepEqual(
        await decideAuthorization(store, DEVICE_B, "enforce"),
        { authorized: false, status: "DEVICE_REJECTED" },
    );
});

test("BOUND_LEGACY same plaintext -> upgraded to hash; different -> DEVICE_REJECTED", async () => {
    const store = new MemoryStore({
        active: true,
        expiresAt: FUTURE,
        deviceBound: true,
        deviceId: DEVICE_A,
    });

    // Different device must not rebind and must be rejected, leaving legacy intact.
    assert.deepEqual(
        await decideAuthorization(store, DEVICE_B, "enforce"),
        { authorized: false, status: "DEVICE_REJECTED" },
    );
    assert.equal((await store.read() as Record<string, unknown>).deviceId, DEVICE_A);

    // Same device proves ownership -> upgraded to hash.
    assert.deepEqual(
        await decideAuthorization(store, DEVICE_A, "enforce"),
        { authorized: true },
    );
    assert.match(
        String((await store.read() as Record<string, unknown>).deviceId),
        /^v1:[0-9a-f]{64}$/,
    );
});

test("INCONSISTENT binding fails closed", async () => {
    const store = new MemoryStore({
        active: true,
        expiresAt: FUTURE,
        deviceBound: true,
        deviceId: null,
    });
    await assert.rejects(() => decideAuthorization(store, DEVICE_A, "enforce"));
});

test("mode optional: UNBOUND + no device -> authorized; BOUND + no device -> rejected", async () => {
    const unbound = new MemoryStore({ active: true, expiresAt: FUTURE, deviceBound: false });
    assert.deepEqual(
        await decideAuthorization(unbound, null, "optional"),
        { authorized: true },
    );

    const { hashDeviceId } = await import("../src/utils/deviceId.js");
    const bound = new MemoryStore({
        active: true,
        expiresAt: FUTURE,
        deviceBound: true,
        deviceId: hashDeviceId(DEVICE_A),
    });
    assert.deepEqual(
        await decideAuthorization(bound, null, "optional"),
        { authorized: false, status: "DEVICE_REJECTED" },
    );
});

test("enforce: no device -> DEVICE_REJECTED even when UNBOUND", async () => {
    const store = new MemoryStore({ active: true, expiresAt: FUTURE, deviceBound: false });
    assert.deepEqual(
        await decideAuthorization(store, null, "enforce"),
        { authorized: false, status: "DEVICE_REJECTED" },
    );
});

test("concurrent first-bind: exactly one device wins", async () => {
    const { hashDeviceId } = await import("../src/utils/deviceId.js");
    const store = new MemoryStore({ active: true, expiresAt: FUTURE, deviceBound: false });

    const [a, b] = await Promise.all([
        decideAuthorization(store, DEVICE_A, "enforce"),
        decideAuthorization(store, DEVICE_B, "enforce"),
    ]);

    const authorized = [a, b].filter((d) => d.authorized);
    const rejected = [a, b].filter((d) => !d.authorized);
    assert.equal(authorized.length, 1);
    assert.equal(rejected.length, 1);

    const persisted = await store.read() as Record<string, unknown>;
    const boundToA = persisted.deviceId === hashDeviceId(DEVICE_A);
    const boundToB = persisted.deviceId === hashDeviceId(DEVICE_B);
    assert.ok(boundToA || boundToB);
});

test("evaluateGate splits absent vs disabled vs expired correctly", () => {
    assert.equal(evaluateGate({ active: true, expiresAt: FUTURE }), null);
    assert.deepEqual(evaluateGate({ active: false, expiresAt: FUTURE }), {
        authorized: false,
        status: "USER_DISABLED",
    });
    assert.deepEqual(evaluateGate({ active: true, expiresAt: PAST }), {
        authorized: false,
        status: "USER_EXPIRED",
    });
});
