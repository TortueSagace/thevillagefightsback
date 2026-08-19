package com.example.villagefightsback.platform;

/**
 * Stand-in for Forge's {@code EventPriority}. Listeners fire in declaration order here, highest first, which is
 * the ordering the mod's handlers were written against.
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
