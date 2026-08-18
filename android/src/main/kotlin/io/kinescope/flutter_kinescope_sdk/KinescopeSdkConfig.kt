package io.kinescope.flutter_kinescope_sdk

/**
 * Host app configuration for the Kinescope Flutter plugin.
 *
 * Set [apiKey] once at startup (same as [io.kinescope.demo.KinescopeDemoConfig.API_KEY] in the native demo).
 */
object KinescopeSdkConfig {
  @Volatile
  var apiKey: String? = null
    private set

  fun setApiKey(value: String?) {
    apiKey = value?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun resolveApiKey(override: String? = null): String? {
    return override?.trim()?.takeIf { it.isNotEmpty() } ?: apiKey
  }
}
