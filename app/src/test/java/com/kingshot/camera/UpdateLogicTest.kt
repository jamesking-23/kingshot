package com.kingshot.camera

import com.kingshot.camera.update.ReleaseAsset
import com.kingshot.camera.update.UpdatePolicy
import com.kingshot.camera.update.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test fun parsesVersionsWithOrWithoutV() {
        assertEquals(listOf(0, 5, 1), VersionCompare.parse("0.5.1"))
        assertEquals(listOf(1, 0), VersionCompare.parse("v1.0"))
        assertNull(VersionCompare.parse("0.5.1-rc1"))
        assertNull(VersionCompare.parse("latest"))
        assertNull(VersionCompare.parse(""))
    }

    @Test fun comparesNumericallyNotAsText() {
        assertTrue(VersionCompare.isNewer("0.5.0", "0.5.1"))
        assertTrue(VersionCompare.isNewer("0.9.0", "0.10.0"))
        assertTrue(VersionCompare.isNewer("0.5", "0.5.1"))
        assertFalse(VersionCompare.isNewer("0.5.1", "0.5.1"))
        assertFalse(VersionCompare.isNewer("0.5.2", "0.5.1"))
        assertFalse(VersionCompare.isNewer("0.5.0", "v0.5.0"))
    }

    @Test fun unreadableVersionsAreNeverTreatedAsNewer() {
        assertFalse(VersionCompare.isNewer("0.5.0", "garbage"))
        assertFalse(VersionCompare.isNewer("garbage", "0.5.1"))
    }

    @Test fun onlyGithubHostsOverHttpsAreAllowed() {
        assertTrue(UpdatePolicy.isAllowedUrl("https://api.github.com/repos/jamesking-23/kingshot/releases/latest"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://github.com/jamesking-23/kingshot/releases/download/v1/KingShot-1.apk"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://objects.githubusercontent.com/x"))
        assertTrue(UpdatePolicy.isAllowedUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("http://github.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://evil.com/github.com"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://github.com.evil.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://evilgithub.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("https://user@evil.com/x"))
        assertFalse(UpdatePolicy.isAllowedUrl("file:///sdcard/x.apk"))
        assertFalse(UpdatePolicy.isAllowedUrl("not a url"))
    }

    @Test fun picksTheApkAndItsChecksum() {
        val assets = listOf(
            ReleaseAsset("notes.txt", "https://github.com/a/notes.txt", 10),
            ReleaseAsset("KingShot-0.5.1.apk.sha256", "https://github.com/a/sum", 90),
            ReleaseAsset("KingShot-0.5.1.apk", "https://github.com/a/apk", 5000)
        )
        val pick = UpdatePolicy.pickAssets(assets)!!
        assertEquals("KingShot-0.5.1.apk", pick.apk.name)
        assertEquals("KingShot-0.5.1.apk.sha256", pick.checksum.name)
    }

    @Test fun refusesAReleaseWithoutAChecksumOrWithAnUnsafeUrl() {
        assertNull(UpdatePolicy.pickAssets(listOf(ReleaseAsset("KingShot-1.apk", "https://github.com/a/apk", 5))))
        val bad = listOf(
            ReleaseAsset("KingShot-1.apk", "https://evil.com/apk", 5),
            ReleaseAsset("KingShot-1.apk.sha256", "https://github.com/a/sum", 5)
        )
        assertNull(UpdatePolicy.pickAssets(bad))
    }

    @Test fun refusesOversizedApk() {
        val big = listOf(
            ReleaseAsset("KingShot-1.apk", "https://github.com/a/apk", UpdatePolicy.MAX_APK_BYTES + 1),
            ReleaseAsset("KingShot-1.apk.sha256", "https://github.com/a/sum", 90)
        )
        assertNull(UpdatePolicy.pickAssets(big))
    }

    @Test fun readsTheChecksumFileInSha256sumFormat() {
        val hex = "a".repeat(64)
        assertEquals(hex, UpdatePolicy.parseSha256("$hex  KingShot-1.apk\n"))
        assertEquals(hex, UpdatePolicy.parseSha256(hex.uppercase()))
        assertNull(UpdatePolicy.parseSha256("short"))
        assertNull(UpdatePolicy.parseSha256("z".repeat(64)))
    }

    @Test fun hexEncodesBytes() {
        assertEquals("00ff10", UpdatePolicy.hex(byteArrayOf(0, -1, 16)))
    }
}
