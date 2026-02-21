package com.mechanicalskies.vsshields.client;

/**
 * Thread-local state tracking which ship VS2 is currently rendering.
 * Set by VSGameEvents.renderShip listener, cleared by postRenderShip.
 * Read by CloakChunkLayerMixin to skip chunk rendering for cloaked ships.
 */
public final class CloakRenderState {
    private static long currentShipId = -1;
    private static boolean skipRendering = false;

    private CloakRenderState() {
    }

    public static void beginShipRender(long shipId, boolean shouldSkip) {
        currentShipId = shipId;
        skipRendering = shouldSkip;
    }

    public static void endShipRender() {
        currentShipId = -1;
        skipRendering = false;
    }

    public static boolean shouldSkipCurrentRender() {
        return skipRendering;
    }

    public static long getCurrentShipId() {
        return currentShipId;
    }
}
