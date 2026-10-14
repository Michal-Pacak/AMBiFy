package pl.ambif.ambify.core.device

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Short, strong vibration confirming that an element was grabbed (e.g. a module icon before dragging).
 * Uses the vibrator directly, because the system touch feedback is weak on some phones
 * and depends on the user's touch vibration setting.
 */
class GrabVibration(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    fun play() {
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(GRAB_DURATION_MS, MAX_AMPLITUDE)
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
        vibrator.vibrate(effect)
    }

    private companion object {
        const val GRAB_DURATION_MS = 40L
        const val MAX_AMPLITUDE = 255
    }
}
