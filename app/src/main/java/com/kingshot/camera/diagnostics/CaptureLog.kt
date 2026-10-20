package com.kingshot.camera.diagnostics

/** Bounded, thread-safe in-memory log. Never holds personal data; the user decides if it is exported. */
class CaptureLog(private val capacity: Int = 500, private val clockMs: () -> Long = { System.currentTimeMillis() }) {
    private val lines = ArrayDeque<String>()
    private val lock = Any()
    @Volatile
    var onChanged: (() -> Unit)? = null

    fun add(message: String) {
        synchronized(lock) {
            if (lines.size >= capacity) lines.removeFirst()
            lines.addLast("${clockMs()} $message")
        }
        onChanged?.invoke()
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun text(): String = snapshot().joinToString("\n")
}
