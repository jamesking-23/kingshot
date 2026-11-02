package com.kingshot.camera.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** Results of an install attempt, delivered by [InstallResultReceiver]. */
object InstallEvents {
    @Volatile
    var onResult: ((success: Boolean, message: String) -> Unit)? = null
}

/** Checks a downloaded APK against this install and hands it to Android's installer, which shows its own confirmation screen. */
class ApkInstaller(private val context: Context) {
    /** Returns a plain-language reason the APK must not be installed, or null if it is a genuine newer build of this app. */
    fun problemWith(apk: File): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return "The downloaded file is not a valid app."
        if (archive.packageName != context.packageName) return "The downloaded file is for a different app."
        val current = pm.getPackageInfo(context.packageName, flags)
        if (archive.longVersionCode <= current.longVersionCode) return "The downloaded build is not newer than the installed one."
        val newSigners = fingerprints(archive.signingInfo?.apkContentsSigners?.map { it.toByteArray() })
        val oldSigners = fingerprints(current.signingInfo?.apkContentsSigners?.map { it.toByteArray() })
        if (newSigners.isEmpty() || newSigners != oldSigners) {
            return "This update is signed with a different key than the installed app, so Android would refuse it. " +
                "Uninstall KingShot once (your photos stay in your gallery), install this release, and later updates will install over it."
        }
        return null
    }

    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("kingshot.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // Mutable so the system can add the status extras.
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    private fun fingerprints(certs: List<ByteArray>?): Set<String> =
        certs.orEmpty().map { UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(it)) }.toSet()
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.onResult?.invoke(true, "Updated.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallEvents.onResult?.invoke(false, "The installation was cancelled.")
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                InstallEvents.onResult?.invoke(false, "Android could not install the update (code $status). $detail".trim())
            }
        }
    }
}
