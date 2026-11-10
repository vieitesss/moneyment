package eu.vieitesss.moneyment.update

import org.json.JSONObject

/** How long an automatic check stays quiet: once a day is enough for a small app. */
const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/** How long "Later" keeps a version out of the way. */
const val SNOOZE_MS = 24L * 60 * 60 * 1000

/** A published release: its version without the leading "v", and the APK to install. */
data class Release(val version: String, val apkUrl: String)

/**
 * The release in GitHub's "latest release" answer, or null when the answer has no tag or no
 * APK asset. Drafts and pre-releases never reach us: the endpoint already leaves them out.
 */
fun parseRelease(json: String): Release? = runCatching {
    val root = JSONObject(json)
    val version = root.getString("tag_name").removePrefix("v").ifEmpty { return null }
    val assets = root.optJSONArray("assets") ?: return null
    val apk = (0 until assets.length())
        .map { assets.getJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk") }
        ?: return null
    val url = apk.optString("browser_download_url").ifEmpty { return null }
    Release(version, url)
}.getOrNull()

/** True when [candidate] is a newer version than [installed], part by part and part as a number. */
fun isNewer(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}

/** True when the last automatic check is old enough (or never happened). */
fun checkDue(lastCheck: Long, now: Long): Boolean = now - lastCheck >= CHECK_INTERVAL_MS

/** True while [version] is the one "Later" put aside, for the rest of its day. */
fun isSnoozed(version: String, snoozedVersion: String?, snoozedUntil: Long, now: Long): Boolean =
    version == snoozedVersion && now < snoozedUntil

private fun versionParts(version: String): List<Int> =
    version.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
