package com.kingshot.camera.capture

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.ProcessingModes
import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.ControlStatus

/** What the user asked for. Wording avoids any "minimal"/"unprocessed" claim: these are requests only. */
data class ProcessingChoice(val requestNoiseReductionOff: Boolean, val requestEdgeOff: Boolean)

/**
 * Capability-checked plan for one still request. A mode is set only if the camera lists it as available.
 * [noiseReductionMode] / [edgeMode] are the values actually to request (null = leave to the device).
 */
data class StillPlan(
    val noiseReductionMode: Int?,
    val edgeMode: Int?,
    val noiseReductionWanted: Boolean,
    val edgeWanted: Boolean,
    val useContinuousPictureAf: Boolean,
    val notes: List<String>
)

object StillRequestPlanner {
    fun plan(caps: CameraCapabilities, choice: ProcessingChoice): StillPlan {
        val notes = mutableListOf<String>()
        var nr: Int? = null
        var edge: Int? = null
        if (choice.requestNoiseReductionOff) {
            if (caps.supportsNoiseReductionOff()) {
                nr = ProcessingModes.NR_OFF
            } else {
                notes += "Noise reduction OFF is not listed by this camera; not requested."
            }
        }
        if (choice.requestEdgeOff) {
            if (caps.supportsEdgeOff()) {
                edge = ProcessingModes.EDGE_OFF
            } else {
                notes += "Edge enhancement OFF is not listed by this camera; not requested."
            }
        }
        return StillPlan(nr, edge, choice.requestNoiseReductionOff, choice.requestEdgeOff, caps.supportsContinuousPictureAf(), notes)
    }

    /** Compares what was requested with what the TotalCaptureResult reported. */
    fun evaluate(plan: StillPlan, reportedNr: Int?, reportedEdge: Int?): List<ControlOutcome> = listOf(
        outcome("NOISE_REDUCTION_MODE", plan.noiseReductionWanted, plan.noiseReductionMode, reportedNr, ProcessingModes::noiseReductionName),
        outcome("EDGE_MODE", plan.edgeWanted, plan.edgeMode, reportedEdge, ProcessingModes::edgeName)
    )

    private fun outcome(
        name: String,
        wanted: Boolean,
        requested: Int?,
        reported: Int?,
        label: (Int?) -> String?
    ): ControlOutcome = when {
        !wanted -> ControlOutcome(name, null, label(reported), ControlStatus.NOT_REQUESTED)
        requested == null -> ControlOutcome(
            name, "OFF", label(reported), ControlStatus.UNAVAILABLE, "OFF is not listed as available on this camera, so it was not requested."
        )
        reported == null -> ControlOutcome(
            name, label(requested), null, ControlStatus.DIFFERS, "The capture result did not include this value."
        )
        reported == requested -> ControlOutcome(name, label(requested), label(reported), ControlStatus.HONORED)
        else -> ControlOutcome(
            name, label(requested), label(reported), ControlStatus.DIFFERS, "The device reported a different mode than requested."
        )
    }
}
