package com.mirage.vpn

import android.content.Context

class ConfigStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY_CONFIG, null) ?: DEFAULT_CONFIG

    fun save(config: String) {
        prefs.edit().putString(KEY_CONFIG, config).apply()
    }

    companion object {
        private const val PREFS_NAME = "mirage_prefs"
        private const val KEY_CONFIG = "config_toml"

        val DEFAULT_CONFIG = """
[server]
host = "mirage.example.com"
port = 443

[authentication]
username = "test"
password = "test"
# trusted_certificates = [
#     "-----BEGIN CERTIFICATE-----\n..."
# ]

[transport]
# Tried in order: "udp" (QUIC) and/or "tcp" (TCP+TLS)
protocols = ["udp", "tcp"]
parallel_connections = 1
dual_stack = false
# sni = "www.example.com"
insecure = false
tcp_nodelay = true
port_hopping_interval_s = 0

[camouflage]
mode = "mirage"
target_sni = "www.microsoft.com"
short_ids = ["abcd1234deadbeef"]
# jls_password = "your-jls-password"
# jls_iv = "your-jls-iv"
# inner_key = "your-inner-encryption-key"

[connection]
mtu = 1280
outer_mtu = 1420
timeout_s = 30
retry_interval_s = 5
keep_alive_interval_s = 25
max_lifetime_s = 300
lifetime_jitter_s = 60
mux_mode = "round_robin"

[obfuscation]
enabled = true
padding_probability = 0.15
padding_min = 40
padding_max = 1200
jitter_min_ms = 0
jitter_max_ms = 0

[network]
routes = [
    "10.11.12.0/24",
    "fd00::/64"
]
excluded_routes = [
    "192.168.0.0/16",
    "172.16.0.0/12",
    "10.0.0.0/8"
]
dns_servers = [
    "1.1.1.1",
    "2606:4700:4700::1111"
]

[log]
level = "info"
""".trimStart()
    }
}
