package pl.ambif.ambify.core.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Order of the navigation bar modules (list of routes), kept on the phone.
 * Moves to the user account on the server in stage 2.
 */
class ModuleOrderStore(context: Context) {
    private val preferences = context.getSharedPreferences("navigation", Context.MODE_PRIVATE)

    fun load(): List<String>? =
        preferences.getString(KEY_MODULE_ORDER, null)?.split(',')?.filter { it.isNotBlank() }

    fun save(order: List<String>) {
        preferences.edit { putString(KEY_MODULE_ORDER, order.joinToString(",")) }
    }

    private companion object {
        const val KEY_MODULE_ORDER = "module_order"
    }
}
