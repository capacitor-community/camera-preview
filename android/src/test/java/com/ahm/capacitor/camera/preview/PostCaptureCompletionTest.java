package com.ahm.capacitor.camera.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for the corrective pass on issue #424: a post-capture preview restart that
 * threw used to be reported to an already-resolved start call, after which the JPEG callback went
 * on to resolve {@code capture()} with the image. The consumer got "success" while the live preview
 * was gone.
 *
 * <p>The restart outcome now flows back to the capture that caused it. These tests cover the
 * completion decision and the session bookkeeping around it; the Camera1 restart itself remains a
 * physical-device assertion.
 */
public class PostCaptureCompletionTest {

    private CaptureCoordinator captures;
    private PreviewSessionCoordinator sessions;

    @Before
    public void setUp() {
        captures = new CaptureCoordinator();
        sessions = new PreviewSessionCoordinator();
        sessions.beginSession();
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();
        assertTrue(sessions.onFirstFrame(sessions.getSessionId()));
        assertTrue("the fixture starts from a genuinely ready preview", sessions.isReady());
    }

    // 16. A successful image plus a successful restart resolves the capture once.

    @Test
    public void successfulImageAndSuccessfulRestart_resolvesCaptureOnce() {
        long token = captures.beginCapture(true, true, true, (message) -> {});

        String failure = CaptureCoordinator.resolveCaptureFailure(null, null);

        assertNull("the capture resolves with the image", failure);
        assertTrue("its own callback settles it", captures.finishCapture(token));
        assertFalse("and cannot settle it twice", captures.finishCapture(token));
        assertTrue("the preview is still usable, so repeated capture keeps working", sessions.isReady());
        assertEquals("which the admission check confirms", null, captures.checkCanCapture(true, sessions.isReady(), true));
    }

    // 17. A successful image plus a restart RuntimeException rejects the capture once and leaves
    //     the preview marked not ready.

    @Test
    public void successfulImageButFailedRestart_rejectsCaptureOnceAndMarksPreviewNotReady() {
        long token = captures.beginCapture(true, true, true, (message) -> {});

        String restartError =
            "failed to restart the camera preview after capture: " +
            CaptureCoordinator.describe(new RuntimeException("startPreview failed"));

        // Preview.restartPreviewAfterCapture() marks the session unusable without announcing a
        // startup failure, because the start call was resolved long ago.
        sessions.markFailed(sessions.getSessionId());

        String failure = CaptureCoordinator.resolveCaptureFailure(null, restartError);

        assertEquals("the capture is rejected with the restart error, not resolved", restartError, failure);
        assertTrue(failure.contains("startPreview failed"));
        assertFalse("readiness is false after a failed restart", sessions.isReady());
        assertEquals(PreviewSessionCoordinator.State.FAILED, sessions.getState());
        assertTrue("the capture settles exactly once", captures.finishCapture(token));
        assertFalse(captures.finishCapture(token));

        assertEquals(
            "and a later capture is refused rather than reaching a dead preview",
            CaptureCoordinator.ERROR_PREVIEW_NOT_READY,
            captures.checkCanCapture(true, sessions.isReady(), true)
        );
    }

    @Test
    public void markFailedDoesNotConsumeAPendingFlip() {
        // markFailed is deliberately not onStartFailure: it must not settle any waiting operation.
        PreviewOperationRouter router = new PreviewOperationRouter();
        long session = sessions.getSessionId();
        router.awaitFlip(session);

        sessions.markFailed(session);

        assertFalse(sessions.isReady());
        assertTrue("no operation was settled by the restart failure", router.isFlipPending());
    }

    @Test
    public void markFailedIgnoresAStaleSession() {
        long staleSession = sessions.getSessionId();
        sessions.beginSession();

        sessions.markFailed(staleSession);

        assertEquals(
            "a replaced session's restart failure must not poison the new one",
            PreviewSessionCoordinator.State.IDLE,
            sessions.getState()
        );
    }

    // 18. An image-processing error together with a restart error produces one deterministic
    //     rejection: the image error wins, because it is the failure of the requested operation.

    @Test
    public void imageErrorAndRestartError_produceOneDeterministicRejection() {
        long token = captures.beginCapture(true, true, true, (message) -> {});

        String imageError = "Picture too large (memory)";
        String restartError = "failed to restart the camera preview after capture: RuntimeException: startPreview failed";

        String failure = CaptureCoordinator.resolveCaptureFailure(imageError, restartError);

        assertEquals("the image-processing failure is the primary error", imageError, failure);
        assertTrue("the capture is settled exactly once", captures.finishCapture(token));
        assertFalse(captures.finishCapture(token));
    }

    @Test
    public void resolveCaptureFailureIsDeterministicForEveryCombination() {
        assertNull(CaptureCoordinator.resolveCaptureFailure(null, null));
        assertEquals("image", CaptureCoordinator.resolveCaptureFailure("image", null));
        assertEquals("restart", CaptureCoordinator.resolveCaptureFailure(null, "restart"));
        assertEquals("image", CaptureCoordinator.resolveCaptureFailure("image", "restart"));
    }

    // 19. A late JPEG callback after a lifecycle abort neither restarts the preview nor settles
    //     another call. The claim fails first, so the completion decision is never reached.

    @Test
    public void lateJpegAfterLifecycleAbort_neitherRestartsNorSettles() {
        long token = captures.beginCapture(true, true, true, (message) -> {});

        // The lifecycle event (pause, stop, switch, output loss) aborts and reports once.
        assertEquals(token, captures.abortActiveCapture());

        // The JPEG callback arrives afterwards and loses its claim, so the whole body is skipped:
        // no image processing, no preview restart, no second outcome.
        assertFalse("the late callback owns nothing", captures.finishCapture(token));
        assertFalse(captures.isCaptureActive(token));
        assertFalse("the slot is free rather than stuck", captures.isCaptureInProgress());
    }

    @Test
    public void lateJpegCannotRestartThePreviewOfAReplacedSession() {
        long capturedSession = sessions.getSessionId();
        long token = captures.beginCapture(true, true, true, (message) -> {});
        captures.abortActiveCapture();

        // A camera switch replaced the session the capture belonged to.
        long newSession = sessions.beginSession();
        assertFalse("the capture's session is gone", capturedSession == newSession);

        assertFalse(captures.finishCapture(token));
        // Preview.restartPreviewAfterCapture() is additionally guarded by the Camera instance the
        // capture was accepted against, which is a code-inspection/device assertion.
    }
}
