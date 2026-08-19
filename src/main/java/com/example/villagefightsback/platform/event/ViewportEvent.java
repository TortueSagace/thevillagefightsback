package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;

/** Camera and fog, per frame. Client-only; fired from GameRenderer and FogRenderer mixins. */
public class ViewportEvent extends Event {
    private final double partialTick;

    public ViewportEvent(double partialTick) {
        this.partialTick = partialTick;
    }

    public double getPartialTick() {
        return this.partialTick;
    }

    /** The camera angles for this frame, after vanilla has worked them out. The mod rolls these for reversed gravity. */
    public static class ComputeCameraAngles extends ViewportEvent {
        private float yaw;
        private float pitch;
        private float roll;

        public ComputeCameraAngles(double partialTick, float yaw, float pitch, float roll) {
            super(partialTick);
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getYaw() {
            return this.yaw;
        }

        public float getPitch() {
            return this.pitch;
        }

        public float getRoll() {
            return this.roll;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public void setRoll(float roll) {
            this.roll = roll;
        }
    }

    /** The fog colour for this frame. */
    public static class ComputeFogColor extends ViewportEvent {
        private float red;
        private float green;
        private float blue;

        public ComputeFogColor(double partialTick, float red, float green, float blue) {
            super(partialTick);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public float getRed() {
            return this.red;
        }

        public float getGreen() {
            return this.green;
        }

        public float getBlue() {
            return this.blue;
        }

        public void setRed(float red) {
            this.red = red;
        }

        public void setGreen(float green) {
            this.green = green;
        }

        public void setBlue(float blue) {
            this.blue = blue;
        }
    }
}
