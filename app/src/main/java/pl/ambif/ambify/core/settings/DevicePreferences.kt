package pl.ambif.ambify.core.settings

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings of this phone only (not of the account): kept in Ustawienia → Aplikacja. */
class DevicePreferences(private val context: Context) {
    private val preferences = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    private val _cardAnimations = MutableStateFlow(preferences.getBoolean(KEY_CARD_ANIMATIONS, true))
    /** The "floating" cards: on by default. */
    val cardAnimations: StateFlow<Boolean> = _cardAnimations.asStateFlow()

    fun setCardAnimations(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CARD_ANIMATIONS, enabled) }
        _cardAnimations.value = enabled
    }

    /** Android's "Remove animations" (animator duration scale 0) turns the card animations off as well. */
    /** The one-time "Pomoc" bubble (0.7.8) was answered by a tap on "?": never shown again on this phone. */
    fun helpSeen(): Boolean = preferences.getBoolean(KEY_HELP_SEEN, false)
    fun setHelpSeen() = preferences.edit { putBoolean(KEY_HELP_SEEN, true) }

    private val _reportsSeenAt = MutableStateFlow(preferences.getLong(KEY_REPORTS_SEEN, 0L))
    /** 0.7.9: when "Moje zgłoszenia" was last opened; newer "Naprawione"/"Wyjaśnione" show the gold dot. */
    val reportsSeenAt: StateFlow<Long> = _reportsSeenAt.asStateFlow()

    fun setReportsSeen(at: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KEY_REPORTS_SEEN, at) }
        _reportsSeenAt.value = at
    }

    fun systemAnimationsOff(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val KEY_CARD_ANIMATIONS = "card_animations"
        const val KEY_REPORTS_SEEN = "reports_seen_at"
        const val KEY_HELP_SEEN = "help_seen_2" // "_2": shown once more on TEST 0.7.90 (Michał, 26.11)
    }
}
