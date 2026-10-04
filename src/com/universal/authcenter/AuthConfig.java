package com.universal.authcenter;

import android.app.Activity;

public final class AuthConfig {

    private final String appId;
    private final String appName;
    private final String backendUrl;
    private final String firebaseApiKey;
    private final String appKey;

    public AuthConfig(String appId, String appName, String backendUrl) {
        this(appId, appName, backendUrl, null, null);
    }

    public AuthConfig(
            String appId,
            String appName,
            String backendUrl,
            String firebaseApiKey
    ) {
        this(appId, appName, backendUrl, firebaseApiKey, null);
    }

    public AuthConfig(
            String appId,
            String appName,
            String backendUrl,
            String firebaseApiKey,
            String appKey
    ) {
        this.appId = appId;
        this.appName = appName;
        this.backendUrl = backendUrl;
        this.firebaseApiKey = firebaseApiKey;
        this.appKey = appKey;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppName() {
        return appName;
    }

    public String getBackendUrl() {
        return backendUrl;
    }

    public String getFirebaseApiKey() {
        return firebaseApiKey;
    }

    public String getAppKey() {
        return appKey;
    }

    public static AuthConfig fromActivity(Activity activity) {
        return fromActivity(activity, null, null);
    }

    public static AuthConfig fromActivity(Activity activity, String firebaseApiKey) {
        return fromActivity(activity, firebaseApiKey, null);
    }

    public static AuthConfig fromActivity(
            Activity activity,
            String firebaseApiKey,
            String appKey
    ) {
        String packageName = activity.getPackageName();
        String appName = packageName;

        try {
            CharSequence label = activity.getApplicationInfo()
                    .loadLabel(activity.getPackageManager());

            if (label != null) {
                appName = label.toString();
            }
        } catch (Exception ignored) {
        }

        return new AuthConfig(
                packageName,
                appName,
                "https://mirzadev-universalauthcenter.vercel.app/",
                firebaseApiKey,
                appKey
        );
    }
}