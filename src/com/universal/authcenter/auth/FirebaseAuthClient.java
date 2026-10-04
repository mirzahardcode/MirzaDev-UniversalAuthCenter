package com.universal.authcenter.auth;

import com.universal.authcenter.AuthenticationCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

public final class FirebaseAuthClient {

    private static final String ENDPOINT =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final int TIMEOUT_MILLIS = 15000;

    private final String apiKey;

    public FirebaseAuthClient(String apiKey) {
        if (isBlank(apiKey)) {
            throw new IllegalArgumentException("Firebase API key must not be blank");
        }
        this.apiKey = apiKey;
    }

    public AuthResponse signIn(String email, String password)
            throws IOException, AuthenticationException {
        if (isBlank(email) || isBlank(password)) {
            throw new AuthenticationException(
                    AuthenticationCallback.ErrorCode.INVALID_REQUEST
            );
        }

        String requestBody;
        try {
            requestBody = new JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put("returnSecureToken", true)
                    .toString();
        } catch (JSONException exception) {
            throw new AuthenticationException(
                    AuthenticationCallback.ErrorCode.INVALID_REQUEST
            );
        }

        HttpURLConnection connection = null;
        try {
            String encodedApiKey = URLEncoder.encode(apiKey, "UTF-8");
            URL url = URI.create(ENDPOINT + encodedApiKey).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] requestBytes = requestBody.getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw readFirebaseError(connection.getErrorStream());
            }

            String responseBody = readResponse(connection.getInputStream());
            return parseResponse(responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static AuthResponse parseResponse(String responseBody)
            throws AuthenticationException {
        try {
            JSONObject response = new JSONObject(responseBody);
            String userId = requiredString(response, "localId");
            String idToken = requiredString(response, "idToken");
            String refreshToken = requiredString(response, "refreshToken");
            String expiresInValue = requiredString(response, "expiresIn");
            long expiresInSeconds = Long.parseLong(expiresInValue);
            long now = System.currentTimeMillis();

            if (expiresInSeconds <= 0
                    || expiresInSeconds > (Long.MAX_VALUE - now) / 1000L) {
                throw new AuthenticationException(
                        AuthenticationCallback.ErrorCode.INVALID_RESPONSE
                );
            }

            return new AuthResponse(
                    userId,
                    idToken,
                    refreshToken,
                    now + expiresInSeconds * 1000L
            );
        } catch (JSONException | NumberFormatException exception) {
            throw new AuthenticationException(
                    AuthenticationCallback.ErrorCode.INVALID_RESPONSE
            );
        }
    }

    private static AuthenticationException readFirebaseError(InputStream errorStream)
            throws IOException {
        if (errorStream == null) {
            return new AuthenticationException(
                    AuthenticationCallback.ErrorCode.HTTP_ERROR
            );
        }

        String errorBody = readResponse(errorStream);
        try {
            JSONObject error = new JSONObject(errorBody)
                    .optJSONObject("error");
            String firebaseCode = error != null
                    ? error.optString("message", "")
                    : "";
            return new AuthenticationException(mapError(firebaseCode));
        } catch (JSONException exception) {
            return new AuthenticationException(
                    AuthenticationCallback.ErrorCode.HTTP_ERROR
            );
        }
    }

    private static AuthenticationCallback.ErrorCode mapError(String firebaseCode) {
        if ("EMAIL_NOT_FOUND".equals(firebaseCode)
                || "INVALID_PASSWORD".equals(firebaseCode)
                || "INVALID_LOGIN_CREDENTIALS".equals(firebaseCode)) {
            return AuthenticationCallback.ErrorCode.INVALID_CREDENTIALS;
        }
        if ("USER_DISABLED".equals(firebaseCode)) {
            return AuthenticationCallback.ErrorCode.ACCOUNT_DISABLED;
        }
        if ("OPERATION_NOT_ALLOWED".equals(firebaseCode)) {
            return AuthenticationCallback.ErrorCode.SIGN_IN_DISABLED;
        }
        if ("TOO_MANY_ATTEMPTS_TRY_LATER".equals(firebaseCode)) {
            return AuthenticationCallback.ErrorCode.RATE_LIMITED;
        }
        return AuthenticationCallback.ErrorCode.HTTP_ERROR;
    }

    private static String requiredString(JSONObject object, String key)
            throws JSONException, AuthenticationException {
        Object value = object.get(key);
        if (!(value instanceof String) || isBlank((String) value)) {
            throw new AuthenticationException(
                    AuthenticationCallback.ErrorCode.INVALID_RESPONSE
            );
        }
        return (String) value;
    }

    private static String readResponse(InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream;
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toString("UTF-8");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class AuthResponse {

        private final String userId;
        private final String idToken;
        private final String refreshToken;
        private final long expiresAtMillis;

        private AuthResponse(
                String userId,
                String idToken,
                String refreshToken,
                long expiresAtMillis
        ) {
            this.userId = userId;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String getUserId() {
            return userId;
        }

        public String getIdToken() {
            return idToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }
    }

    public static final class AuthenticationException extends Exception {

        private final AuthenticationCallback.ErrorCode errorCode;

        private AuthenticationException(AuthenticationCallback.ErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        public AuthenticationCallback.ErrorCode getErrorCode() {
            return errorCode;
        }
    }
}