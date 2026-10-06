import { createHmac } from "node:crypto";

const DEVICE_ID_PATTERN = /^[0-9a-f]{16}$/;
const ZERO_DEVICE_ID = "0000000000000000";
const HASHED_DEVICE_ID_PATTERN = /^v1:[0-9a-f]{64}$/;
const HASH_VERSION = "v1";

export function normalizeDeviceId(value: unknown): string | null {
    if (typeof value !== "string") {
        return null;
    }

    const normalized = value.trim();
    if (!DEVICE_ID_PATTERN.test(normalized) || normalized === ZERO_DEVICE_ID) {
        return null;
    }

    return normalized;
}

export function isHashedDeviceId(value: unknown): value is string {
    return typeof value === "string" && HASHED_DEVICE_ID_PATTERN.test(value);
}

export function hashDeviceId(deviceId: string): string {
    const pepper = process.env.DEVICE_ID_PEPPER;
    if (!pepper || pepper.trim().length < 32) {
        throw new Error("Device binding pepper is not configured");
    }

    const digest = createHmac("sha256", pepper)
        .update(deviceId, "utf8")
        .digest("hex");

    return `${HASH_VERSION}:${digest}`;
}
