package io.kinescope.flutter_kinescope_sdk

import android.os.Handler
import android.os.Looper
import io.kinescope.sdk.api.KinescopeApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object KinescopeVideoCatalogHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getVideos(callback: (Result<List<Map<String, Any?>>>) -> Unit) {
        val apiKey = KinescopeSdkConfig.apiKey
        if (apiKey.isNullOrBlank()) {
            mainHandler.post {
                callback(
                    Result.failure(
                        IllegalStateException(
                            "Kinescope API key is not configured. " +
                                "Call KinescopeOfflineDownload.initialize(apiKey: ...).",
                        ),
                    ),
                )
            }
            return
        }

        scope.launch {
            try {
                val apiHelper = KinescopeApiConfig.createApiHelper(apiKey)
                val response = apiHelper.getAllVideos().first()
                val videos = response.data.map { video ->
                    mapOf(
                        "id" to video.id,
                        "title" to video.title,
                        "duration" to video.duration,
                    )
                }
                withContext(Dispatchers.Main) {
                    callback(Result.success(videos))
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(error))
                }
            }
        }
    }
}
