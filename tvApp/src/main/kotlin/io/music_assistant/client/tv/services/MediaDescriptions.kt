package io.music_assistant.client.tv.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.EpisodeNumberFormat
import io.music_assistant.client.data.model.client.items.subtitleWith

/** A `android.resource://` URI for a drawable, for media-session icons. */
fun @receiver:DrawableRes Int.toUri(context: Context): Uri = Uri.parse(
    ContentResolver.SCHEME_ANDROID_RESOURCE +
        "://" + context.resources.getResourcePackageName(this) +
        '/' + context.resources.getResourceTypeName(this) +
        '/' + context.resources.getResourceEntryName(this),
)

/**
 * The media-session description of an item (the queue shown by the system media UI). The phone
 * app's version also serves Android Auto browse rows; the TV has no media browser.
 */
fun AppMediaItem.toMediaDescription(
    defaultIconUri: Uri,
    episodeNumbers: EpisodeNumberFormat = EpisodeNumberFormat.SOURCE,
): MediaDescriptionCompat = MediaDescriptionCompat.Builder()
    .setMediaId("${itemId}__${uri}__${mediaType}__$provider")
    .setTitle(displayName)
    .setSubtitle(subtitleWith(episodeNumbers))
    .setMediaUri(uri?.let { Uri.parse(it) })
    .setIconUri(image(ImageType.THUMB)?.url?.let { Uri.parse(it) } ?: defaultIconUri)
    .build()
