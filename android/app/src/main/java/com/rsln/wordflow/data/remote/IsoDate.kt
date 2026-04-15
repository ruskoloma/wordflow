package com.rsln.wordflow.data.remote

import java.time.Instant

/**
 * Converts between ISO-8601 strings (the server's wire format) and
 * epoch millis (what WordEntity / CollectionEntity store internally).
 *
 * Silently falls back to 0L / now() on parse errors so one malformed
 * server timestamp can't crash a whole sync pull. The server owns
 * these values so a bad date is a server-side bug, not something the
 * client should surface.
 *
 * java.time.Instant requires API 26+, which matches our minSdk.
 */
object IsoDate {
    fun parse(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    fun format(millis: Long): String =
        Instant.ofEpochMilli(millis).toString()

    /**
     * Like parse(), but falls back to now() instead of 0 — used for
     * optional server fields (like created_at) where 0 would be
     * wildly misleading for the UI.
     */
    fun parseOrNow(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
