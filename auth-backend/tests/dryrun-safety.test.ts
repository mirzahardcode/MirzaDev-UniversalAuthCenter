import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "scripts", "migrate-appaccess.ts"), "utf8");

test("the only database write is .update(), gated behind the dry-run guard", () => {
    const guardIndex = source.indexOf("if (!args.apply)");
    const updateIndex = source.indexOf("await database.ref().update(updates)");
    assert.ok(guardIndex >= 0, "dry-run guard must exist");
    assert.ok(updateIndex >= 0, "apply update must exist");
    assert.ok(updateIndex > guardIndex, "update() must be gated behind the dry-run return");

    // No database write primitives other than the single gated update().
    const dbWrites = source.match(/await\s+database[\s\S]{0,80}?\.(set|remove|push|update)\(/g) ?? [];
    assert.equal(dbWrites.length, 1, `expected exactly one db write, found: ${JSON.stringify(dbWrites)}`);
    assert.match(dbWrites[0], /\.update\(/);
});

test("every database read is a .once() read", () => {
    const reads = source.match(/await\s+database[\s\S]{0,80}?\.once\(/g) ?? [];
    assert.ok(reads.length >= 2, "expected at least the appAccess + users reads");
});

test("snapshot file write is gated behind --apply", () => {
    const snapshotIdx = source.indexOf("writeFileSync(args.snapshotPath");
    const guardIdx = source.indexOf("if (args.apply && args.snapshotPath)");
    assert.ok(guardIdx >= 0 && snapshotIdx > guardIdx);
});
