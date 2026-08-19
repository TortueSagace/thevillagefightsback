package com.example.villagefightsback.platform;

import java.util.function.Supplier;

/**
 * Stand-in for Forge's {@code DistExecutor}. Only the one method the mod actually uses is provided.
 *
 * <p>The nested-supplier shape ({@code () -> () -> clientOnlyCall()}) is not decoration: it is what keeps the
 * client-only class out of the server's verifier. The outer lambda is only ever invoked on the right side, so a
 * dedicated server never loads the class named inside it.
 */
public final class DistExecutor {
    private DistExecutor() {}

    /** Runs {@code toRun.get().run()} only when this JVM is on {@code dist}; otherwise does nothing. */
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
        if (Dist.current() == dist) {
            toRun.get().run();
        }
    }
}
