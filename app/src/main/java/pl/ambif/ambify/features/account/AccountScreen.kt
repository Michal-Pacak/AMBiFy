package pl.ambif.ambify.features.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.ui.theme.AMBiFyTheme

@Composable
fun AccountScreen() {
    val serverApi = remember { ServerApi() }
    val scope = rememberCoroutineScope()
    var serverStatus by remember { mutableStateOf<ServerStatus>(ServerStatus.Checking) }
    var diagnostics by remember { mutableStateOf<DiagnosticsState?>(null) }

    LaunchedEffect(Unit) {
        serverStatus = serverApi.status()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Konto",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ServerCard(
                status = serverStatus,
                onCheckConnection = {
                    diagnostics = DiagnosticsState.Loading
                    serverStatus = ServerStatus.Checking
                    scope.launch {
                        diagnostics = serverApi.diagnosticsState()
                        serverStatus = serverApi.status()
                    }
                },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Kalendarz Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Nie połączono konta. Po połączeniu będzie można wybierać synchronizację osobno dla każdego wydarzenia.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "W rodzinie rodzic będzie mógł połączyć także prywatny kalendarz dziecka.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    diagnostics?.let { state ->
        DiagnosticsDialog(state = state, onDismiss = { diagnostics = null })
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountScreenPreview() {
    AMBiFyTheme {
        AccountScreen()
    }
}
