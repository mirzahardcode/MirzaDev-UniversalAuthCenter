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

        start(
                activity,
                AuthConfig.fromActivity(
                        activity,
                        GeneratedConfig.FIREBASE_API_KEY,
                        GeneratedConfig.APP_KEY
                ),
                null
        );
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

            if (callback != null) {
                callback.onError(result);
            }

            return;
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
        if (config == null
                || isBlank(config.getAppId())
                || isBlank(config.getBackendUrl())
                || isBlank(config.getAppKey())) {
            callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Invalid authorization configuration"
            ));
            return;
        }

        Context context = activity.getApplicationContext();
        try {
            SessionManager sessionManager = new SessionManager(
                    context != null ? context : activity,
                    config.getAppId()
            );
            AuthState state = sessionManager.getState();
            if (!state.hasAuthenticatedSession()
                    || isBlank(sessionManager.getTokenManager().getIdToken())) {
                callback.onDenied(new AuthResult(
                        AuthResult.Status.DENIED,
                        "Authentication required before authorization"
                ));
                return;
            }

            String deviceId = DeviceIdentity.get(context);
            if (isBlank(deviceId)) {
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
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.CONFIGURATION_ERROR
            );
            return;
        }
        if (isBlank(email) || isBlank(password)) {
            postAuthenticationFailure(
                    callback,
                    AuthenticationCallback.ErrorCode.INVALID_REQUEST
            );
            return;
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
        try {
            AuthorizationClient.AuthorizationResponse response =
                    authorizationClient.authorize(appKey, idToken, deviceId);

            if (response.isAuthorized()) {
                sessionManager.markAuthorized();
                MAIN_HANDLER.post(() -> callback.onAuthorized(new AuthResult(
                        AuthResult.Status.AUTHORIZED,
                        response.getMessage()
                )));
                return;
            }

            sessionManager.markAccessDenied();
            MAIN_HANDLER.post(() -> callback.onDenied(new AuthResult(
                    AuthResult.Status.DENIED,
                    response.getMessage()
            )));
        } catch (AuthorizationClient.AuthorizationException exception) {
            if (STATUS_DEVICE_REJECTED.equals(exception.getStatus())) {
                try {
                    sessionManager.markDeviceRejected();
                } catch (RuntimeException ignored) {
                    // The session may already be in a terminal state.
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

                MAIN_HANDLER.post(() -> callback.onDenied(new AuthResult(
                        AuthResult.Status.DENIED,
                        exception.getMessage()
                )));
                return;
            }

            MAIN_HANDLER.post(() -> callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    exception.getMessage()
            )));
        } catch (IOException exception) {
            MAIN_HANDLER.post(() -> callback.onError(new AuthResult(
                    AuthResult.Status.ERROR,
                    "Authorization network request failed"
            )));
        } catch (RuntimeException exception) {
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
            postFailureAfterSessionUpdate(
                    sessionManager,
                    callback,
                    exception.getErrorCode()
            );
            return;
        } catch (IOException exception) {
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
            MAIN_HANDLER.post(() -> callback.onAuthenticated(state));
        } catch (RuntimeException exception) {
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
}