package com.ogh.app.oauth

import android.content.Context
import android.util.Log
import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages OAuth tokens for connected provider accounts.
 *
 * Tokens are encrypted with AES-GCM using a non-exportable Android Keystore key.
 * Storage initialization and decryption fail closed; tokens are never stored in plaintext.
 */
class TokenManager(context: Context) {

    companion object {
        private const val TAG = "TokenManager"
    }

    private val storage = runCatching { AndroidKeystoreTokenStorage(context) }
        .onFailure { Log.e(TAG, "Secure token storage is unavailable", it) }
        .getOrNull()

    private val _accounts = MutableStateFlow<Map<StreamingProvider, ProviderAccount>>(emptyMap())
    val accounts: StateFlow<Map<StreamingProvider, ProviderAccount>> = _accounts.asStateFlow()

    init {
        loadAll()
    }

    fun saveAccount(account: ProviderAccount): Boolean {
        val secureStorage = storage ?: return false
        val key = account.provider.name
        val stored = runCatching {
            secureStorage.write("${key}_access_token", account.accessToken)
            secureStorage.write("${key}_refresh_token", account.refreshToken)
            secureStorage.write("${key}_expires_at", account.expiresAtMs.toString())
            secureStorage.write("${key}_user_name", account.userName)
            secureStorage.write("${key}_channel_id", account.channelId)
        }.onFailure {
            Log.e(TAG, "Failed to store ${account.provider.displayName} account securely", it)
        }.isSuccess
        if (!stored) return false

        val updated = _accounts.value.toMutableMap()
        updated[account.provider] = account
        _accounts.value = updated
        Log.i(TAG, "Account saved: ${account.provider.displayName}")
        return true
    }

    fun updateAccessToken(provider: StreamingProvider, accessToken: String, expiresAtMs: Long) {
        val existing = _accounts.value[provider] ?: return
        saveAccount(existing.copy(accessToken = accessToken, expiresAtMs = expiresAtMs))
    }

    fun removeAccount(provider: StreamingProvider) {
        val key = provider.name
        storage?.remove(
            "${key}_access_token",
            "${key}_refresh_token",
            "${key}_expires_at",
            "${key}_user_name",
            "${key}_channel_id",
        )

        val updated = _accounts.value.toMutableMap()
        updated.remove(provider)
        _accounts.value = updated
        Log.i(TAG, "Account removed: ${provider.displayName}")
    }

    fun getAccount(provider: StreamingProvider): ProviderAccount? = _accounts.value[provider]

    private fun loadAll() {
        val secureStorage = storage ?: return
        val loaded = mutableMapOf<StreamingProvider, ProviderAccount>()
        for (provider in StreamingProvider.entries) {
            val key = provider.name
            runCatching {
                val accessToken = secureStorage.read("${key}_access_token") ?: return@runCatching
                loaded[provider] = ProviderAccount(
                    provider = provider,
                    accessToken = accessToken,
                    refreshToken = secureStorage.read("${key}_refresh_token"),
                    expiresAtMs = secureStorage.read("${key}_expires_at")?.toLongOrNull() ?: 0,
                    userName = secureStorage.read("${key}_user_name").orEmpty(),
                    channelId = secureStorage.read("${key}_channel_id").orEmpty(),
                )
            }.onFailure {
                Log.e(TAG, "Failed to decrypt ${provider.displayName} account", it)
                removeAccount(provider)
            }
        }
        _accounts.value = loaded
        Log.i(TAG, "Loaded ${loaded.size} connected accounts")
    }
}
