package io.kinescope.flutter_kinescope_sdk

import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

object KinescopeOfflineIds {
    fun stableContentId(manifestUri: String, heightPx: Int = 0): String {
        return try {
            val base = manifestUri.substringBefore("?")
            val stablePart = if (heightPx > 0) "$base#$heightPx" else base
            val digest = MessageDigest.getInstance("SHA-256").digest(stablePart.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    fun videoIdFromManifest(manifestUri: String): String? {
        return try {
            val parts = manifestUri.substringBefore("?").split("/")
            parts.getOrNull(parts.size - 2)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
