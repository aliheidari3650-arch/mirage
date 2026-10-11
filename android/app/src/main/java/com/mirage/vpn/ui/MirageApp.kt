package com.mirage.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirage.vpn.BuildConfig
import com.mirage.vpn.MirageVpnService
import com.mirage.vpn.VpnViewModel

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
fun MirageApp(vm: VpnViewModel) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val message by vm.statusMessage.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val protocol by vm.activeProtocol.collectAsStateWithLifecycle()
    val running by vm.isRunning.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()

    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editorSession by rememberSaveable { mutableStateOf(0) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var useOwnServer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.profile.value?.let { startVpn(context, it.toml) }
        }
    }

    fun prepareAndStart() {
        val p = vm.profile.value ?: return
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpn(context, p.toml)
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

    fun scan() {
        launchQrScanner(
            context,
            onResult = { text -> vm.importFromText(text)?.let(vm::say) },
            onUnavailable = { vm.say(SCANNER_UNAVAILABLE) },
        )
    }

    val showSignIn = profile == null && BuildConfig.DEFAULT_CONFIG_B64.isNotEmpty() && !useOwnServer

    Scaffold(
        topBar = { TopAppBar(title = { Text("Turbo VPN") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (showSignIn) {
            SignInScreen(
                onSignIn = vm::signIn,
                onUseDifferentServer = {
                    useOwnServer = true
                    showEditor = true
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            HomeScreen(
                status = status,
                message = message,
                metrics = metrics,
                samples = samples,
                protocol = protocol,
                profile = profile,
                onPower = {
                    when {
                        running -> stopVpn(context)
                        profile == null -> showEditor = true
                        else -> onConnectClicked()
                    }
                },
                onServer = { showEditor = true },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }

    if (showEditor) {
        key(editorSession) {
            ServerEditorSheet(
                initial = profile,
                onDismiss = { showEditor = false },
                onSave = { saved ->
                    vm.saveProfile(saved)
                    showEditor = false
                    vm.say(
                        if (status == MirageVpnService.STATUS_CONNECTED) "Saved. Reconnect to use the new settings."
                        else "Server saved",
                    )
                },
                onScan = ::scan,
                onPaste = { showPaste = true },
            )
        }
    }

    if (showPaste) {
        PasteImportSheet(
            onDismiss = { showPaste = false },
            onSubmit = { text ->
                val err = vm.importFromText(text)
                if (err == null) showPaste = false
                err
            },
        )
    }

    pending?.let { p ->
        ImportConfirmSheet(
            pending = p,
            replacing = profile != null,
            onConfirm = {
                val saved = vm.confirmImport()
                // Rebuild the editor from the new profile.
                showEditor = false
                editorSession++
                if (saved != null && saved.needsCredentials) {
                    showEditor = true
                    vm.say("Enter your username and password.")
                } else {
                    vm.say("Server saved")
                }
            },
            onDismiss = vm::dismissImport,
        )
    }
}
