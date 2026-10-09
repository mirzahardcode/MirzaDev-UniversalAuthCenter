package com.universal.authcenter;

import com.universal.authcenter.auth.AuthState;

/**
 * TEMPORARY DEBUG — immutable, sanitized snapshot of one authentication
 * attempt.
 *
 * <p>Every value here is either non-sensitive configuration, a coarse status
 * enum name, a masked identifier, or a sanitized/truncated server message.
 * Full UID, full Android ID, application key, Firebase tokens, the raw
 * {@code Authorization} header, and raw request/response bodies never enter
 * this object.
 *
 * <p>Fields start as {@link #UNKNOWN} and are only overwritten when the
 * corresponding authentication stage actually runs. A value is never
 * guessed.
 */
public final class AuthDebugSnapshot {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String NOT_AVAILABLE = "NOT AVAILABLE";

    private String backendHost = UNKNOWN;
    private String packageName = UNKNOWN;
    private String appKeyMasked = UNKNOWN;
    private String configStatus = UNKNOWN;
    private String firebaseSession = UNKNOWN;
    private String uidMasked = UNKNOWN;
    private String idToken = UNKNOWN;
    private String deviceIdentity = UNKNOWN;
    private String lastAttemptTime = NOT_AVAILABLE;
    private String lastCompletedStage = NOT_AVAILABLE;
    private String requestStatus = "NOT STARTED";
    private String httpStatus = NOT_AVAILABLE;
    private String applicationStatus = NOT_AVAILABLE;
    private String errorMessage = NOT_AVAILABLE;
    private String responseTime = UNKNOWN;
    private String exceptionCategory = NOT_AVAILABLE;

    AuthDebugSnapshot copy() {
        AuthDebugSnapshot copy = new AuthDebugSnapshot();
        copy.backendHost = backendHost;
        copy.packageName = packageName;
        copy.appKeyMasked = appKeyMasked;
        copy.configStatus = configStatus;
        copy.firebaseSession = firebaseSession;
        copy.uidMasked = uidMasked;
        copy.idToken = idToken;
        copy.deviceIdentity = deviceIdentity;
        copy.lastAttemptTime = lastAttemptTime;
        copy.lastCompletedStage = lastCompletedStage;
        copy.requestStatus = requestStatus;
        copy.httpStatus = httpStatus;
        copy.applicationStatus = applicationStatus;
        copy.errorMessage = errorMessage;
        copy.responseTime = responseTime;
        copy.exceptionCategory = exceptionCategory;
        return copy;
    }

    AuthDebugSnapshot initialize(
            String backendHost,
            String packageName,
            String appKey,
            boolean hasFirebaseApiKey
    ) {
        this.backendHost = hostOrUnknown(backendHost);
        this.packageName = packageNameOrUnknown(packageName);
        this.appKeyMasked = maskSecret(appKey);
        boolean hasBackendHost = !UNKNOWN.equals(this.backendHost);
        boolean hasPackageName = !UNKNOWN.equals(this.packageName);
        boolean hasAppKey = !isBlank(appKey);
        if (hasBackendHost && hasPackageName && hasAppKey && hasFirebaseApiKey) {
            this.configStatus = "LOADED";
        } else {
            StringBuilder missing = new StringBuilder("INCOMPLETE (missing ");
            if (!hasBackendHost) {
                missing.append("backendHost, ");
            }
            if (!hasPackageName) {
                missing.append("packageName, ");
            }
            if (!hasAppKey) {
                missing.append("appKey, ");
            }
            if (!hasFirebaseApiKey) {
                missing.append("firebaseApiKey, ");
            }
            missing.setLength(missing.length() - 2);
            this.configStatus = missing.append(')').toString();
        }
        return this;
    }

    AuthDebugSnapshot configFailure(String reason) {
        if ("AuthConfig is null".equals(reason)) {
            this.configStatus = "FAILED (AuthConfig is null)";
        } else if ("Invalid authorization configuration".equals(reason)) {
            this.configStatus = "FAILED (Invalid authorization configuration)";
        } else {
            this.configStatus = "FAILED (reason suppressed)";
        }
        return this;
    }

    AuthDebugSnapshot firebaseSession(boolean authenticated, String userId) {
        this.firebaseSession = authenticated ? "authenticated" : "not authenticated";
        this.uidMasked = authenticated ? maskUid(userId) : NOT_AVAILABLE;
        return this;
    }

    AuthDebugSnapshot idToken(boolean present, boolean valid) {
        if (!present) {
            this.idToken = "missing";
        } else if (valid) {
            this.idToken = "present (valid)";
        } else {
            this.idToken = "present (expired)";
        }
        return this;
    }

    AuthDebugSnapshot deviceIdentity(boolean evaluated, boolean available) {
        if (!evaluated) {
            this.deviceIdentity = "NOT EVALUATED";
        } else {
            this.deviceIdentity = available ? "valid" : "unavailable";
        }
        return this;
    }

    AuthDebugSnapshot attemptStarted(String timestamp) {
        this.lastAttemptTime = valueOrUnknown(timestamp);
        this.lastCompletedStage = NOT_AVAILABLE;
        this.requestStatus = "NOT STARTED";
        this.httpStatus = NOT_AVAILABLE;
        this.applicationStatus = NOT_AVAILABLE;
        this.errorMessage = NOT_AVAILABLE;
        this.responseTime = UNKNOWN;
        this.exceptionCategory = NOT_AVAILABLE;
        return this;
    }

    AuthDebugSnapshot stage(String stage) {
        this.lastCompletedStage = stage;
        return this;
    }

    AuthDebugSnapshot requestStart() {
        this.requestStatus = "IN PROGRESS";
        return this;
    }

    AuthDebugSnapshot httpResponse(int statusCode, boolean bodyAvailable, long elapsedMillis) {
        this.httpStatus = statusCode > 0 ? String.valueOf(statusCode) : NOT_AVAILABLE;
        this.requestStatus = "COMPLETED";
        this.responseTime = elapsedMillis >= 0 ? elapsedMillis + " ms" : UNKNOWN;
        if (!bodyAvailable) {
            this.applicationStatus = "HTTP_ERROR_BODY_UNAVAILABLE";
        }
        return this;
    }

    AuthDebugSnapshot applicationStatus(String status) {
        if (status != null && !status.trim().isEmpty()) {
            this.applicationStatus = knownStatus(status);
        }
        return this;
    }

    AuthDebugSnapshot errorMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            this.errorMessage = knownMessage(message);
        }
        return this;
    }

    AuthDebugSnapshot networkError(String category) {
        this.requestStatus = "NETWORK ERROR";
        this.exceptionCategory = valueOrUnknown(category);
        return this;
    }

    AuthDebugSnapshot parsingError(String category) {
        this.requestStatus = "COMPLETED";
        this.exceptionCategory = valueOrUnknown(category);
        return this;
    }

    AuthDebugSnapshot requestFailure(String category) {
        this.requestStatus = "FAILED";
        this.exceptionCategory = valueOrUnknown(category);
        return this;
    }

    AuthDebugSnapshot responseError(String category) {
        this.exceptionCategory = valueOrUnknown(category);
        return this;
    }

    AuthDebugSnapshot sessionState(AuthState state) {
        if (state != null) {
            this.firebaseSession = state.hasAuthenticatedSession()
                    ? "authenticated"
                    : "not authenticated";
        }
        return this;
    }

    public String getBackendHost() {
        return backendHost;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppKeyMasked() {
        return appKeyMasked;
    }

    public String getConfigStatus() {
        return configStatus;
    }

    public String getFirebaseSession() {
        return firebaseSession;
    }

    public String getUidMasked() {
        return uidMasked;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getDeviceIdentity() {
        return deviceIdentity;
    }

    public String getLastAttemptTime() {
        return lastAttemptTime;
    }

    public String getLastCompletedStage() {
        return lastCompletedStage;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public String getHttpStatus() {
        return httpStatus;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public String getExceptionCategory() {
        return exceptionCategory;
    }

    // --- masking / sanitization helpers ---

    private static String maskUid(String userId) {
        if (isBlank(userId)) {
            return NOT_AVAILABLE;
        }
        String trimmed = userId.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        int keep = Math.min(4, trimmed.length());
        return "***" + trimmed.substring(trimmed.length() - keep);
    }

    private static String maskSecret(String secret) {
        if (isBlank(secret)) {
            return "NOT CONFIGURED";
        }
        return "*** (len=" + secret.trim().length() + ")";
    }

    private static String knownStatus(String status) {
        String normalized = status.trim().toUpperCase(java.util.Locale.US);
        switch (normalized) {
            case "AUTHORIZED":
            case "ACCESS_DENIED":
            case "DEVICE_REJECTED":
            case "USER_DISABLED":
            case "USER_EXPIRED":
            case "UNKNOWN_APP":
            case "BAD_REQUEST":
            case "UNAUTHENTICATED":
            case "INVALID_TOKEN":
            case "AUTHORIZATION_UNAVAILABLE":
            case "INTERNAL_ERROR":
            case "METHOD_NOT_ALLOWED":
            case "AUTHENTICATION_REQUIRED":
            case "INVALID_CREDENTIALS":
            case "ACCOUNT_DISABLED":
            case "SIGN_IN_DISABLED":
            case "RATE_LIMITED":
            case "HTTP_ERROR":
            case "INVALID_RESPONSE":
            case "INVALID_REQUEST":
            case "CONFIGURATION_ERROR":
            case "SESSION_ERROR":
                return normalized;
            default:
                return UNKNOWN;
        }
    }

    private static String knownMessage(String message) {
        String normalized = message.trim();
        switch (normalized) {
            case "Access granted":
            case "Access denied":
            case "Unknown application":
            case "Authentication required":
            case "Invalid or expired token":
            case "Authorization service unavailable":
            case "Internal server error":
            case "Method not allowed":
            case "A valid appKey is required":
            case "A valid deviceId is required":
            case "Device identity is unavailable":
                return normalized;
            default:
                return "UNRECOGNIZED MESSAGE (content suppressed)";
        }
    }

    private static String hostOrUnknown(String value) {
        if (isBlank(value)) {
            return UNKNOWN;
        }
        try {
            java.net.URI uri = new java.net.URI(value.trim());
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return UNKNOWN;
            }
            String normalizedHost = host.toLowerCase(java.util.Locale.US);
            return uri.getPort() < 0
                    ? normalizedHost
                    : normalizedHost + ":" + uri.getPort();
        } catch (java.net.URISyntaxException exception) {
            return UNKNOWN;
        }
    }

    private static String packageNameOrUnknown(String value) {
        if (isBlank(value)) {
            return UNKNOWN;
        }
        String normalized = value.trim();
        return normalized.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
                ? normalized
                : UNKNOWN;
    }

    private static String valueOrUnknown(String value) {
        return isBlank(value) ? UNKNOWN : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
