package eu.vieitesss.moneyment.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where the newest release is announced. */
private const val LATEST_RELEASE = "https://api.github.com/repos/vieitesss/moneyment/releases/latest"

/** No APK of this app comes close to this size; a bigger download is not ours and would fill the phone. */
private const val MAX_APK_BYTES = 100L * 1024 * 1024

/** The system tells us how the install went by sending this broadcast back to the app. */
const val INSTALL_STATUS = "eu.vieitesss.moneyment.INSTALL_STATUS"

/**
 * The thin Android side of updating: fetch, download, and hand the APK to the installer.
 * Every decision lives in the tested functions above; these return null or throw.
 */
class UpdateManager(private val context: Context) {

    /** The newest published release, or null when offline or on any error. Blocking. */
    fun latestRelease(): Release? = runCatching {
        val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Moneyment")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * Saves the APK of [release] in app storage and returns it, reporting how far it got as a
     * fraction, or null while the size is unknown. Blocking.
     */
    fun download(release: Release, onProgress: (Float?) -> Unit): File {
        val target = apkFile()
        target.delete()
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Moneyment")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            if (total > MAX_APK_BYTES) error("APK too large")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied > MAX_APK_BYTES) error("APK too large")
                        onProgress(if (total > 0) copied.toFloat() / total else null)
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        return target
    }

    /** The downloaded APK; gone once the install is over. */
    fun apkFile(): File = File(context.filesDir, "moneyment-update.apk")

    /** False when the phone still has to be told this app may install packages. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the phone setting that allows installs from this app; the person comes back to us. */
    fun askToInstall() {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Copies the APK into a PackageInstaller session and asks the system to go ahead. Blocking. */
    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // The phone then refuses an APK of any other app, and for this app it already demands
        // the same signing key and a version that is not older.
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("moneyment.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(statusIntent(sessionId))
                // From here the phone owns the session and its staged APK.
                committed = true
            }
        } catch (e: Throwable) {
            // A session that never reached the commit would otherwise keep its staged data.
            if (!committed) runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /** Where the installer reports back; the ViewModel listens for [INSTALL_STATUS]. */
    private fun statusIntent(sessionId: Int): IntentSender {
        val intent = Intent(INSTALL_STATUS).setPackage(context.packageName)
        // The installer fills its answer into the mutable PendingIntent it is given.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }
}
