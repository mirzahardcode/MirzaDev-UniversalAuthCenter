package com.universal.authcenter.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public final class SessionManager {

    private static final String PREFERENCES_PREFIX = "universal_auth_center_session_";
    private static final String KEY_AUTHENTICATION_STATUS = "authentication_status";
    private static final String KEY_AUTHORIZATION_STATUS = "authorization_status";
    private static final String KEY_USER_ID = "user_id";
    private static final Object PROCESS_SESSION_LOCK = new Object();
    private static final Map<String, AuthState.AuthenticationStatus> PROCESS_SESSIONS =
            new HashMap<String, AuthState.AuthenticationStatus>();

    private final String appId;
    private final SharedPreferences preferences;
    private final TokenManager tokenManager;

    public SessionManager(Context context, String appId) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (appId == null || appId.trim().isEmpty()) {
            throw new IllegalArgumentException("App ID must not be blank");
        }
        this.appId = appId;

        Context applicationContext = context.getApplicationContext();
        Context storageContext = applicationContext != null
                ? applicationContext
                : context;
        preferences = storageContext.getSharedPreferences(
                PREFERENCES_PREFIX + appId,
                Context.MODE_PRIVATE
        );
        tokenManager = new TokenManager(storageContext, appId);
    }

    public synchronized AuthState getState() {
        synchronized (PROCESS_SESSION_LOCK) {
            return getStateLocked();
        }
    }

    private AuthState getStateLocked() {
        AuthState.AuthenticationStatus authenticationStatus =
                readAuthenticationStatus();
        AuthState.AuthorizationStatus authorizationStatus =
                readAuthorizationStatus();

        if ((authenticationStatus == AuthState.AuthenticationStatus.AUTHENTICATED
                || authenticationStatus == AuthState.AuthenticationStatus.AUTHENTICATING)
                && !isProcessAuthenticationActive(authenticationStatus)) {
            clearSession(AuthState.AuthenticationStatus.SESSION_EXPIRED);
            authenticationStatus = AuthState.AuthenticationStatus.SESSION_EXPIRED;
            authorizationStatus = AuthState.AuthorizationStatus.NOT_CHECKED;
        }

        if (authenticationStatus == AuthState.AuthenticationStatus.AUTHENTICATED
                && !tokenManager.hasValidIdToken()) {
            clearSession(AuthState.AuthenticationStatus.SESSION_EXPIRED);
            authenticationStatus = AuthState.AuthenticationStatus.SESSION_EXPIRED;
            authorizationStatus = AuthState.AuthorizationStatus.NOT_CHECKED;
        }

        String userId = authenticationStatus
                == AuthState.AuthenticationStatus.AUTHENTICATED
                ? preferences.getString(KEY_USER_ID, null)
                : null;
        long expiresAtMillis = authenticationStatus
                == AuthState.AuthenticationStatus.AUTHENTICATED
                ? tokenManager.getExpiresAtMillis()
                : 0L;

        return new AuthState(
                authenticationStatus,
                authorizationStatus,
                userId,
                expiresAtMillis
        );
    }

    public synchronized boolean hasLocalSession() {
        return getState().hasAuthenticatedSession();
    }

    public synchronized void beginAuthentication() {
        synchronized (PROCESS_SESSION_LOCK) {
            setProcessAuthenticationStatus(AuthState.AuthenticationStatus.AUTHENTICATING);
            try {
                tokenManager.clear();
                persistState(
                        AuthState.AuthenticationStatus.AUTHENTICATING,
                        AuthState.AuthorizationStatus.NOT_CHECKED,
                        null
                );
            } catch (RuntimeException exception) {
                clearProcessAuthenticationStatus();
                throw exception;
            }
        }
    }

    public synchronized void completeAuthentication(
            String userId,
            String idToken,
            String refreshToken,
            long expiresAtMillis
    ) {
        synchronized (PROCESS_SESSION_LOCK) {
            requireStatus(AuthState.AuthenticationStatus.AUTHENTICATING);
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID must not be blank");
            }

            tokenManager.storeTokens(idToken, refreshToken, expiresAtMillis);
            try {
                persistState(
                        AuthState.AuthenticationStatus.AUTHENTICATED,
                        AuthState.AuthorizationStatus.NOT_CHECKED,
                        userId
                );
                setProcessAuthenticationStatus(AuthState.AuthenticationStatus.AUTHENTICATED);
            } catch (RuntimeException exception) {
                clearProcessAuthenticationStatus();
                tokenManager.clear();
                throw exception;
            }
        }
    }

    public synchronized void markAuthorized() {
        requireAuthenticatedSession();
        persistState(
                AuthState.AuthenticationStatus.AUTHENTICATED,
                AuthState.AuthorizationStatus.AUTHORIZED,
                preferences.getString(KEY_USER_ID, null)
        );
    }

    public synchronized void markAccessDenied() {
        requireAuthenticatedSession();
        persistState(
                AuthState.AuthenticationStatus.AUTHENTICATED,
                AuthState.AuthorizationStatus.ACCESS_DENIED,
                preferences.getString(KEY_USER_ID, null)
        );
    }

    public synchronized void markDeviceRejected() {
        requireAuthenticatedSession();
        persistState(
                AuthState.AuthenticationStatus.AUTHENTICATED,
                AuthState.AuthorizationStatus.DEVICE_REJECTED,
                preferences.getString(KEY_USER_ID, null)
        );
    }

    public synchronized void failAuthentication() {
        synchronized (PROCESS_SESSION_LOCK) {
            requireStatus(AuthState.AuthenticationStatus.AUTHENTICATING);
            clearSession(AuthState.AuthenticationStatus.AUTH_FAILED);
        }
    }

    public synchronized void markNetworkError() {
        synchronized (PROCESS_SESSION_LOCK) {
            requireStatus(AuthState.AuthenticationStatus.AUTHENTICATING);
            clearProcessAuthenticationStatus();
            persistState(
                    AuthState.AuthenticationStatus.NETWORK_ERROR,
                    AuthState.AuthorizationStatus.NOT_CHECKED,
                    preferences.getString(KEY_USER_ID, null)
            );
        }
    }

    public synchronized void logout() {
        synchronized (PROCESS_SESSION_LOCK) {
            clearProcessAuthenticationStatus();
            tokenManager.clear();
            persistState(
                    AuthState.AuthenticationStatus.SIGNED_OUT,
                    AuthState.AuthorizationStatus.NOT_CHECKED,
                    null
            );
        }
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    private void requireAuthenticatedSession() {
        if (!getState().hasAuthenticatedSession()) {
            throw new IllegalStateException("An authenticated session is required");
        }
    }

    private void requireStatus(AuthState.AuthenticationStatus expectedStatus) {
        if (readAuthenticationStatus() != expectedStatus) {
            throw new IllegalStateException(
                    "Expected authentication status " + expectedStatus
            );
        }
    }

    private void clearSession(AuthState.AuthenticationStatus nextStatus) {
        clearProcessAuthenticationStatus();
        tokenManager.clear();
        persistState(
                nextStatus,
                AuthState.AuthorizationStatus.NOT_CHECKED,
                null
        );
    }

    private void persistState(
            AuthState.AuthenticationStatus authenticationStatus,
            AuthState.AuthorizationStatus authorizationStatus,
            String userId
    ) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_AUTHENTICATION_STATUS, authenticationStatus.name())
                .putString(KEY_AUTHORIZATION_STATUS, authorizationStatus.name());
        if (userId == null) {
            editor.remove(KEY_USER_ID);
        } else {
            editor.putString(KEY_USER_ID, userId);
        }

        if (!editor.commit()) {
            throw new IllegalStateException("Unable to persist session state");
        }
    }

    private boolean isProcessAuthenticationActive(
            AuthState.AuthenticationStatus status
    ) {
        synchronized (PROCESS_SESSION_LOCK) {
            return PROCESS_SESSIONS.get(appId) == status;
        }
    }

    private void setProcessAuthenticationStatus(
            AuthState.AuthenticationStatus status
    ) {
        synchronized (PROCESS_SESSION_LOCK) {
            PROCESS_SESSIONS.put(appId, status);
        }
    }

    private void clearProcessAuthenticationStatus() {
        synchronized (PROCESS_SESSION_LOCK) {
            PROCESS_SESSIONS.remove(appId);
        }
    }

    private AuthState.AuthenticationStatus readAuthenticationStatus() {
        String value = preferences.getString(
                KEY_AUTHENTICATION_STATUS,
                AuthState.AuthenticationStatus.SIGNED_OUT.name()
        );
        try {
            return AuthState.AuthenticationStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AuthState.AuthenticationStatus.SIGNED_OUT;
        }
    }

    private AuthState.AuthorizationStatus readAuthorizationStatus() {
        String value = preferences.getString(
                KEY_AUTHORIZATION_STATUS,
                AuthState.AuthorizationStatus.NOT_CHECKED.name()
        );
        try {
            return AuthState.AuthorizationStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AuthState.AuthorizationStatus.NOT_CHECKED;
        }
    }
}