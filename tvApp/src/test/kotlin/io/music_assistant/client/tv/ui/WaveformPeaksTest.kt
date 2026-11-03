package io.music_assistant.client.tv.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WaveformPeaksTest {
    @Test
    fun noBinsOrNoBarsGiveNoPeaks() {
        assertEquals(emptyList(), waveformPeaks(emptyList(), 10))
        assertEquals(emptyList(), waveformPeaks(listOf(0.5f), 0))
    }

    @Test
    fun moreBinsThanBarsKeepTheHighestOfEachBar() {
        val bins = listOf(0.1f, 0.9f, 0.2f, 0.3f, 0.8f, 0.4f)
        assertEquals(listOf(0.9f, 0.8f), waveformPeaks(bins, 2))
    }

    @Test
    fun fewerBinsThanBarsRepeatEachBin() {
        assertEquals(listOf(0.1f, 0.1f, 0.7f, 0.7f), waveformPeaks(listOf(0.1f, 0.7f), 4))
    }

    @Test
    fun theLastBinIsCovered() {
        val bins = listOf(0.1f, 0.2f, 0.3f, 0.4f, 1f)
        assertEquals(1f, waveformPeaks(bins, 3).last())
    }
}
