package com.example.villagefightsback.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Stand-in for Forge's {@code net.minecraftforge.api.distmarker.Dist}, so the many call sites that ask "am I on
 * the client?" read the same on this branch as they do on the Forge one.
 *
 * <p>Fabric's own name for the idea is {@link EnvType}; this enum is a thin alias over it.
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    /** The side this JVM is running as. */
    public static Dist current() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? CLIENT : DEDICATED_SERVER;
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
