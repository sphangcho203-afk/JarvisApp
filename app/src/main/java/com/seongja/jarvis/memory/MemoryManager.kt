package com.seongja.jarvis.memory

import android.content.Context

class MemoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_memory_v3", Context.MODE_PRIVATE)

    fun remember(key: String, value: String) {
        prefs.edit().putString(key.cleanKey(), value.trim()).apply()
    }

    fun recall(key: String): String? = prefs.getString(key.cleanKey(), null)

    fun forget(key: String) {
        prefs.edit().remove(key.cleanKey()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun allMemory(): Map<String, String> {
        return prefs.all.mapNotNull { (key, value) ->
            val text = value as? String ?: return@mapNotNull null
            key to text
        }.toMap()
    }

    fun callsign(): String = recall("callsign") ?: "Sir"

    private fun String.cleanKey(): String = lowercase().trim().replace(Regex("[^a-z0-9_ -]"), "").replace(" ", "_")
}
