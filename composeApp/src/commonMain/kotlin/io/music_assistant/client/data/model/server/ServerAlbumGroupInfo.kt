package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a library album stands within its MusicBrainz release group. */
@Serializable
data class ServerAlbumGroupInfo(
    @SerialName("release_group_id") val releaseGroupId: String? = null,
    @SerialName("version_count") val versionCount: Int = 1,
    @SerialName("default_item_id") val defaultItemId: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("can_set_default") val canSetDefault: Boolean = false,
)
