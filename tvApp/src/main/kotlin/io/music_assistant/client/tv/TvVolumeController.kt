package io.music_assistant.client.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shows a transient volume overlay and hides it after [HIDE_DELAY_MS] of inactivity. */
class TvVolumeController {
    private val scope = CoroutineScope(SupervisorJob())

    data class VolumeInfo(
        /** 0.0–1.0, used as the progress bar fill. */
        val level: Float,
        val isMuted: Boolean = false,
        val playerName: String? = null,
    )

    private val _overlay = MutableStateFlow<VolumeInfo?>(null)
    val overlay: StateFlow<VolumeInfo?> = _overlay.asStateFlow()

    private var hideJob: Job? = null

    val isVisible: Boolean get() = overlay.value != null

    fun show(level: Float, isMuted: Boolean = false, playerName: String? = null) {
        hideJob?.cancel()
        _overlay.value = VolumeInfo(level.coerceIn(0f, 1f), isMuted, playerName)
        hideJob = scope.launch {
            delay(HIDE_DELAY_MS)
            _overlay.value = null
        }
    }

    private companion object {
        const val HIDE_DELAY_MS = 3_000L
    }
}
