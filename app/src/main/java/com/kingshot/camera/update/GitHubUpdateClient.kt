package com.kingshot.camera.update

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * The only network code in the app. It runs only when the user taps "Check for updates" or "Download and install", talks only to GitHub
 * over HTTPS (see [UpdatePolicy]), follows redirects by hand so every hop is checked, and sends no identifiers beyond a User-Agent.
 */
class GitHubUpdateClient(private val userAgent: String) {
    class UpdateException(message: String) : Exception(message)

    fun fetchLatest(): ReleaseInfo {
        val text = getText(UpdatePolicy.LATEST_URL, "application/vnd.github+json")
        try {
            val o = JSONObject(text)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) throw UpdateException("The newest release is not a final release.")
            val arr = o.optJSONArray("assets")
            val assets = (0 until (arr?.length() ?: 0)).map { i ->
                val a = arr!!.getJSONObject(i)
                ReleaseAsset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size"))
            }
            return ReleaseInfo(o.getString("tag_name"), o.optString("body"), assets)
        } catch (e: JSONException) {
            throw UpdateException("GitHub sent a reply the app could not read.")
        }
    }

    fun getText(url: String, accept: String): String {
        val conn = open(url, accept)
        try {
            val out = conn.inputStream.use { it.readNBytesLimited(UpdatePolicy.MAX_TEXT_BYTES) }
            return out.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads to [dest] (via a .part file), checks the size equals [expectedSize], and returns the file's SHA-256 as lower-case hex. */
    fun download(url: String, dest: File, expectedSize: Long, onProgress: (Int) -> Unit): String {
        val part = File(dest.parentFile, dest.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(url, "application/octet-stream")
        try {
            var total = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > expectedSize || total > UpdatePolicy.MAX_APK_BYTES) throw UpdateException("The download was larger than announced.")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        val percent = (total * PERCENT / expectedSize).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (total != expectedSize) throw UpdateException("The download was incomplete.")
            if (!part.renameTo(dest)) throw UpdateException("The download could not be saved.")
            return UpdatePolicy.hex(digest.digest())
        } finally {
            conn.disconnect()
            if (part.exists()) part.delete() // our own half-finished cache file
        }
    }

    private fun open(startUrl: String, accept: String): HttpURLConnection {
        var url = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!UpdatePolicy.isAllowedUrl(url)) throw UpdateException("The update address is not allowed.")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> return conn
                    301, 302, 303, 307, 308 -> {
                        val location = conn.getHeaderField("Location") ?: throw UpdateException("GitHub sent a redirect without an address.")
                        url = URI(url).resolve(location).toString()
                        conn.disconnect()
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException("No release has been published yet.")
                    HttpURLConnection.HTTP_FORBIDDEN, 429 -> throw UpdateException("GitHub is limiting requests right now. Try again in a while.")
                    else -> throw UpdateException("GitHub answered with error $code.")
                }
            } catch (e: IOException) {
                conn.disconnect()
                throw e
            }
        }
        throw UpdateException("Too many redirects.")
    }

    private fun java.io.InputStream.readNBytesLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw UpdateException("The reply was larger than expected.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 5
        private const val BUFFER = 16 * 1024
        private const val PERCENT = 100
    }
}
