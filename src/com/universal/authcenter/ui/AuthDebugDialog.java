package com.universal.authcenter.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import com.universal.authcenter.AuthDebug;
import com.universal.authcenter.AuthDebugSnapshot;

/**
 * TEMPORARY DEBUG — read-only "AUTH DEBUG" overlay showing the sanitized
 * snapshot of the last authentication attempt.
 *
 * <p>The panel never feeds data back into the login flow: it can be opened
 * before or after a sign-in attempt and closed without cancelling anything.
 * It refreshes on open, on demand (Refresh), and whenever
 * {@link AuthDebug} publishes a new snapshot while it is visible.
 */
public final class AuthDebugDialog extends Dialog implements AuthDebug.Listener {

    private static final int COLOR_BACKGROUND = Color.parseColor("#111418");
    private static final int COLOR_PANEL = Color.parseColor("#1B2027");
    private static final int COLOR_BORDER = Color.parseColor("#2E3742");
    private static final int COLOR_LABEL = Color.parseColor("#7F8B99");
    private static final int COLOR_VALUE = Color.parseColor("#E6EAF0");
    private static final int COLOR_VALUE_ERROR = Color.parseColor("#FF6B6B");
    private static final int COLOR_ACCENT = Color.parseColor("#4FC3F7");

    private final WeakReference<Activity> activityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinearLayout rowsContainer;

    private boolean cleanedUp;

    public AuthDebugDialog(Activity activity) {
        super(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        this.activityRef = new WeakReference<Activity>(activity);
        this.rowsContainer = new LinearLayout(activity);
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                cleanup();
            }
        });
    }

    private Activity getActivity() {
        return activityRef != null ? activityRef.get() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setCancelable(true);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new ColorDrawable(COLOR_BACKGROUND));
        }
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AuthDebug.registerListener(this);
        refresh();
    }

    @Override
    protected void onStop() {
        AuthDebug.unregisterListener(this);
        super.onStop();
    }

    @Override
    public void show() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) {
            return;
        }
        if (cleanedUp || isShowing()) {
            return;
        }
        super.show();
    }

    @Override
    public void onDebugSnapshot(AuthDebugSnapshot snapshot) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!cleanedUp && isShowing()) {
                    render(rowsContainer, AuthDebug.getSnapshot());
                }
            }
        });
    }

    private void refresh() {
        render(rowsContainer, AuthDebug.getSnapshot());
    }

    private void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        AuthDebug.unregisterListener(this);
        if (rowsContainer != null) {
            rowsContainer.removeAllViews();
        }
        activityRef.clear();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(24));

        TextView header = new TextView(getContext());
        header.setText("DEBUG AUTENTIKASI");
        header.setTextSize(18f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(COLOR_ACCENT);
        root.addView(header, matchWrap());

        TextView subheader = new TextView(getContext());
        subheader.setText("Cuplikan data tersanitasi — nilai token, UID lengkap, ID perangkat, dan kata sandi tidak ditampilkan.");
        subheader.setTextSize(12f);
        subheader.setTextColor(COLOR_LABEL);
        subheader.setPadding(0, dpToPx(4), 0, dpToPx(12));
        root.addView(subheader, matchWrap());

        Button refreshButton = new Button(getContext());
        refreshButton.setText("Muat ulang");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refresh();
            }
        });
        root.addView(refreshButton, wrapWrap());

        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setPadding(0, dpToPx(12), 0, 0);
        root.addView(rowsContainer, matchWrap());

        Button closeButton = new Button(getContext());
        closeButton.setText("Tutup");
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        LinearLayout.LayoutParams closeParams = wrapWrap();
        closeParams.topMargin = dpToPx(16);
        root.addView(closeButton, closeParams);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void render(LinearLayout container, AuthDebugSnapshot snapshot) {
        container.removeAllViews();

        addRow(container, "Host backend", snapshot.getBackendHost(), false);
        addRow(container, "Nama paket", snapshot.getPackageName(), false);
        addRow(container, "Kunci aplikasi", snapshot.getAppKeyMasked(), false);
        addRow(container, "Status konfigurasi", snapshot.getConfigStatus(), false);
        addRow(container, "Sesi Firebase", snapshot.getFirebaseSession(), false);
        addRow(container, "UID (disamarkan)", snapshot.getUidMasked(), false);
        addRow(container, "Token ID", snapshot.getIdToken(), false);
        addRow(container, "Identitas perangkat", snapshot.getDeviceIdentity(), false);
        addRow(container, "Waktu percobaan terakhir", snapshot.getLastAttemptTime(), false);
        addRow(container, "Tahap terakhir yang selesai", snapshot.getLastCompletedStage(), false);
        addRow(container, "Status permintaan", snapshot.getRequestStatus(), false);
        addRow(container, "Status HTTP", snapshot.getHttpStatus(), false);
        addRow(container, "Status aplikasi", snapshot.getApplicationStatus(), false);
        addRow(container, "Pesan server", snapshot.getErrorMessage(), false);
        addRow(container, "Waktu respons", snapshot.getResponseTime(), false);
        addRow(
                container,
                "Kategori pengecualian",
                localizedValue(snapshot.getExceptionCategory()),
                !AuthDebugSnapshot.NOT_AVAILABLE.equals(snapshot.getExceptionCategory())
        );
    }

    private void addRow(LinearLayout container, String label, String value, boolean isError) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(4), 0, dpToPx(8));

        TextView labelView = new TextView(getContext());
        labelView.setText(label.toUpperCase());
        labelView.setTextSize(11f);
        labelView.setTextColor(COLOR_LABEL);
        row.addView(labelView, matchWrap());

        TextView valueView = new TextView(getContext());
        valueView.setText(localizedValue(
                value == null || value.isEmpty() ? AuthDebugSnapshot.UNKNOWN : value
        ));
        valueView.setTextSize(14f);
        valueView.setTextColor(isError ? COLOR_VALUE_ERROR : COLOR_VALUE);
        row.addView(valueView, matchWrap());

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_PANEL);
        card.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        View divider = new View(getContext());
        divider.setBackgroundColor(COLOR_BORDER);
        divider.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        ));

        card.addView(row);
        container.addView(card, matchWrap());
        container.addView(divider, matchWrap());
    }

    private String localizedValue(String value) {
        if (value == null) {
            return "BELUM DIKETAHUI";
        }
        switch (value) {
            case AuthDebugSnapshot.UNKNOWN:
                return "BELUM DIKETAHUI";
            case AuthDebugSnapshot.NOT_AVAILABLE:
                return "TIDAK TERSEDIA";
            case "NOT CONFIGURED":
                return "BELUM DIKONFIGURASI";
            case "NOT STARTED":
                return "BELUM DIMULAI";
            case "IN PROGRESS":
                return "SEDANG BERLANGSUNG";
            case "COMPLETED":
                return "SELESAI";
            case "FAILED":
                return "GAGAL";
            case "NETWORK ERROR":
                return "KESALAHAN JARINGAN";
            case "LOADED":
                return "DIMUAT";
            case "authenticated":
                return "terautentikasi";
            case "not authenticated":
                return "belum terautentikasi";
            case "missing":
                return "tidak ada";
            case "present (valid)":
                return "tersedia (valid)";
            case "present (expired)":
                return "tersedia (kedaluwarsa)";
            case "NOT EVALUATED":
                return "BELUM DIPERIKSA";
            case "valid":
                return "valid";
            case "unavailable":
                return "tidak tersedia";
            case "HTTP_ERROR_BODY_UNAVAILABLE":
                return "KESALAHAN HTTP (ISI RESPONS TIDAK TERSEDIA)";
            case "CONFIG FAILED":
                return "KONFIGURASI GAGAL";
            case "CONFIG LOADED":
                return "KONFIGURASI DIMUAT";
            case "FIREBASE SESSION LOADED":
                return "SESI FIREBASE DIMUAT";
            case "AUTHORIZATION BLOCKED: AUTHENTICATION REQUIRED":
                return "OTORISASI DIBLOKIR: AUTENTIKASI DIPERLUKAN";
            case "ID TOKEN OBTAINED":
                return "TOKEN ID DIPEROLEH";
            case "DEVICE IDENTITY UNAVAILABLE":
                return "IDENTITAS PERANGKAT TIDAK TERSEDIA";
            case "DEVICE IDENTITY EVALUATED":
                return "IDENTITAS PERANGKAT DIPERIKSA";
            case "FIREBASE SIGN-IN BLOCKED":
                return "PROSES MASUK FIREBASE DIBLOKIR";
            case "FIREBASE SIGN-IN STARTED":
                return "PROSES MASUK FIREBASE DIMULAI";
            case "FIREBASE SIGN-IN FAILED":
                return "PROSES MASUK FIREBASE GAGAL";
            case "AUTHORIZATION REQUEST STARTED":
                return "PERMINTAAN OTORISASI DIMULAI";
            case "HTTP RESPONSE RECEIVED":
                return "RESPONS HTTP DITERIMA";
            case "RESPONSE PARSED":
                return "RESPONS DIURAI";
            case "AUTHORIZATION FAILED":
                return "OTORISASI GAGAL";
            case "FIREBASE SESSION UPDATE FAILED":
                return "PEMBARUAN SESI FIREBASE GAGAL";
            case "FIREBASE SIGN-IN REJECTED":
                return "PROSES MASUK FIREBASE DITOLAK";
            case "FIREBASE NETWORK ERROR":
                return "KESALAHAN JARINGAN FIREBASE";
            case "AUTHORIZED":
                return "DIOTORISASI";
            case "ACCESS_DENIED":
                return "AKSES DITOLAK";
            case "DEVICE_REJECTED":
                return "PERANGKAT DITOLAK";
            case "USER_DISABLED":
                return "PENGGUNA DINONAKTIFKAN";
            case "USER_EXPIRED":
                return "MASA BERLAKU PENGGUNA HABIS";
            case "UNKNOWN_APP":
                return "APLIKASI TIDAK DIKENAL";
            case "BAD_REQUEST":
                return "PERMINTAAN TIDAK VALID";
            case "UNAUTHENTICATED":
                return "BELUM TERAUTENTIKASI";
            case "INVALID_TOKEN":
                return "TOKEN TIDAK VALID";
            case "AUTHORIZATION_UNAVAILABLE":
                return "LAYANAN OTORISASI TIDAK TERSEDIA";
            case "INTERNAL_ERROR":
                return "KESALAHAN INTERNAL";
            case "METHOD_NOT_ALLOWED":
                return "METODE TIDAK DIIZINKAN";
            case "AUTHENTICATION_REQUIRED":
                return "AUTENTIKASI DIPERLUKAN";
            case "INVALID_CREDENTIALS":
                return "KREDENSIAL TIDAK VALID";
            case "ACCOUNT_DISABLED":
                return "AKUN DINONAKTIFKAN";
            case "SIGN_IN_DISABLED":
                return "PROSES MASUK DINONAKTIFKAN";
            case "RATE_LIMITED":
                return "BATAS PERMINTAAN TERCAPAI";
            case "HTTP_ERROR":
                return "KESALAHAN HTTP";
            case "INVALID_RESPONSE":
                return "RESPONS TIDAK VALID";
            case "INVALID_REQUEST":
                return "PERMINTAAN TIDAK VALID";
            case "CONFIGURATION_ERROR":
                return "KESALAHAN KONFIGURASI";
            case "SESSION_ERROR":
                return "KESALAHAN SESI";
            case "Access granted":
                return "Akses diberikan";
            case "Access denied":
                return "Akses ditolak";
            case "Unknown application":
                return "Aplikasi tidak dikenal";
            case "Authentication required":
                return "Autentikasi diperlukan";
            case "Invalid or expired token":
                return "Token tidak valid atau kedaluwarsa";
            case "Authorization service unavailable":
                return "Layanan otorisasi tidak tersedia";
            case "Internal server error":
                return "Kesalahan internal server";
            case "Method not allowed":
                return "Metode tidak diizinkan";
            case "A valid appKey is required":
                return "appKey yang valid diperlukan";
            case "A valid deviceId is required":
                return "deviceId yang valid diperlukan";
            case "Device identity is unavailable":
                return "Identitas perangkat tidak tersedia";
            case "UNRECOGNIZED MESSAGE (content suppressed)":
                return "PESAN TIDAK DIKENALI (konten disembunyikan)";
            case "AuthConfig is null":
                return "AuthConfig tidak tersedia";
            case "Invalid authorization configuration":
                return "Konfigurasi otorisasi tidak valid";
            case "FAILED (AuthConfig is null)":
                return "GAGAL (AuthConfig tidak tersedia)";
            case "FAILED (Invalid authorization configuration)":
                return "GAGAL (konfigurasi otorisasi tidak valid)";
            case "FAILED (reason suppressed)":
                return "GAGAL (alasan disembunyikan)";
            default:
                if (value.startsWith("INCOMPLETE (missing ") && value.endsWith(")")) {
                    return localizeMissingConfiguration(value);
                }
                if (value.startsWith("*** (len=") && value.endsWith(")")) {
                    return value.replace("*** (len=", "*** (panjang=");
                }
                return value;
        }
    }

    private String localizeMissingConfiguration(String value) {
        String missing = value.substring("INCOMPLETE (missing ".length(), value.length() - 1);
        String[] keys = missing.split(", ");
        StringBuilder localized = new StringBuilder("TIDAK LENGKAP (kurang: ");
        for (int index = 0; index < keys.length; index++) {
            if (index > 0) {
                localized.append(", ");
            }
            switch (keys[index]) {
                case "backendHost":
                    localized.append("host backend");
                    break;
                case "packageName":
                    localized.append("nama paket");
                    break;
                case "appKey":
                    localized.append("kunci aplikasi");
                    break;
                case "firebaseApiKey":
                    localized.append("kunci API Firebase");
                    break;
                default:
                    localized.append(keys[index]);
                    break;
            }
        }
        return localized.append(')').toString();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
