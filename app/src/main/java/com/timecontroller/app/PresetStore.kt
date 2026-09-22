package com.timecontroller.app

import android.content.Context

/**
 * Preset durasi timer, disimpan dalam detik.
 * Data disimpan di SharedPreferences supaya tetap ada walau app ditutup.
 */
data class Preset(val totalSeconds: Int) {
    fun label(): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (seconds == 0) "$minutes mnt" else "${minutes}m ${seconds}d"
    }
}

object PresetStore {
    private const val PREFS_NAME = "time_controller_prefs"
    private const val KEY_PRESETS = "presets_seconds"

    private val defaults = listOf(60, 300, 600, 900, 1800, 3600)

    fun getPresets(context: Context): List<Preset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PRESETS, null)
        val seconds = if (saved.isNullOrEmpty()) {
            defaults
        } else {
            saved.split(",").mapNotNull { it.toIntOrNull() }
        }
        return seconds.map { Preset(it) }
    }

    fun addPreset(context: Context, totalSeconds: Int) {
        val current = getPresets(context).map { it.totalSeconds }.toMutableList()
        if (!current.contains(totalSeconds)) {
            current.add(totalSeconds)
            save(context, current)
        }
    }

    fun removePreset(context: Context, totalSeconds: Int) {
        val current = getPresets(context).map { it.totalSeconds }.toMutableList()
        current.remove(totalSeconds)
        save(context, current)
    }

    private fun save(context: Context, seconds: List<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRESETS, seconds.joinToString(",")).apply()
    }
}
