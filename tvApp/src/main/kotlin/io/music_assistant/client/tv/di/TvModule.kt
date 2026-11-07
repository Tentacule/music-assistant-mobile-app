package io.music_assistant.client.tv.di

import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.tv.TvLyrics
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.TvUserActivity
import io.music_assistant.client.tv.TvVolumeController
import io.music_assistant.client.tv.TvWaveform
import io.music_assistant.client.tv.accounts.TvAccountManager
import io.music_assistant.client.tv.audio.TapAudioSink
import io.music_assistant.client.tv.audio.VisualizerTap
import io.music_assistant.client.tv.services.SharedMediaSessionManager
import io.music_assistant.client.tv.services.TvVolumeProvider
import io.music_assistant.client.tv.ui.TvPlaybackSettingsViewModel
import io.music_assistant.client.utils.sendspinClock
import io.music_assistant.sendspin.android.AudioTrackSink
import io.music_assistant.sendspin.api.AudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun tvModule() = module {
    single { TvVolumeController() }
    single { TvVolumeProvider(get(), get(), get()) }
    // No VolumeProvider: the TV uses setPlaybackToLocal (default), so Android TV handles
    // system volume natively. Volume keys are intercepted in TvMainActivity instead.
    single { SharedMediaSessionManager(androidContext(), get(), get()) }
    // A TV never connects to a car; the shared module still asks for the signal.
    single<CarConnectionMonitor> { NoCarConnectionMonitor }
    single { TvPreferences(androidContext()) }
    single { TvLyrics(get()) }
    single { TvWaveform(get()) }
    single { TvUserActivity() }
    single(createdAtStart = true) { TvAccountManager(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { TvPlaybackSettingsViewModel(get()) }
    // The TV's audio output, with a copy for the Now playing visualizations. Replaces the shared
    // binding (this module loads after androidModule()).
    single { VisualizerTap(sendspinClock) }
    single<AudioSink> { TapAudioSink(AudioTrackSink(androidContext(), sendspinClock), get()) }
}

private object NoCarConnectionMonitor : CarConnectionMonitor {
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
}
