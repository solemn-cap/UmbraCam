package com.umbracam.app

import org.junit.Assert.*
import org.junit.Test

class DestinationTest {
    @Test fun routesAreUniqueAndIncludeAllHomeSections() {
        assertEquals(Destination.entries.size, Destination.entries.map { it.route }.toSet().size)
        assertEquals(listOf("camera", "reaction", "safety", "vault"), Destination.studios.map { it.route })
        assertFalse(Destination.studios.contains(Destination.SETTINGS))
    }
}
