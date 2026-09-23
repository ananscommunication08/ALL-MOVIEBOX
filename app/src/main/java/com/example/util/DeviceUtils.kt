package com.example.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {
    /**
     * Determines whether the current device is an Android TV / Leanback device.
     * Evaluates UI mode and system features (FEATURE_LEANBACK, type.television).
     */
    fun isAndroidTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               pm.hasSystemFeature("android.hardware.type.television")
    }
}
