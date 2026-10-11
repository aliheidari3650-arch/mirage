package com.mirage.vpn.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * The single active server profile.
 *
 * [configJson] is the ClientConfig JSON (serde shape). It is treated as immutable: copy it
 * (`JSONObject(configJson.toString())`) before editing in place. [toml] is what gets handed to
 * the VPN service.
 */
data class Profile(val name: String, val configJson: JSONObject, val toml: String) {
    val host: String get() = configJson.optJSONObject("server")?.optString("host", "").orEmpty()
    val port: Int get() = configJson.optJSONObject("server")?.optInt("port", 443) ?: 443

    val username: String get() = configJson.optJSONObject("authentication")?.optString("username", "").orEmpty()
    val password: String get() = configJson.optJSONObject("authentication")?.optString("password", "").orEmpty()
    val needsCredentials: Boolean get() = username.isBlank() || password.isEmpty()

    val protocolLabel: String
        get() {
            val arr = configJson.optJSONObject("transport")?.optJSONArray("protocols")
            val list = if (arr == null) listOf("udp", "tcp") else List(arr.length()) { arr.optString(it) }
            return when {
                list.size > 1 -> "Auto"
                list.firstOrNull() == "udp" -> "QUIC"
                list.firstOrNull() == "tcp" -> "TCP"
                else -> "Auto"
            }
        }

    val allTraffic: Boolean
        get() {
            val arr = configJson.optJSONObject("network")?.optJSONArray("routes") ?: return false
            return (0 until arr.length()).any { arr.optString(it) == "0.0.0.0/0" }
        }

    val disguised: Boolean
        get() {
            val camo = configJson.optJSONObject("camouflage") ?: return true
            return camo.optString("mode", "mirage") != "none"
        }

    val summary: String
        get() = listOfNotNull(
            protocolLabel,
            if (allTraffic) "All traffic" else "Private network only",
            if (disguised) "Disguised" else null,
        ).joinToString(" · ")
}

/** Persists the active [Profile]. Uses EncryptedSharedPreferences (it holds the password). */
class ProfileStore(context: Context) {
    private val prefs: SharedPreferences = open(context.applicationContext)

    fun load(): Profile? = try {
        val json = prefs.getString(KEY_JSON, null)
        val toml = prefs.getString(KEY_TOML, null)
        if (json == null || toml == null) null
        else Profile(prefs.getString(KEY_NAME, "").orEmpty(), JSONObject(json), toml)
    } catch (e: Exception) {
        Log.w(TAG, "Could not read saved profile", e)
        null
    }

    fun save(profile: Profile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name)
            .putString(KEY_JSON, profile.configJson.toString())
            .putString(KEY_TOML, profile.toml)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "ProfileStore"
        const val KEY_NAME = "profile_name"
        const val KEY_JSON = "profile_json"
        const val KEY_TOML = "profile_toml"

        fun open(context: Context): SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "mirage_profile_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore problems happen on some devices/ROMs; better to work than to crash.
            Log.w(TAG, "Encrypted storage unavailable, using plain preferences", e)
            context.getSharedPreferences("mirage_profile_plain", Context.MODE_PRIVATE)
        }
    }
}
