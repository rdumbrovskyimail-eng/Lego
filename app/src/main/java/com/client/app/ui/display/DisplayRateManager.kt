package com.client.app.ui.display

import android.view.Window

object DisplayRateManager {
    fun setHighRefreshRate(window: Window, enable: Boolean) {
        runCatching {
            val layoutParams = window.attributes
            layoutParams.preferredRefreshRate = if (enable) 120.0f else 0.0f
            layoutParams.preferredDisplayModeId = 0
            window.attributes = layoutParams
        }
    }
}