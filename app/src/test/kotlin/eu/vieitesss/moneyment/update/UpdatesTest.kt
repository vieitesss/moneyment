package eu.vieitesss.moneyment.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private val hour = 60 * 60 * 1000L

    private val latest = """
        {
          "url": "https://api.github.com/repos/vieitesss/moneyment/releases/1",
          "assets_url": "https://api.github.com/repos/vieitesss/moneyment/releases/1/assets",
          "tag_name": "v0.1.1",
          "target_commitish": "main",
          "name": "v0.1.1",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "moneyment-0.1.1.apk",
              "browser_download_url": "https://github.com/vieitesss/moneyment/releases/download/v0.1.1/moneyment-0.1.1.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun releaseParsesIntoVersionAndApkUrl() {
        val release = parseRelease(latest)
        assertEquals("0.1.1", release?.version)
        assertEquals(
            "https://github.com/vieitesss/moneyment/releases/download/v0.1.1/moneyment-0.1.1.apk",
            release?.apkUrl,
        )
    }

    @Test
    fun releaseWithoutAnApkAssetIsNoUpdate() {
        val noApk = """{"tag_name":"v0.1.1","assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}]}"""
        assertNull(parseRelease(noApk))
    }

    @Test
    fun tagWithoutTheLeadingVStillParses() {
        val plain = """{"tag_name":"0.1.1","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""
        assertEquals("0.1.1", parseRelease(plain)?.version)
    }

    @Test
    fun brokenOrEmptyJsonIsNoUpdate() {
        assertNull(parseRelease(""))
        assertNull(parseRelease("not json"))
        assertNull(parseRelease("""{"message":"Not Found"}"""))
        assertNull(parseRelease("""{"tag_name":"v0.1.1"}"""))
    }

    @Test
    fun aHigherVersionIsNewer() {
        assertTrue(isNewer("0.1.1", "0.1.0"))
        assertTrue(isNewer("0.2.0", "0.1.9"))
        assertTrue(isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun theSameOrAnOlderVersionIsNotNewer() {
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.1.1"))
        assertFalse(isNewer("0.1.0", "1.0.0"))
    }

    @Test
    fun partsCompareAsNumbersNotAsText() {
        assertTrue(isNewer("0.10.0", "0.9.0"))
        assertFalse(isNewer("0.9.0", "0.10.0"))
        assertFalse(isNewer("1.0", "1.0.0"))
        assertTrue(isNewer("1.0.1", "1.0"))
    }

    @Test
    fun theFirstCheckOfAFreshInstallIsDue() {
        assertTrue(checkDue(lastCheck = 0L, now = 100 * hour))
    }

    @Test
    fun aCheckLessThanADayOldIsNotDue() {
        val now = 100 * hour
        assertFalse(checkDue(lastCheck = now - hour, now = now))
        assertFalse(checkDue(lastCheck = now - 24 * hour + 1, now = now))
    }

    @Test
    fun aCheckADayOldIsDueAgain() {
        val now = 100 * hour
        assertTrue(checkDue(lastCheck = now - 24 * hour, now = now))
        assertTrue(checkDue(lastCheck = now - 40 * hour, now = now))
    }

    @Test
    fun theSnoozedVersionStaysQuiet() {
        val now = 100 * hour
        assertTrue(isSnoozed("0.1.1", snoozedVersion = "0.1.1", snoozedUntil = now + hour, now = now))
    }

    @Test
    fun theSnoozeExpires() {
        val now = 100 * hour
        assertFalse(isSnoozed("0.1.1", snoozedVersion = "0.1.1", snoozedUntil = now, now = now))
        assertFalse(isSnoozed("0.1.1", snoozedVersion = "0.1.1", snoozedUntil = now - hour, now = now))
    }

    @Test
    fun anotherVersionIsNotSnoozed() {
        val now = 100 * hour
        assertFalse(isSnoozed("0.2.0", snoozedVersion = "0.1.1", snoozedUntil = now + hour, now = now))
        assertFalse(isSnoozed("0.1.1", snoozedVersion = null, snoozedUntil = 0L, now = now))
    }
}
