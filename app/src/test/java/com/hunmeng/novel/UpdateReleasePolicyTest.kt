package com.hunmeng.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleasePolicyTest {
    private val official =
        "https://github.com/DEXTERITY-maker/hunmeng-novel-app/releases/download/v0.3.5/HunmengNovel.apk"

    @Test fun newerPublishedVersionIsDetected() {
        assertTrue(UpdateReleasePolicy.isNewer("v0.3.5", "0.3.4"))
        assertTrue(UpdateReleasePolicy.isNewer("v1.0.0", "0.3.5"))
    }

    @Test fun installedOrOlderVersionDoesNotShowUpdateModal() {
        assertFalse(UpdateReleasePolicy.isNewer("v0.3.4", "0.3.4"))
        assertFalse(UpdateReleasePolicy.isNewer("v0.3.3", "0.3.4"))
        assertFalse(UpdateReleasePolicy.isNewer("garbage", "0.3.4"))
        assertFalse(UpdateReleasePolicy.isNewer("v0.3.5", "garbage"))
    }

    @Test fun firstOpenChecksImmediatelyAndResumeUsesCooldown() {
        val interval = 15 * 60 * 1000L
        assertTrue(UpdateReleasePolicy.shouldCheck(-1, 1000, interval))
        assertFalse(UpdateReleasePolicy.shouldCheck(1000, 2000, interval))
        assertFalse(UpdateReleasePolicy.shouldCheck(1000, 1000 + interval - 1, interval))
        assertTrue(UpdateReleasePolicy.shouldCheck(1000, 1000 + interval, interval))
    }

    @Test fun officialReleaseApksAccepted() {
        assertTrue(UpdateReleasePolicy.trustedApk(official))
        assertTrue(UpdateReleasePolicy.trustedApk(
            "https://github.com/DEXTERITY-maker/hunmeng-novel-app/releases/download/v0.3.5/HunmengNovel-v0.3.5.apk"
        ))
    }

    @Test fun externalOrManipulatedDownloadUrlsRejected() {
        assertFalse(UpdateReleasePolicy.trustedApk("https://example.com/HunmengNovel.apk"))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace("https:", "http:")))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace("github.com", "github.com.evil.test")))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace("hunmeng-novel-app", "hunmeng-console-android")))
        assertFalse(UpdateReleasePolicy.trustedApk(official + "?redirect=https://example.com"))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace("https://github.com/", "https://evil.test@github.com/")))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace("v0.3.5", "not-a-version")))
        assertFalse(UpdateReleasePolicy.trustedApk(official.replace(".apk", ".zip")))
    }
}
