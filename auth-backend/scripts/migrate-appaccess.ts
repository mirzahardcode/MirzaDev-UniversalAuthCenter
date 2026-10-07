/**
 * Offline one-shot migration: legacy `/users/{uid}` authorization fields
 * consolidated into `/appAccess/{appKey}/{uid}`.
 *
 * Offline only. This file is intentionally NOT wired into the Vercel API
 * surface and must never be exposed as an HTTP route.
 *
 * Guarantees:
 *   - non-destructive: never deletes or rewrites `/users` (read-only there)
 *   - idempotent: writes only when the target value actually differs
 *   - dry-run capable: default mode; `--apply` is required to write
 *   - fail-closed: ambiguous/conflicting/inconsistent data is skipped and
 *     reported as NEEDS_REVIEW, never guessed
 *   - never creates a new `/appAccess/{appKey}/{uid}` node
 *   - never rebinds a device: `deviceId` is copied verbatim, never re-hashed
 *   - legacy plaintext device ids are preserved as-is; they are only upgraded
 *     to a hash later, inside `AuthorizationService`, once the client proves
 *     the same plaintext.
 *
 * Usage (Node 24, from `auth-backend/`):
 *   node scripts/migrate-appaccess.ts --dry-run
 *   node scripts/migrate-appaccess.ts --apply --snapshot ./migration-snapshot.json
 */
import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import type { Database } from "firebase-admin/database";
import { writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { isHashedDeviceId, normalizeDeviceId } from "../src/utils/deviceId.js";

type Record_ = Record<string, unknown>;

interface Args {
    apply: boolean;
    snapshotPath: string | null;
}

export interface FieldPlan {
    field: string;
    source: unknown;
    current: unknown;
    action: "copy" | "noop" | "conflict";
}

export interface TargetPlan {
    appKey: string;
    uid: string;
    status: "MIGRATE" | "NOOP" | "NEEDS_REVIEW";
    reason?: string;
    fields: FieldPlan[];
    /** No `/users/{uid}` source node existed for this target. */
    legacyMissing: boolean;
    /** The legacy `/users` binding classification, when a source existed. */
    legacyBinding: BindingClass | null;
    /** True when a gate field (active/expiresAt) conflicts with `/users`. */
    gateConflict: boolean;
}

const GATE_FIELDS = ["active", "expiresAt"] as const;
const BINDING_FIELDS = ["deviceBound", "deviceBoundAt", "deviceId"] as const;

type BindingClass = "UNBOUND" | "BOUND_HASH" | "BOUND_LEGACY" | "INCONSISTENT";

function parseArgs(argv: string[]): Args {
    let apply = false;
    let snapshotPath: string | null = null;

    for (let index = 0; index < argv.length; index += 1) {
        const arg = argv[index];
        if (arg === "--apply") {
            apply = true;
        } else if (arg === "--dry-run") {
            apply = false;
        } else if (arg === "--snapshot") {
            const next = argv[index + 1];
            if (!next || next.startsWith("--")) {
                throw new Error("--snapshot requires a file path");
            }
            snapshotPath = next;
            index += 1;
        } else {
            throw new Error(`Unknown argument: ${arg}`);
        }
    }

    return { apply, snapshotPath };
}

function requiredEnv(name: string): string {
    const value = process.env[name];
    if (!value || value.trim().length === 0) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

function connectDatabase(): Database {
    if (getApps().length === 0) {
        const projectId = requiredEnv("FIREBASE_PROJECT_ID");
        const clientEmail = requiredEnv("FIREBASE_CLIENT_EMAIL");
        const privateKey = requiredEnv("FIREBASE_PRIVATE_KEY")
            .replace(/\\n/g, "\n")
            .replace(/\r\n/g, "\n");
        const databaseURL = requiredEnv("FIREBASE_DATABASE_URL");

        initializeApp({
            credential: cert({ projectId, clientEmail, privateKey }),
            databaseURL,
        });
    }

    return getDatabase();
}

function asRecord(value: unknown): Record_ | null {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        return null;
    }
    return value as Record_;
}

function isValueDefined(value: unknown): boolean {
    return value !== undefined && value !== null
        && !(typeof value === "string" && value.trim().length === 0);
}

/**
 * Mirrors `AuthorizationService.classifyBindingState` so the migration tool
 * and the runtime agree on what a valid binding looks like.
 */
export function classifyBinding(record: Record_): "UNBOUND" | "BOUND_HASH" | "BOUND_LEGACY" | "INCONSISTENT" {
    const deviceBound = record.deviceBound;
    const deviceId = record.deviceId;
    const hasStoredDeviceId = typeof deviceId === "string" && deviceId.trim().length > 0;

    if (deviceBound === true && isHashedDeviceId(deviceId)) {
        return "BOUND_HASH";
    }
    if (deviceBound === true && normalizeDeviceId(deviceId) !== null) {
        return "BOUND_LEGACY";
    }
    if ((deviceBound === false || deviceBound === undefined || deviceBound === null)
            && !hasStoredDeviceId) {
        return "UNBOUND";
    }
    return "INCONSISTENT";
}

function planField(field: string, source: unknown, current: unknown): FieldPlan | null {
    if (!isValueDefined(source)) {
        // Nothing authoritative to copy from the legacy node.
        return null;
    }

    if (!isValueDefined(current)) {
        return { field, source, current, action: "copy" };
    }

    // Deep-equal is sufficient for the scalar/short-string shapes involved, but
    // compare defensively to avoid false "conflict" on object identity.
    if (JSON.stringify(current) === JSON.stringify(source)) {
        return { field, source, current, action: "noop" };
    }

    return { field, source, current, action: "conflict" };
}

export function buildPlan(appKey: string, uid: string, legacy: Record_ | null, target: Record_): TargetPlan {
    const fields: FieldPlan[] = [];
    const legacyMissing = legacy === null;

    for (const field of GATE_FIELDS) {
        const planned = planField(field, legacy ? legacy[field] : undefined, target[field]);
        if (planned) {
            fields.push(planned);
        }
    }

    const legacyBinding: BindingClass | null = legacy ? classifyBinding(legacy) : null;

    if (legacyBinding === "INCONSISTENT") {
        return {
            appKey,
            uid,
            status: "NEEDS_REVIEW",
            reason: "legacy binding is INCONSISTENT",
            fields,
            legacyMissing,
            legacyBinding,
            gateConflict: false,
        };
    }

    if (legacyBinding === "BOUND_HASH" || legacyBinding === "BOUND_LEGACY") {
        for (const field of BINDING_FIELDS) {
            const planned = planField(field, legacy ? legacy[field] : undefined, target[field]);
            if (planned) {
                fields.push(planned);
            }
        }
    }

    const conflicts = fields.filter((entry) => entry.action === "conflict");
    if (conflicts.length > 0) {
        const conflictFields = conflicts.map((c) => c.field);
        return {
            appKey,
            uid,
            status: "NEEDS_REVIEW",
            reason: `target disagrees with source on: ${conflictFields.join(", ")}`,
            fields,
            legacyMissing,
            legacyBinding,
            gateConflict: conflictFields.some((field) =>
                (GATE_FIELDS as readonly string[]).includes(field)),
        };
    }

    const copies = fields.filter((entry) => entry.action === "copy");
    if (copies.length === 0) {
        return {
            appKey,
            uid,
            status: "NOOP",
            fields,
            legacyMissing,
            legacyBinding,
            gateConflict: false,
        };
    }

    return {
        appKey,
        uid,
        status: "MIGRATE",
        fields,
        legacyMissing,
        legacyBinding,
        gateConflict: false,
    };
}

async function main(): Promise<void> {
    const args = parseArgs(process.argv.slice(2));
    const database = connectDatabase();

    console.log(`mode: ${args.apply ? "APPLY" : "DRY-RUN"}`);

    const appAccessSnapshot = await database.ref("appAccess").once("value");
    const appAccessValue = asRecord(appAccessSnapshot.val());

    if (!appAccessValue) {
        console.log("no /appAccess nodes found; nothing to migrate");
        return;
    }

    if (args.apply && args.snapshotPath) {
        const usersSnapshot = await database.ref("users").once("value");
        const snapshot = {
            takenAt: new Date().toISOString(),
            users: usersSnapshot.val(),
            appAccess: appAccessSnapshot.val(),
        };
        writeFileSync(args.snapshotPath, JSON.stringify(snapshot, null, 2), "utf8");
        console.log(`snapshot written: ${args.snapshotPath}`);
    }

    const plans: TargetPlan[] = [];
    const updates: Record<string, Record_> = {};

    for (const appKey of Object.keys(appAccessValue)) {
        const usersForApp = asRecord(appAccessValue[appKey]);
        if (!usersForApp) {
            continue;
        }

        for (const uid of Object.keys(usersForApp)) {
            const target = asRecord(usersForApp[uid]);
            if (!target) {
                plans.push({
                    appKey,
                    uid,
                    status: "NEEDS_REVIEW",
                    reason: "appAccess node is not a record",
                    fields: [],
                    legacyMissing: false,
                    legacyBinding: null,
                    gateConflict: false,
                });
                continue;
            }

            const legacySnapshot = await database.ref("users").child(uid).once("value");
            const legacy = asRecord(legacySnapshot.val());

            const plan = buildPlan(appKey, uid, legacy, target);
            plans.push(plan);

            if (plan.status === "MIGRATE") {
                const patch: Record_ = {};
                for (const entry of plan.fields) {
                    if (entry.action === "copy") {
                        patch[entry.field] = entry.source;
                    }
                }
                updates[`appAccess/${appKey}/${uid}`] = patch;
            }
        }
    }

    // Redact identifiers in output: never print raw UIDs or device ids.
    const redact = (value: string): string => {
        if (value.length <= 4) {
            return "****";
        }
        return `${value.slice(0, 2)}…${value.slice(-2)}`;
    };

    for (const plan of plans) {
        const detail = plan.fields
            .map((entry) => `${entry.field}:${entry.action}`)
            .join(" ");
        console.log(
            `${plan.status.padEnd(12)} appAccess/${plan.appKey}/${redact(plan.uid)}`
            + `  [src:${plan.legacyMissing ? "MISSING" : plan.legacyBinding ?? "n/a"}]`
            + (plan.reason ? `  (${plan.reason})` : "")
            + (detail ? `  [${detail}]` : ""),
        );
    }

    const categories = {
        appKeys: Object.keys(appAccessValue).length,
        targets: plans.length,
        readyToMigrate: plans.filter((p) => p.status === "MIGRATE").length,
        alreadyMigratedOrNoChange: plans.filter((p) => p.status === "NOOP").length,
        needsReview: plans.filter((p) => p.status === "NEEDS_REVIEW").length,
        missingUsersSource: plans.filter((p) => p.legacyMissing).length,
        gateFieldConflicts: plans.filter((p) => p.gateConflict).length,
        legacyPlaintextBindings: plans.filter((p) => p.legacyBinding === "BOUND_LEGACY").length,
        inconsistentBindings: plans.filter((p) => p.legacyBinding === "INCONSISTENT").length,
        hashedBindings: plans.filter((p) => p.legacyBinding === "BOUND_HASH").length,
        unboundTargets: plans.filter((p) => !p.legacyMissing && p.legacyBinding === "UNBOUND").length,
    };

    console.log(`summary: ${JSON.stringify(categories)}`);

    if (!args.apply) {
        console.log("dry-run complete; no writes performed");
        return;
    }

    if (categories.readyToMigrate === 0) {
        console.log("nothing to apply");
        return;
    }

    await database.ref().update(updates);
    console.log(`applied ${categories.readyToMigrate} update(s)`);
}

const isDirectInvocation =
    process.argv[1] !== undefined
    && import.meta.url === pathToFileURL(process.argv[1]).href;

if (isDirectInvocation) {
    main().catch((error) => {
        console.error("migration failed:", error instanceof Error ? error.message : error);
        process.exitCode = 1;
    });
}

