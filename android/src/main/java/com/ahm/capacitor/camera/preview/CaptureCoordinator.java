package com.ahm.capacitor.camera.preview;

/**
 * Framework-free gate around Camera1 still capture.
 *
 * <p>It guarantees three things that issue #424 showed were missing:
 *
 * <ul>
 *   <li>a capture is refused, with a reason, when there is no camera, when the preview has not
 *       produced a first frame yet, when the fragment is not resumed, or when another capture is
 *       already in flight - instead of silently returning and leaving the caller unresolved;
 *   <li>every synchronous Camera1 call made while setting up and triggering the capture runs
 *       inside an exception boundary, so a {@code RuntimeException} such as
 *       {@code "takePicture failed"} becomes an error callback rather than an uncaught exception
 *       that terminates the process;
 *   <li>each accepted capture produces exactly one outcome, because only the holder of the
 *       capture token may settle it.
 * </ul>
 *
 * <p>Each accepted capture is identified by a monotonically increasing <em>token</em> rather than
 * by a boolean. A boolean cannot tell a late JPEG callback belonging to an aborted capture apart
 * from a newer capture that is legitimately in flight, so the late callback could settle - or
 * interfere with - the wrong request. Every settlement therefore names the token it believes it
 * owns, and only the token that is still active wins.
 *
 * <p>This class performs no Camera1 work itself; the caller injects it as a {@link CaptureAction}.
 * That keeps the decision and exactly-once logic unit testable on the JVM.
 */
class CaptureCoordinator {

    /** Sentinel meaning "no capture": returned by a refused {@code beginCapture} or a no-op abort. */
    static final long NO_CAPTURE = 0L;

    static final String ERROR_NO_CAMERA = "camera is not running";
    static final String ERROR_PREVIEW_NOT_READY = "camera preview is not ready";
    static final String ERROR_NOT_RESUMED = "camera preview is not resumed";
    static final String ERROR_CAPTURE_IN_PROGRESS = "a capture is already in progress";

    /** The Camera1 capture setup and {@code takePicture()} invocation supplied by the caller. */
    interface CaptureAction {
        void run() throws Exception;
    }

    /** Sink for the single failure message produced by a refused or failed capture. */
    interface ErrorReporter {
        void onCaptureError(String message);
    }

    /** Token of the capture that is currently accepted, or {@link #NO_CAPTURE}. */
    private long activeToken = NO_CAPTURE;

    /** Last token handed out; never reused, so a stale token can never match a newer capture. */
    private long lastToken = NO_CAPTURE;

    synchronized boolean isCaptureInProgress() {
        return activeToken != NO_CAPTURE;
    }

    synchronized long getActiveCaptureToken() {
        return activeToken;
    }

    /** @return true when {@code token} names the capture that is still in flight. */
    synchronized boolean isCaptureActive(long token) {
        return token != NO_CAPTURE && token == activeToken;
    }

    /**
     * Decides whether a capture may start.
     *
     * @return null when the capture may start, otherwise the reason it may not
     */
    synchronized String checkCanCapture(boolean hasCamera, boolean previewReady, boolean resumed) {
        if (!hasCamera) {
            return ERROR_NO_CAMERA;
        }
        if (!resumed) {
            return ERROR_NOT_RESUMED;
        }
        if (!previewReady) {
            return ERROR_PREVIEW_NOT_READY;
        }
        if (activeToken != NO_CAPTURE) {
            return ERROR_CAPTURE_IN_PROGRESS;
        }
        return null;
    }

    /**
     * Claims the capture slot when the preconditions hold. When they do not, the reason is
     * reported once through {@code reporter} and no state changes.
     *
     * @return the token identifying the accepted capture, or {@link #NO_CAPTURE} when refused
     */
    long beginCapture(boolean hasCamera, boolean previewReady, boolean resumed, ErrorReporter reporter) {
        String rejection;
        long token = NO_CAPTURE;
        synchronized (this) {
            rejection = checkCanCapture(hasCamera, previewReady, resumed);
            if (rejection == null) {
                token = ++lastToken;
                activeToken = token;
            }
        }
        if (rejection != null) {
            reporter.onCaptureError(rejection);
            return NO_CAPTURE;
        }
        return token;
    }

    /**
     * Runs the injected Camera1 capture work inside an exception boundary. A failure releases the
     * capture slot and is reported once; it never propagates to the caller's thread.
     */
    void performCapture(long token, CaptureAction action, ErrorReporter reporter) {
        try {
            action.run();
        } catch (Exception exception) {
            // Covers RuntimeException from getParameters()/setParameters()/takePicture() as well
            // as checked exceptions. Without this boundary the crash in issue #424 reached the
            // thread's default handler and terminated the process.
            if (finishCapture(token)) {
                reporter.onCaptureError("failed to take picture: " + describe(exception));
            }
        }
    }

    /**
     * Releases the capture slot on behalf of {@code token}.
     *
     * @return true only for the token that actually ends the in-flight capture, which is therefore
     *         the one that must report its single outcome. A token from an aborted or already
     *         settled capture returns false, which is what makes a late JPEG callback a no-op.
     */
    synchronized boolean finishCapture(long token) {
        if (token == NO_CAPTURE || token != activeToken) {
            return false;
        }
        activeToken = NO_CAPTURE;
        return true;
    }

    /**
     * Ends whatever capture is currently accepted, for a lifecycle event that invalidates it -
     * a pause, a camera switch, the loss of the preview output, or fragment destruction.
     *
     * <p>The slot is left free for a later capture, and the aborted token can never settle again.
     *
     * @return the token that was aborted, or {@link #NO_CAPTURE} when no capture was active
     */
    synchronized long abortActiveCapture() {
        long token = activeToken;
        activeToken = NO_CAPTURE;
        return token;
    }

    /**
     * Decides the single outcome of a completed capture from the image-processing result and the
     * synchronous outcome of the post-capture preview restart.
     *
     * <p>Camera1 stops the preview to take a picture, so the restart is part of completing the
     * capture. A restart that throws leaves the app with no live preview; reporting the image as a
     * success would hide that. The rule is deterministic:
     *
     * <ul>
     *   <li>image processing failed - that is the primary error, because it is the failure of the
     *       operation the caller actually asked for. A restart failure alongside it is only logged.
     *   <li>image processing succeeded but the restart threw - the capture is rejected with the
     *       restart error, and the preview session is separately marked unusable.
     *   <li>both succeeded - the capture resolves with the image.
     * </ul>
     *
     * @return null when the capture must resolve with the image, otherwise the single error to
     *         reject it with
     */
    static String resolveCaptureFailure(String imageError, String restartError) {
        if (imageError != null) {
            return imageError;
        }
        return restartError;
    }

    /** Formats a throwable for an error message without leaking stack or environment details. */
    static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
