import { getAppConfig } from "../../src/config/apps.js";
import {
    AuthorizationUnavailableError,
    InvalidFirebaseTokenError,
    verifyFirebaseIdToken,
} from "../../src/firebase/admin.js";
import { authorizeUser } from "../../src/services/AuthorizationService.js";
import { normalizeDeviceId } from "../../src/utils/deviceId.js";
import {
    sendResponse,
    VercelRequest,
    VercelResponse,
} from "../../src/utils/response.js";

function readRequestBody(body: unknown): Record<string, unknown> | null {
    let parsedBody = body;
    if (typeof body === "string") {
        try {
            parsedBody = JSON.parse(body) as unknown;
        } catch {
            return null;
        }
    }

    if (typeof parsedBody !== "object" || parsedBody === null || Array.isArray(parsedBody)) {
        return null;
    }

    return parsedBody as Record<string, unknown>;
}

function readAppKey(body: unknown): string | null {
    const parsedBody = readRequestBody(body);
    if (!parsedBody) {
        return null;
    }

    const appKey = parsedBody.appKey;
    return typeof appKey === "string" && appKey.trim().length > 0
        ? appKey.trim()
        : null;
}

function readDeviceId(body: unknown): string | null | undefined {
    const parsedBody = readRequestBody(body);
    if (!parsedBody || !Object.prototype.hasOwnProperty.call(parsedBody, "deviceId")) {
        return undefined;
    }

    const rawDeviceId = parsedBody.deviceId;
    if (rawDeviceId === null || rawDeviceId === undefined) {
        return null;
    }

    return normalizeDeviceId(rawDeviceId);
}

function readBearerToken(request: VercelRequest): string | null {
    const header = request.headers.authorization;
    const value = Array.isArray(header) ? header[0] : header;
    const match = typeof value === "string"
        ? /^Bearer\s+(\S+)$/.exec(value.trim())
        : null;
    return match ? match[1] : null;
}

export default async function handler(
    request: VercelRequest,
    response: VercelResponse,
): Promise<void> {
    if (request.method !== "POST") {
        response.setHeader("Allow", "POST");
        sendResponse(response, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
        return;
    }

    const appKey = readAppKey(request.body);
    if (!appKey) {
        sendResponse(response, 400, "BAD_REQUEST", "A valid appKey is required");
        return;
    }

    if (!getAppConfig(appKey)) {
        sendResponse(response, 404, "UNKNOWN_APP", "Unknown application");
        return;
    }

    const token = readBearerToken(request);
    if (!token) {
        sendResponse(response, 401, "UNAUTHENTICATED", "Authentication required");
        return;
    }

    let uid: string;
    try {
        const decodedToken = await verifyFirebaseIdToken(token);
        uid = decodedToken.uid;
    } catch (error) {
        if (error instanceof InvalidFirebaseTokenError) {
            sendResponse(response, 401, "INVALID_TOKEN", "Invalid or expired token");
            return;
        }
        if (error instanceof AuthorizationUnavailableError) {
            sendResponse(
                response,
                503,
                "AUTHORIZATION_UNAVAILABLE",
                "Authorization service unavailable",
            );
            return;
        }
        sendResponse(response, 500, "INTERNAL_ERROR", "Internal server error");
        return;
    }

    const deviceId = readDeviceId(request.body);
    const bindingMode = process.env.DEVICE_BINDING_MODE?.trim().toLowerCase();

    if (bindingMode !== "off") {
        if (deviceId === null) {
            sendResponse(response, 400, "BAD_REQUEST", "A valid deviceId is required");
            return;
        }

        if (deviceId === undefined && bindingMode !== "optional") {
            sendResponse(response, 400, "BAD_REQUEST", "A valid deviceId is required");
            return;
        }
    }

    try {
        const decision = await authorizeUser(
            uid,
            appKey,
            deviceId === undefined ? null : deviceId,
        );

        if (decision.authorized) {
            sendResponse(response, 200, "AUTHORIZED", "Access granted");
            return;
        }

        if (decision.status === "UNKNOWN_APP") {
            sendResponse(response, 404, "UNKNOWN_APP", "Unknown application");
            return;
        }

        sendResponse(response, 403, decision.status, "Access denied");
    } catch (error) {
        if (error instanceof AuthorizationUnavailableError) {
            sendResponse(
                response,
                503,
                "AUTHORIZATION_UNAVAILABLE",
                "Authorization service unavailable",
            );
            return;
        }
        sendResponse(response, 500, "INTERNAL_ERROR", "Internal server error");
    }
}
