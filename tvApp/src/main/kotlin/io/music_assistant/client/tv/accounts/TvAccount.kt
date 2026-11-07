package io.music_assistant.client.tv.accounts

import io.music_assistant.client.api.ConnectionInfo
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** When the TV asks for the PIN of an account. */
enum class PinMode {
    /** Never: anyone can switch to the account. */
    NONE,

    /** When another account switches to this one. */
    ON_SWITCH,

    /** When another account switches to this one, and when the app starts on it. */
    ON_SWITCH_AND_START,
}

/**
 * One user signed in on one server. The TV keeps several and switches between them. [userId] is
 * null for an account that an earlier version saved: the server names its user at the next sign-in.
 * [token] is null once the server refused it: the account then needs a new sign-in.
 */
data class TvAccount(
    val serverId: String,
    val serverName: String?,
    val host: String,
    val port: Int,
    val isTls: Boolean,
    val basePath: String,
    val userId: String?,
    val username: String?,
    val displayName: String?,
    val role: String?,
    val token: String?,
    val pinMode: PinMode = PinMode.NONE,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val lastUsedAt: Long = 0,
) {
    val id: String get() = accountId(serverId, userId)

    val connectionInfo: ConnectionInfo get() = ConnectionInfo(host, port, isTls, basePath)

    val hasPin: Boolean get() = pinMode != PinMode.NONE && pinHash != null

    /** The name to show: the user, or the server for an account whose user is still unknown. */
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: serverLabel

    val serverLabel: String get() = serverName?.takeIf { it.isNotBlank() } ?: "$host:$port"

    fun withPin(pin: String, mode: PinMode): TvAccount {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return copy(pinMode = mode, pinHash = hashPin(pin, salt), pinSalt = salt.encode())
    }

    fun withoutPin(): TvAccount = copy(pinMode = PinMode.NONE, pinHash = null, pinSalt = null)

    fun checkPin(pin: String): Boolean {
        val hash = pinHash ?: return true
        val salt = pinSalt?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } ?: return false
        return hashPin(pin, salt) == hash
    }
}

fun accountId(serverId: String, userId: String?) = "$serverId/${userId ?: "?"}"

/** A PIN has few combinations, so a slow hash only delays a guess on a stolen copy of the file. */
private fun hashPin(pin: String, salt: ByteArray): String {
    val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_BITS)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.encode()
}

private fun ByteArray.encode(): String = Base64.getEncoder().encodeToString(this)

const val PIN_LENGTH = 4
private const val SALT_BYTES = 16
private const val PIN_HASH_ITERATIONS = 20_000
private const val PIN_HASH_BITS = 256
