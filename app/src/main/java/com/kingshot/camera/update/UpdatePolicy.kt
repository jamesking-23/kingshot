package com.kingshot.camera.update

import java.net.URI

data class ReleaseAsset(val name: String, val url: String, val size: Long)

data class ReleaseInfo(val tag: String, val notes: String, val assets: List<ReleaseAsset>)

/** The APK to install and the checksum file that must match it. */
data class UpdateAssets(val apk: ReleaseAsset, val checksum: ReleaseAsset)

/** Compares release versions such as "0.5.1". Anything that is not plain dotted numbers (for example "0.5.1-rc1") is unreadable and never counts as newer. */
object VersionCompare {
    fun parse(text: String): List<Int>? {
        val t = text.trim().removePrefix("v").removePrefix("V")
        if (t.isEmpty()) return null
        val parts = t.split('.')
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return if (nums.any { it < 0 }) null else nums
    }

    fun isNewer(installed: String, candidate: String): Boolean {
        val a = parse(installed) ?: return false
        val b = parse(candidate) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }
}

/** Rules for what an update check may contact and accept. No network code here, so it can be tested on a JVM. */
object UpdatePolicy {
    const val REPO = "jamesking-23/kingshot"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    const val MAX_TEXT_BYTES = 1024L * 1024

    private val exactHosts = setOf("api.github.com", "github.com")
    private const val ASSET_HOST_SUFFIX = ".githubusercontent.com"

    /** HTTPS only, no user info, and only GitHub's own hosts (including the hosts GitHub redirects release downloads to). */
    fun isAllowedUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: java.net.URISyntaxException) {
            return false
        }
        if (uri.scheme != "https" || uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return host in exactHosts || (host.endsWith(ASSET_HOST_SUFFIX) && host.length > ASSET_HOST_SUFFIX.length)
    }

    /** The release must carry `KingShot-<version>.apk` and a matching `.apk.sha256`, both on allowed URLs, and the APK must not be oversized. */
    fun pickAssets(assets: List<ReleaseAsset>): UpdateAssets? {
        val apk = assets.firstOrNull { it.name.startsWith("KingShot-") && it.name.endsWith(".apk") } ?: return null
        val sum = assets.firstOrNull { it.name == apk.name + ".sha256" } ?: return null
        if (!isAllowedUrl(apk.url) || !isAllowedUrl(sum.url)) return null
        if (apk.size <= 0 || apk.size > MAX_APK_BYTES) return null
        return UpdateAssets(apk, sum)
    }

    /** First 64-hex-digit word in a `sha256sum` style file, lower-cased; null if there is none. */
    fun parseSha256(text: String): String? {
        val word = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty().lowercase()
        return if (word.length == 64 && word.all { it in '0'..'9' || it in 'a'..'f' }) word else null
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
