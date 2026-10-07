import { cert, getApps, initializeApp } from "firebase-admin/app";
import type { App } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import type { DecodedIdToken } from "firebase-admin/auth";

export class AuthorizationUnavailableError extends Error {
    constructor() {
        super("Authorization service unavailable");
        this.name = "AuthorizationUnavailableError";
    }
}

export class InvalidFirebaseTokenError extends Error {
    constructor() {
        super("Invalid or expired token");
        this.name = "InvalidFirebaseTokenError";
    }
}

function requiredEnvironmentVariable(name: string): string {
    const value = process.env[name];
    if (!value || value.trim().length === 0) {
        throw new Error("Missing Firebase Admin configuration");
    }
    return value;
}

export function getFirebaseAdminApp(): App {
    const existingApp = getApps().find((app) => app.name === "[DEFAULT]");
    if (existingApp) {
        return existingApp;
    }

    try {
        const projectId = requiredEnvironmentVariable("FIREBASE_PROJECT_ID");
        const clientEmail = requiredEnvironmentVariable("FIREBASE_CLIENT_EMAIL");
        const privateKey = requiredEnvironmentVariable("FIREBASE_PRIVATE_KEY")
            .replace(/\\n/g, "\n")
            .replace(/\r\n/g, "\n");
        const databaseURL = requiredEnvironmentVariable("FIREBASE_DATABASE_URL");

        return initializeApp({
            credential: cert({ projectId, clientEmail, privateKey }),
            databaseURL,
        });
    } catch {
        throw new AuthorizationUnavailableError();
    }
}

const INVALID_TOKEN_CODES = new Set([
    "auth/argument-error",
    "auth/id-token-expired",
    "auth/id-token-revoked",
    "auth/invalid-id-token",
    "auth/invalid-argument",
]);

function isInvalidTokenError(error: unknown): boolean {
    if (typeof error !== "object" || error === null || !("code" in error)) {
        return false;
    }
    return typeof error.code === "string" && INVALID_TOKEN_CODES.has(error.code);
}

export async function verifyFirebaseIdToken(token: string): Promise<DecodedIdToken> {
    try {
        return await getAuth(getFirebaseAdminApp()).verifyIdToken(token);
    } catch (error) {
        if (isInvalidTokenError(error)) {
            throw new InvalidFirebaseTokenError();
        }
        if (error instanceof AuthorizationUnavailableError) {
            throw error;
        }
        throw new AuthorizationUnavailableError();
    }
}