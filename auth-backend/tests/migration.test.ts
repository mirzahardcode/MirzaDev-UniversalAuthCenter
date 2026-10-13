import { test } from "node:test";
import assert from "node:assert/strict";

import { buildPlan, classifyBinding } from "../scripts/migrate-appaccess.js";

const LEGACY_HASH = `v1:${"a".repeat(64)}`;

test("classifyBinding mirrors runtime states", () => {
    assert.equal(classifyBinding({ deviceBound: false }), "UNBOUND");
    assert.equal(classifyBinding({}), "UNBOUND");
    assert.equal(
        classifyBinding({ deviceBound: true, deviceId: LEGACY_HASH }),
        "BOUND_HASH",
    );
    assert.equal(
        classifyBinding({ deviceBound: true, deviceId: "abcdef0123456789" }),
        "BOUND_LEGACY",
    );
    assert.equal(
        classifyBinding({ deviceBound: true, deviceId: null }),
        "INCONSISTENT",
    );
    assert.equal(
        classifyBinding({ deviceBound: "yes", deviceId: "abcdef0123456789" }),
        "INCONSISTENT",
    );
});

test("copies only the fields the target is missing", () => {
    const plan = buildPlan(
        "cracking-exam",
        "uid-1",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: LEGACY_HASH, deviceBoundAt: 111 },
        { active: true },
    );

    assert.equal(plan.status, "MIGRATE");
    const copied = plan.fields.filter((f) => f.action === "copy").map((f) => f.field).sort();
    assert.deepEqual(copied, ["deviceBound", "deviceBoundAt", "deviceId", "expiresAt"]);
    // `active` already equals the source -> noop, never rewritten.
    assert.equal(plan.fields.find((f) => f.field === "active")?.action, "noop");
});

test("legacy plaintext deviceId is copied verbatim, never re-hashed", () => {
    const plan = buildPlan(
        "cracking-exam",
        "uid-2",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: "abcdef0123456789" },
        { active: true, expiresAt: 5000 },
    );

    const deviceIdPlan = plan.fields.find((f) => f.field === "deviceId");
    assert.equal(deviceIdPlan?.action, "copy");
    assert.equal(deviceIdPlan?.source, "abcdef0123456789");
    assert.equal(deviceIdPlan?.source, "abcdef0123456789"); // not a v1: hash
});

test("is idempotent: an already-migrated target yields NOOP", () => {
    const legacy = { active: true, expiresAt: 5000, deviceBound: true, deviceId: LEGACY_HASH, deviceBoundAt: 111 };
    const target = { active: true, expiresAt: 5000, deviceBound: true, deviceId: LEGACY_HASH, deviceBoundAt: 111 };
    const plan = buildPlan("cracking-exam", "uid-3", legacy, target);
    assert.equal(plan.status, "NOOP");
    assert.equal(plan.fields.filter((f) => f.action === "copy").length, 0);
});

test("fails closed on conflicting gate fields", () => {
    const plan = buildPlan(
        "cracking-exam",
        "uid-4",
        { active: true, expiresAt: 5000 },
        { active: false, expiresAt: 5000 },
    );
    assert.equal(plan.status, "NEEDS_REVIEW");
    assert.match(plan.reason ?? "", /active/);
});

test("fails closed on inconsistent legacy binding", () => {
    const plan = buildPlan(
        "cracking-exam",
        "uid-5",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: null },
        { active: true },
    );
    assert.equal(plan.status, "NEEDS_REVIEW");
    assert.match(plan.reason ?? "", /INCONSISTENT/);
});

test("unbound target with no legacy binding data does not copy binding fields", () => {
    const plan = buildPlan(
        "cracking-exam",
        "uid-6",
        { active: true, expiresAt: 5000 },
        { active: true, expiresAt: 5000 },
    );
    assert.equal(plan.status, "NOOP");
    assert.equal(plan.fields.find((f) => f.field === "deviceId"), undefined);
});

test("reports legacyMissing when no /users source exists", () => {
    const plan = buildPlan("cracking-exam", "uid-7", null, { active: true, expiresAt: 5000 });
    assert.equal(plan.legacyMissing, true);
    assert.equal(plan.legacyBinding, null);
    assert.equal(plan.status, "NOOP");
});

test("flags gateConflict only for gate-field conflicts", () => {
    const gate = buildPlan(
        "cracking-exam",
        "uid-8",
        { active: true, expiresAt: 5000 },
        { active: false, expiresAt: 5000 },
    );
    assert.equal(gate.gateConflict, true);
    assert.equal(gate.status, "NEEDS_REVIEW");

    const bindingOnly = buildPlan(
        "cracking-exam",
        "uid-9",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: LEGACY_HASH },
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: `v1:${"b".repeat(64)}` },
    );
    assert.equal(bindingOnly.status, "NEEDS_REVIEW");
    assert.equal(bindingOnly.gateConflict, false);
});

test("classifies legacy plaintext and hashed bindings for summary counters", () => {
    const legacy = buildPlan(
        "cracking-exam",
        "uid-10",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: "abcdef0123456789" },
        { active: true, expiresAt: 5000 },
    );
    assert.equal(legacy.legacyBinding, "BOUND_LEGACY");

    const hashed = buildPlan(
        "cracking-exam",
        "uid-11",
        { active: true, expiresAt: 5000, deviceBound: true, deviceId: LEGACY_HASH },
        { active: true, expiresAt: 5000 },
    );
    assert.equal(hashed.legacyBinding, "BOUND_HASH");
});
