package com.ahm.capacitor.camera.preview;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Framework-free holder for the callback id of one pending Capacitor call.
 *
 * <p>Issue #424 showed two ways a saved {@code PluginCall} can go wrong: a second request
 * overwriting the id of a call that is still pending, so the first one hangs forever; and two
 * different code paths settling the same id, so the JavaScript promise is completed twice.
 *
 * <p>The slot closes both. A request must {@link #claim(String)} it - which fails while another
 * request holds it - and whichever path {@link #consume()}s it first is the only one that may
 * settle the call. Every other path, including a native lifecycle callback arriving after an
 * explicit {@code stop()} already rejected the call, finds the slot empty and does nothing.
 */
class PendingCallSlot {

    private static final String EMPTY = "";

    private final String name;
    private final AtomicReference<String> callbackId = new AtomicReference<>(EMPTY);

    /** @param name human-readable name of the operation, used only in diagnostics */
    PendingCallSlot(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    /**
     * Claims the slot for a request.
     *
     * @return true when the caller now owns the slot; false when another call is already pending,
     *         in which case the caller must reject rather than overwrite it
     */
    boolean claim(String callbackId) {
        if (callbackId == null || callbackId.isEmpty()) {
            return false;
        }
        return this.callbackId.compareAndSet(EMPTY, callbackId);
    }

    /**
     * Takes the pending callback id and empties the slot.
     *
     * @return the callback id for the caller to settle, or null when nothing was pending - which
     *         is what makes a late or duplicate settlement a no-op
     */
    String consume() {
        String claimed = callbackId.getAndSet(EMPTY);
        if (claimed == null || claimed.isEmpty()) {
            return null;
        }
        return claimed;
    }

    boolean isPending() {
        String claimed = callbackId.get();
        return claimed != null && !claimed.isEmpty();
    }

    @Override
    public String toString() {
        return "PendingCallSlot{" + name + ", pending=" + isPending() + "}";
    }
}
