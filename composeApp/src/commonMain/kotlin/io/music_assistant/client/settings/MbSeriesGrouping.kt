package io.music_assistant.client.settings

/** Whether the album library list groups albums by MusicBrainz series. */
enum class MbSeriesGrouping {
    FLAT,
    GROUPED,
    ;

    fun toggled(): MbSeriesGrouping = when (this) {
        FLAT -> GROUPED
        GROUPED -> FLAT
    }
}
