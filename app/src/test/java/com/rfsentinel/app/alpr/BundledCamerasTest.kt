package com.rfsentinel.app.alpr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled NSW cameras ship in the shape AlprStore reads, and merge under OpenStreetMap's copies. */
class BundledCamerasTest {

    private val bundled = BundledCameras.load { File("src/main/assets/$it").inputStream() }

    @Test
    fun nswCamerasLoadFromTheAsset() {
        assertEquals(348, bundled.size) // 126 fixed + 222 red-light speed camera points (TfNSW, 2023)
        assertEquals(126, bundled.count { it.type == KnownCamera.Kind.SPEED })
        assertEquals(222, bundled.count { it.type == KnownCamera.Kind.RED_LIGHT })
        assertTrue(bundled.all { it.lat in -38.0..-28.0 && it.lon in 140.0..154.0 })
        assertTrue(bundled.all { it.osmId.startsWith("tfnsw/") && it.operator == "Transport for NSW" })
        assertEquals(bundled.size, bundled.distinctBy { it.osmId }.size)
        assertEquals("Red-light camera", bundled.first { it.type == KnownCamera.Kind.RED_LIGHT }.label)
    }

    @Test
    fun missingFileIsSkipped() {
        assertTrue(BundledCameras.load { throw java.io.FileNotFoundException(it) }.isEmpty())
    }

    @Test
    fun mergeKeepsTheDownloadedCopyOfTheSameCamera() {
        // A site with one camera: no other bundled speed camera within 120 m, so exactly one copy goes.
        val speed = bundled.filter { it.type == KnownCamera.Kind.SPEED }
        val b = speed.first { c -> KnownCameras.near(speed, c.lat, c.lon, 120.0).size == 1 }
        // OSM's node 30 m from the bundled one, with the speed limit: the bundled copy goes.
        val osm = KnownCamera("node/1", b.lat + 0.00027, b.lon, null, null, 90, KnownCamera.Kind.SPEED, 60)
        // A red-light camera at the same spot is another camera: both stay.
        val red = KnownCamera("node/2", b.lat, b.lon, null, null, null, KnownCamera.Kind.RED_LIGHT)
        // A plate reader somewhere else entirely is untouched.
        val far = KnownCamera("node/3", 40.0, -100.0, "Flock Safety", null, null, KnownCamera.Kind.ALPR)
        val merged = BundledCameras.merge(listOf(osm, red, far), bundled)
        assertEquals(bundled.size - 1 + 3, merged.size)
        assertTrue(merged.none { it.osmId == b.osmId })
        assertTrue(merged.any { it.osmId == "node/1" } && merged.any { it.osmId == "node/2" } && merged.any { it.osmId == "node/3" })
        assertEquals(2, merged.indexOf(far)) // downloaded first, bundled after
    }

    @Test
    fun mergeWithNothingDownloadedIsTheBundledList() {
        assertEquals(bundled, BundledCameras.merge(emptyList(), bundled))
        assertEquals(emptyList<KnownCamera>(), BundledCameras.merge(emptyList(), emptyList()))
    }
}
