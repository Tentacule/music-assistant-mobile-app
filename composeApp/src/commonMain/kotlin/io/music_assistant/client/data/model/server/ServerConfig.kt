package io.music_assistant.client.data.model.server

import io.music_assistant.client.api.Answer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A server settings object (`Config` in `music_assistant_models`): its entries, by key. */
@Serializable
data class ServerConfig(
    val values: Map<String, ServerConfigEntry> = emptyMap(),
)

/** One setting. `type` is the server's `ConfigEntryType`, such as `boolean`, `integer` or `string`. */
@Serializable
data class ServerConfigEntry(
    val key: String,
    val type: String,
    val value: JsonElement? = null,
    @SerialName("default_value") val defaultValue: JsonElement? = null,
    val options: List<ServerConfigOption> = emptyList(),
    val range: List<Int>? = null,
    @SerialName("depends_on") val dependsOn: String? = null,
    @SerialName("depends_on_value") val dependsOnValue: JsonElement? = null,
    @SerialName("depends_on_value_not") val dependsOnValueNot: JsonElement? = null,
    val hidden: Boolean = false,
) {
    /** The value in effect, as text: the stored value or else the default. */
    val currentText: String?
        get() = value.primitiveText() ?: defaultValue.primitiveText()

    /** Whether the setting applies, given the other entries of the same config. */
    fun appliesWith(entries: Map<String, ServerConfigEntry>): Boolean {
        val parent = dependsOn?.let { entries[it] }?.currentText
        val expected = dependsOnValue.primitiveText()
        val excluded = dependsOnValueNot.primitiveText()
        return when {
            dependsOn == null -> true
            expected != null -> parent == expected
            excluded != null -> parent != excluded
            else -> parent != null && parent != "false" && parent.isNotEmpty()
        }
    }
}

@Serializable
data class ServerConfigOption(
    val value: JsonElement,
    val title: String? = null,
    val disabled: Boolean = false,
) {
    val valueText: String? get() = value.primitiveText()
}

/** The settings in an answer, or null when the server answered with an error. */
fun Answer.serverConfig(): ServerConfig? = takeUnless { json.containsKey("error_code") }?.resultAs<ServerConfig>()

/** The content of a JSON primitive as text, and null for a JSON null or a non-primitive. */
private fun JsonElement?.primitiveText(): String? = (this as? JsonPrimitive)?.contentOrNull
