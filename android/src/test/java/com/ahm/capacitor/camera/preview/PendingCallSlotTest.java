package com.ahm.capacitor.camera.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression coverage for the Capacitor call slots behind issue #424: a second request must never
 * overwrite the callback id of a call that is still pending, and no two paths may settle the same
 * call.
 *
 * <p>The scenario that motivates the corrective pass is the last test: {@code stop()} rejects the
 * pending calls and then tears the fragment down, which makes CameraActivity abort the capture and
 * settle its operations a moment later. That second wave must find the slots already empty.
 */
public class PendingCallSlotTest {

    @Test
    public void aClaimedSlotReportsItsPendingCall() {
        PendingCallSlot slot = new PendingCallSlot("capture");

        assertFalse(slot.isPending());
        assertTrue(slot.claim("call-1"));
        assertTrue(slot.isPending());
        assertEquals("capture", slot.getName());
    }

    @Test
    public void aSecondRequestCannotOverwriteAPendingCall() {
        PendingCallSlot slot = new PendingCallSlot("capture");
        assertTrue(slot.claim("call-1"));

        assertFalse("the second request must be rejected, not swapped in", slot.claim("call-2"));
        assertEquals("the first call still owns the slot", "call-1", slot.consume());
    }

    @Test
    public void onlyTheFirstConsumerSettlesTheCall() {
        PendingCallSlot slot = new PendingCallSlot("start");
        slot.claim("call-1");

        assertEquals("call-1", slot.consume());
        assertNull("a second settlement finds nothing to settle", slot.consume());
        assertFalse(slot.isPending());
    }

    @Test
    public void anEmptySlotConsumesToNull() {
        PendingCallSlot slot = new PendingCallSlot("flip");

        assertNull(slot.consume());
        assertFalse(slot.isPending());
    }

    @Test
    public void anEmptyOrMissingCallbackIdIsNotAClaim() {
        PendingCallSlot slot = new PendingCallSlot("flip");

        assertFalse(slot.claim(null));
        assertFalse(slot.claim(""));
        assertFalse(slot.isPending());
        assertTrue("a real id still claims it", slot.claim("call-1"));
    }

    @Test
    public void theSlotIsReusableAfterItSettles() {
        PendingCallSlot slot = new PendingCallSlot("capture");

        slot.claim("call-1");
        assertEquals("call-1", slot.consume());

        assertTrue("a later capture claims the freed slot", slot.claim("call-2"));
        assertEquals("call-2", slot.consume());
    }

    // 7. Explicit stop followed by the native teardown cannot double-settle the same plugin call.

    @Test
    public void stopFollowedByNativeTeardownSettlesTheCaptureOnce() {
        PendingCallSlot capture = new PendingCallSlot("capture");
        PendingCallSlot flip = new PendingCallSlot("flip");
        PendingCallSlot start = new PendingCallSlot("start");

        capture.claim("capture-call");
        flip.claim("flip-call");
        start.claim("start-call");

        // stop() rejects everything still in flight before removing the fragment.
        assertEquals("capture-call", capture.consume());
        assertEquals("flip-call", flip.consume());
        assertEquals("start-call", start.consume());

        // The fragment teardown then aborts the capture and settles its stale operations. Every one
        // of those reports must be a no-op at the plugin layer.
        assertNull("the capture abort finds nothing left to reject", capture.consume());
        assertNull("the flip settlement finds nothing left to reject", flip.consume());
        assertNull("the start settlement finds nothing left to reject", start.consume());
    }
}
