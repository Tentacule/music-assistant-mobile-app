package io.music_assistant.client.tv

import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The remote's last key press: its time restarts the idle timer of the Now playing mode, and its
 * code tells a focus move the user asked for (D-pad Left into the menu) from one the system made.
 */
class TvUserActivity {
    private val _lastKeyAt = MutableStateFlow(SystemClock.elapsedRealtime())
    val lastKeyAt: StateFlow<Long> = _lastKeyAt.asStateFlow()

    @Volatile
    var lastKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
        private set

    private val _nowPlayingToggles = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The remote's Menu key: opens or closes the Now playing mode from any screen. */
    val nowPlayingToggles: SharedFlow<Unit> = _nowPlayingToggles

    fun toggleNowPlaying() {
        _nowPlayingToggles.tryEmit(Unit)
    }

    fun onKey(keyCode: Int) {
        lastKeyCode = keyCode
        _lastKeyAt.value = SystemClock.elapsedRealtime()
    }
}
