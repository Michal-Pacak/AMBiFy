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
    fun systemAnimationsOff(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val KEY_CARD_ANIMATIONS = "card_animations"
    }
}
