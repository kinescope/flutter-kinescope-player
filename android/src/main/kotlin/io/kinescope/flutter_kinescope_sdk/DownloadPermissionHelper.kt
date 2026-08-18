package io.kinescope.flutter_kinescope_sdk

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object DownloadPermissionHelper {
    const val REQUEST_CODE = 7341

    fun hasPermissions(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun needsRequest(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermissions(activity)
    }

    fun requestPermissions(activity: Activity) {
        if (!needsRequest(activity)) {
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE,
        )
    }

    fun isOurRequest(requestCode: Int): Boolean = requestCode == REQUEST_CODE
}
