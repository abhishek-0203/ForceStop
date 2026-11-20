package com.abhishek.forcestop.block

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

object BlockRepository {

    private const val PREFS_NAME = "block"
    private const val KEY_STATE = "state"
    private const val KEY_SELECTED_TARGET = "selectedTarget"
    private const val KEY_DEFAULT_MINUTES = "defaultMinutes"
    private const val KEY_LEGACY_RESTRICTED = "restrictedPackages"

    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow(StoredState())
    val state: StateFlow<StoredState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = prefs.getString(KEY_STATE, null)?.let(::decode)
            ?: StoredState(restricted = prefs.getStringSet(KEY_LEGACY_RESTRICTED, emptySet()).orEmpty().toSet())
    }

    @Synchronized
    fun update(transform: (StoredState) -> StoredState) {
        val updated = transform(_state.value)
        if (updated == _state.value) return
        prefs.edit { putString(KEY_STATE, encode(updated)) }
        _state.value = updated
    }

    var selectedTarget: String?
        get() = prefs.getString(KEY_SELECTED_TARGET, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_TARGET, value) }

    /** Duration used by the Quick Settings tile and widget. */
    var defaultMinutes: Int
        get() = prefs.getInt(KEY_DEFAULT_MINUTES, 60)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_MINUTES, value) }

    private fun encode(state: StoredState): String = JSONObject()
        .put("manual", JSONObject(state.manual))
        .put("skips", JSONObject(state.skips))
        .put("restricted", JSONArray(state.restricted.toList()))
        .put("schedules", JSONArray(state.schedules.map { schedule ->
            JSONObject()
                .put("id", schedule.id)
                .put("package", schedule.packageName)
                .put("days", JSONArray(schedule.days.map { it.value }))
                .put("start", schedule.startMinute)
                .put("end", schedule.endMinute)
                .put("enabled", schedule.enabled)
        }))
        .toString()

    private fun decode(json: String): StoredState? = runCatching {
        val root = JSONObject(json)
        StoredState(
            manual = root.getJSONObject("manual").toLongMap(),
            skips = root.getJSONObject("skips").toLongMap(),
            restricted = root.getJSONArray("restricted").let { array -> List(array.length()) { array.getString(it) }.toSet() },
            schedules = root.getJSONArray("schedules").let { array ->
                List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val days = item.getJSONArray("days")
                    Schedule(
                        id = item.getString("id"),
                        packageName = item.getString("package"),
                        days = List(days.length()) { DayOfWeek.of(days.getInt(it)) }.toSet(),
                        startMinute = item.getInt("start"),
                        endMinute = item.getInt("end"),
                        enabled = item.getBoolean("enabled"),
                    )
                }
            },
        )
    }.getOrNull()

    private fun JSONObject.toLongMap(): Map<String, Long> = keys().asSequence().associateWith { getLong(it) }
}
