package com.ahm.capacitor.camera.preview;

/**
 * Framework-free router that decides <em>which</em> plugin operation a preview-session readiness or
 * failure event settles.
 *
 * <p>Two plugin calls wait on a first preview frame: {@code start()} on the initial session, and
 * {@code flip()} on the session created for the switched-in camera. Both are announced through the
 * same {@link Preview.PreviewStateListener} callbacks, so the event must carry the session id and
 * this router must map it back to the operation that is waiting for it. Inferring the target from
 * "whichever callback-id slot happens to be non-empty" is what allowed a flip to consume the start
 * call, and a stale session to settle a live one.
 *
 * <p>Each operation is registered against exactly one session id. An event for any other session -
 * a released camera, a replaced lifecycle, a fragment that has gone away - settles nothing.
 *
 * <p>The router also owns flip admission, because "another flip is already pending" is the same
 * piece of state. All methods are synchronized and never call out, so they cannot deadlock against
 * the Android UI or Camera1 callback threads.
 */
class PreviewOperationRouter {

    /** Sentinel meaning "no session": no operation is registered against it. */
    static final long NO_SESSION = 0L;

    static final String ERROR_FLIP_IN_PROGRESS = "a camera flip is already in progress";

    /** The plugin operation a readiness or failure event belongs to. */
    enum Operation {
        /** The event settles nothing: no operation was waiting on that session. */
        NONE,
        /** The event settles the pending {@code start()} call. */
        START,
        /** The event settles the pending {@code flip()} call. */
        FLIP
    }

    private long startSession = NO_SESSION;
    private long flipSession = NO_SESSION;

    /** Registers the session whose first frame resolves the pending {@code start()} call. */
    synchronized void awaitStart(long session) {
        startSession = session;
    }

    /** Registers the session whose first frame resolves the pending {@code flip()} call. */
    synchronized void awaitFlip(long session) {
        flipSession = session;
    }

    synchronized boolean isStartPending() {
        return startSession != NO_SESSION;
    }

    synchronized boolean isFlipPending() {
        return flipSession != NO_SESSION;
    }

    synchronized long getStartSession() {
        return startSession;
    }

    synchronized long getFlipSession() {
        return flipSession;
    }

    /**
     * Non-consuming query: does {@code session} still own a pending {@code start()} or {@code flip()}?
     *
     * <p>Lifecycle teardown uses this to decide whether pausing the current preview session has a
     * startup failure to report. Unlike {@link #settle(long)}, {@link #consumeStart()} and
     * {@link #consumeFlip()}, it changes no state, so asking cannot settle or lose an operation.
     *
     * @return true only when {@code session} is the currently registered start or flip session;
     *         false for {@link #NO_SESSION}, for an unrelated session, and once the operation has
     *         been settled or consumed
     */
    synchronized boolean hasPendingOperationForSession(long session) {
        // Guard explicitly: with nothing pending both fields hold NO_SESSION, so a bare equality
        // check would report NO_SESSION itself as pending.
        if (session == NO_SESSION) {
            return false;
        }
        return session == startSession || session == flipSession;
    }

    /**
     * Consumes and returns the operation waiting on {@code session}.
     *
     * <p>A session is registered by at most one operation, so the answer is unambiguous. Consuming
     * is what makes the settlement exactly-once: a duplicate or late event for the same session
     * returns {@link Operation#NONE}.
     *
     * @return the operation to settle, or {@link Operation#NONE} for a stale or already settled
     *         session
     */
    synchronized Operation settle(long session) {
        if (session == NO_SESSION) {
            return Operation.NONE;
        }
        if (session == flipSession) {
            flipSession = NO_SESSION;
            return Operation.FLIP;
        }
        if (session == startSession) {
            startSession = NO_SESSION;
            return Operation.START;
        }
        return Operation.NONE;
    }

    /**
     * Consumes a pending flip regardless of which session it was waiting on, for a lifecycle event
     * that invalidates it (pause, stop, destruction).
     *
     * @return true when a flip was pending and is now the caller's to reject
     */
    synchronized boolean consumeFlip() {
        if (flipSession == NO_SESSION) {
            return false;
        }
        flipSession = NO_SESSION;
        return true;
    }

    /**
     * Consumes a pending start regardless of which session it was waiting on.
     *
     * @return true when a start was pending and is now the caller's to reject
     */
    synchronized boolean consumeStart() {
        if (startSession == NO_SESSION) {
            return false;
        }
        startSession = NO_SESSION;
        return true;
    }

    /**
     * Decides whether a camera flip may start.
     *
     * @return null when the flip may start, otherwise the reason it may not
     */
    synchronized String checkCanFlip(boolean hasCamera, boolean previewReady, boolean resumed, boolean captureInProgress) {
        if (!hasCamera) {
            return CaptureCoordinator.ERROR_NO_CAMERA;
        }
        if (!resumed) {
            return CaptureCoordinator.ERROR_NOT_RESUMED;
        }
        if (!previewReady) {
            return CaptureCoordinator.ERROR_PREVIEW_NOT_READY;
        }
        if (captureInProgress) {
            // A user capture in flight is not thrown away for a flip; the flip is refused instead.
            return CaptureCoordinator.ERROR_CAPTURE_IN_PROGRESS;
        }
        if (flipSession != NO_SESSION) {
            return ERROR_FLIP_IN_PROGRESS;
        }
        return null;
    }

    @Override
    public synchronized String toString() {
        return "PreviewOperationRouter{startSession=" + startSession + ", flipSession=" + flipSession + "}";
    }
}
