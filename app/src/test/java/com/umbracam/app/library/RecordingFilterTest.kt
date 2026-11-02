package com.umbracam.app.library

import org.junit.Assert.*
import org.junit.Test

class RecordingFilterTest {
    private val camera = CatalogRecording("camera/a", "camera", "camera-test.mp4", 2, 20, 1000, true)
    private val screen = CatalogRecording("screen/b", "screen", "screen-test.mp4", 1, 10, 2000, true)
    private val entries = listOf(camera, screen)
    private fun label(entry: CatalogRecording) = if (entry == camera) "Camera recording Nov 2, 2026" else "Screen recording Nov 1, 2026"
    @Test fun sourceAndSearchAreCombined() {
        assertTrue(filterRecordings(entries, "camera", "screen", ::label).isEmpty())
        assertEquals(listOf(camera), filterRecordings(entries, "camera", "recording", ::label))
    }
    @Test fun filenameSearchIgnoresCaseAndSurroundingWhitespace() {
        assertEquals(listOf(screen), filterRecordings(entries, "all", "  SCREEN-TEST.MP4  ", ::label))
    }
    @Test fun visibleLocalizedDescriptionAndDateAreSearchable() {
        assertEquals(listOf(camera), filterRecordings(entries, "all", "camera recording", ::label))
        assertEquals(listOf(screen), filterRecordings(entries, "all", "Nov 1", ::label))
    }
    @Test fun emptySearchRetainsOrderAndUnknownSourcesDoNotExposeOtherEntries() {
        assertEquals(entries, filterRecordings(entries, "all", " ", ::label))
        assertTrue(filterRecordings(entries, "missing", "", ::label).isEmpty())
        assertEquals(listOf(camera, screen), entries)
    }
}
