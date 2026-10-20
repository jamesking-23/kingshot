package com.kingshot.camera.capabilities

/** Chooses the default rear camera from discovered capabilities. Never uses the device model. */
object CameraSelector {
    /**
     * First top-level rear camera that supports BACKWARD_COMPATIBLE and reports a JPEG size.
     * ASSUMPTION (unverified on device, DV-001): that this is the main camera. The report lists every camera.
     */
    fun pickRear(cameras: List<CameraCapabilities>): CameraCapabilities? = cameras.firstOrNull {
        it.physicalOf == null && it.isRear() && "BACKWARD_COMPATIBLE" in it.capabilities && it.jpegSizes.isNotEmpty()
    }
}
