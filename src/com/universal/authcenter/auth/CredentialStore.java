package com.universal.authcenter.auth;

import android.content.Context;
import android.content.SharedPreferences;

public final class CredentialStore {

    private static final String PREFERENCES_PREFIX =
            "universal_auth_center_credentials_";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";

    private final SharedPreferences preferences;

    public CredentialStore(Context context, String appId) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (appId == null || appId.trim().isEmpty()) {
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

    public void saveSuccessfulCredentials(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        boolean saved = preferences.edit()
                .putString(KEY_EMAIL, email.trim())
                .putString(KEY_PASSWORD, password)
                .commit();
        if (!saved) {
            throw new IllegalStateException(
                    "Unable to persist successful login credentials"
            );
        }
    }

    public SavedCredentials getSavedCredentials() {
        String email = preferences.getString(KEY_EMAIL, null);
        String password = preferences.getString(KEY_PASSWORD, null);
        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {
            return null;
        }
        return new SavedCredentials(email, password);
    }

    public static final class SavedCredentials {

        private final String email;
        private final String password;

        private SavedCredentials(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }
    }
}
