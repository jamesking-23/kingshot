package com.kingshot.camera

import com.kingshot.camera.capture.CaptureIdGenerator
import com.kingshot.camera.media.PairNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PreservationTest {
    @Test fun idsNeverRepeatEvenInTheSameMillisecond() {
        val g = CaptureIdGenerator(ZoneId.of("UTC"))
        val ids = (1..1000).map { g.next(1_700_000_000_000L) }
        assertEquals(1000, ids.toSet().size)
    }

    @Test fun jpegAndDngOfOneShotShareAStem() {
        assertEquals(PairNaming.stem("KS_20261101_100000_3.jpg"), PairNaming.stem("KS_20261101_100000_3.dng"))
    }

    @Test fun groupsPairsAndSingles() {
        val p = PairNaming.pairs(listOf("KS_a_1.jpg", "KS_a_1.dng", "KS_a_2.jpg", "notes.txt", "KS_a_3.DNG"))
        assertEquals(3, p.size)
        assertTrue(p[0].isComplete)
        assertNull(p[1].dng)
        assertNull(p[2].jpeg)
    }

    @Test fun duplicateRoleForAStemIsReportedNotMerged() {
        val d = PairNaming.duplicates(listOf("KS_a_1.jpg", "KS_a_1.jpeg", "KS_a_2.jpg"))
        assertEquals(setOf("KS_a_1.jpg", "KS_a_1.jpeg"), d.toSet())
    }

    @Test fun detectsMediaStoreAutoRename() {
        assertTrue(PairNaming.looksAutoRenamed("KS_a_1 (1).jpg"))
        assertFalse(PairNaming.looksAutoRenamed("KS_a_1.jpg"))
    }
}
