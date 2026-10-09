package com.universal.authcenter;

import android.os.Handler;
import android.os.Looper;

import com.universal.authcenter.auth.AuthState;

import java.lang.ref.WeakReference;

/**
 * TEMPORARY DEBUG — central accumulator for the AUTH DEBUG snapshot.
 *
 * <p>Auth stages run on a background worker (and occasionally on the Android
 * main thread), so every mutation goes through this single class. The
 * {@link AuthDebugSnapshot} is replaced, never mutated in place, which keeps
 * publication safe across threads for this diagnostic overlay.
 *
 * <p>Live UI refresh is deliberately <em>not</em> pushed from here: the only
 * listener is the local {@code AuthDebugDialog}, refreshed on open and on
 * demand. Nothing is written to Logcat.
 */
public final class AuthDebug {

    public interface Listener {
        void onDebugSnapshot(AuthDebugSnapshot snapshot);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static volatile WeakReference<Listener> listenerRef =
            new WeakReference<Listener>(null);
    private static AuthDebugSnapshot snapshot = new AuthDebugSnapshot();

    private AuthDebug() {
        // Utility class
    }

    /** @return the current sanitized snapshot (never {@code null}) */
    public static AuthDebugSnapshot getSnapshot() {
        synchronized (LOCK) {
            return snapshot;
        }
    }

    /** Resets the snapshot at the start of a new authentication attempt. */
    public static void reset() {
        AuthDebugSnapshot updated;
        synchronized (LOCK) {
            updated = new AuthDebugSnapshot();
            snapshot = updated;
        }
        dispatch(updated);
    }

    public static void registerListener(Listener listener) {
        synchronized (LOCK) {
            listenerRef = new WeakReference<Listener>(listener);
        }
    }

    public static void unregisterListener(Listener listener) {
        synchronized (LOCK) {
            Listener current = listenerRef.get();
            if (current == null || current == listener) {
                listenerRef = new WeakReference<Listener>(null);
            }
        }
    }

    // --- instrumentation entry points used by AuthCenter ---

    /** Event 1: configuration resolved (or failed to resolve). */
    public static void recordConfig(
            String backendHost,
            String packageName,
            String appKey,
            boolean hasFirebaseApiKey
    ) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.initialize(backendHost, packageName, appKey, hasFirebaseApiKey);
            }
        });
    }

    public static void recordConfigFailure(String reason) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.configFailure(reason);
            }
        });
    }

    /** Event 2: Firebase session read from local storage. */
    public static void recordFirebaseSession(boolean authenticated, String userId) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.firebaseSession(authenticated, userId);
            }
        });
    }

    /** Event 3: ID token presence/validity (token value never captured). */
    public static void recordIdToken(boolean present, boolean valid) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.idToken(present, valid);
            }
        });
    }

    /** Event 4: device identity evaluated (value never captured). */
    public static void recordDeviceIdentity(boolean available) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.deviceIdentity(true, available);
            }
        });
    }

    /** Marks the start of the authorization request (records the timestamp). */
    public static void recordAttemptStarted(String timestamp) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.attemptStarted(timestamp);
            }
        });
    }

    public static void recordStage(String stage) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.stage(stage);
            }
        });
    }

    /** Event 5: authorization HTTP request started. */
    public static void recordRequestStart() {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.requestStart();
            }
        });
    }

    /** Event 6: HTTP response received. */
    public static void recordHttpResponse(
            int statusCode,
            boolean bodyAvailable,
            long elapsedMillis
    ) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.httpResponse(statusCode, bodyAvailable, elapsedMillis);
            }
        });
    }

    /** Event 7: response parsed; the backend application status, if any. */
    public static void recordApplicationStatus(String status) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.applicationStatus(status);
            }
        });
    }

    public static void recordServerMessage(String message) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.errorMessage(message);
            }
        });
    }

    /** Event 8: authorization outcome (success, denial, or error). */
    public static void recordOutcome(String stage, AuthState state) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.stage(stage);
                current.sessionState(state);
            }
        });
    }

    public static void recordNetworkError(String category) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.networkError(category);
            }
        });
    }

    public static void recordParsingError(String category) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.parsingError(category);
            }
        });
    }

    public static void recordResponseError(String category) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.responseError(category);
            }
        });
    }

    public static void recordRequestFailure(String category) {
        mutate(new Mutation() {
            @Override
            public void apply(AuthDebugSnapshot current) {
                current.requestFailure(category);
            }
        });
    }

    private static void mutate(Mutation mutation) {
        AuthDebugSnapshot updated;
        synchronized (LOCK) {
            updated = snapshot.copy();
            mutation.apply(updated);
            snapshot = updated;
        }
        dispatch(updated);
    }

    private static void dispatch(final AuthDebugSnapshot updated) {
        final Listener listener = listenerRef.get();
        if (listener == null) {
            return;
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                listener.onDebugSnapshot(updated);
            }
        });
    }

    private interface Mutation {
        void apply(AuthDebugSnapshot current);
    }
}
