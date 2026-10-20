package com.kingshot.camera.raw

/** Wraps a resource so that [close] reaches the delegate exactly once, whatever path calls it. */
class OnceCloseable(private val delegate: AutoCloseable) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }
}

/**
 * Matches images to capture results by sensor timestamp. Bounded and time-limited.
 *
 * Ownership rules (so every Image is closed exactly once):
 *  - A matched image is handed to the caller inside [Match]; the caller must close it.
 *  - An orphaned or evicted image is handed to the caller inside [Orphan.Image]; the caller must close it.
 *  - Images still held at [closeAll] are closed by the matcher.
 * The matcher never closes an image it has already handed out.
 *
 * Assumes Image.timestamp equals the result's SENSOR_TIMESTAMP. That assumption is unverified on device (DV-001).
 */
class TimestampMatcher<I : AutoCloseable, R>(private val capacity: Int, private val timeoutMs: Long) {
    init {
        require(capacity > 0 && timeoutMs > 0)
    }

    data class Match<I, R>(val timestamp: Long, val image: I, val result: R)

    sealed class Orphan<out I, out R> {
        data class Image<I>(val timestamp: Long, val image: I, val reason: String) : Orphan<I, Nothing>()
        data class Result<R>(val timestamp: Long, val result: R, val reason: String) : Orphan<Nothing, R>()
    }

    private class Held<T>(val value: T, val atMs: Long)

    private val images = LinkedHashMap<Long, Held<I>>()
    private val results = LinkedHashMap<Long, Held<R>>()
    private val lock = Any()

    /** Outcome of one call: at most one match plus anything evicted to stay within capacity. */
    data class Outcome<I, R>(val match: Match<I, R>?, val orphans: List<Orphan<I, R>>)

    fun onImage(timestamp: Long, image: I, nowMs: Long): Outcome<I, R> = synchronized(lock) {
        val result = results.remove(timestamp)
        if (result != null) return Outcome(Match(timestamp, image, result.value), emptyList())
        val orphans = mutableListOf<Orphan<I, R>>()
        images.put(timestamp, Held(image, nowMs))?.let {
            // Duplicate timestamp: hand the older image back instead of leaking it.
            orphans += Orphan.Image(timestamp, it.value, "duplicate image timestamp")
        }
        while (images.size > capacity) {
            val oldest = images.entries.first()
            images.remove(oldest.key)
            orphans += Orphan.Image(oldest.key, oldest.value.value, "evicted: matcher over capacity")
        }
        Outcome(null, orphans)
    }

    fun onResult(timestamp: Long, result: R, nowMs: Long): Outcome<I, R> = synchronized(lock) {
        val image = images.remove(timestamp)
        if (image != null) return Outcome(Match(timestamp, image.value, result), emptyList())
        val orphans = mutableListOf<Orphan<I, R>>()
        results.put(timestamp, Held(result, nowMs))
        while (results.size > capacity) {
            val oldest = results.entries.first()
            results.remove(oldest.key)
            orphans += Orphan.Result(oldest.key, oldest.value.value, "evicted: matcher over capacity")
        }
        Outcome(null, orphans)
    }

    /** Returns everything that has waited longer than the timeout. Call periodically. */
    fun sweep(nowMs: Long): List<Orphan<I, R>> = synchronized(lock) {
        val out = mutableListOf<Orphan<I, R>>()
        val imgIt = images.entries.iterator()
        while (imgIt.hasNext()) {
            val e = imgIt.next()
            if (nowMs - e.value.atMs >= timeoutMs) {
                imgIt.remove()
                out += Orphan.Image(e.key, e.value.value, "timeout: no result with this timestamp")
            }
        }
        val resIt = results.entries.iterator()
        while (resIt.hasNext()) {
            val e = resIt.next()
            if (nowMs - e.value.atMs >= timeoutMs) {
                resIt.remove()
                out += Orphan.Result(e.key, e.value.value, "timeout: no image with this timestamp")
            }
        }
        out
    }

    /** Shutdown: closes every image still held. Returns how many were closed. */
    fun closeAll(): Int = synchronized(lock) {
        var n = 0
        for (h in images.values) {
            try {
                h.value.close()
            } catch (_: Exception) {
                // Closing during shutdown must not throw; the image is gone either way.
            }
            n++
        }
        images.clear()
        results.clear()
        n
    }

    fun pendingImages(): Int = synchronized(lock) { images.size }
    fun pendingResults(): Int = synchronized(lock) { results.size }
}
