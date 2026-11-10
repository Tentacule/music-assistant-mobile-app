package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerAlbumSeriesInfo(
    @SerialName("release_group_id") val releaseGroupId: String,
    @SerialName("series_id") val seriesId: String,
    @SerialName("series_name") val seriesName: String,
    @SerialName("order_number") val orderNumber: String? = null,
)
