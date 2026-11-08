package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.AudioSource
import io.music_assistant.client.data.model.client.items.EpisodeNumberFormat
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.data.model.client.items.subtitleWith
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.album_versions_count
import musicassistantclient.composeapp.generated.resources.episode_number
import musicassistantclient.composeapp.generated.resources.episode_number_season
import musicassistantclient.composeapp.generated.resources.item_subtitle_ai_host
import musicassistantclient.composeapp.generated.resources.item_subtitle_audio_source
import musicassistantclient.composeapp.generated.resources.item_subtitle_genre
import musicassistantclient.composeapp.generated.resources.item_subtitle_playlist
import musicassistantclient.composeapp.generated.resources.item_subtitle_playlist_dynamic
import musicassistantclient.composeapp.generated.resources.item_subtitle_podcast
import musicassistantclient.composeapp.generated.resources.item_subtitle_radio
import musicassistantclient.composeapp.generated.resources.item_subtitle_series
import musicassistantclient.composeapp.generated.resources.item_subtitle_sound
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Returns the item's `subtitle` (with the localized episode label for a podcast
 * episode) if non-null; otherwise a localized fallback for types whose subtitle
 * is just the content-type label.
 */
/**
 * [text] followed by how many versions a collapsed album stands for (see `collapseVersions`),
 * such as "The Prodigy · 2 versions". Null when both are missing.
 */
@Composable
fun Album.withVersionCount(text: String?): String? = listOfNotNull(
    text?.takeIf { it.isNotBlank() },
    versionCount.takeIf { it > 1 }?.let { pluralStringResource(Res.plurals.album_versions_count, it, it) },
).joinToString(" · ").takeIf { it.isNotEmpty() }

@Composable
fun AppMediaItem.localizedSubtitle(): String? = when (this) {
    is PodcastEpisode -> subtitleWith(
        EpisodeNumberFormat(
            seasonEpisode = stringResource(Res.string.episode_number_season),
            episodeOnly = stringResource(Res.string.episode_number),
        ),
    )
    // A collapsed album says how many versions it stands for (see collapseVersions).
    is Album -> withVersionCount(subtitle)
    else -> subtitle
} ?: when (this) {
    is Playlist -> stringResource(if (this.isDynamic) Res.string.item_subtitle_playlist_dynamic else Res.string.item_subtitle_playlist)
    is Genre -> stringResource(Res.string.item_subtitle_genre)
    is MediaCollection -> stringResource(Res.string.item_subtitle_series)
    is Podcast -> stringResource(Res.string.item_subtitle_podcast)
    is RadioStation -> stringResource(Res.string.item_subtitle_radio)
    is SoundEffect -> stringResource(if (isAiHost) Res.string.item_subtitle_ai_host else Res.string.item_subtitle_sound)
    is AudioSource -> stringResource(Res.string.item_subtitle_audio_source)
    else -> null
}
