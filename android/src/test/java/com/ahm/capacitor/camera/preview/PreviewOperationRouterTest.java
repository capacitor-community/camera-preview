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

    // Normal-stop log correction: CameraActivity.onPause() asks the router, not preview readiness,
    // whether the paused session still owns a start or flip with a failure to report.

    @Test
    public void hasPendingOperationForSession_isFalseWithNothingPendingAndForNoSession() {
        assertFalse(
            "with nothing pending both fields hold NO_SESSION, which must not read as pending",
            router.hasPendingOperationForSession(PreviewOperationRouter.NO_SESSION)
        );
        assertFalse("no operation is registered to any real session", router.hasPendingOperationForSession(1L));

        router.awaitStart(3L);
        router.awaitFlip(4L);
        assertFalse(
            "NO_SESSION never owns an operation, even while others are pending",
            router.hasPendingOperationForSession(PreviewOperationRouter.NO_SESSION)
        );
    }

    @Test
    public void pendingStart_isReportedOnlyForItsOwnSession() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);

        assertTrue(router.hasPendingOperationForSession(startSession));
        assertFalse("another session does not own the start", router.hasPendingOperationForSession(startSession + 1L));

        assertTrue("asking twice still finds it: the query did not consume it", router.hasPendingOperationForSession(startSession));
        assertEquals(startSession, router.getStartSession());
        assertEquals(
            "the start is still there to be settled exactly once",
            PreviewOperationRouter.Operation.START,
            router.settle(startSession)
        );
    }

    @Test
    public void pendingFlip_isReportedOnlyForItsOwnSession() {
        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);

        assertTrue(router.hasPendingOperationForSession(flipSession));
        assertFalse("another session does not own the flip", router.hasPendingOperationForSession(flipSession + 1L));

        assertTrue("asking twice still finds it: the query did not consume it", router.hasPendingOperationForSession(flipSession));
        assertEquals(flipSession, router.getFlipSession());
        assertEquals(
            "the flip is still there to be settled exactly once",
            PreviewOperationRouter.Operation.FLIP,
            router.settle(flipSession)
        );
    }

    @Test
    public void unrelatedSessionQuery_doesNotConsumeTheRealPendingOperations() {
        long startSession = 5L;
        long flipSession = 6L;
        long unrelatedSession = 42L;
        router.awaitStart(startSession);
        router.awaitFlip(flipSession);

        for (int i = 0; i < 3; i++) {
            assertFalse(router.hasPendingOperationForSession(unrelatedSession));
        }

        assertEquals("the start registration is untouched", startSession, router.getStartSession());
        assertEquals("the flip registration is untouched", flipSession, router.getFlipSession());
        assertTrue(router.hasPendingOperationForSession(startSession));
        assertTrue(router.hasPendingOperationForSession(flipSession));
        assertEquals(PreviewOperationRouter.Operation.START, router.settle(startSession));
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
    }

    @Test
    public void settledStartOrFlip_isNoLongerPending() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);
        assertEquals(PreviewOperationRouter.Operation.START, router.settle(startSession));
        assertFalse("a settled start is not pending", router.hasPendingOperationForSession(startSession));

        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);
        assertEquals(PreviewOperationRouter.Operation.FLIP, router.settle(flipSession));
        assertFalse("a settled flip is not pending", router.hasPendingOperationForSession(flipSession));
    }

    @Test
    public void startOrFlipConsumedByTeardown_isNoLongerPending() {
        long startSession = sessions.beginSession();
        router.awaitStart(startSession);
        assertTrue(router.consumeStart());
        assertFalse("a start consumed by teardown is not pending", router.hasPendingOperationForSession(startSession));

        long flipSession = sessions.beginSession();
        router.awaitFlip(flipSession);
        assertTrue(router.consumeFlip());
        assertFalse("a flip consumed by teardown is not pending", router.hasPendingOperationForSession(flipSession));
    }

    @Test
    public void normalStopOfAReadySession_hasNoPendingOperationToFail() {
        // start(): the session delivers its first frame and CameraActivity.onPreviewReady settles it.
        long session = sessions.beginSession();
        router.awaitStart(session);
        driveSessionToFirstFrame();
        assertEquals(PreviewOperationRouter.Operation.START, router.settle(session));

        // stop(): removing the container view destroys the output before onPause() runs.
        sessions.onOutputLost();

        assertFalse("output loss makes the settled session look unready", sessions.isReady());
        assertEquals(PreviewSessionCoordinator.State.WAITING_FOR_OUTPUT, sessions.getState());
        assertFalse(
            "yet nothing is waiting on it, so onPause() must not report a startup failure",
            router.hasPendingOperationForSession(session)
        );
    }

    @Test
    public void pauseBeforeTheFirstFrame_stillHasThePendingStartToReject() {
        long session = sessions.beginSession();
        router.awaitStart(session);
        sessions.onCameraAttached();
        sessions.onOutputAvailable();
        sessions.onPreviewStarting();
        sessions.onPreviewStarted();

        assertTrue("a genuinely pending start is still reported", router.hasPendingOperationForSession(session));

        // onPause() -> Preview.failStartup(session) -> onPreviewStartFailed(session) -> settle(session).
        assertTrue(sessions.onStartFailure(session));
        assertEquals("the pending start is rejected", PreviewOperationRouter.Operation.START, router.settle(session));
        assertFalse(router.hasPendingOperationForSession(session));
        assertEquals("exactly once", PreviewOperationRouter.Operation.NONE, router.settle(session));
    }

    @Test
    public void operationRegisteredToAnotherSession_doesNotMakeTheCurrentSessionPending() {
        long staleSession = sessions.beginSession();
        router.awaitStart(staleSession);
        long currentSession = sessions.beginSession();

        assertTrue("a session-agnostic check would see an operation", router.isStartPending());
        assertFalse(
            "but the current session owns none, so it must not be marked failed",
            router.hasPendingOperationForSession(currentSession)
        );

        assertTrue("the stale start is left for the teardown sweep", router.consumeStart());
    }
}
