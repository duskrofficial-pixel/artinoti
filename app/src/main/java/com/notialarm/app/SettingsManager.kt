package com.notialarm.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

data class AlarmRule(
    val id: String,
    val appPackage: String,
    val keyword: String,
    val matchType: String = "Contains", // Contains, Exact, Regex
    val caseSensitive: Boolean = false
) : Serializable

data class HistoryItem(
    val timestamp: Long,
    val appPackage: String,
    val appName: String,
    val title: String,
    val text: String,
    val matchedKeyword: String
) : Serializable

data class AppConfig(
    val rules: List<AlarmRule>,
    val snoozeDurationMin: Int,
    val soundUri: String?,
    val diagnosticLogging: Boolean
) : Serializable

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notialarm_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getRules(): List<AlarmRule> {
        val json = prefs.getString("rules", "[]") ?: "[]"
        val type = object : TypeToken<List<AlarmRule>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveRules(rules: List<AlarmRule>) {
        prefs.edit().putString("rules", gson.toJson(rules)).apply()
    }

    fun addRule(rule: AlarmRule) {
        val current = getRules().toMutableList()
        current.add(rule)
        saveRules(current)
    }

    fun removeRule(ruleId: String) {
        val current = getRules().filter { it.id != ruleId }
        saveRules(current)
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history", "[]") ?: "[]"
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveHistory(history: List<HistoryItem>) {
        prefs.edit().putString("history", gson.toJson(history)).apply()
    }

    fun addHistoryItem(item: HistoryItem) {
        val current = getHistory().toMutableList()
        current.add(0, item) // newest first
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        saveHistory(current)
    }

    fun clearHistory() {
        saveHistory(emptyList())
    }

    var snoozeDurationMin: Int
        get() = prefs.getInt("snooze_duration", 5)
        set(value) = prefs.edit().putInt("snooze_duration", value).apply()

    var soundUri: String?
        get() = prefs.getString("sound_uri", null)
        set(value) = prefs.edit().putString("sound_uri", value).apply()

    var diagnosticLogging: Boolean
        get() = prefs.getBoolean("diagnostic_logging", false)
        set(value) = prefs.edit().putBoolean("diagnostic_logging", value).apply()

    fun exportConfigJson(): String {
        val config = AppConfig(
            rules = getRules(),
            snoozeDurationMin = snoozeDurationMin,
            soundUri = soundUri,
            diagnosticLogging = diagnosticLogging
        )
        return gson.toJson(config)
    }

    fun importConfigJson(json: String): Boolean {
        return try {
            val config = gson.fromJson(json, AppConfig::class.java)
            saveRules(config.rules)
            snoozeDurationMin = config.snoozeDurationMin
            soundUri = config.soundUri
            diagnosticLogging = config.diagnosticLogging
            true
        } catch (e: Exception) {
            false
        }
    }
}
