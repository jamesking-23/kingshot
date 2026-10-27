package com.kingshot.camera.profiles

import com.kingshot.camera.capabilities.CameraCapabilities

enum class ProfileName(val label: String) {
    NATURAL("Natural"),
    CUSTOM("Custom");

    companion object {
        /** "Natural" while the requests equal what Natural would make on this camera; otherwise "Custom". */
        fun of(caps: CameraCapabilities, requestNr: Boolean, requestEdge: Boolean): ProfileName {
            val natural = NaturalProfile.resolve(caps)
            val nr = requestNr && caps.supportsNoiseReductionOff()
            val edge = requestEdge && caps.supportsEdgeOff()
            return if (nr == natural.requestNoiseReductionOff && edge == natural.requestEdgeOff) NATURAL else CUSTOM
        }
    }
}

/** Natural: ask for the least KingShot-side and device-side enhancement the camera lists, keep automatic exposure and focus. */
data class ResolvedProfile(
    val requestNoiseReductionOff: Boolean,
    val requestEdgeOff: Boolean,
    val notes: List<String>,
    val disclosure: String
)

object NaturalProfile {
    const val DISCLOSURE =
        "Natural asks the camera for noise reduction and edge enhancement OFF where it lists them. The JPEG may still be processed by the device."

    fun resolve(caps: CameraCapabilities): ResolvedProfile {
        val notes = mutableListOf<String>()
        val nr = caps.supportsNoiseReductionOff()
        val edge = caps.supportsEdgeOff()
        if (!nr) notes += "Noise reduction OFF is not listed by this camera; Natural does not request it."
        if (!edge) notes += "Edge enhancement OFF is not listed by this camera; Natural does not request it."
        return ResolvedProfile(nr, edge, notes, DISCLOSURE)
    }
}
