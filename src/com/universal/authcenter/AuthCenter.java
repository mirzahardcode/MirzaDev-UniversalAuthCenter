package com.universal.authcenter;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.universal.authcenter.auth.AuthState;
import com.universal.authcenter.auth.AuthorizationClient;
import com.universal.authcenter.auth.DeviceIdentity;
import com.universal.authcenter.auth.FirebaseAuthClient;
import com.universal.authcenter.auth.SessionManager;
import com.universal.authcenter.ui.LoginDialog;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuthCenter {

    private static final String TAG = "AuthCenter";
    private static final String STATUS_DEVICE_REJECTED = "DEVICE_REJECTED";
    private static final int HTTP_OK = 200;
    private static final java.text.SimpleDateFormat DEBUG_TIMESTAMP_FORMAT =
            new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService AUTH_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private AuthCenter() {
        // Utility class
    }

    public static void start(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        AuthConfig config = AuthConfig.fromActivity(
                activity,
                GeneratedConfig.FIREBASE_API_KEY,
                GeneratedConfig.APP_KEY
        );

        start(activity, config, null);
    }

    public static void start(
            Activity activity,
            AuthConfig config,
            AuthCallback callback
    ) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (config == null) {
            AuthResult result = new AuthResult(
                    AuthResult.Status.ERROR,
                    "AuthConfig is null"
            );

            if (AuthDebugConfig.ENABLED) {
                AuthDebug.reset();
                AuthDebug.recordConfigFailure("AuthConfig is null");
                AuthDebug.recordStage("CONFIG FAILED");
            }

            if (callback != null) {
                callback.onError(result);
            }

            return;
        }

        if (AuthDebugConfig.ENABLED) {
            AuthDebug.reset();
            AuthDebug.recordConfig(
                    config.getBackendUrl(),
                    config.getAppId(),
                    config.getAppKey(),
                    !isBlank(config.getFirebaseApiKey())
            );
            AuthDebug.recordStage("CONFIG LOADED");
        }

        Log.d(
                TAG,
                "Starting authentication for: " + config.getAppId()
        );

        try {
            Context context = activity.getApplicationContext();
            SessionManager sessionManager = new SessionManager(
                    context != null ? context : activity,
                    config.getAppId()
            );
            AuthState state = sessionManager.getState();
            Log.d(TAG, "Identity authenticated: " + state.hasAuthenticatedSession());

            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordFirebaseSession(
                        state.hasAuthenticatedSession(),
                        state.getUserId()
                );
                AuthDebug.recordStage("FIREBASE SESSION LOADED");

                boolean hasValidIdToken = sessionManager.getTokenManager()
                        .hasValidIdToken();
                AuthDebug.recordIdToken(
                        !isBlank(sessionManager.getTokenManager().getIdToken()),
                        hasValidIdToken
                );
            }

            if (!state.hasAuthenticatedSession()
                    || state.getAuthenticationStatus() == AuthState.AuthenticationStatus.SESSION_EXPIRED
                    || state.getAuthenticationStatus() == AuthState.AuthenticationStatus.NETWORK_ERROR
                    || state.getAuthorizationStatus() == AuthState.AuthorizationStatus.ACCESS_DENIED
                    || state.getAuthorizationStatus() == AuthState.AuthorizationStatus.DEVICE_REJECTED
                    || state.getAuthorizationStatus() == AuthState.AuthorizationStatus.NOT_CHECKED) {
                showLoginDialog(activity, config, callback);
            }
        } catch (RuntimeException exception) {
            if (callback != null) {
                callback.onError(new AuthResult(
                        AuthResult.Status.ERROR,
                        "Unable to read authentication state"
                ));
            }
        }
    }

    public static void showLoginDialog(
            Activity activity,
            AuthConfig config,
            AuthCallback callback
    ) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (config == null) {
            if (callback != null) {
                callback.onError(new AuthResult(
                        AuthResult.Status.ERROR,
                        "AuthConfig is null"
                ));
            }
            return;
        }

        try {
            LoginDialog dialog = new LoginDialog(activity, config, callback);
            dialog.show();
        } catch (RuntimeException exception) {
            if (callback != null) {
                callback.onError(new AuthResult(
                        AuthResult.Status.ERROR,
                        "Unable to show login dialog"
                ));
            }
        }
    }

    public static void authorize(
            Activity activity,
            AuthConfig config,
            AuthCallback callback
    ) {
        if (callback == null) {
            return;
        }
        if (activity == null || activity.isFinishing()) {
            callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Activity is not available"
            ));
            return;
        }
        if (AuthDebugConfig.ENABLED) {
            AuthDebug.recordAttemptStarted(formatTimestamp(System.currentTimeMillis()));
        }
        if (config == null
                || isBlank(config.getAppId())
                || isBlank(config.getBackendUrl())
                || isBlank(config.getAppKey())) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordConfigFailure("Invalid authorization configuration");
                AuthDebug.recordStage("CONFIG FAILED");
            }
            callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Invalid authorization configuration"
            ));
            return;
        }

        if (AuthDebugConfig.ENABLED) {
            AuthDebug.recordConfig(
                    config.getBackendUrl(),
                    config.getAppId(),
                    config.getAppKey(),
                    !isBlank(config.getFirebaseApiKey())
            );
            AuthDebug.recordStage("CONFIG LOADED");
        }

        Context context = activity.getApplicationContext();
        try {
            SessionManager sessionManager = new SessionManager(
                    context != null ? context : activity,
                    config.getAppId()
            );
            AuthState state = sessionManager.getState();
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordFirebaseSession(
                        state.hasAuthenticatedSession(),
                        state.getUserId()
                );
                AuthDebug.recordStage("FIREBASE SESSION LOADED");
            }

            if (!state.hasAuthenticatedSession()
                    || isBlank(sessionManager.getTokenManager().getIdToken())) {
                if (AuthDebugConfig.ENABLED) {
                    AuthDebug.recordIdToken(
                            !isBlank(sessionManager.getTokenManager().getIdToken()),
                            sessionManager.getTokenManager().hasValidIdToken()
                    );
                    AuthDebug.recordStage("AUTHORIZATION BLOCKED: AUTHENTICATION REQUIRED");
                    AuthDebug.recordApplicationStatus("AUTHENTICATION_REQUIRED");
                }
                callback.onDenied(new AuthResult(
                        AuthResult.Status.DENIED,
                        "Authentication required before authorization"
                ));
                return;
            }

            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordIdToken(true, sessionManager.getTokenManager().hasValidIdToken());
                AuthDebug.recordStage("ID TOKEN OBTAINED");
            }

            String deviceId = DeviceIdentity.get(context);
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordDeviceIdentity(!isBlank(deviceId));
                AuthDebug.recordStage(
                        isBlank(deviceId)
                                ? "DEVICE IDENTITY UNAVAILABLE"
                                : "DEVICE IDENTITY EVALUATED"
                );
            }
            if (isBlank(deviceId)) {
                if (AuthDebugConfig.ENABLED) {
                    AuthDebug.recordApplicationStatus("DEVICE_REJECTED");
                    AuthDebug.recordServerMessage("Device identity is unavailable");
                }
                callback.onError(new AuthResult(
                        AuthResult.Status.ERROR,
                        "Device identity is unavailable"
                ));
                return;
            }

            AuthorizationClient client = new AuthorizationClient(
                    config.getBackendUrl()
            );
            AUTH_EXECUTOR.execute(() -> authorizeSession(
                    sessionManager,
                    config.getAppKey(),
                    sessionManager.getTokenManager().getIdToken(),
                    deviceId,
                    client,
                    callback
            ));
        } catch (RuntimeException exception) {
            callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Unable to prepare authorization request"
            ));
        }
    }

    public static void signIn(
            Activity activity,
            AuthConfig config,
            String email,
            String password,
            AuthenticationCallback callback
    ) {
        if (callback == null) {
            return;
        }
        if (activity == null || activity.isFinishing()) {
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.INVALID_REQUEST
            );
            return;
        }
        if (config == null
                || isBlank(config.getAppId())
                || isBlank(config.getFirebaseApiKey())) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("CONFIGURATION_ERROR");
                AuthDebug.recordStage("FIREBASE SIGN-IN BLOCKED");
            }
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.CONFIGURATION_ERROR
            );
            return;
        }
        if (isBlank(email) || isBlank(password)) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("INVALID_REQUEST");
                AuthDebug.recordStage("FIREBASE SIGN-IN BLOCKED");
            }
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.INVALID_REQUEST
            );
            return;
        }

        if (AuthDebugConfig.ENABLED) {
            AuthDebug.recordAttemptStarted(formatTimestamp(System.currentTimeMillis()));
            AuthDebug.recordStage("FIREBASE SIGN-IN STARTED");
        }
        Context context = activity.getApplicationContext();
        try {
            SessionManager sessionManager = new SessionManager(
                    context != null ? context : activity,
                    config.getAppId()
            );
            FirebaseAuthClient authClient = new FirebaseAuthClient(
                    config.getFirebaseApiKey()
            );

            AUTH_EXECUTOR.execute(() -> authenticate(
                    sessionManager,
                    authClient,
                    email,
                    password,
                    callback
            ));
        } catch (RuntimeException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("SESSION_ERROR");
                AuthDebug.recordStage("FIREBASE SIGN-IN FAILED");
            }
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.SESSION_ERROR
            );
        }
    }

    private static void authorizeSession(
            SessionManager sessionManager,
            String appKey,
            String idToken,
            String deviceId,
            AuthorizationClient authorizationClient,
            AuthCallback callback
    ) {
        if (AuthDebugConfig.ENABLED) {
            AuthDebug.recordRequestStart();
            AuthDebug.recordStage("AUTHORIZATION REQUEST STARTED");
        }
        long startedAtMillis = android.os.SystemClock.elapsedRealtime();

        try {
            AuthorizationClient.AuthorizationResponse response =
                    authorizationClient.authorize(appKey, idToken, deviceId);

            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordHttpResponse(
                        HTTP_OK,
                        true,
                        android.os.SystemClock.elapsedRealtime() - startedAtMillis
                );
                AuthDebug.recordStage("HTTP RESPONSE RECEIVED");
                AuthDebug.recordApplicationStatus(response.getStatus());
                AuthDebug.recordServerMessage(response.getMessage());
                AuthDebug.recordStage("RESPONSE PARSED");
            }

            if (response.isAuthorized()) {
                sessionManager.markAuthorized();
                if (AuthDebugConfig.ENABLED) {
                    AuthDebug.recordOutcome("AUTHORIZED", sessionManager.getState());
                }
                MAIN_HANDLER.post(() -> callback.onAuthorized(new AuthResult(
                        AuthResult.Status.AUTHORIZED,
                        response.getMessage()
                )));
                return;
            }

            sessionManager.markAccessDenied();
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordOutcome("ACCESS_DENIED", sessionManager.getState());
            }
            MAIN_HANDLER.post(() -> callback.onDenied(new AuthResult(
                    AuthResult.Status.DENIED,
                    response.getMessage()
            )));
        } catch (AuthorizationClient.AuthorizationException exception) {
            if (AuthDebugConfig.ENABLED) {
                if (exception.getHttpStatus() > 0) {
                    AuthDebug.recordHttpResponse(
                            exception.getHttpStatus(),
                            !exception.isBodyUnavailable(),
                            android.os.SystemClock.elapsedRealtime() - startedAtMillis
                    );
                    AuthDebug.recordStage("HTTP RESPONSE RECEIVED");
                }
                AuthDebug.recordApplicationStatus(exception.getStatus());
                AuthDebug.recordServerMessage(exception.getMessage());
                if (exception.isParsingError()) {
                    AuthDebug.recordParsingError("INVALID_RESPONSE");
                } else if (exception.getHttpStatus() <= 0) {
                    AuthDebug.recordRequestFailure("AUTHORIZATION_REQUEST_INVALID");
                } else if (!exception.isAccessDenied()
                        && !STATUS_DEVICE_REJECTED.equals(exception.getStatus())) {
                    AuthDebug.recordResponseError(
                            exception.getHttpStatus() == HTTP_OK
                                    ? "INVALID_AUTHORIZATION_RESPONSE"
                                    : "HTTP_AUTHORIZATION_ERROR"
                    );
                } else {
                    AuthDebug.recordStage("RESPONSE PARSED");
                }
            }

            if (STATUS_DEVICE_REJECTED.equals(exception.getStatus())) {
                try {
                    sessionManager.markDeviceRejected();
                } catch (RuntimeException ignored) {
                    // The session may already be in a terminal state.
                }

                if (AuthDebugConfig.ENABLED) {
                    AuthDebug.recordOutcome("DEVICE_REJECTED", sessionManager.getState());
                }

                MAIN_HANDLER.post(() -> callback.onDenied(new AuthResult(
                        AuthResult.Status.DEVICE_REJECTED,
                        exception.getMessage()
                )));
                return;
            }

            if (exception.isAccessDenied()) {
                try {
                    sessionManager.markAccessDenied();
                } catch (RuntimeException ignored) {
                    // The session may already be in a terminal state.
                }

                if (AuthDebugConfig.ENABLED) {
                    AuthDebug.recordOutcome("ACCESS_DENIED", sessionManager.getState());
                }

                MAIN_HANDLER.post(() -> callback.onDenied(new AuthResult(
                        AuthResult.Status.DENIED,
                        exception.getMessage()
                )));
                return;
            }

            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordOutcome("AUTHORIZATION ERROR", sessionManager.getState());
            }

            MAIN_HANDLER.post(() -> callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    exception.getMessage()
            )));
        } catch (IOException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordNetworkError("IO_EXCEPTION");
                AuthDebug.recordStage("NETWORK ERROR");
            }
            MAIN_HANDLER.post(() -> callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Authorization network request failed"
            )));
        } catch (RuntimeException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordRequestFailure("RUNTIME_EXCEPTION");
                AuthDebug.recordStage("AUTHORIZATION FAILED");
            }
            MAIN_HANDLER.post(() -> callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Authorization session failed"
            )));
        }
    }

    private static void authenticate(
            SessionManager sessionManager,
            FirebaseAuthClient authClient,
            String email,
            String password,
            AuthenticationCallback callback
    ) {
        try {
            sessionManager.beginAuthentication();
        } catch (RuntimeException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("SESSION_ERROR");
                AuthDebug.recordStage("FIREBASE SESSION UPDATE FAILED");
            }
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.SESSION_ERROR
            );
            return;
        }

        FirebaseAuthClient.AuthResponse response;
        try {
            response = authClient.signIn(email, password);
        } catch (FirebaseAuthClient.AuthenticationException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordStage("FIREBASE SIGN-IN REJECTED");
                AuthDebug.recordApplicationStatus(
                        exception.getErrorCode() != null
                                ? exception.getErrorCode().name()
                                : null
                );
            }
            postFailureAfterSessionUpdate(
                    sessionManager,
                    callback,
                    exception.getErrorCode()
            );
            return;
        } catch (IOException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordNetworkError("FIREBASE_IO_EXCEPTION");
                AuthDebug.recordStage("FIREBASE NETWORK ERROR");
            }
            try {
                sessionManager.markNetworkError();
                MAIN_HANDLER.post(callback::onNetworkError);
            } catch (RuntimeException sessionException) {
                postAuthenticationFailure(
                        callback,
                        AuthenticationCallback.ErrorCode.SESSION_ERROR
                );
            }
            return;
        } catch (RuntimeException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("SESSION_ERROR");
                AuthDebug.recordStage("FIREBASE SIGN-IN FAILED");
            }
            postFailureAfterSessionUpdate(
                    sessionManager,
                    callback,
                    AuthenticationCallback.ErrorCode.SESSION_ERROR
            );
            return;
        }

        try {
            sessionManager.completeAuthentication(
                    response.getUserId(),
                    response.getIdToken(),
                    response.getRefreshToken(),
                    response.getExpiresAtMillis()
            );
            AuthState state = sessionManager.getState();
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordFirebaseSession(true, response.getUserId());
                AuthDebug.recordIdToken(true, true);
                AuthDebug.recordStage("ID TOKEN OBTAINED");
            }
            MAIN_HANDLER.post(() -> callback.onAuthenticated(state));
        } catch (RuntimeException exception) {
            if (AuthDebugConfig.ENABLED) {
                AuthDebug.recordApplicationStatus("SESSION_ERROR");
                AuthDebug.recordStage("FIREBASE SESSION UPDATE FAILED");
            }
            postFailureAfterSessionUpdate(
                    sessionManager,
                    callback,
                    AuthenticationCallback.ErrorCode.SESSION_ERROR
            );
        }
    }

    private static void postFailureAfterSessionUpdate(
            SessionManager sessionManager,
            AuthenticationCallback callback,
            AuthenticationCallback.ErrorCode errorCode
    ) {
        AuthenticationCallback.ErrorCode resultCode = errorCode;
        try {
            sessionManager.failAuthentication();
        } catch (RuntimeException exception) {
            resultCode = AuthenticationCallback.ErrorCode.SESSION_ERROR;
        }
        postAuthenticationFailure(callback, resultCode);
    }

    private static void postAuthenticationFailure(
            AuthenticationCallback callback,
            AuthenticationCallback.ErrorCode errorCode
    ) {
        MAIN_HANDLER.post(() -> callback.onAuthenticationFailed(errorCode));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** TEMPORARY DEBUG — wall-clock time for the debug snapshot only. */
    private static String formatTimestamp(long millis) {
        synchronized (DEBUG_TIMESTAMP_FORMAT) {
            return DEBUG_TIMESTAMP_FORMAT.format(new java.util.Date(millis));
        }
    }
}