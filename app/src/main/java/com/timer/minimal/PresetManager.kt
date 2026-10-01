package com.timer.minimal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A named, user-saved workout configuration.
 *
 * [workDurationSec] / [restDurationSec] / [totalSets] apply to routine mode;
 * [inputMinutes] applies to Death by Burpees.
 */
data class Preset(
    val id: String,
    val name: String,
    val mode: TimerMode,
    val workDurationSec: Int,
    val restDurationSec: Int,
    val totalSets: Int,
    val inputMinutes: Int
) {
    fun toConfig(): TimerEngine.Config = when (mode) {
        TimerMode.ROUTINE -> TimerEngine.Config(
            mode = mode,
            workDurationSec = workDurationSec,
            restDurationSec = restDurationSec,
            totalSets = totalSets
        )
        TimerMode.DEATH_BURPEES -> TimerEngine.Config(
            mode = mode,
            inputMinutes = inputMinutes
        )
    }

    companion object {
        /**
         * Ready-made routines so a first-time user can start training without
         * typing anything. Ids are stable so they can be reset or updated.
         */
        val BUILT_IN: List<Preset> = listOf(
            Preset(
                id = "builtin_tabata",
                name = "Tabata",
                mode = TimerMode.ROUTINE,
                workDurationSec = 20,
                restDurationSec = 10,
                totalSets = 8,
                inputMinutes = 5
            ),
            Preset(
                id = "builtin_emom_10",
                name = "EMOM 10",
                mode = TimerMode.ROUTINE,
                workDurationSec = 60,
                restDurationSec = 0,
                totalSets = 10,
                inputMinutes = 10
            ),
            Preset(
                id = "builtin_amrap_20",
                name = "AMRAP 20",
                mode = TimerMode.ROUTINE,
                workDurationSec = 300,
                restDurationSec = 60,
                totalSets = 4,
                inputMinutes = 20
            )
        )
    }
}

/**
 * Persists user-created presets as a JSON array in SharedPreferences.
 * Built-in presets are returned by [listAll] but are never stored.
 */
class PresetManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "timer_presets"
        private const val KEY_PRESETS = "presets"
        const val MAX_PRESETS = 20
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Built-ins first, then the user's own in insertion order. */
    fun listAll(): List<Preset> = Preset.BUILT_IN + loadUserPresets()

    fun loadUserPresets(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.toPreset()
            }
        }.getOrDefault(emptyList())
    }

    fun findById(id: String): Preset? = listAll().firstOrNull { it.id == id }

    /**
     * Saves or replaces a preset. [id] is reused when the caller passes the id of an
     * existing preset, which is how renaming works.
     */
    fun save(preset: Preset): Preset {
        val user = loadUserPresets().toMutableList()
        val index = user.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            user[index] = preset
        } else {
            if (user.size >= MAX_PRESETS) user.removeAt(0)
            user += preset
        }
        persist(user)
        return preset
    }

    fun delete(id: String) {
        persist(loadUserPresets().filterNot { it.id == id })
    }

    private fun persist(presets: List<Preset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("mode", preset.mode.name)
                    put("work", preset.workDurationSec)
                    put("rest", preset.restDurationSec)
                    put("sets", preset.totalSets)
                    put("minutes", preset.inputMinutes)
                }
            )
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun JSONObject.toPreset(): Preset? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val mode = runCatching { TimerMode.valueOf(optString("mode")) }
            .getOrDefault(TimerMode.ROUTINE)
        return Preset(
            id = id,
            name = optString("name", "Preset"),
            mode = mode,
            workDurationSec = optInt("work", 60),
            restDurationSec = optInt("rest", 180),
            totalSets = optInt("sets", 8),
            inputMinutes = optInt("minutes", 5)
        )
    }
}
