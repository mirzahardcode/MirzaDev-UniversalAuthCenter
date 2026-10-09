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
        header.setText("AUTH DEBUG — TEMPORARY");
        header.setTextSize(18f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(COLOR_ACCENT);
        root.addView(header, matchWrap());

        TextView subheader = new TextView(getContext());
        subheader.setText("Sanitized snapshot — no tokens, UID, device ID, or passwords are shown.");
        subheader.setTextSize(12f);
        subheader.setTextColor(COLOR_LABEL);
        subheader.setPadding(0, dpToPx(4), 0, dpToPx(12));
        root.addView(subheader, matchWrap());

        Button refreshButton = new Button(getContext());
        refreshButton.setText("Refresh");
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
        closeButton.setText("Close");
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

        addRow(container, "Backend host", snapshot.getBackendHost(), false);
        addRow(container, "Package name", snapshot.getPackageName(), false);
        addRow(container, "App key", snapshot.getAppKeyMasked(), false);
        addRow(container, "Config status", snapshot.getConfigStatus(), false);
        addRow(container, "Firebase session", snapshot.getFirebaseSession(), false);
        addRow(container, "UID (masked)", snapshot.getUidMasked(), false);
        addRow(container, "ID token", snapshot.getIdToken(), false);
        addRow(container, "Device identity", snapshot.getDeviceIdentity(), false);
        addRow(container, "Last attempt", snapshot.getLastAttemptTime(), false);
        addRow(container, "Last completed stage", snapshot.getLastCompletedStage(), false);
        addRow(container, "Request status", snapshot.getRequestStatus(), false);
        addRow(container, "HTTP status", snapshot.getHttpStatus(), false);
        addRow(container, "Application status", snapshot.getApplicationStatus(), false);
        addRow(container, "Server message", snapshot.getErrorMessage(), false);
        addRow(container, "Response time", snapshot.getResponseTime(), false);
        addRow(
                container,
                "Exception category",
                snapshot.getExceptionCategory(),
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
        valueView.setText(value == null || value.isEmpty() ? AuthDebugSnapshot.UNKNOWN : value);
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
