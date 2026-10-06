package com.universal.authcenter;

public final class AuthResult {

    public enum Status {
        AUTHORIZED,
        DENIED,
        ERROR,
        DEVICE_REJECTED
    }

    private final Status status;
    private final String message;

    public AuthResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAuthorized() {
        return status == Status.AUTHORIZED;
    }
}