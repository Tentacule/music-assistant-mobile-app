package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerAlbumVersionEnrichment(
    @SerialName("mb_release_id") val mbReleaseId: String,
    @SerialName("catalog_numbers") val catalogNumbers: List<String> = emptyList(),
    @SerialName("label_names") val labelNames: List<String> = emptyList(),
    @SerialName("track_count_per_disc") val trackCountPerDisc: List<Int> = emptyList(),
    @SerialName("release_group_id") val releaseGroupId: String? = null,
) {
    val totalTrackCount: Int get() = trackCountPerDisc.sum()
}
