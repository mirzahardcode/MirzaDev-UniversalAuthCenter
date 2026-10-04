package com.universal.authcenter;

public interface AuthCallback {

    void onAuthorized(AuthResult result);

    void onDenied(AuthResult result);

    void onError(AuthResult result);
}