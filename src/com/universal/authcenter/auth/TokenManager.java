package com.universal.authcenter.auth;

import android.content.Context;
import android.content.SharedPreferences;

public final class TokenManager {

    private static final String PREFERENCES_PREFIX = "universal_auth_center_tokens_";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at_millis";

    private final SharedPreferences preferences;

    public TokenManager(Context context, String appId) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (isBlank(appId)) {
            throw new IllegalArgumentException("App ID must not be blank");
        }

        Context applicationContext = context.getApplicationContext();
        Context storageContext = applicationContext != null
                ? applicationContext
                : context;
        preferences = storageContext.getSharedPreferences(
                PREFERENCES_PREFIX + appId,
                Context.MODE_PRIVATE
        );
    }

    public void storeTokens(
            String idToken,
            String refreshToken,
            long expiresAtMillis
    ) {
        if (isBlank(idToken)) {
            throw new IllegalArgumentException("ID token must not be blank");
        }
        if (expiresAtMillis <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Token expiry must be in the future");
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putLong(KEY_EXPIRES_AT, expiresAtMillis);
        if (isBlank(refreshToken)) {
            editor.remove(KEY_REFRESH_TOKEN);
        } else {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        }

        if (!editor.commit()) {
            throw new IllegalStateException("Unable to persist tokens");
        }
    }

    public String getIdToken() {
        return preferences.getString(KEY_ID_TOKEN, null);
    }

    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public long getExpiresAtMillis() {
        return preferences.getLong(KEY_EXPIRES_AT, 0L);
    }

    public boolean hasValidIdToken() {
        String idToken = getIdToken();
        return !isBlank(idToken)
                && System.currentTimeMillis() < getExpiresAtMillis();
    }

    public void clear() {
        if (!preferences.edit().clear().commit()) {
            throw new IllegalStateException("Unable to clear tokens");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}