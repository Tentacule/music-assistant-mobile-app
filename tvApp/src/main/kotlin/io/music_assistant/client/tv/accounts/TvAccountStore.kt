package io.music_assistant.client.tv.accounts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The saved accounts, with their tokens. The file is excluded from the backups (`backup_rules.xml`,
 * `data_extraction_rules.xml`), like the shared secrets store.
 */
class TvAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_accounts", Context.MODE_PRIVATE)

    fun load(): List<TvAccount> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toAccount() }
    }.getOrDefault(emptyList())

    fun save(accounts: List<TvAccount>) {
        val array = JSONArray().apply { accounts.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    var activeId: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    /** Whether the accounts of the shared settings were imported once. */
    var migrated: Boolean
        get() = prefs.getBoolean(KEY_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIGRATED, value).apply()

    private fun TvAccount.toJson() = JSONObject().apply {
        put("serverId", serverId)
        putOpt("serverName", serverName)
        put("host", host)
        put("port", port)
        put("isTls", isTls)
        put("basePath", basePath)
        putOpt("userId", userId)
        putOpt("username", username)
        putOpt("displayName", displayName)
        putOpt("role", role)
        putOpt("token", token)
        put("pinMode", pinMode.name)
        putOpt("pinHash", pinHash)
        putOpt("pinSalt", pinSalt)
        put("lastUsedAt", lastUsedAt)
    }

    private fun JSONObject.toAccount(): TvAccount? {
        val serverId = optString("serverId").takeIf { it.isNotBlank() } ?: return null
        val host = optString("host").takeIf { it.isNotBlank() } ?: return null
        return TvAccount(
            serverId = serverId,
            serverName = optStringOrNull("serverName"),
            host = host,
            port = optInt("port", DEFAULT_PORT),
            isTls = optBoolean("isTls", false),
            basePath = optString("basePath", ""),
            userId = optStringOrNull("userId"),
            username = optStringOrNull("username"),
            displayName = optStringOrNull("displayName"),
            role = optStringOrNull("role"),
            token = optStringOrNull("token"),
            pinMode = PinMode.entries.firstOrNull { it.name == optString("pinMode") } ?: PinMode.NONE,
            pinHash = optStringOrNull("pinHash"),
            pinSalt = optStringOrNull("pinSalt"),
            lastUsedAt = optLong("lastUsedAt", 0),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ACTIVE = "active_id"
        const val KEY_MIGRATED = "migrated"
        const val DEFAULT_PORT = 8095
    }
}
