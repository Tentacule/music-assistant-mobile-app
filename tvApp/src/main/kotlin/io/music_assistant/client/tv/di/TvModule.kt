package io.music_assistant.client.tv.di

import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.TvUserActivity
import io.music_assistant.client.tv.services.SharedMediaSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun tvModule() = module {
    single { SharedMediaSessionManager(androidContext(), get(), get()) }
    // A TV never connects to a car; the shared module still asks for the signal.
    single<CarConnectionMonitor> { NoCarConnectionMonitor }
    single { TvPreferences(androidContext()) }
    single { TvUserActivity() }
}

private object NoCarConnectionMonitor : CarConnectionMonitor {
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
}
