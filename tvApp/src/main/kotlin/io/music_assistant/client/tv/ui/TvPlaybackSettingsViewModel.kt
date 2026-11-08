package io.music_assistant.client.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerConfig
import io.music_assistant.client.data.model.server.ServerSettingEntry
import io.music_assistant.client.data.model.server.serverConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/** Where a playback setting lives on the server. */
sealed interface TvConfigSource {
    /** The settings of one queue, so of one player. Each follows the server default until it is set. */
    data class Queue(val queueId: String) : TvConfigSource

    /** The settings of a server controller: the defaults of all players, and server-wide values. */
    data class Core(val domain: String) : TvConfigSource
}

/**
 * Reads and saves the playback settings of the server. A source that the server does not answer
 * (no permission, or a server older than these settings) has no entries, and its rows stay hidden.
 */
class TvPlaybackSettingsViewModel(private val client: ServiceClient) : ViewModel() {
    private val log = Logger.withTag("TvPlaybackSettings")

    private val _configs = MutableStateFlow<Map<TvConfigSource, Map<String, ServerSettingEntry>>>(emptyMap())
    val configs: StateFlow<Map<TvConfigSource, Map<String, ServerSettingEntry>>> = _configs.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    // One save at a time, so the answer to an older save cannot overwrite a newer choice.
    private val saves = Mutex()

    fun load(source: TvConfigSource) {
        viewModelScope.launch { fetch(source) }
    }

    fun save(source: TvConfigSource, key: String, value: JsonElement) {
        _saveFailed.value = false
        // Shown at once; the answer of the server, or a fresh read after a failure, settles it.
        _configs.update { configs ->
            val entries = configs[source] ?: return@update configs
            val entry = entries[key] ?: return@update configs
            configs + (source to entries + (key to entry.copy(value = value)))
        }
        viewModelScope.launch {
            saves.withLock {
                val saved = client.sendRequest(source.saveRequest(key, value))
                    .onFailure { log.w(it) { "Saving $key failed" } }
                    .getOrNull()
                    ?.serverConfig()
                saved?.let { store(source, it) } ?: run {
                    _saveFailed.value = true
                    fetch(source)
                }
            }
        }
    }

    private suspend fun fetch(source: TvConfigSource) {
        val config = client.sendRequest(source.readRequest())
            .onFailure { log.w(it) { "Reading $source failed" } }
            .getOrNull()
            ?.serverConfig()
        config?.let { store(source, it) } ?: _configs.update { it - source }
    }

    // The answer to a save has no dynamic options (the playlists of the autoplay setting), and only
    // the read fills them, so the options already known stay.
    private fun store(source: TvConfigSource, config: ServerConfig) {
        _configs.update { configs ->
            val known = configs[source].orEmpty()
            val entries = config.values.mapValues { (key, entry) ->
                entry.copy(options = entry.options.ifEmpty { known[key]?.options.orEmpty() })
            }
            configs + (source to entries)
        }
    }

    private fun TvConfigSource.readRequest(): Request = when (this) {
        is TvConfigSource.Queue -> Request.Config.queue(queueId)
        is TvConfigSource.Core -> Request.Config.core(domain)
    }

    private fun TvConfigSource.saveRequest(key: String, value: JsonElement): Request = when (this) {
        is TvConfigSource.Queue -> Request.Config.saveQueue(queueId, key, value)
        is TvConfigSource.Core -> Request.Config.saveCore(domain, key, value)
    }
}
