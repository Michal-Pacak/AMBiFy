package pl.ambif.ambify.features.home

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.Dashboard

/**
 * The phone's copy of the Dashboard (per community), so it shows at once and without the server; the server keeps
 * the real one. Also which person each "Lekcje" widget shows now (only on this phone). Signing out clears it.
 */
class DashboardStore(context: Context) {
    private val preferences = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(communityId: String): Dashboard? =
        preferences.getString(KEY_DASHBOARD + communityId, null)
            ?.let { runCatching { json.decodeFromString<Dashboard>(it) }.getOrNull() }

    fun save(communityId: String, dashboard: Dashboard) {
        preferences.edit { putString(KEY_DASHBOARD + communityId, json.encodeToString(dashboard)) }
    }

    /** A change made here that the server has not got yet (sent again before the server's copy is taken). */
    fun pending(communityId: String): Boolean = preferences.getBoolean(KEY_PENDING + communityId, false)

    fun setPending(communityId: String, pending: Boolean) {
        preferences.edit { putBoolean(KEY_PENDING + communityId, pending) }
    }

    /** The person last picked in a widget with a person switch. */
    fun person(widgetId: String): String? = preferences.getString(KEY_PERSON + widgetId, null)

    fun savePerson(widgetId: String, personId: String) {
        preferences.edit { putString(KEY_PERSON + widgetId, personId) }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_DASHBOARD = "dashboard_"
        const val KEY_PERSON = "person_"
        const val KEY_PENDING = "pending_"
    }
}
