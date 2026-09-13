package com.ahm.capacitor.camera.preview;

/**
 * Framework-free state machine that reconciles the two asynchronous inputs a Camera1 preview
 * needs before it can run: an opened {@code android.hardware.Camera} and a usable output target
 * (a {@code SurfaceHolder} for the SurfaceView path, or a {@code SurfaceTexture} for the
 * TextureView/opacity path).
 *
 * <p>The two inputs can arrive in either order. Whichever arrives last must trigger the
 * configure/start work, and repeated callbacks for an input that is already known must be
 * idempotent. This class owns that decision so it can be unit tested on the JVM without any
 * Android framework dependency; {@link Preview} performs the resulting Camera1 side effects.
 *
 * <p>Readiness is <em>not</em> "startPreview() returned". It is "the camera delivered its first
 * preview frame", signalled through {@link #onFirstFrame(long)}. See issue #424.
 *
 * <p>Every callback carries the session id it was registered for. A session is invalidated by
 * {@link #beginSession()}, so callbacks belonging to a released or replaced camera can never
 * complete the current start attempt.
 *
 * <p>All methods are synchronized on this instance and never call out to other components, so
 * they cannot deadlock against the Android UI or Camera1 callback threads.
 */
class PreviewSessionCoordinator {

    enum State {
        /** No camera attached; nothing to do. */
        IDLE,
        /** A camera is attached but the output target is not usable yet. */
        WAITING_FOR_OUTPUT,
        /** Binding the output target, configuring parameters and calling startPreview(). */
        CONFIGURING,
        /** startPreview() returned; waiting for the first delivered preview frame. */
        WAITING_FIRST_FRAME,
        /** A first preview frame was delivered; the preview is genuinely usable. */
        READY,
        /** Startup failed or timed out; this session will not become ready. */
        FAILED
    }

    private State state = State.IDLE;
    private long sessionId = 0L;
    private boolean cameraAttached = false;
    private boolean outputAvailable = false;
    private boolean readinessAnnounced = false;
    private boolean failureAnnounced = false;

    /**
     * Starts a new preview lifecycle and invalidates every callback registered by the previous
     * one.
     *
     * @return the id of the new session, to be passed back by later callbacks
     */
    synchronized long beginSession() {
        sessionId++;
        state = State.IDLE;
        cameraAttached = false;
        outputAvailable = false;
        readinessAnnounced = false;
        failureAnnounced = false;
        return sessionId;
    }

    synchronized long getSessionId() {
        return sessionId;
    }

    synchronized State getState() {
        return state;
    }

    /** @return true only when a first preview frame has been observed for the current session. */
    synchronized boolean isReady() {
        return state == State.READY;
    }

    synchronized boolean isCameraAttached() {
        return cameraAttached;
    }

    synchronized boolean isOutputAvailable() {
        return outputAvailable;
    }

    /**
     * Records that a camera has been attached to the preview.
     *
     * @return true when the caller must now configure and start the preview
     */
    synchronized boolean onCameraAttached() {
        cameraAttached = true;
        if (state == State.IDLE && !outputAvailable) {
            state = State.WAITING_FOR_OUTPUT;
        }
        return shouldStart();
    }

    /** Records that the camera has been detached/released. The session is no longer ready. */
    synchronized void onCameraDetached() {
        cameraAttached = false;
        if (state != State.FAILED) {
            state = State.IDLE;
        }
    }

    /**
     * Records that the output target (surface or surface texture) is usable.
     *
     * @return true when the caller must now configure and start the preview
     */
    synchronized boolean onOutputAvailable() {
        outputAvailable = true;
        return shouldStart();
    }

    /**
     * Records that the already-configured output target changed size, which requires the preview
     * to be reconfigured and restarted.
     *
     * @return true when the caller must now reconfigure and restart the preview
     */
    synchronized boolean onOutputResized() {
        if (!cameraAttached || !outputAvailable) {
            return false;
        }
        if (state == State.WAITING_FIRST_FRAME || state == State.READY) {
            return true;
        }
        return shouldStart();
    }

    /** Records that the output target was destroyed. The session is no longer ready. */
    synchronized void onOutputLost() {
        outputAvailable = false;
        if (state == State.CONFIGURING || state == State.WAITING_FIRST_FRAME || state == State.READY) {
            state = cameraAttached ? State.WAITING_FOR_OUTPUT : State.IDLE;
        }
    }

    /** Called immediately before the caller binds the output and configures Camera1 parameters. */
    synchronized void onPreviewStarting() {
        state = State.CONFIGURING;
    }

    /**
     * Called after {@code Camera.startPreview()} returned without throwing. This is necessary but
     * not sufficient for readiness, so the session moves to {@link State#WAITING_FIRST_FRAME}.
     */
    synchronized void onPreviewStarted() {
        if (state == State.CONFIGURING) {
            state = State.WAITING_FIRST_FRAME;
        }
    }

    /**
     * Called when Camera1 delivers the first preview frame of a session.
     *
     * @param session the session the one-shot frame callback was registered for
     * @return true exactly once per session, when the caller must announce that the preview is
     *         ready; false for stale, duplicate or already-settled sessions
     */
    synchronized boolean onFirstFrame(long session) {
        if (session != sessionId || state != State.WAITING_FIRST_FRAME) {
            return false;
        }
        state = State.READY;
        if (readinessAnnounced || failureAnnounced) {
            return false;
        }
        readinessAnnounced = true;
        return true;
    }

    /**
     * Called when any step of the startup path fails.
     *
     * @param session the session the failing work belonged to
     * @return true exactly once per session, when the caller must announce the failure
     */
    synchronized boolean onStartFailure(long session) {
        if (session != sessionId) {
            return false;
        }
        state = State.FAILED;
        if (failureAnnounced) {
            return false;
        }
        failureAnnounced = true;
        return true;
    }

    /**
     * Marks the session unusable without announcing an outcome to a waiting operation.
     *
     * <p>Used when the preview dies after its start call was already settled - the post-capture
     * preview restart failing, for instance. The failure belongs to the operation that triggered
     * it (the capture), so routing it through {@link #onStartFailure(long)} would either be
     * ignored or, worse, settle an unrelated pending operation.
     */
    synchronized void markFailed(long session) {
        if (session != sessionId) {
            return;
        }
        state = State.FAILED;
    }

    /**
     * Called when the first preview frame did not arrive within the startup timeout.
     *
     * @param session the session the timeout was scheduled for
     * @return true exactly once per session, when the caller must announce the timeout
     */
    synchronized boolean onTimeout(long session) {
        if (session != sessionId || readinessAnnounced || failureAnnounced || state == State.READY) {
            return false;
        }
        state = State.FAILED;
        failureAnnounced = true;
        return true;
    }

    /** @return true when both inputs are present and no start attempt is in flight or settled. */
    private boolean shouldStart() {
        return cameraAttached && outputAvailable && (state == State.IDLE || state == State.WAITING_FOR_OUTPUT);
    }

    @Override
    public synchronized String toString() {
        return (
            "PreviewSessionCoordinator{session=" +
            sessionId +
            ", state=" +
            state +
            ", camera=" +
            cameraAttached +
            ", output=" +
            outputAvailable +
            ", ready=" +
            readinessAnnounced +
            ", failed=" +
            failureAnnounced +
            "}"
        );
    }
}
