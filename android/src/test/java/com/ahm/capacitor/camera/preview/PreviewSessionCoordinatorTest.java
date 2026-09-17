package com.ahm.capacitor.camera.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for the startup race and readiness semantics of issue #424.
 *
 * <p>These are plain JVM tests over the extracted decision logic. They prove the ordering and
 * exactly-once rules that {@link Preview} relies on; they do not and cannot prove Camera1 hardware
 * behaviour, which stays a physical-device assertion.
 */
public class PreviewSessionCoordinatorTest {

    private PreviewSessionCoordinator coordinator;

    @Before
    public void setUp() {
        coordinator = new PreviewSessionCoordinator();
        coordinator.beginSession();
    }

    /** Drives a session all the way to READY and returns its id. */
    private long driveToReady() {
        long session = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();
        assertTrue(coordinator.onFirstFrame(session));
        return session;
    }

    // 1. Output target arrives before the camera: attaching the camera later triggers the start.

    @Test
    public void outputBeforeCamera_startsWhenCameraArrives() {
        assertFalse("no camera yet, nothing to start", coordinator.onOutputAvailable());
        assertEquals(PreviewSessionCoordinator.State.IDLE, coordinator.getState());

        assertTrue("the camera is the last input, so it must trigger the start", coordinator.onCameraAttached());
    }

    // 2. Camera arrives before the output target: the surface callback later triggers the start.

    @Test
    public void cameraBeforeOutput_startsWhenOutputArrives() {
        assertFalse("no output target yet, nothing to start", coordinator.onCameraAttached());
        assertEquals(PreviewSessionCoordinator.State.WAITING_FOR_OUTPUT, coordinator.getState());

        assertTrue("the output target is the last input, so it must trigger the start", coordinator.onOutputAvailable());
    }

    // 3. Repeated callbacks are idempotent and never start the preview twice.

    @Test
    public void repeatedCallbacks_areIdempotent() {
        coordinator.onCameraAttached();
        assertTrue(coordinator.onOutputAvailable());

        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        assertFalse("a repeated camera attachment must not restart the preview", coordinator.onCameraAttached());
        assertFalse("a repeated surface callback must not restart the preview", coordinator.onOutputAvailable());

        long session = coordinator.getSessionId();
        assertTrue(coordinator.onFirstFrame(session));
        assertFalse("a duplicate first frame must not announce readiness twice", coordinator.onFirstFrame(session));
        assertFalse("a repeated callback after readiness must not restart the preview", coordinator.onOutputAvailable());
    }

    @Test
    public void outputResize_restartsOnlyWhenAlreadyRunning() {
        assertFalse("resizing without inputs cannot start anything", coordinator.onOutputResized());

        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        assertTrue("a genuine size change must reconfigure the running preview", coordinator.onOutputResized());
    }

    // 4. A live camera, and even a successful startPreview(), is not readiness.

    @Test
    public void cameraAndStartedPreview_areNotReadiness() {
        coordinator.onCameraAttached();
        assertFalse("an attached camera is not readiness", coordinator.isReady());

        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        assertFalse("configuring is not readiness", coordinator.isReady());

        coordinator.onPreviewStarted();
        assertFalse("startPreview() returning is not readiness", coordinator.isReady());
        assertEquals(PreviewSessionCoordinator.State.WAITING_FIRST_FRAME, coordinator.getState());
    }

    // 5. The first preview frame transitions to ready and announces exactly once.

    @Test
    public void firstFrame_becomesReadyAndAnnouncesOnce() {
        long session = driveToReady();

        assertTrue(coordinator.isReady());
        assertEquals(PreviewSessionCoordinator.State.READY, coordinator.getState());
        assertFalse("readiness is announced exactly once", coordinator.onFirstFrame(session));
    }

    @Test
    public void firstFrameFromAnOldSession_cannotMarkTheNewOneReady() {
        long staleSession = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        long newSession = coordinator.beginSession();
        assertNotEquals(staleSession, newSession);
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        assertFalse("a frame from the released camera must be ignored", coordinator.onFirstFrame(staleSession));
        assertFalse(coordinator.isReady());

        assertTrue("the current session still becomes ready on its own frame", coordinator.onFirstFrame(newSession));
    }

    // 6. A startup exception announces a failure exactly once.

    @Test
    public void startupFailure_isAnnouncedOnce() {
        long session = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();

        assertTrue(coordinator.onStartFailure(session));
        assertFalse("the pending start call must not be rejected twice", coordinator.onStartFailure(session));
        assertEquals(PreviewSessionCoordinator.State.FAILED, coordinator.getState());
        assertFalse(coordinator.isReady());
    }

    @Test
    public void failedSession_cannotLaterBecomeReady() {
        long session = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onStartFailure(session);

        assertFalse("a late frame must not resolve a start that already rejected", coordinator.onFirstFrame(session));
        assertFalse(coordinator.isReady());
        assertFalse("a failed session does not silently retry", coordinator.onOutputAvailable());
    }

    @Test
    public void startupFailureFromAnOldSession_isIgnored() {
        long staleSession = coordinator.getSessionId();
        coordinator.beginSession();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();

        assertFalse("a failure from a replaced session must not reject the new start", coordinator.onStartFailure(staleSession));
        assertNotEquals(PreviewSessionCoordinator.State.FAILED, coordinator.getState());
    }

    // 7. The first-frame timeout rejects the start once and leaves a consistent state.

    @Test
    public void timeout_rejectsOnceAndLeavesSessionNotReady() {
        long session = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        assertTrue(coordinator.onTimeout(session));
        assertFalse("the timeout must not reject the start twice", coordinator.onTimeout(session));
        assertEquals(PreviewSessionCoordinator.State.FAILED, coordinator.getState());
        assertFalse(coordinator.isReady());
    }

    @Test
    public void timeout_doesNotFireForAReadyOrStaleSession() {
        long session = driveToReady();
        assertFalse("a late timeout must not reject an already resolved start", coordinator.onTimeout(session));
        assertTrue(coordinator.isReady());

        long staleSession = session;
        coordinator.beginSession();
        assertFalse("a timeout from a replaced session must be ignored", coordinator.onTimeout(staleSession));
    }

    @Test
    public void timeoutAfterFailure_doesNotRejectTwice() {
        long session = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        assertTrue(coordinator.onStartFailure(session));

        assertFalse("the timeout must not add a second rejection", coordinator.onTimeout(session));
    }

    // 11. Pause/stop/surface destruction clears readiness and invalidates stale callbacks.

    @Test
    public void cameraDetach_clearsReadiness() {
        driveToReady();

        coordinator.onCameraDetached();

        assertFalse("a detached camera can no longer be captured from", coordinator.isReady());
        assertEquals(PreviewSessionCoordinator.State.IDLE, coordinator.getState());
        assertFalse(coordinator.isCameraAttached());
    }

    @Test
    public void outputLoss_clearsReadinessAndRecoversWhenTheOutputReturns() {
        driveToReady();

        coordinator.onOutputLost();
        assertFalse("a destroyed surface means the preview is no longer usable", coordinator.isReady());
        assertEquals(PreviewSessionCoordinator.State.WAITING_FOR_OUTPUT, coordinator.getState());

        assertTrue("a recreated surface must reconfigure and restart the preview", coordinator.onOutputAvailable());
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();

        long session = coordinator.getSessionId();
        assertFalse("the start call was already resolved, so readiness must not be announced again", coordinator.onFirstFrame(session));
        assertTrue("the preview is usable again", coordinator.isReady());
    }

    @Test
    public void beginSession_invalidatesEveryCallbackOfThePreviousLifecycle() {
        long staleSession = driveToReady();

        coordinator.beginSession();

        assertFalse("readiness is dropped by the new lifecycle", coordinator.isReady());
        assertFalse(coordinator.isCameraAttached());
        assertFalse(coordinator.isOutputAvailable());
        assertFalse(coordinator.onFirstFrame(staleSession));
        assertFalse(coordinator.onStartFailure(staleSession));
        assertFalse(coordinator.onTimeout(staleSession));
    }

    // 12. Cleanup after a failure still allows a later clean start.

    @Test
    public void cleanStartIsPossibleAfterAFailedSession() {
        long failedSession = coordinator.getSessionId();
        coordinator.onCameraAttached();
        coordinator.onOutputAvailable();
        coordinator.onPreviewStarting();
        assertTrue(coordinator.onStartFailure(failedSession));

        coordinator.onCameraDetached();
        coordinator.onOutputLost();

        long newSession = coordinator.beginSession();
        assertNotEquals(failedSession, newSession);
        assertEquals(PreviewSessionCoordinator.State.IDLE, coordinator.getState());

        assertFalse(coordinator.onCameraAttached());
        assertTrue(coordinator.onOutputAvailable());
        coordinator.onPreviewStarting();
        coordinator.onPreviewStarted();
        assertTrue("the restarted session announces its own readiness", coordinator.onFirstFrame(newSession));
        assertTrue(coordinator.isReady());
    }
}
