package pl.ambif.ambify.modules.timetable

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import pl.ambif.ambify.ui.theme.AMBiFyTheme
import pl.ambif.ambify.ui.theme.Luxury

/** The alarm of an extra activity: over the lock screen, ringing until "Wyłącz" or "Drzemka 5 min". */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty()
        ring()

        fun close() {
            getSystemService(NotificationManager::class.java).cancel(id)
            finish()
        }

        setContent {
            AMBiFyTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(Luxury.Background).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayLarge, color = Luxury.GoldLight)
                    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                    Text("AMBiFy · $text", style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted, textAlign = TextAlign.Center)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = {
                            ReminderScheduler.snooze(this@AlarmActivity, id, title, text, minutes = 5)
                            close()
                        }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Drzemka 5 min") }
                        Button(onClick = { close() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Wyłącz") }
                    }
                }
            }
        }
    }

    private fun ring() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            isLooping = true
            play()
        }
        getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }

    override fun onDestroy() {
        ringtone?.stop()
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        super.onDestroy()
    }
}
