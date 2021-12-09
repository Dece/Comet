package dev.lowrespalmtree.comet

import android.net.Uri
import android.util.Log

/**
 * Transform a relative URI to an absolute Gemini URI
 *
 * This is mostly to translate user-friendly URLs such as "medusae.space" into
 * "gemini://medusae.space". This assumes that the Uri parsing put what the user probably intended
 * as the hostname into the path field instead of the authority. Thus, it will NOT merely translate
 * any absolute URI into a gemini-scheme URI.
 */
fun toGeminiUri(uri: Uri): Uri =
    Uri.Builder()
        .scheme("gemini")
        .authority(uri.path)
        .query(uri.query)
        .fragment(uri.fragment)
        .build()

/** Return the URI obtained from considering the relative part of an URI wrt/ a base URI. */
fun joinUrls(base: String, relative: String): Uri {
    val baseUri = Uri.parse(base)
    val relUri = Uri.parse(relative)
    val newPath = removeDotSegments(
        if (relative.startsWith("/")) {
            relUri.path ?: ""
        } else {
            removeLastSegment(
                baseUri.path ?: ""
            ) + "/" +
                    (relUri.path ?: "")
        }
    )
    return Uri.Builder()
        .scheme(baseUri.scheme)
        .authority(baseUri.authority)
        .path(newPath)
        .query(relUri.query)
        .fragment(relUri.fragment)
        .build()
}

/** Remove all the sneaky dot segments from the path. */
internal fun removeDotSegments(path: String): String {
    var output = ""
    var slice = path
    while (slice.isNotEmpty()) {
        if (slice.startsWith("../")) {
            slice = slice.substring(3)
        } else if (slice.startsWith("./") || slice.startsWith("/./")) {
            slice = slice.substring(2)
        } else if (slice == "/.") {
            slice = "/"
        } else if (slice.startsWith("/../")) {
            slice = "/" + slice.substring(4)
            output = removeLastSegment(output)
        } else if (slice == "/..") {
            slice = "/"
            output = removeLastSegment(output)
        } else if (slice == "." || slice == "..") {
            slice = ""
        } else {
            val (firstSegment, remaining) = popFirstSegment(slice)
            output += firstSegment
            slice = remaining
        }
    }
    return output
}

/** Remove the last segment of that path, including preceding "/" if any. */
internal fun removeLastSegment(path: String): String {
    val lastSlashIndex = path.indexOfLast { it == '/' }
    return if (lastSlashIndex > -1) path.slice(0 until lastSlashIndex) else path
}

/** Return first segment and the rest. */
internal fun popFirstSegment(path: String): Pair<String, String> {
    if (path.isEmpty())
        return Pair(path, "")
    var nextSlash = path.substring(1).indexOf("/")
    if (nextSlash == -1)
        return Pair(path, "")
    nextSlash++
    return Pair(path.substring(0, nextSlash), path.substring(nextSlash))
}