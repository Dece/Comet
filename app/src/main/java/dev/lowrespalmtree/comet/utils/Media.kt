package dev.lowrespalmtree.comet.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import dev.lowrespalmtree.comet.MimeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Download media data from a ByteArray channel, into the media store.
 *
 * Run entirely in an IO coroutine. Convoluted because it tries to work properly with the Android 10
 * MediaStore API and be backward-compatible as well. Still unsure it succeeds with both.
 *
 * @param channel incoming bytes of server response
 * @param uri URI corresponding to the response
 * @param mimeType MIME type parsed from the response's meta; MUST be either image, audio or video
 * @param scope CoroutineScope to use for launching the download job
 * @param contentResolver ContentResolver for MediaStore access
 * @param onSuccess callback on completed download, with the saved media URI
 * @param onError callback on error with a short message to show
 */
fun downloadMedia(
    channel: Channel<ByteArray>,
    uri: Uri,
    mimeType: MimeType,
    scope: CoroutineScope,
    contentResolver: ContentResolver,
    onSuccess: (mediaUri: Uri) -> Unit,
    onError: (message: String) -> Unit,
) {
    val filename = uri.lastPathSegment.orEmpty().ifBlank { UUID.randomUUID().toString() }
    scope.launch(Dispatchers.IO) {
        // On Android Q and after, we use the proper MediaStore APIs. Proper…
        val filetype = mimeType.main.also { assert(it in listOf("image", "audio", "video")) }
        val mediaUri: Uri
        if (isPostQ()) {
            val details = ContentValues().apply {
                put(getIsPendingCV(filetype), 1)
                put(getDisplayNameCV(filetype), filename)
                put(getRelativePathCV(filetype), getRelativePath(filetype))
            }
            mediaUri = contentResolver.insert(getContentUri(filetype), details)
                    ?: return@launch Unit.also { onError("can't create local media file") }
            contentResolver.openOutputStream(mediaUri)?.use { os ->
                for (chunk in channel)
                    os.write(chunk)
            } ?: return@launch Unit.also { onError("can't open output stream") }
            details.clear()
            details.put(getIsPendingCV(filetype), 0)
            contentResolver.update(mediaUri, details, null, null)
        }
        // Before that, use the traditional clunky APIs. TODO test this stuff
        else {
            val collUri = getContentUri(filetype)
            val outputFile = File(File(collUri.toFile(), "Comet"), filename)
            FileOutputStream(outputFile).use { fos ->
                for (chunk in channel)
                    fos.buffered().write(chunk)
            }
            mediaUri = outputFile.toUri()
        }
        onSuccess(mediaUri)
    }
}

/** Get the default external content URI for this file type. */
private fun getContentUri(type: String) =
    when (type) {
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else -> throw UnsupportedOperationException()
    }

/** Get the display name *content value string identifier* for this file type. */
private fun getDisplayNameCV(type: String) =
    when (type) {
        "image" -> MediaStore.Images.Media.DISPLAY_NAME
        "audio" -> MediaStore.Audio.Media.DISPLAY_NAME
        "video" -> MediaStore.Video.Media.DISPLAY_NAME
        else -> throw UnsupportedOperationException()
    }

/** Get the isPending flag *content value string identifier* for this file type. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun getIsPendingCV(type: String) =
    when (type) {
        "image" -> MediaStore.Images.Media.IS_PENDING
        "audio" -> MediaStore.Audio.Media.IS_PENDING
        "video" -> MediaStore.Video.Media.IS_PENDING
        else -> throw UnsupportedOperationException()
    }

/** Get the relative path *content value string identifier* for this file type. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun getRelativePathCV(type: String) =
    when (type) {
        "image" -> MediaStore.Images.Media.RELATIVE_PATH
        "audio" -> MediaStore.Audio.Media.RELATIVE_PATH
        "video" -> MediaStore.Video.Media.RELATIVE_PATH
        else -> throw UnsupportedOperationException()
    }

/** Get the actual relative path for this file type, usually standard with a "Comet" subfolder. */
private fun getRelativePath(type: String) =
    when (type) {
        "image" -> Environment.DIRECTORY_PICTURES
        "audio" -> Environment.DIRECTORY_MUSIC  // TODO should be a user choice
        "video" -> Environment.DIRECTORY_MOVIES
        else -> throw UnsupportedOperationException()
    } + "/Comet"