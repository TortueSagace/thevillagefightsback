package com.example.villagefightsback.platform;

/**
 * Base class for every shim event, mirroring the slice of Forge's {@code Event} the mod relies on: cancellation.
 *
 * <p>Cancellation is load-bearing here -- the mod cancels events at 45 call sites -- so whatever fires an event
 * MUST check {@link #isCanceled()} afterwards and actually act on it. An event that can be cancelled but whose
 * firing site ignores the flag is a silent behaviour change from the Forge build.
 */
public class Event {
    private boolean canceled;

    /** True when a handler has asked for the underlying vanilla action to be called off. */
    public boolean isCanceled() {
        return this.canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
