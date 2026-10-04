package com.universal.authcenter.auth;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class AuthorizationClient {

    private static final int TIMEOUT_MILLIS = 15000;

    private final String backendUrl;

    public AuthorizationClient(String backendUrl) {
        if (isBlank(backendUrl)) {
            throw new IllegalArgumentException("Backend URL must not be blank");
        }

        this.backendUrl = backendUrl.endsWith("/")
                ? backendUrl.substring(0, backendUrl.length() - 1)
                : backendUrl;
    }

    public AuthorizationResponse authorize(String appKey, String idToken)
            throws IOException, AuthorizationException {
        if (isBlank(appKey)) {
            throw new AuthorizationException(false, "Application key is required");
        }
        if (isBlank(idToken)) {
            throw new AuthorizationException(false, "Firebase ID token is required");
        }

        String requestBody;
        try {
            requestBody = new JSONObject()
                    .put("appKey", appKey)
                    .toString();
        } catch (JSONException exception) {
            throw new AuthorizationException(false, "Invalid authorization request");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(backendUrl + "/api/auth/authorize");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + idToken);

            byte[] requestBytes = requestBody.getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return parseResponse(readResponse(connection.getInputStream()));
            }

            String payload = readResponse(connection.getErrorStream());
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                    || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new AuthorizationException(
                        false,
                        extractMessage(payload, "Access denied")
                );
            }

            throw new AuthorizationException(
                    true,
                    extractMessage(payload, "Authorization request failed")
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static AuthorizationResponse parseResponse(String responseBody)
            throws AuthorizationException {
        try {
            JSONObject response = new JSONObject(responseBody);
            String status = response.optString("status", "");
            if (!"AUTHORIZED".equals(status)) {
                throw new AuthorizationException(false, "Invalid authorization response");
            }
            String message = response.optString(
                    "message",
                    "Access granted"
            );

            if (isBlank(message)) {
                message = "Access granted";
            }

            return new AuthorizationResponse(true, null, message);
        } catch (JSONException exception) {
            throw new AuthorizationException(true, "Invalid authorization response");
        }
    }

    private static String extractMessage(String responseBody, String fallback) {
        if (isBlank(responseBody)) {
            return fallback;
        }

        try {
            JSONObject response = new JSONObject(responseBody);
            String message = response.optString("message", "");
            if (!isBlank(message)) {
                return message;
            }
            String error = response.optString("error", "");
            if (!isBlank(error)) {
                return error;
            }
        } catch (JSONException ignored) {
            // Fallback to the raw payload when the backend is not returning JSON.
        }

        return responseBody;
    }

    private static String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

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

    public static final class AuthorizationResponse {

        private final boolean authorized;
        private final String uid;
        private final String message;

        public AuthorizationResponse(boolean authorized, String uid, String message) {
            this.authorized = authorized;
            this.uid = uid;
            this.message = message;
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public String getUid() {
            return uid;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class AuthorizationException extends Exception {

        private final boolean accessDenied;

        public AuthorizationException(boolean accessDenied, String message) {
            super(message);
            this.accessDenied = accessDenied;
        }

        public boolean isAccessDenied() {
            return accessDenied;
        }
    }
}
