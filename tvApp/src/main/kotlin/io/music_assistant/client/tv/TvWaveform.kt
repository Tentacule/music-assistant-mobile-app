package io.music_assistant.client.tv

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The waveform of what a provider streams: the energy of the track (0 to 1) in bins that cover its
 * duration. The bins found are kept for the last tracks. A track without bins is not kept, because
 * the server can analyze it later.
 */
class TvWaveform(private val client: ServiceClient) {
    private val log = Logger.withTag("TvWaveform")
    private val cache = lruMap<String, List<Float>>()

    suspend fun bins(itemId: String, provider: String): List<Float>? {
        val key = "$provider/$itemId"
        synchronized(cache) { cache[key]?.let { return it } }
        val request = Request(
            command = "audio_analysis/wave_form",
            args = buildJsonObject {
                put("item_id", itemId)
                put("provider_instance_id_or_domain", provider)
            },
        )
        val bins = client.sendRequest(request)
            .onFailure { log.w(it) { "Waveform of $key failed to load" } }
            .getOrNull()
            ?.resultAs<List<Float>>()
            ?.takeIf { it.isNotEmpty() }
        log.d { "Waveform of $key: ${bins?.size ?: 0} bins" }
        bins ?: return null
        synchronized(cache) { cache[key] = bins }
        return bins
    }
}
