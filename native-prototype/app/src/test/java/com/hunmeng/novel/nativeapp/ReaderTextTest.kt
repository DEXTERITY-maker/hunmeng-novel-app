package com.hunmeng.novel.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextTest {
    @Test fun markdownExtractsParagraphs() {
        val text = "# Chapter 1\n\nHello **reader**.\n\nSecond paragraph."
        assertEquals(listOf("Chapter 1", "Hello reader.", "Second paragraph."), toReaderParagraphs(text))
    }
    @Test fun suspiciousPathsCannotBeRequested() {
        assertTrue(HunmengApi.isValidSlug("the-time-machine"))
        assertFalse(HunmengApi.isValidSlug("../admin"))
        assertFalse(HunmengApi.isValidSlug("book?redirect=external"))
    }
    @Test fun imageLinksAreLimitedToSiteAssets() {
        assertEquals("https://hunmeng-novel.ru/images/book.webp", HunmengApi.coverUrl("/images/book.webp"))
        assertEquals(null, HunmengApi.coverUrl("https://example.com/tracker.jpg"))
        assertEquals(null, HunmengApi.coverUrl("/images/../../admin"))
    }
}
