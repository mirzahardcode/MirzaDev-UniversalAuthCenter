import { getDatabase } from "firebase-admin/database";
import { getAppConfig } from "../config/apps.js";
import {
    AuthorizationUnavailableError,
    getFirebaseAdminApp,
} from "../firebase/admin.js";
import {
    hashDeviceId,
    isHashedDeviceId,
    normalizeDeviceId,
} from "../utils/deviceId.js";

export type AuthorizationDenialStatus =
    | "UNKNOWN_APP"
    | "USER_DISABLED"
    | "USER_EXPIRED"
    | "ACCESS_DENIED"
    | "DEVICE_REJECTED";

export type AuthorizationDecision =
    | { authorized: true }
    | { authorized: false; status: AuthorizationDenialStatus };

export type DeviceBindingMode = "off" | "optional" | "enforce";
type AccessRecord = Record<string, unknown>;
export type BindingState =
    | "UNBOUND"
    | "BOUND_HASH"
    | "BOUND_LEGACY"
    | "INCONSISTENT";

/**
 * Minimal read/transaction surface over `/appAccess/{appKey}/{uid}`.
 *
 * Kept narrow so the decision logic can be exercised without a live Firebase
 * connection. The production implementation is built in {@link firebaseStore}.
 */
export interface AccessStore {
    read(): Promise<unknown>;
    transact(updater: (current: unknown) => unknown): Promise<unknown>;
}

function getDeviceBindingMode(): DeviceBindingMode {
    const configured = process.env.DEVICE_BINDING_MODE?.trim().toLowerCase();

    if (configured === "off" || configured === "optional" || configured === "enforce") {
        return configured;
    }

    console.warn("DEVICE_BINDING_MODE is missing or invalid; defaulting to enforce");
    return "enforce";
}

function asAccessRecord(value: unknown): AccessRecord | null {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        return null;
    }

    return value as AccessRecord;
}

function isActiveRecord(record: AccessRecord): boolean {
    return record.active === true;
}

function isUnexpiredRecord(record: AccessRecord): boolean {
    const expiresAt = record.expiresAt;
    return typeof expiresAt === "number"
        && Number.isFinite(expiresAt)
        && expiresAt > 0
        && expiresAt > Date.now();
}

/**
 * Applies the authorization gate shared by the initial read and the value
 * returned by the binding transaction.
 *
 * A missing node is handled by the caller (ACCESS_DENIED); this function is
 * only invoked with a node that exists.
 */
export function evaluateGate(record: AccessRecord): AuthorizationDecision | null {
    if (!isActiveRecord(record)) {
        return { authorized: false, status: "USER_DISABLED" };
    }

    if (!isUnexpiredRecord(record)) {
        return { authorized: false, status: "USER_EXPIRED" };
    }

    return null;
}

export function classifyBindingState(record: AccessRecord): BindingState {
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

/**
 * Pure decision core. Consumes only the `AccessStore`; the caller is
 * responsible for mapping the app key to a store bound to
 * `/appAccess/{appKey}/{uid}`.
 */
export async function decideAuthorization(
    store: AccessStore,
    deviceId: string | null,
    mode: DeviceBindingMode,
): Promise<AuthorizationDecision> {
    const initialRaw = await store.read();

    if (initialRaw === null || initialRaw === undefined) {
        return { authorized: false, status: "ACCESS_DENIED" };
    }

    const initial = asAccessRecord(initialRaw);
    if (!initial) {
        // The node exists but is not a record; fail closed.
        throw new AuthorizationUnavailableError();
    }

    const gateDecision = evaluateGate(initial);
    if (gateDecision) {
        return gateDecision;
    }

    if (mode === "off") {
        return { authorized: true };
    }

    const bindingState = classifyBindingState(initial);

    if (deviceId === null) {
        if (mode === "optional" && bindingState === "UNBOUND") {
            return { authorized: true };
        }

        return { authorized: false, status: "DEVICE_REJECTED" };
    }

    const requestedDeviceHash = hashDeviceId(deviceId);

    const finalRaw = await store.transact((currentValue) => {
        const current = asAccessRecord(currentValue);
        if (!current) {
            return;
        }

        if (!isActiveRecord(current) || !isUnexpiredRecord(current)) {
            return currentValue;
        }

        const currentBindingState = classifyBindingState(current);

        switch (currentBindingState) {
            case "BOUND_HASH":
            case "INCONSISTENT":
                return currentValue;

            case "BOUND_LEGACY": {
                const legacyDeviceId = normalizeDeviceId(current.deviceId);
                if (legacyDeviceId !== deviceId) {
                    return currentValue;
                }

                return {
                    ...current,
                    deviceId: requestedDeviceHash,
                };
            }

            case "UNBOUND":
                return {
                    ...current,
                    deviceId: requestedDeviceHash,
                    deviceBound: true,
                    deviceBoundAt: Date.now(),
                };
        }
    });

    if (finalRaw === null || finalRaw === undefined) {
        return { authorized: false, status: "ACCESS_DENIED" };
    }

    const final = asAccessRecord(finalRaw);
    if (!final) {
        throw new AuthorizationUnavailableError();
    }

    const finalGateDecision = evaluateGate(final);
    if (finalGateDecision) {
        return finalGateDecision;
    }

    const finalState = classifyBindingState(final);

    if (finalState === "BOUND_HASH") {
        return final.deviceId === requestedDeviceHash
            ? { authorized: true }
            : { authorized: false, status: "DEVICE_REJECTED" };
    }

    if (finalState === "BOUND_LEGACY") {
        const legacyDeviceId = normalizeDeviceId(final.deviceId);

        return legacyDeviceId === deviceId
            ? (() => {
                throw new AuthorizationUnavailableError();
            })()
            : { authorized: false, status: "DEVICE_REJECTED" };
    }

    if (finalState === "UNBOUND" && mode === "optional") {
        return { authorized: true };
    }

    throw new AuthorizationUnavailableError();
}

function firebaseStore(appKey: string, uid: string): AccessStore {
    const accessRef = getDatabase(getFirebaseAdminApp())
        .ref("appAccess")
        .child(appKey)
        .child(uid);

    return {
        read: async () => (await accessRef.once("value")).val(),
        transact: async (updater) => {
            const result = await accessRef.transaction(updater);
            return result.snapshot.val();
        },
    };
}

export async function authorizeUser(
    uid: string,
    appKey: string,
    deviceId: string | null,
): Promise<AuthorizationDecision> {
    if (!getAppConfig(appKey)) {
        return { authorized: false, status: "UNKNOWN_APP" };
    }

    try {
        return await decideAuthorization(
            firebaseStore(appKey, uid),
            deviceId,
            getDeviceBindingMode(),
        );
    } catch (error) {
        if (error instanceof AuthorizationUnavailableError) {
            throw error;
        }
        throw new AuthorizationUnavailableError();
    }
}
