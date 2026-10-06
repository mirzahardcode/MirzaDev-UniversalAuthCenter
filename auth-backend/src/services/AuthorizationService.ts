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

type DeviceBindingMode = "off" | "optional" | "enforce";
type UserRecord = Record<string, unknown>;
type BindingState =
    | "UNBOUND"
    | "BOUND_HASH"
    | "BOUND_LEGACY"
    | "INCONSISTENT";

function getDeviceBindingMode(): DeviceBindingMode {
    const configured = process.env.DEVICE_BINDING_MODE?.trim().toLowerCase();

    if (configured === "off" || configured === "optional" || configured === "enforce") {
        return configured;
    }

    console.warn("DEVICE_BINDING_MODE is missing or invalid; defaulting to enforce");
    return "enforce";
}

function isActiveUser(user: UserRecord): boolean {
    return user.active === true;
}

function isUnexpiredUser(user: UserRecord): boolean {
    const expiresAt = user.expiresAt;
    return typeof expiresAt === "number"
        && Number.isFinite(expiresAt)
        && expiresAt > 0
        && expiresAt > Date.now();
}

function classifyBindingState(user: UserRecord): BindingState {
    const deviceBound = user.deviceBound;
    const deviceId = user.deviceId;
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

export async function authorizeUser(
    uid: string,
    appKey: string,
    deviceId: string | null,
): Promise<AuthorizationDecision> {
    if (!getAppConfig(appKey)) {
        return { authorized: false, status: "UNKNOWN_APP" };
    }

    try {
        const database = getDatabase(getFirebaseAdminApp());
        const userRef = database.ref("users").child(uid);
        const userSnapshot = await userRef.once("value");
        const user = userSnapshot.val() as UserRecord | null;

        if (!user || !isActiveUser(user)) {
            return { authorized: false, status: "USER_DISABLED" };
        }

        if (!isUnexpiredUser(user)) {
            return { authorized: false, status: "USER_EXPIRED" };
        }

        const accessSnapshot = await database.ref("appAccess")
            .child(appKey)
            .child(uid)
            .once("value");
        const appAccess = accessSnapshot.val() as Record<string, unknown> | null;

        if (!appAccess || appAccess.active !== true) {
            return { authorized: false, status: "ACCESS_DENIED" };
        }

        const mode = getDeviceBindingMode();

        if (mode === "off") {
            return { authorized: true };
        }

        const bindingState = classifyBindingState(user);

        if (deviceId === null) {
            if (mode === "optional" && bindingState === "UNBOUND") {
                return { authorized: true };
            }

            return { authorized: false, status: "DEVICE_REJECTED" };
        }

        const requestedDeviceHash = hashDeviceId(deviceId);

        const transaction = await userRef.transaction((currentValue) => {
            if (typeof currentValue !== "object"
                    || currentValue === null
                    || Array.isArray(currentValue)) {
                return;
            }

            const current = currentValue as UserRecord;

            if (!isActiveUser(current) || !isUnexpiredUser(current)) {
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

        const finalUser = transaction.snapshot.val() as UserRecord | null;
        if (!finalUser || !isActiveUser(finalUser)) {
            return { authorized: false, status: "USER_DISABLED" };
        }

        if (!isUnexpiredUser(finalUser)) {
            return { authorized: false, status: "USER_EXPIRED" };
        }

        const finalState = classifyBindingState(finalUser);

        if (finalState === "BOUND_HASH") {
            return finalUser.deviceId === requestedDeviceHash
                ? { authorized: true }
                : { authorized: false, status: "DEVICE_REJECTED" };
        }

        if (finalState === "BOUND_LEGACY") {
            const legacyDeviceId = normalizeDeviceId(finalUser.deviceId);

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
    } catch (error) {
        if (error instanceof AuthorizationUnavailableError) {
            throw error;
        }
        throw new AuthorizationUnavailableError();
    }
}
