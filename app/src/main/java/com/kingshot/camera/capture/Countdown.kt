package com.kingshot.camera.capture

/** Self-timer arithmetic on elapsed milliseconds, so it can be tested without a clock. */
class Countdown(private val totalSeconds: Int) {
    private val totalMs = totalSeconds.coerceAtLeast(0) * 1000L

    fun finished(elapsedMs: Long): Boolean = elapsedMs.coerceAtLeast(0L) >= totalMs

    /** Whole seconds still to run, rounded up, so the display shows 3, 2, 1 and never 0 before the shot. */
    fun secondsLeft(elapsedMs: Long): Int {
        val left = totalMs - elapsedMs.coerceAtLeast(0L)
        return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
    }
}
