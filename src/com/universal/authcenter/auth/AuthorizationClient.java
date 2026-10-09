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
    private static final String DEVICE_REJECTED = "DEVICE_REJECTED";

    private final String backendUrl;

    public AuthorizationClient(String backendUrl) {
        if (isBlank(backendUrl)) {
            throw new IllegalArgumentException("Backend URL must not be blank");
        }

        this.backendUrl = backendUrl.endsWith("/")
                ? backendUrl.substring(0, backendUrl.length() - 1)
                : backendUrl;
    }

    public AuthorizationResponse authorize(
            String appKey,
            String idToken,
            String deviceId
    ) throws IOException, AuthorizationException {
        if (isBlank(appKey)) {
            throw new AuthorizationException(false, null, "Application key is required");
        }
        if (isBlank(idToken)) {
            throw new AuthorizationException(false, null, "Firebase ID token is required");
        }
        if (isBlank(deviceId)) {
            throw new AuthorizationException(
                    false,
                    "DEVICE_REJECTED",
                    "Device identity is unavailable"
            );
        }

        String requestBody;
        try {
            requestBody = new JSONObject()
                    .put("appKey", appKey)
                    .put("deviceId", deviceId)
                    .toString();
        } catch (JSONException exception) {
            throw new AuthorizationException(
                    false,
                    null,
                    "Invalid authorization request"
            );
        }

        HttpURLConnection connection = null;
        String payload = null;
        boolean bodyAvailable = false;
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
                String successBody = readResponse(connection.getInputStream());
                return parseResponse(successBody, responseCode);
            }

            payload = readResponse(connection.getErrorStream());
            bodyAvailable = !isBlank(payload);

            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                String status = extractStatus(payload);
                if (DEVICE_REJECTED.equals(status)) {
                    throw new AuthorizationException(
                            true,
                            status,
                            extractMessage(payload, "Device is rejected"),
                            responseCode,
                            bodyAvailable,
                            false
                    );
                }

                throw new AuthorizationException(
                        true,
                        status,
                        extractMessage(payload, "Access denied"),
                        responseCode,
                        bodyAvailable,
                        false
                );
            }

            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new AuthorizationException(
                        false,
                        extractStatus(payload),
                        extractMessage(payload, "Authentication is required"),
                        responseCode,
                        bodyAvailable,
                        false
                );
            }

            throw new AuthorizationException(
                    false,
                    extractStatus(payload),
                    extractMessage(payload, "Authorization request failed"),
                    responseCode,
                    bodyAvailable,
                    false
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static AuthorizationResponse parseResponse(
            String responseBody,
            int httpStatus
    ) throws AuthorizationException {
        try {
            JSONObject response = new JSONObject(responseBody);
            String status = response.optString("status", "");
            if (!"AUTHORIZED".equals(status)) {
                throw new AuthorizationException(
                        false,
                        status,
                        "Invalid authorization response",
                        httpStatus,
                        true,
                        false
                );
            }
            String message = response.optString(
                    "message",
                    "Access granted"
            );

            if (isBlank(message)) {
                message = "Access granted";
            }

            return new AuthorizationResponse(true, status, message);
        } catch (JSONException exception) {
            throw new AuthorizationException(
                    false,
                    null,
                    "Invalid authorization response",
                    httpStatus,
                    true,
                    true
            );
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

    private static String extractStatus(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }

        try {
            JSONObject response = new JSONObject(responseBody);
            String status = response.optString("status", "");
            return isBlank(status) ? null : status;
        } catch (JSONException ignored) {
            return null;
        }
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
        private final String status;
        private final String uid;
        private final String message;

        public AuthorizationResponse(boolean authorized, String uid, String message) {
            this(authorized, null, uid, message);
        }

        public AuthorizationResponse(
                boolean authorized,
                String status,
                String uid,
                String message
        ) {
            this.authorized = authorized;
            this.status = status;
            this.uid = uid;
            this.message = message;
        }

        public boolean isAuthorized() {
            return authorized;
        }

        /** @return the backend application status, or {@code null} when unavailable */
        public String getStatus() {
            return status;
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
        private final String status;
        private final int httpStatus;
        private final boolean bodyUnavailable;
        private final boolean parsingError;

        public AuthorizationException(boolean accessDenied, String message) {
            this(accessDenied, null, message);
        }

        public AuthorizationException(
                boolean accessDenied,
                String status,
                String message
        ) {
            this(accessDenied, status, message, 0, true, false);
        }

        public AuthorizationException(
                boolean accessDenied,
                String status,
                String message,
                int httpStatus,
                boolean bodyAvailable,
                boolean parsingError
        ) {
            super(message);
            this.accessDenied = accessDenied;
            this.status = status;
            this.httpStatus = httpStatus;
            this.bodyUnavailable = !bodyAvailable;
            this.parsingError = parsingError;
        }

        public boolean isAccessDenied() {
            return accessDenied;
        }

        /** @return the backend status, or {@code null} when unavailable */
        public String getStatus() {
            return status;
        }

        /** @return the HTTP status code, or {@code 0} when the call never completed */
        public int getHttpStatus() {
            return httpStatus;
        }

        /** @return {@code true} when no response body was available to read */
        public boolean isBodyUnavailable() {
            return bodyUnavailable;
        }

        /** @return {@code true} when the response body could not be parsed */
        public boolean isParsingError() {
            return parsingError;
        }
    }
}
