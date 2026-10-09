package com.universal.authcenter.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import com.universal.authcenter.AuthCallback;
import com.universal.authcenter.AuthCenter;
import com.universal.authcenter.AuthCenterVersion;
import com.universal.authcenter.AuthConfig;
import com.universal.authcenter.AuthDebugConfig;
import com.universal.authcenter.AuthResult;
import com.universal.authcenter.AuthenticationCallback;
import com.universal.authcenter.auth.CredentialStore;

public final class LoginDialog extends Dialog {

    private static final String TAG = "UniversalAuthCenter";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final WeakReference<Activity> activityRef;
    private final AuthConfig config;
    private final Typeface mediumTypeface;
    private final Typeface boldTypeface;
    private AuthCallback callback;

    private EditText emailField;
    private EditText passwordField;
    private Button submitButton;
    private TextView statusView;
    private ProgressBar loadingView;
    private AuthDebugDialog authDebugDialog;
    private boolean cleanedUp;

    public LoginDialog(Activity activity, AuthConfig config, AuthCallback callback) {
        super(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        this.activityRef = new WeakReference<Activity>(activity);
        this.config = config;
        this.callback = callback;
        mediumTypeface = loadTypeface(
                "font/sf-pro-medium",
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );
        boldTypeface = loadTypeface(
                "font/sf-pro-bold",
                Typeface.create("sans-serif", Typeface.BOLD)
        );
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                cleanup();
            }
        });
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
    public void dismiss() {
        if (isShowing()) {
            super.dismiss();
        }
        cleanup();
    }

    private Activity getActivity() {
        return activityRef != null ? activityRef.get() : null;
    }

    private boolean isUiSafe() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) {
            return false;
        }
        return isShowing();
    }

    private void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;

        if (submitButton != null) {
            submitButton.setOnClickListener(null);
            submitButton = null;
        }
        if (passwordField != null) {
            passwordField.setText("");
            passwordField = null;
        }
        if (emailField != null) {
            emailField = null;
        }
        if (statusView != null) {
            statusView = null;
        }
        if (loadingView != null) {
            loadingView = null;
        }
        callback = null;
        activityRef.clear();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new ColorDrawable(Color.rgb(16, 16, 16)));
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(24));
        root.setBackground(new CenterGlowDrawable());

        TextView titleView = new TextView(getContext());
        titleView.setText(config != null && !TextUtils.isEmpty(config.getAppName())
                ? config.getAppName()
                : "Universal Auth");
        titleView.setTextSize(24f);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(boldTypeface);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, dpToPx(8));

        FrameLayout titleBar = new FrameLayout(getContext());
        titleBar.addView(titleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        titleBar.addView(createTopActions(), debugButtonParams());
        root.addView(titleBar, matchParentWrapContentParams());

        TextView subtitleView = new TextView(getContext());
        subtitleView.setText("Sign in to continue");
        subtitleView.setTextSize(16f);
        subtitleView.setTextColor(Color.rgb(238, 238, 238));
        subtitleView.setTypeface(mediumTypeface);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, 0, 0, dpToPx(24));
        root.addView(subtitleView, matchParentWrapContentParams());

        TextView emailLabel = new TextView(getContext());
        emailLabel.setText("Email");
        emailLabel.setTextColor(Color.WHITE);
        emailLabel.setTypeface(mediumTypeface);
        emailLabel.setPadding(0, 0, 0, dpToPx(8));
        root.addView(emailLabel, matchParentWrapContentParams());

        emailField = new EditText(getContext());
        emailField.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setHint("you@example.com");
        emailField.setSingleLine(true);
        emailField.setTextColor(Color.WHITE);
        emailField.setHintTextColor(Color.rgb(190, 190, 190));
        emailField.setTypeface(mediumTypeface);
        emailField.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        emailField.setBackground(createFieldBackground());
        root.addView(emailField, matchParentWrapContentParams());

        TextView passwordLabel = new TextView(getContext());
        passwordLabel.setText("Password");
        passwordLabel.setTextColor(Color.WHITE);
        passwordLabel.setTypeface(mediumTypeface);
        passwordLabel.setPadding(0, dpToPx(16), 0, dpToPx(8));
        root.addView(passwordLabel, matchParentWrapContentParams());

        passwordField = new EditText(getContext());
        passwordField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        passwordField.setHint("Password");
        passwordField.setSingleLine(true);
        passwordField.setTextColor(Color.WHITE);
        passwordField.setHintTextColor(Color.rgb(190, 190, 190));
        passwordField.setTypeface(mediumTypeface);
        passwordField.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        passwordField.setBackground(createFieldBackground());
        root.addView(passwordField, matchParentWrapContentParams());
        prefillSavedCredentials();

        statusView = new TextView(getContext());
        statusView.setText("");
        statusView.setTextSize(14f);
        statusView.setTextColor(Color.rgb(255, 150, 150));
        statusView.setTypeface(mediumTypeface);
        statusView.setPadding(0, dpToPx(16), 0, dpToPx(8));
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, matchParentWrapContentParams());

        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dpToPx(16), 0, 0);

        submitButton = new Button(getContext());
        submitButton.setText("Sign In");
        submitButton.setTextColor(Color.rgb(20, 20, 20));
        submitButton.setTypeface(boldTypeface);
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(Color.rgb(245, 245, 245));
        buttonBackground.setCornerRadius(dpToPx(28));
        submitButton.setBackground(buttonBackground);
        submitButton.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        loadingView = new ProgressBar(getContext());
        loadingView.setVisibility(View.GONE);
        loadingView.setIndeterminate(true);
        actionRow.addView(submitButton, new LinearLayout.LayoutParams(
                dpToPx(180),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        actionRow.addView(loadingView, new LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
        ));
        root.addView(actionRow);

        TextView versionView = new TextView(getContext());
        versionView.setText("AuthCenter v" + AuthCenterVersion.VERSION);
        versionView.setTextSize(12f);
        versionView.setTextColor(Color.argb(150, 80, 80, 80));
        versionView.setGravity(Gravity.CENTER);
        versionView.setTypeface(mediumTypeface);
        versionView.setPadding(0, dpToPx(28), 0, dpToPx(8));
        root.addView(versionView, matchParentWrapContentParams());

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private Typeface loadTypeface(String assetPath, Typeface fallback) {
        try {
            return Typeface.createFromAsset(getContext().getAssets(), assetPath);
        } catch (RuntimeException exception) {
            Log.w(
                    TAG,
                    "Unable to load optional font asset " + assetPath
                            + " (" + exception.getClass().getSimpleName() + ")"
            );
            return fallback;
        }
    }

    private GradientDrawable createFieldBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(205, 24, 24, 24));
        background.setCornerRadius(dpToPx(12));
        background.setStroke(dpToPx(1), Color.argb(190, 220, 220, 220));
        return background;
    }

    private void prefillSavedCredentials() {
        try {
            CredentialStore.SavedCredentials credentials =
                    new CredentialStore(getContext(), config.getAppId())
                            .getSavedCredentials();
            if (credentials != null) {
                emailField.setText(credentials.getEmail());
                passwordField.setText(credentials.getPassword());
            }
        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Unable to load saved credentials ("
                            + exception.getClass().getSimpleName()
                            + ")"
            );
        }
    }

    private void attemptLogin() {
        String email = emailField.getText() == null
                ? ""
                : emailField.getText().toString().trim();
        String password = passwordField.getText() == null
                ? ""
                : passwordField.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Email dan password wajib diisi.");
            return;
        }

        setSubmitting(true);
        showMessage("");
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            clearPasswordField();
            return;
        }
        AuthCenter.signIn(activity, config, email, password, new AuthenticationCallback() {
            @Override
            public void onAuthenticated(com.universal.authcenter.auth.AuthState state) {
                if (!isUiSafe()) {
                    clearPasswordField();
                    return;
                }
                setSubmitting(false);
                showMessage("Verifikasi akses...");
                AuthCenter.authorize(activity, config, new AuthCallback() {
                    @Override
                    public void onAuthorized(AuthResult result) {
                        saveSuccessfulCredentials(activity, email, password);
                        if (!isUiSafe()) {
                            clearPasswordField();
                            return;
                        }
                        hideLoading();
                        clearPasswordField();
                        if (callback != null) {
                            callback.onAuthorized(result);
                        }
                        dismiss();
                    }

                    @Override
                    public void onDenied(AuthResult result) {
                        if (!isUiSafe()) {
                            clearPasswordField();
                            return;
                        }
                        hideLoading();
                        showError(resolveDeniedMessage(result));
                        if (callback != null) {
                            callback.onDenied(result);
                        }
                    }

                    @Override
                    public void onError(AuthResult result) {
                        if (!isUiSafe()) {
                            clearPasswordField();
                            return;
                        }
                        hideLoading();
                        showError(resolveErrorMessage(result));
                        if (callback != null) {
                            callback.onError(result);
                        }
                    }
                });
            }

            @Override
            public void onAuthenticationFailed(ErrorCode errorCode) {
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isUiSafe()) {
                            clearPasswordField();
                            return;
                        }
                        setSubmitting(false);
                        showError(mapAuthenticationError(errorCode));
                    }
                });
            }

            @Override
            public void onNetworkError() {
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isUiSafe()) {
                            clearPasswordField();
                            return;
                        }
                        setSubmitting(false);
                        showError("Tidak dapat terhubung ke server.");
                    }
                });
            }
        });
    }

    private void saveSuccessfulCredentials(
            Activity activity,
            String email,
            String password
    ) {
        try {
            new CredentialStore(activity, config.getAppId())
                    .saveSuccessfulCredentials(email, password);
        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Unable to save credentials for the next login ("
                            + exception.getClass().getSimpleName()
                            + ")"
            );
        }
    }

    private void setSubmitting(boolean submitting) {
        if (!isUiSafe()) {
            return;
        }
        if (submitButton != null) {
            submitButton.setEnabled(!submitting);
            submitButton.setAlpha(submitting ? 0.6f : 1f);
        }
        if (loadingView != null) {
            loadingView.setVisibility(submitting ? View.VISIBLE : View.GONE);
        }
        if (passwordField != null) {
            passwordField.setEnabled(!submitting);
        }
        if (emailField != null) {
            emailField.setEnabled(!submitting);
        }
    }

    private void hideLoading() {
        setSubmitting(false);
    }

    private void clearPasswordField() {
        if (passwordField != null) {
            passwordField.setText("");
        }
    }

    private void showError(String message) {
        if (!isUiSafe() || statusView == null) {
            return;
        }
        statusView.setTextColor(Color.rgb(255, 150, 150));
        statusView.setText(message);
    }

    private void showMessage(String message) {
        if (!isUiSafe() || statusView == null) {
            return;
        }
        statusView.setTextColor(Color.WHITE);
        statusView.setText(message);
    }

    private String mapAuthenticationError(AuthenticationCallback.ErrorCode errorCode) {
        if (errorCode == null) {
            return "Login gagal.";
        }
        switch (errorCode) {
            case INVALID_CREDENTIALS:
                return "Email atau password salah.";
            case ACCOUNT_DISABLED:
                return "Akun ini dinonaktifkan.";
            case SIGN_IN_DISABLED:
                return "Login saat ini tidak tersedia.";
            case RATE_LIMITED:
                return "Terlalu banyak percobaan. Coba lagi nanti.";
            case INVALID_REQUEST:
                return "Data login tidak valid.";
            case CONFIGURATION_ERROR:
                return "Konfigurasi login tidak valid.";
            case SESSION_ERROR:
                return "Sesi login tidak dapat diproses.";
            case INVALID_RESPONSE:
                return "Respons autentikasi tidak valid.";
            case HTTP_ERROR:
                return "Tidak dapat terhubung ke layanan autentikasi.";
            default:
                return "Login gagal.";
        }
    }

    private String resolveDeniedMessage(AuthResult result) {
        if (result != null && !TextUtils.isEmpty(result.getMessage())) {
            return result.getMessage();
        }
        return "Akun ini tidak memiliki akses ke aplikasi.";
    }

    private String resolveErrorMessage(AuthResult result) {
        if (result != null && !TextUtils.isEmpty(result.getMessage())) {
            return result.getMessage();
        }
        return "Validasi akses gagal.";
    }

    private ViewGroup.LayoutParams matchParentWrapContentParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    /**
     * TEMPORARY DEBUG — small {@code ">_" } affordance shown only while the
     * feature gate is enabled. It opens the read-only AUTH DEBUG panel and has
     * no effect on the login flow.
     */
    private TextView createDebugButton() {
        TextView debugButton = new TextView(getContext());
        debugButton.setText(">_");
        debugButton.setTextSize(14f);
        debugButton.setTypeface(Typeface.MONOSPACE);
        debugButton.setTextColor(Color.WHITE);
        debugButton.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        debugButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDebugPanel();
            }
        });
        return debugButton;
    }

    private View createTopActions() {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        if (AuthDebugConfig.ENABLED) {
            actions.addView(createDebugButton());
        }

        TextView contactButton = new TextView(getContext());
        contactButton.setText("\u2709");
        contactButton.setTextSize(20f);
        contactButton.setTextColor(Color.WHITE);
        contactButton.setGravity(Gravity.CENTER);
        contactButton.setContentDescription("Contact and support");
        contactButton.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        contactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showContactOptions();
            }
        });
        actions.addView(contactButton);
        return actions;
    }

    private void showContactOptions() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(
                activity,
                android.R.style.Theme_DeviceDefault_Dialog_Alert
        )
                .setTitle("Contact & support")
                .setItems(
                        new String[] {"Developer website", "Instagram", "Telegram"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    openContactLink("https://github.com/mirzahardcode");
                                } else if (which == 1) {
                                    openContactLink("https://instagram.com/mrrrzza");
                                } else if (which == 2) {
                                    openContactLink("https://t.me/mirzaadev");
                                }
                            }
                        }
                )
                .show();
    }

    private void openContactLink(String url) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    activity,
                    "No app available to open this link.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private FrameLayout.LayoutParams debugButtonParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT
        );
    }

    private void openDebugPanel() {
        if (!AuthDebugConfig.ENABLED) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        authDebugDialog = new AuthDebugDialog(activity);
        authDebugDialog.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
