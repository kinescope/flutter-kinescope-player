package io.kinescope.flutter_kinescope_sdk

import android.net.Uri
import androidx.media3.common.MimeTypes

/**
 * Resolves user-entered download targets: Kinescope video id / page URL, or a raw HLS/DASH manifest.
 */
internal object KinescopeDownloadUrlResolver {
    sealed class Target {
        data class VideoId(val videoId: String) : Target()
        data class Manifest(
            val uri: Uri,
            val mimeType: String,
        ) : Target()
    }

    fun resolve(raw: String): Target {
        val input = raw.trim()
        require(input.isNotEmpty()) { "URL or video id is empty" }

        if (looksLikeBareVideoId(input)) {
            return Target.VideoId(input)
        }

        val uri = Uri.parse(input)
        val host = uri.host?.lowercase().orEmpty()
        if (host.contains("kinescope")) {
            extractKinescopeVideoId(uri)?.let { return Target.VideoId(it) }
        }

        if (looksLikeManifest(uri, input)) {
            return Target.Manifest(
                uri = uri,
                mimeType = mimeTypeFor(uri, input),
            )
        }

        // Path-only kinescope-style ids with query noise, e.g. after failed Uri host parse.
        val pathId = uri.pathSegments.lastOrNull { it.isNotBlank() && looksLikeBareVideoId(it) }
        if (pathId != null && !looksLikeManifest(uri, input)) {
            return Target.VideoId(pathId)
        }

        throw IllegalArgumentException(
            "Unrecognized download link. Use a Kinescope video id/URL or an .m3u8/.mpd manifest.",
        )
    }

    private fun looksLikeBareVideoId(value: String): Boolean {
        if (value.contains('/') || value.contains('.') || value.contains(':')) {
            return false
        }
        return value.matches(Regex("^[A-Za-z0-9_-]{6,}$"))
    }

    private fun extractKinescopeVideoId(uri: Uri): String? {
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) {
            return null
        }
        // /embed/{id}, /{id}, /video/{id}
        val embedIndex = segments.indexOfFirst { it.equals("embed", ignoreCase = true) }
        if (embedIndex >= 0 && embedIndex + 1 < segments.size) {
            return segments[embedIndex + 1].takeIf { looksLikeBareVideoId(it) }
        }
        val videoIndex = segments.indexOfFirst { it.equals("video", ignoreCase = true) }
        if (videoIndex >= 0 && videoIndex + 1 < segments.size) {
            return segments[videoIndex + 1].takeIf { looksLikeBareVideoId(it) }
        }
        return segments.lastOrNull { looksLikeBareVideoId(it) }
    }

    private fun looksLikeManifest(uri: Uri, raw: String): Boolean {
        val path = uri.path?.lowercase().orEmpty()
        val lower = raw.lowercase()
        return path.endsWith(".m3u8") ||
            path.endsWith(".mpd") ||
            lower.contains(".m3u8") ||
            lower.contains(".mpd") ||
            lower.contains("application/vnd.apple.mpegurl") ||
            lower.contains("application/dash+xml")
    }

    private fun mimeTypeFor(uri: Uri, raw: String): String {
        val path = uri.path?.lowercase().orEmpty()
        val lower = raw.lowercase()
        return if (path.endsWith(".mpd") || lower.contains(".mpd") || lower.contains("dash+xml")) {
            MimeTypes.APPLICATION_MPD
        } else {
            MimeTypes.APPLICATION_M3U8
        }
    }
}
