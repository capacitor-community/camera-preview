package com.ahm.capacitor.camera.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for the capture half of issue #424: a capture attempted before the preview
 * is ready must reject instead of reaching {@code Camera.takePicture()}, concurrent captures must
 * not overwrite each other, and a {@code RuntimeException} from the Camera1 calls must become a
 * single error callback rather than an uncaught exception that terminates the process.
 *
 * <p>These are plain JVM tests over the extracted decision logic. The Camera1 work itself is
 * injected, so the tests prove the gating and exactly-once rules, not hardware behaviour.
 */
public class CaptureCoordinatorTest {

    private CaptureCoordinator coordinator;
    private List<String> errors;
    private CaptureCoordinator.ErrorReporter reporter;

    @Before
    public void setUp() {
        coordinator = new CaptureCoordinator();
        errors = new ArrayList<>();
        reporter = (message) -> errors.add(message);
    }

    // 8. A capture before readiness rejects and never invokes takePicture.

    @Test
    public void captureBeforeReadiness_rejectsAndDoesNotInvokeTakePicture() {
        boolean[] takePictureInvoked = { false };

        long token = coordinator.beginCapture(true, false, true, reporter);

        assertEquals("a capture must not start before the preview is ready", CaptureCoordinator.NO_CAPTURE, token);
        assertEquals(1, errors.size());
        assertEquals(CaptureCoordinator.ERROR_PREVIEW_NOT_READY, errors.get(0));
        assertFalse("the capture slot must stay free after a refusal", coordinator.isCaptureInProgress());

        // The caller only reaches the Camera1 work when beginCapture() accepted.
        if (token != CaptureCoordinator.NO_CAPTURE) {
            coordinator.performCapture(token, () -> takePictureInvoked[0] = true, reporter);
        }
        assertFalse("Camera.takePicture() must never be reached", takePictureInvoked[0]);
    }

    @Test
    public void captureWithoutACamera_rejects() {
        assertEquals(CaptureCoordinator.NO_CAPTURE, coordinator.beginCapture(false, true, true, reporter));
        assertEquals(1, errors.size());
        assertEquals(CaptureCoordinator.ERROR_NO_CAMERA, errors.get(0));
    }

    @Test
    public void captureWhileNotResumed_rejects() {
        assertEquals(CaptureCoordinator.NO_CAPTURE, coordinator.beginCapture(true, true, false, reporter));
        assertEquals(1, errors.size());
        assertEquals(CaptureCoordinator.ERROR_NOT_RESUMED, errors.get(0));
    }

    @Test
    public void checkCanCapture_allowsOnlyAReadyResumedCamera() {
        assertNull(coordinator.checkCanCapture(true, true, true));
        assertEquals(CaptureCoordinator.ERROR_NO_CAMERA, coordinator.checkCanCapture(false, true, true));
        assertEquals(CaptureCoordinator.ERROR_NOT_RESUMED, coordinator.checkCanCapture(true, true, false));
        assertEquals(CaptureCoordinator.ERROR_PREVIEW_NOT_READY, coordinator.checkCanCapture(true, false, true));
    }

    // 9. A concurrent capture rejects and does not disturb the first one.

    @Test
    public void concurrentCapture_rejectsAndLeavesTheFirstCaptureInFlight() {
        long first = coordinator.beginCapture(true, true, true, reporter);
        assertNotEquals(CaptureCoordinator.NO_CAPTURE, first);
        assertTrue(coordinator.isCaptureInProgress());

        assertEquals(
            "the second capture must be refused",
            CaptureCoordinator.NO_CAPTURE,
            coordinator.beginCapture(true, true, true, reporter)
        );
        assertEquals(1, errors.size());
        assertEquals(CaptureCoordinator.ERROR_CAPTURE_IN_PROGRESS, errors.get(0));

        assertTrue("the first capture is still the one that owns the slot", coordinator.isCaptureActive(first));
        assertTrue("and it is the one that settles", coordinator.finishCapture(first));
        assertFalse(coordinator.isCaptureInProgress());
    }

    @Test
    public void onlyOneCallerCanSettleACapture() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        assertTrue("the first caller owns the outcome", coordinator.finishCapture(token));
        assertFalse("a late picture callback must not produce a second outcome", coordinator.finishCapture(token));
    }

    @Test
    public void captureIsPossibleAgainAfterTheFirstOneCompletes() {
        long first = coordinator.beginCapture(true, true, true, reporter);
        assertTrue(coordinator.finishCapture(first));

        long second = coordinator.beginCapture(true, true, true, reporter);
        assertNotEquals("repeated capture must keep working", CaptureCoordinator.NO_CAPTURE, second);
        assertNotEquals("tokens are never reused", first, second);
        assertTrue(errors.isEmpty());
    }

    // 10. A RuntimeException from the capture setup or takePicture becomes an error callback and
    //     does not escape. This is the exact crash reported in issue #424.

    @Test
    public void runtimeExceptionFromTakePicture_becomesAnErrorCallbackAndDoesNotEscape() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        coordinator.performCapture(
            token,
            () -> {
                throw new RuntimeException("takePicture failed");
            },
            reporter
        );

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("takePicture failed"));
        assertTrue(errors.get(0).startsWith("failed to take picture: "));
        assertFalse("the capture slot must be released so the next capture can run", coordinator.isCaptureInProgress());
        assertFalse("the failure already settled the capture", coordinator.finishCapture(token));
    }

    @Test
    public void checkedExceptionFromCaptureSetup_becomesAnErrorCallback() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        coordinator.performCapture(
            token,
            () -> {
                throw new java.io.IOException("parameters unavailable");
            },
            reporter
        );

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("IOException"));
        assertFalse(coordinator.isCaptureInProgress());
    }

    @Test
    public void successfulCaptureSetup_leavesTheSlotForThePictureCallback() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        coordinator.performCapture(
            token,
            () -> {
                /* Camera.takePicture() returned; the JPEG callback settles the capture. */
            },
            reporter
        );

        assertTrue(errors.isEmpty());
        assertTrue("the capture is still in flight until the picture callback arrives", coordinator.isCaptureInProgress());
        assertTrue(coordinator.finishCapture(token));
    }

    // Corrective pass - capture abort and token behaviour (issue #424 follow-up).

    // 1. An active capture aborted for a camera switch rejects once and releases the slot.

    @Test
    public void abortForCameraSwitch_endsTheCaptureOnceAndFreesTheSlot() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        assertEquals("the abort names the capture it ended", token, coordinator.abortActiveCapture());
        assertFalse("the slot is free for a later capture", coordinator.isCaptureInProgress());
        assertEquals("a second abort finds nothing to end", CaptureCoordinator.NO_CAPTURE, coordinator.abortActiveCapture());
        assertFalse("the aborted capture can never settle again", coordinator.finishCapture(token));
    }

    // 2. An active capture aborted for output loss behaves identically; the caller supplies the
    //    reason, so one abort path covers pause, switch, output loss and destruction.

    @Test
    public void abortForOutputLoss_endsTheCaptureOnceAndFreesTheSlot() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        assertEquals(token, coordinator.abortActiveCapture());
        assertFalse(coordinator.isCaptureInProgress());
        assertFalse("the JPEG callback for the lost output settles nothing", coordinator.finishCapture(token));
    }

    // 3. Aborting when no capture is active is a no-op.

    @Test
    public void abortWithNoActiveCapture_isANoOp() {
        assertEquals(CaptureCoordinator.NO_CAPTURE, coordinator.abortActiveCapture());
        assertFalse(coordinator.isCaptureInProgress());
        assertTrue("nothing is reported for a capture that never existed", errors.isEmpty());

        long token = coordinator.beginCapture(true, true, true, reporter);
        assertNotEquals("a capture is still possible afterwards", CaptureCoordinator.NO_CAPTURE, token);
    }

    // 4. A JPEG completion for an aborted capture is ignored.

    @Test
    public void pictureCallbackForAnAbortedCapture_isIgnored() {
        long token = coordinator.beginCapture(true, true, true, reporter);
        coordinator.abortActiveCapture();

        assertFalse("the late picture callback must not produce a second outcome", coordinator.finishCapture(token));
        assertFalse(coordinator.isCaptureActive(token));
    }

    // 5. An old JPEG completion cannot settle a newer capture. This is why the token replaced the
    //    boolean: with a boolean, the stale callback below would have ended capture two.

    @Test
    public void oldPictureCallbackCannotSettleANewerCapture() {
        long first = coordinator.beginCapture(true, true, true, reporter);
        coordinator.abortActiveCapture();

        long second = coordinator.beginCapture(true, true, true, reporter);
        assertNotEquals(first, second);

        assertFalse("the stale callback must not settle the newer capture", coordinator.finishCapture(first));
        assertTrue("the newer capture is untouched and still in flight", coordinator.isCaptureActive(second));
        assertTrue("and only its own callback settles it", coordinator.finishCapture(second));
    }

    // 6. A new capture can succeed after an older one was aborted.

    @Test
    public void captureSucceedsAfterAnEarlierCaptureWasAborted() {
        long aborted = coordinator.beginCapture(true, true, true, reporter);
        coordinator.abortActiveCapture();

        long token = coordinator.beginCapture(true, true, true, reporter);
        assertNotEquals(CaptureCoordinator.NO_CAPTURE, token);
        assertTrue(errors.isEmpty());

        coordinator.performCapture(
            token,
            () -> {
                /* Camera.takePicture() returned. */
            },
            reporter
        );
        assertTrue("its own callback settles it exactly once", coordinator.finishCapture(token));
        assertFalse(coordinator.finishCapture(aborted));
        assertTrue(errors.isEmpty());
    }

    // 9 (capture half). A flip refused while a capture is in flight must leave that capture alone.

    @Test
    public void refusingAFlipDoesNotDisturbTheActiveCapture() {
        long token = coordinator.beginCapture(true, true, true, reporter);

        PreviewOperationRouter router = new PreviewOperationRouter();
        assertEquals(
            CaptureCoordinator.ERROR_CAPTURE_IN_PROGRESS,
            router.checkCanFlip(true, true, true, coordinator.isCaptureInProgress())
        );

        assertTrue("the capture is still in flight", coordinator.isCaptureActive(token));
        assertTrue("and settles normally", coordinator.finishCapture(token));
    }

    @Test
    public void describe_namesTheOperationWithoutAStackTrace() {
        assertEquals("RuntimeException: takePicture failed", CaptureCoordinator.describe(new RuntimeException("takePicture failed")));
        assertEquals("IllegalStateException", CaptureCoordinator.describe(new IllegalStateException()));
        assertEquals("unknown error", CaptureCoordinator.describe(null));
    }
}
