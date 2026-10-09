package com.universal.authcenter;

/**
 * TEMPORARY DEBUG — compile-time feature gate for the AUTH DEBUG panel.
 *
 * <p>This flag only switches the authentication diagnostics overlay on. It
 * never changes authorization behavior, the {@code {appKey, deviceId}}
 * payload, {@code DEVICE_BINDING_MODE}, or any backend contract. It is the
 * single, obvious knob used to remove the panel before a release build.
 *
 * <p>Disable it one of two ways:
 * <ol>
 *   <li>Set {@link #ENABLED} to {@code false} and rebuild (quickest); or</li>
 *   <li>Delete this class together with
 *       {@link AuthDebugSnapshot}, {@link AuthDebug} and
 *       {@code com.universal.authcenter.ui.AuthDebugDialog}, and remove the
 *       {@code ">_" } button wiring in
 *       {@code com.universal.authcenter.ui.LoginDialog} (full removal).</li>
 * </ol>
 *
 * <p>When {@link #ENABLED} is {@code false} the debug button is not built and
 * no diagnostic events are processed.
 */
public final class AuthDebugConfig {

    public static final boolean ENABLED = true;

    private AuthDebugConfig() {
        // Utility class
    }
}
