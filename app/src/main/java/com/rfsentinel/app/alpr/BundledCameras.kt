package com.rfsentinel.app.alpr

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

/**
 * Enforcement cameras shipped inside the app, for regions whose government publishes
 * them as open data: Transport for NSW's fixed and red-light speed cameras
 * (assets/cameras/nsw_tfnsw.json, built by tools/gen_nsw_cameras.py; CC BY 4.0).
 * They are on the map and warn from the first start, with nothing downloaded; where
 * OpenStreetMap has the same camera the downloaded copy wins (it may carry the speed
 * limit and the direction). Pure Kotlin, so it's unit-tested on the JVM.
 */
object BundledCameras {

    val FILES = listOf("cameras/nsw_tfnsw.json")

    /** A downloaded camera of the same kind this close to a bundled one is the same camera. */
    const val SAME_M = 60.0

    private val listType = object : TypeToken<List<KnownCamera>>() {}.type

    /** Every bundled camera; a file that won't open or parse is skipped. */
    fun load(open: (String) -> InputStream): List<KnownCamera> = FILES.flatMap { name ->
        runCatching { open(name).bufferedReader().use { Gson().fromJson<List<KnownCamera>>(it, listType) } }.getOrNull().orEmpty()
    }

    /** The downloaded cameras, plus the bundled ones they haven't got. */
    fun merge(downloaded: List<KnownCamera>, bundled: List<KnownCamera>): List<KnownCamera> {
        if (bundled.isEmpty()) return downloaded
        // Only the downloaded cameras in the bundled ones' region are compared (the US plate readers are tens of thousands).
        val s = bundled.minOf { it.lat } - 0.01; val n = bundled.maxOf { it.lat } + 0.01
        val w = bundled.minOf { it.lon } - 0.01; val e = bundled.maxOf { it.lon } + 0.01
        val nearby = downloaded.filter { it.lat in s..n && it.lon in w..e }
        return downloaded + bundled.filter { b -> KnownCameras.near(nearby, b.lat, b.lon, SAME_M).none { it.first.type == b.type } }
    }
}
