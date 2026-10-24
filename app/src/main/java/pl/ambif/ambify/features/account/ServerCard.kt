package pl.ambif.ambify.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.shared.CheckStatus
import pl.ambif.ambify.shared.DiagnosticResult
import pl.ambif.ambify.shared.HealthStatus

/** The four states of the connection shown on the Account screen. */
sealed interface ServerStatus {
    data object Checking : ServerStatus
    data class Connected(val version: String) : ServerStatus
    data class OtherProblem(val version: String) : ServerStatus
    data object Unreachable : ServerStatus
}

/** Result of "Check connection": the measured connection plus the server's own checks. */
sealed interface DiagnosticsState {
    data object Loading : DiagnosticsState
    data class Ready(val connectionMillis: Long, val checks: List<DiagnosticResult>) : DiagnosticsState
    data object Unreachable : DiagnosticsState
}

suspend fun ServerApi.status(): ServerStatus = runCatching { health().value }.fold(
    onSuccess = { health ->
        if (health.status == HealthStatus.OK) ServerStatus.Connected(health.version)
        else ServerStatus.OtherProblem(health.version)
    },
    onFailure = { ServerStatus.Unreachable },
)

suspend fun ServerApi.diagnosticsState(): DiagnosticsState = runCatching { diagnostics() }.fold(
    onSuccess = { DiagnosticsState.Ready(it.millis, it.value.checks) },
    onFailure = { DiagnosticsState.Unreachable },
)

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFF9A825)
private val Red = Color(0xFFC62828)

@Composable
fun ServerCard(status: ServerStatus, onCheckConnection: () -> Unit) {
    val (color, label) = when (status) {
        ServerStatus.Checking -> MaterialTheme.colorScheme.outline to "Sprawdzanie…"
        is ServerStatus.Connected -> Green to "Połączono · wersja ${status.version}"
        is ServerStatus.OtherProblem -> Amber to "Serwer działa — inny problem"
        ServerStatus.Unreachable -> Red to "Brak połączenia"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Serwer AMBiFy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape),
                )
                Box(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(
                onClick = onCheckConnection,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Sprawdź połączenie")
            }
        }
    }
}

@Composable
fun DiagnosticsDialog(state: DiagnosticsState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        },
        title = { Text("Diagnostyka") },
        text = {
            when (state) {
                DiagnosticsState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                DiagnosticsState.Unreachable -> DiagnosticRow(
                    CheckStatus.ERROR, "Połączenie", "brak odpowiedzi serwera",
                )
                is DiagnosticsState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticRow(CheckStatus.OK, "Połączenie", "${state.connectionMillis} ms")
                    state.checks.forEach { DiagnosticRow(it.status, it.name, it.detail) }
                }
            }
        },
    )
}

@Composable
private fun DiagnosticRow(status: CheckStatus, name: String, detail: String) {
    val symbol = when (status) {
        CheckStatus.OK -> "✅"
        CheckStatus.WARNING -> "⚠️"
        CheckStatus.ERROR -> "❌"
        CheckStatus.NOT_AVAILABLE -> "—"
    }
    val textColor = if (status == CheckStatus.NOT_AVAILABLE) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(text = symbol, modifier = Modifier.width(28.dp))
        Column {
            Text(text = name, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
