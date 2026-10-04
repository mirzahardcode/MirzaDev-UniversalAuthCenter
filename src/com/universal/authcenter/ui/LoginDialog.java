package com.universal.authcenter.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import com.universal.authcenter.AuthCallback;
import com.universal.authcenter.AuthConfig;
import com.universal.authcenter.AuthResult;
import com.universal.authcenter.AuthenticationCallback;
import com.universal.authcenter.AuthCenter;

public final class LoginDialog extends Dialog {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final WeakReference<Activity> activityRef;
    private final AuthConfig config;
    private AuthCallback callback;

    private EditText emailField;
    private EditText passwordField;
    private Button submitButton;
    private TextView statusView;
    private ProgressBar loadingView;
    private boolean cleanedUp;

    public LoginDialog(Activity activity, AuthConfig config, AuthCallback callback) {
        super(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        this.activityRef = new WeakReference<Activity>(activity);
        this.config = config;
        this.callback = callback;
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
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
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
        root.setBackgroundColor(Color.WHITE);

        TextView titleView = new TextView(getContext());
        titleView.setText(config != null && !TextUtils.isEmpty(config.getAppName())
                ? config.getAppName()
                : "Universal Auth");
        titleView.setTextSize(24f);
        titleView.setTextColor(Color.DKGRAY);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, dpToPx(8));
        root.addView(titleView, matchParentWrapContentParams());

        TextView subtitleView = new TextView(getContext());
        subtitleView.setText("Sign in to continue");
        subtitleView.setTextSize(16f);
        subtitleView.setTextColor(Color.GRAY);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, 0, 0, dpToPx(24));
        root.addView(subtitleView, matchParentWrapContentParams());

        TextView emailLabel = new TextView(getContext());
        emailLabel.setText("Email");
        emailLabel.setTextColor(Color.DKGRAY);
        emailLabel.setPadding(0, 0, 0, dpToPx(8));
        root.addView(emailLabel, matchParentWrapContentParams());

        emailField = new EditText(getContext());
        emailField.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setHint("you@example.com");
        emailField.setSingleLine(true);
        emailField.setTextColor(Color.DKGRAY);
        emailField.setHintTextColor(Color.LTGRAY);
        emailField.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        root.addView(emailField, matchParentWrapContentParams());

        TextView passwordLabel = new TextView(getContext());
        passwordLabel.setText("Password");
        passwordLabel.setTextColor(Color.DKGRAY);
        passwordLabel.setPadding(0, dpToPx(16), 0, dpToPx(8));
        root.addView(passwordLabel, matchParentWrapContentParams());

        passwordField = new EditText(getContext());
        passwordField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        passwordField.setHint("Password");
        passwordField.setSingleLine(true);
        passwordField.setTextColor(Color.DKGRAY);
        passwordField.setHintTextColor(Color.LTGRAY);
        passwordField.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        root.addView(passwordField, matchParentWrapContentParams());

        statusView = new TextView(getContext());
        statusView.setText("");
        statusView.setTextSize(14f);
        statusView.setTextColor(Color.RED);
        statusView.setPadding(0, dpToPx(16), 0, dpToPx(8));
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, matchParentWrapContentParams());

        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dpToPx(16), 0, 0);

        submitButton = new Button(getContext());
        submitButton.setText("Sign In");
        submitButton.setTextColor(Color.WHITE);
        submitButton.setBackgroundColor(Color.parseColor("#1E88E5"));
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

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void attemptLogin() {
        String email = emailField.getText() == null
                ? ""
                : emailField.getText().toString().trim();
        String password = passwordField.getText() == null
                ? ""
                : passwordField.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            clearPasswordField();
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
                        clearPasswordField();
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
                        clearPasswordField();
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
                        clearPasswordField();
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
                        clearPasswordField();
                        showError("Tidak dapat terhubung ke server.");
                    }
                });
            }
        });
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
        statusView.setTextColor(Color.RED);
        statusView.setText(message);
    }

    private void showMessage(String message) {
        if (!isUiSafe() || statusView == null) {
            return;
        }
        statusView.setTextColor(Color.DKGRAY);
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

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
