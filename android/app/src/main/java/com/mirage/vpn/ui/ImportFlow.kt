package com.mirage.vpn.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mirage.vpn.data.AccessLink
import com.mirage.vpn.data.CodecResult
import com.mirage.vpn.data.ConfigCodec
import org.json.JSONObject

/** A decoded, parser-validated config waiting for the user's confirmation. */
data class PendingImport(val name: String, val toml: String, val json: JSONObject) {
    val host: String get() = json.optJSONObject("server")?.optString("host", "").orEmpty()
    val port: Int get() = json.optJSONObject("server")?.optInt("port", 443) ?: 443
    val insecure: Boolean get() = json.optJSONObject("transport")?.optBoolean("insecure", false) ?: false
}

const val SCANNER_UNAVAILABLE = "Scanner isn't available. Paste the link instead."

object ImportParser {
    /** Accepts a mirage:// link (alone or inside other text) or raw client TOML. */
    fun fromText(text: String): CodecResult<PendingImport> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return CodecResult.Err("Paste a mirage:// link or a TOML config.")

        val link = AccessLink.find(trimmed)
        var name: String? = null
        val toml: String
        if (link != null) {
            when (val d = AccessLink.decode(link)) {
                is CodecResult.Err -> return d
                is CodecResult.Ok -> {
                    name = d.value.name
                    toml = d.value.toml
                }
            }
        } else {
            toml = trimmed
        }

        val json = when (val p = ConfigCodec.parse(toml)) {
            is CodecResult.Err -> return CodecResult.Err("This isn't a valid server config. ${p.message}")
            is CodecResult.Ok -> p.value
        }
        val canonical = when (val t = ConfigCodec.toToml(json)) {
            is CodecResult.Ok -> t.value
            is CodecResult.Err -> toml
        }
        val host = json.optJSONObject("server")?.optString("host", "").orEmpty()
        return CodecResult.Ok(PendingImport(name?.takeIf { it.isNotBlank() } ?: host, canonical, json))
    }
}

/** Opens the Google code scanner (QR only, no CAMERA permission needed). */
fun launchQrScanner(context: Context, onResult: (String) -> Unit, onUnavailable: () -> Unit) {
    try {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) onUnavailable() else onResult(raw)
            }
            .addOnFailureListener { onUnavailable() }
        // User backing out of the scanner is not an error: addOnCanceledListener is deliberately unused.
    } catch (e: Exception) {
        onUnavailable()
    }
}

/** Sheet to paste a mirage:// link or raw TOML. [onSubmit] returns an error message, or null on success. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteImportSheet(onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import config", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                label = { Text("Paste a mirage:// link or a client config (TOML)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = error != null,
                supportingText = error?.let { msg -> { Text(msg) } },
            )
            TextButton(onClick = {
                clipboard.getText()?.text?.let { text = it; error = null }
            }) { Text("Paste from clipboard") }
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { error = onSubmit(text) }, modifier = Modifier.weight(1f)) { Text("Import") }
            }
        }
    }
}

/** "Add server <name>?" (or Replace when a profile already exists). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfirmSheet(
    pending: PendingImport,
    replacing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (replacing) "Replace server with ${pending.name}?" else "Add server ${pending.name}?",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${pending.host}:${pending.port}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            if (replacing) {
                Text(
                    "Your current server settings will be replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pending.insecure) {
                Text(
                    "Warning: this config turns off certificate checking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Only add servers from sources you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(if (replacing) "Replace" else "Add") }
            }
        }
    }
}
