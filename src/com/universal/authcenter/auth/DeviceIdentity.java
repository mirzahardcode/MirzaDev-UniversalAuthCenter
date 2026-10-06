package com.universal.authcenter.auth;

import android.content.Context;
import android.provider.Settings;

import java.util.Locale;

/**
 * Reads the device identifier exposed by {@link Settings.Secure#ANDROID_ID}.
 *
 * <p>Self-contained utility: no network, no Firebase, no backend, no storage,
 * and no cryptographic operations. When the identifier cannot be determined
 * it fails safely by returning {@code null}. The first successful read is
 * cached so {@link Settings} is not queried repeatedly.
 */
public final class DeviceIdentity {

    private static final String ANDROID_ID_GENERIC = "9774d56d682e549c";
    private static final String ANDROID_ID_RESET = "0000000000000000";

    private static volatile String cachedDeviceId;

    private DeviceIdentity() {
        // Prevent instantiation.
    }

    /**
     * @return the normalized device identifier, or {@code null} when it
     *         cannot be determined
     */
    public static String get(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        String cached = cachedDeviceId;
        if (cached != null) {
            return cached;
        }

        synchronized (DeviceIdentity.class) {
            if (cachedDeviceId == null) {
                cachedDeviceId = readDeviceId(context);
            }
            return cachedDeviceId;
        }
    }

    private static String readDeviceId(Context context) {
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            return normalize(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || ANDROID_ID_GENERIC.equals(trimmed)
                || ANDROID_ID_RESET.equals(trimmed)) {
            return null;
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }
}