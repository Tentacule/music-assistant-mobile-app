package io.music_assistant.client.settings

/** Whether the album lists show the versions of an album as one entry or each on its own. */
enum class VersionGrouping {
    GROUPED,
    FLAT,
    ;

    fun toggled(): VersionGrouping = when (this) {
        GROUPED -> FLAT
        FLAT -> GROUPED
    }
}
