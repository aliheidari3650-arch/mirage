package com.mirage.vpn

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MirageTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MirageTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun startVpn(context: Context, config: String) {
    val intent = Intent(context, MirageVpnService::class.java)
        .setAction(MirageVpnService.ACTION_CONNECT)
        .putExtra(MirageVpnService.EXTRA_CONFIG, config)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopVpn(context: Context) {
    val intent = Intent(context, MirageVpnService::class.java)
        .setAction(MirageVpnService.ACTION_DISCONNECT)
    ContextCompat.startForegroundService(context, intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: VpnViewModel = viewModel()) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val message by vm.statusMessage.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val protocol by vm.activeProtocol.collectAsStateWithLifecycle()
    val running by vm.isRunning.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpn(context, vm.config.value)
        }
    }

    fun prepareAndStart() {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpn(context, vm.config.value)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Proceed regardless: the VPN works without a visible notification.
        prepareAndStart()
    }

    fun onConnectClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            prepareAndStart()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mirage VPN") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(status, message, metrics, protocol)

            OutlinedTextField(
                value = config,
                onValueChange = vm::updateConfig,
                label = { Text("Config (TOML)") },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                maxLines = 24,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            )

            Button(
                onClick = { if (running) stopVpn(context) else onConnectClicked() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: Int,
    message: String?,
    metrics: LongArray,
    protocol: String,
) {
    val label = when (status) {
        1 -> "Connecting"
        2 -> "Connected"
        3 -> "Error"
        else -> "Disconnected"
    }
    val container = when (status) {
        2 -> MaterialTheme.colorScheme.primaryContainer
        3 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.headlineMedium)
            if (!message.isNullOrBlank()) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (status == 2 && metrics.size >= 5) {
                Text("Uptime: ${formatUptime(metrics[4])}")
                Text("Sent: ${formatBytes(metrics[0])}")
                Text("Received: ${formatBytes(metrics[1])}")
                if (protocol.isNotBlank()) {
                    Text("Protocol: $protocol")
                }
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return if (i == 0) "${value.toLong()} B" else String.format(Locale.US, "%.2f %s", value, units[i])
}
