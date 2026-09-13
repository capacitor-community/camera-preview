package com.ahm.capacitor.camera.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for the corrective pass on issue #424: {@code flip()} used to resolve as soon
 * as {@code switchCamera()} returned, so it reported success for a camera that may never have
 * opened, never bound its output, or never produced a frame.
 *
 * <p>{@code flip()} now waits on the switched-in camera's own preview session. These plain JVM
 * tests cover the admission rules and the session-to-operation routing that make that settlement
 * exactly-once and immune to stale callbacks. The Camera1 side effects themselves remain a
 * physical-device assertion.
 */
public class PreviewOperationRouterTest {

    private PreviewOperationRouter router;
    private PreviewSessionCoordinator sessions;

    @Before
    public void setUp() {
        router = new PreviewOperationRouter();
        sessions = new PreviewSessionCoordinator();
    }

    /** Drives a session to READY the way Preview does, and returns its id. */
    private long driveSessionToFirstFrame() {
        long session = sessions.getSessionId();
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();
        assertTrue(sessions.onFirstFrame(session));
        return session;
    }

    // 8. flip before the current preview is ready rejects without switching.

    @Test
    public void flipBeforeReadiness_rejectsWithoutSwitching() {
        String rejection = router.checkCanFlip(true, false, true, false);

        assertEquals(CaptureCoordinator.ERROR_PREVIEW_NOT_READY, rejection);
        assertFalse("a refused flip must not register itself as pending", router.isFlipPending());
    }

    @Test
    public void flipWithoutACameraOrWhilePaused_rejects() {
        assertEquals(CaptureCoordinator.ERROR_NO_CAMERA, router.checkCanFlip(false, true, true, false));
        assertEquals(CaptureCoordinator.ERROR_NOT_RESUMED, router.checkCanFlip(true, true, false, false));
        assertFalse(router.isFlipPending());
    }

    @Test
    public void flipOnAReadyResumedCameraIsAdmitted() {
        assertNull(router.checkCanFlip(true, true, true, false));
    }

    // 9. flip during a capture rejects; the capture is the caller's work and is not thrown away.

    @Test
    public void flipDuringCapture_rejectsAndLeavesTheCaptureIntact() {
        CaptureCoordinator captures = new CaptureCoordinator();
        long capture = captures.beginCapture(true, true, true, (message) -> {});

        String rejection = router.checkCanFlip(true, true, true, captures.isCaptureInProgress());

        assertEquals(CaptureCoordinator.ERROR_CAPTURE_IN_PROGRESS, rejection);
        assertFalse("the refused flip is not pending", router.isFlipPending());
        assertTrue("the capture keeps running", captures.isCaptureActive(capture));
        assertTrue("and settles on its own callback", captures.finishCapture(capture));
    }

    // 10. A concurrent flip rejects without overwriting the first one.

    @Test
    public void concurrentFlip_rejectsWithoutOverwritingTheFirst() {
        long firstFlipSession = 7L;
        router.awaitFlip(firstFlipSession);

        assertEquals(PreviewOperationRouter.ERROR_FLIP_IN_PROGRESS, router.checkCanFlip(true, true, true, false));
        assertEquals("the first flip still owns the pending slot", firstFlipSession, router.getFlipSession());

        assertEquals(
            "and only the first flip's session settles it",
            PreviewOperationRouter.Operation.FLIP,
            router.settle(firstFlipSession)
        );
    }

    // 11. flip does not resolve merely because Camera.open() or startPreview() returned.

    @Test
    public void flipDoesNotResolveBecauseStartPreviewReturned() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        // Camera.open() succeeded and the preview was configured and started...
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();

        assertFalse("startPreview() returning is not readiness", sessions.isReady());
        assertTrue("so the flip is still pending", router.isFlipPending());

        // ...only the first delivered frame is.
        assertTrue(sessions.onFirstFrame(flipSession));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
    }

    // 12. The switched session's first frame resolves flip exactly once.

    @Test
    public void switchedSessionFirstFrame_resolvesFlipExactlyOnce() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);
        driveSessionToFirstFrame();

        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertEquals("a duplicate readiness event settles nothing", PreviewOperationRouter.Operation.NONE, router.settle(flipSession));
        assertFalse(router.isFlipPending());
    }

    // 13. Every switched-session startup failure rejects flip exactly once.

    @Test
    public void switchedCameraOpenFailure_rejectsFlipOnce() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        // Camera.open() threw before anything was attached.
        assertTrue(sessions.onStartFailure(flipSession));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertEquals(PreviewOperationRouter.Operation.NONE, router.settle(flipSession));
    }

    @Test
    public void switchedCameraConfigurationOrStartFailure_rejectsFlipOnce() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();

        // setPreviewDisplay()/setParameters()/startPreview() threw.
        assertTrue(sessions.onStartFailure(flipSession));
        assertFalse(sessions.isReady());
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertEquals(PreviewOperationRouter.Operation.NONE, router.settle(flipSession));
    }

    @Test
    public void switchedCameraFirstFrameTimeout_rejectsFlipOnce() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();

        assertTrue(sessions.onTimeout(flipSession));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertEquals(PreviewOperationRouter.Operation.NONE, router.settle(flipSession));
    }

    // 14. Stale readiness or failure from another session cannot settle the flip.

    @Test
    public void readinessFromAnotherSessionCannotSettleTheFlip() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);
        driveSessionToFirstFrame();
        assertEquals(PreviewOperationRouter.Operation.START, router.settle(startSession));

        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        assertEquals(
            "the already consumed start session settles nothing",
            PreviewOperationRouter.Operation.NONE,
            router.settle(startSession)
        );
        assertTrue("the flip is untouched", router.isFlipPending());

        assertEquals("an unknown session settles nothing either", PreviewOperationRouter.Operation.NONE, router.settle(flipSession + 99L));
        assertTrue(router.isFlipPending());
    }

    // 10 (start half). A flip callback can never consume the initial start call.

    @Test
    public void flipCannotConsumeThePendingStartCall() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);

        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        assertEquals("the flip session settles the flip", PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertTrue("the start call is still pending", router.isStartPending());
        assertEquals("and only its own session settles it", PreviewOperationRouter.Operation.START, router.settle(startSession));
    }

    // 15. Stop or pause during a pending flip rejects it once.

    @Test
    public void pauseOrStopDuringAPendingFlip_rejectsItOnce() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        assertTrue("the teardown owns the rejection", router.consumeFlip());
        assertFalse("and cannot reject it twice", router.consumeFlip());
        assertFalse(router.isFlipPending());
        assertEquals("a late failure for that session settles nothing", PreviewOperationRouter.Operation.NONE, router.settle(flipSession));
    }

    @Test
    public void teardownSettlesAPendingStartOnce() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);

        assertTrue(router.consumeStart());
        assertFalse(router.consumeStart());
        assertEquals(PreviewOperationRouter.Operation.NONE, router.settle(startSession));
    }

    @Test
    public void teardownWithNothingPendingReportsNothingToSettle() {
        assertFalse(router.consumeFlip());
        assertFalse(router.consumeStart());
    }

    @Test
    public void aFlipIsPossibleAgainAfterAFailedOne() {
        long failedFlip = sessions.beginSession();
        router.awaitFlip(failedFlip);
        assertTrue(sessions.onStartFailure(failedFlip));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(failedFlip));

        assertNull("the next flip is admitted once the preview is ready again", router.checkCanFlip(true, true, true, false));

        long retry = sessions.beginSession();
        router.awaitFlip(retry);
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();
        assertTrue(sessions.onFirstFrame(retry));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(retry));
    }
}
