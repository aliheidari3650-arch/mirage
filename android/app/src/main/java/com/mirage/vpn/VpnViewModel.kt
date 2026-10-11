package com.mirage.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConfigStore(application)

    private val _config = MutableStateFlow(store.load())
    val config: StateFlow<String> = _config

    val status: StateFlow<Int> = MirageVpnService.status
    val statusMessage: StateFlow<String?> = MirageVpnService.statusMessage
    val metrics: StateFlow<LongArray> = MirageVpnService.metrics
    val activeProtocol: StateFlow<String> = MirageVpnService.activeProtocol
    val isRunning: StateFlow<Boolean> = MirageVpnService.isRunning

    fun updateConfig(value: String) {
        _config.value = value
        store.save(value)
    }
}
