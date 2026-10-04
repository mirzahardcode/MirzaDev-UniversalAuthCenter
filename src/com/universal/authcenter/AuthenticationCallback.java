package com.universal.authcenter;

import com.universal.authcenter.auth.AuthState;

public interface AuthenticationCallback {

    enum ErrorCode {
        INVALID_CREDENTIALS,
        ACCOUNT_DISABLED,
        SIGN_IN_DISABLED,
        RATE_LIMITED,
        HTTP_ERROR,
        INVALID_RESPONSE,
        INVALID_REQUEST,
        CONFIGURATION_ERROR,
        SESSION_ERROR
    }

    void onAuthenticated(AuthState state);

    void onAuthenticationFailed(ErrorCode errorCode);

    void onNetworkError();
}