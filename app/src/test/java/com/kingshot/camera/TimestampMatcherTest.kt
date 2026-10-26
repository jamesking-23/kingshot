package com.kingshot.camera

import com.kingshot.camera.raw.OnceCloseable
import com.kingshot.camera.raw.TimestampMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampMatcherTest {
    private class Fake : AutoCloseable {
        var closes = 0
        override fun close() {
            closes++
        }
    }

    private fun matcher(capacity: Int = 3, timeout: Long = 1000) = TimestampMatcher<Fake, String>(capacity, timeout)

    @Test
    fun imageThenResultMatches() {
        val m = matcher()
        val img = Fake()
        assertNull(m.onImage(10, img, 0).match)
        val out = m.onResult(10, "r", 5)
        assertSame(img, out.match.image)
        assertEquals("r", out.match.result)
        assertEquals(0, m.pendingImages())
    }

    @Test
    fun resultThenImageMatches() {
        val m = matcher()
        assertNull(m.onResult(7, "r", 0).match)
        val img = Fake()
        val out = m.onImage(7, img, 1)
        assertSame(img, out.match.image)
        assertEquals(0, m.pendingResults())
    }

    @Test
    fun matcherNeverClosesWhatItHandsOut() {
        val m = matcher()
        val img = Fake()
        m.onImage(1, img, 0)
        val match = m.onResult(1, "r", 0).match
        assertNotNull(match)
        assertEquals(0, img.closes)
        assertEquals(0, m.closeAll())
        assertEquals(0, img.closes)
    }

    @Test
    fun timeoutReturnsOrphansOnce() {
        val m = matcher(timeout = 100)
        val img = Fake()
        m.onImage(1, img, 0)
        m.onResult(2, "r", 0)
        assertTrue(m.sweep(99).isEmpty())
        val orphans = m.sweep(100)
        assertEquals(2, orphans.size)
        assertTrue(orphans.any { it is TimestampMatcher.Orphan.Image })
        assertTrue(orphans.any { it is TimestampMatcher.Orphan.Result })
        assertTrue(m.sweep(1000).isEmpty())
        assertEquals(0, img.closes) // caller owns orphans
    }

    @Test
    fun overCapacityEvictsOldestAndHandsItBack() {
        val m = matcher(capacity = 2)
        val a = Fake()
        val b = Fake()
        val c = Fake()
        m.onImage(1, a, 0)
        m.onImage(2, b, 0)
        val out = m.onImage(3, c, 0)
        assertEquals(1, out.orphans.size)
        val o = out.orphans[0] as TimestampMatcher.Orphan.Image
        assertSame(a, o.image)
        assertEquals(2, m.pendingImages())
    }

    @Test
    fun duplicateTimestampDoesNotLeakTheOlderImage() {
        val m = matcher()
        val a = Fake()
        val b = Fake()
        m.onImage(5, a, 0)
        val out = m.onImage(5, b, 1)
        assertSame(a, (out.orphans.single() as TimestampMatcher.Orphan.Image).image)
        assertEquals(1, m.pendingImages())
    }

    @Test
    fun closeAllClosesHeldImagesExactlyOnce() {
        val m = matcher()
        val a = Fake()
        val b = Fake()
        m.onImage(1, a, 0)
        m.onImage(2, b, 0)
        assertEquals(2, m.closeAll())
        assertEquals(1, a.closes)
        assertEquals(1, b.closes)
        assertEquals(0, m.closeAll())
    }

    @Test
    fun everyImageEndsUpInExactlyOneBucket() {
        val m = matcher(capacity = 2, timeout = 50)
        val images = (1..20).map { Fake() }
        var matched = 0
        var orphaned = 0
        images.forEachIndexed { i, img ->
            val ts = i.toLong()
            val out = m.onImage(ts, img, ts * 10)
            if (out.match != null) matched++
            orphaned += out.orphans.count { it is TimestampMatcher.Orphan.Image }
            if (i % 3 == 0) {
                val r = m.onResult(ts, "r$i", ts * 10)
                if (r.match != null) matched++
                orphaned += r.orphans.count { it is TimestampMatcher.Orphan.Image }
            }
        }
        orphaned += m.sweep(10_000).count { it is TimestampMatcher.Orphan.Image }
        val closedByShutdown = m.closeAll()
        assertEquals(images.size, matched + orphaned + closedByShutdown)
    }

    @Test
    fun onceCloseableClosesDelegateOnce() {
        val f = Fake()
        val o = OnceCloseable(f)
        o.close()
        o.close()
        o.close()
        assertEquals(1, f.closes)
        assertTrue(o.isClosed)
    }
}
