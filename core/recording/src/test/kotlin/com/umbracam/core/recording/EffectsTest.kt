package com.umbracam.core.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {
    @Test fun builtInFilterListIsStableAndComplete() {
        assertEquals(FilterId.entries, BuiltInFilterDiscovery.available())
    }

    @Test fun filterToneIsDeterministicAndIntensityBounded() {
        assertEquals(FilterTone(1.12f,1.025f,.88f), FilterConfig(FilterId.WARM).tone())
        assertEquals(FilterTone(1f,1f,1f,true), FilterConfig(FilterId.MONOCHROME).tone())
        assertEquals(FilterTone(1f,1f,1f), FilterConfig(FilterId.WARM,0f).tone())
        assertEquals(FilterTone(1f,1f,1f),FilterConfig(FilterId.COOL,-1f).tone())
        assertTrue(FilterConfig(FilterId.VINTAGE,Float.NaN).tone().red.isFinite())
    }
}
