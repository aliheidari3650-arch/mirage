package com.mirage.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mirage.vpn.data.AccessLink
import com.mirage.vpn.data.CodecResult
import com.mirage.vpn.data.ConfigCodec
import com.mirage.vpn.data.Profile
import com.mirage.vpn.data.ProfileStore
import com.mirage.vpn.ui.ImportParser
import com.mirage.vpn.ui.PendingImport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProfileStore(application)

    private val _profile = MutableStateFlow(store.load())
    val profile: StateFlow<Profile?> = _profile

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport

    // One-shot snackbar messages (buffered so deep-link results aren't lost before the UI collects).
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val status: StateFlow<Int> = MirageVpnService.status
    val statusMessage: StateFlow<String?> = MirageVpnService.statusMessage
    val metrics: StateFlow<LongArray> = MirageVpnService.metrics
    val activeProtocol: StateFlow<String> = MirageVpnService.activeProtocol
    val isRunning: StateFlow<Boolean> = MirageVpnService.isRunning

    // Total bytes/second for the last 40 one-second metric ticks (sparkline).
    private val _samples = MutableStateFlow<List<Float>>(emptyList())
    val samples: StateFlow<List<Float>> = _samples

    init {
        viewModelScope.launch {
            var last = -1L
            combine(MirageVpnService.status, MirageVpnService.metrics) { s, m -> s to m }
                .collect { (s, m) ->
                    if (s != MirageVpnService.STATUS_CONNECTED || m.size < 2) {
                        last = -1L
                        if (_samples.value.isNotEmpty()) _samples.value = emptyList()
                    } else {
                        val total = m[0] + m[1]
                        if (last >= 0) {
                            _samples.value = (_samples.value + (total - last).coerceAtLeast(0L).toFloat()).takeLast(MAX_SAMPLES)
                        }
                        last = total
                    }
                }
        }
    }

    fun say(message: String) {
        _messages.trySend(message)
    }

    fun saveProfile(profile: Profile) {
        store.save(profile)
        _profile.value = profile
    }

    /**
     * Decodes a link / TOML into a pending import (shown in the confirm sheet).
     * Returns an error message, or null on success.
     */
    fun importFromText(text: String): String? =
        when (val r = ImportParser.fromText(text)) {
            is CodecResult.Err -> r.message
            is CodecResult.Ok -> {
                _pendingImport.value = r.value
                null
            }
        }

    fun dismissImport() {
        _pendingImport.value = null
    }

    /** Saves the pending import as the active profile and returns it. */
    fun confirmImport(): Profile? {
        val p = _pendingImport.value ?: return null
        _pendingImport.value = null
        val profile = Profile(p.name, p.json, p.toml)
        saveProfile(profile)
        return profile
    }

    /**
     * Sign-in for builds with a built-in server: fills username/password into the embedded
     * config and saves it. Returns an error message, or null on success.
     */
    fun signIn(username: String, password: String): String? {
        val bytes = AccessLink.decodeBase64(BuildConfig.DEFAULT_CONFIG_B64)
            ?: return "The built-in server configuration is damaged."
        val json = when (val p = ConfigCodec.parse(String(bytes, Charsets.UTF_8))) {
            is CodecResult.Err -> return p.message
            is CodecResult.Ok -> p.value
        }
        // Edit in place so every other key of the built-in config is preserved.
        val auth = json.optJSONObject("authentication") ?: JSONObject().also { json.put("authentication", it) }
        auth.put("username", username.trim())
        auth.put("password", password)
        val toml = when (val t = ConfigCodec.toToml(json)) {
            is CodecResult.Err -> return t.message
            is CodecResult.Ok -> t.value
        }
        when (val check = ConfigCodec.parse(toml)) {
            is CodecResult.Err -> return check.message
            is CodecResult.Ok -> Unit
        }
        val host = json.optJSONObject("server")?.optString("host", "").orEmpty()
        saveProfile(Profile(host, json, toml))
        return null
    }

    private companion object {
        const val MAX_SAMPLES = 40
    }
}
