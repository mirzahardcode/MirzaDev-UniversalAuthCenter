package com.universal.authcenter.auth;

public final class AuthState {

    public enum AuthenticationStatus {
        SIGNED_OUT,
        AUTHENTICATING,
        AUTHENTICATED,
        AUTH_FAILED,
        SESSION_EXPIRED,
        NETWORK_ERROR
    }

    public enum AuthorizationStatus {
        NOT_CHECKED,
        AUTHORIZED,
        ACCESS_DENIED,
        DEVICE_REJECTED
    }

    private final AuthenticationStatus authenticationStatus;
    private final AuthorizationStatus authorizationStatus;
    private final String userId;
    private final long expiresAtMillis;

    AuthState(
            AuthenticationStatus authenticationStatus,
            AuthorizationStatus authorizationStatus,
            String userId,
            long expiresAtMillis
    ) {
        this.authenticationStatus = authenticationStatus;
        this.authorizationStatus = authorizationStatus;
        this.userId = userId;
        this.expiresAtMillis = expiresAtMillis;
    }

    public AuthenticationStatus getAuthenticationStatus() {
        return authenticationStatus;
    }

    public AuthorizationStatus getAuthorizationStatus() {
        return authorizationStatus;
    }

    public String getUserId() {
        return userId;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean hasAuthenticatedSession() {
        return authenticationStatus == AuthenticationStatus.AUTHENTICATED;
    }

    public boolean isAuthorized() {
        return hasAuthenticatedSession()
                && authorizationStatus == AuthorizationStatus.AUTHORIZED;
    }

    public boolean isReady() {
        return isAuthorized();
    }
}