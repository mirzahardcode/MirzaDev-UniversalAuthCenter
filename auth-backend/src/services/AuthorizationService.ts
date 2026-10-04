import { getDatabase } from "firebase-admin/database";
import { getAppConfig } from "../config/apps.js";
import {
    AuthorizationUnavailableError,
    getFirebaseAdminApp,
} from "../firebase/admin.js";

export type AuthorizationDenialStatus =
    | "UNKNOWN_APP"
    | "USER_DISABLED"
    | "USER_EXPIRED"
    | "ACCESS_DENIED";

export type AuthorizationDecision =
    | { authorized: true }
    | { authorized: false; status: AuthorizationDenialStatus };

export async function authorizeUser(
    uid: string,
    appKey: string,
): Promise<AuthorizationDecision> {
    if (!getAppConfig(appKey)) {
        return { authorized: false, status: "UNKNOWN_APP" };
    }

    try {
        const database = getDatabase(getFirebaseAdminApp());
        const userSnapshot = await database.ref("users").child(uid).once("value");
        const user = userSnapshot.val() as Record<string, unknown> | null;

        if (!user || user.active !== true) {
            return { authorized: false, status: "USER_DISABLED" };
        }

        const expiresAt = user.expiresAt;
        if (typeof expiresAt !== "number"
                || !Number.isFinite(expiresAt)
                || expiresAt <= 0
                || expiresAt <= Date.now()) {
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

        return { authorized: true };
    } catch (error) {
        if (error instanceof AuthorizationUnavailableError) {
            throw error;
        }
        throw new AuthorizationUnavailableError();
    }
}