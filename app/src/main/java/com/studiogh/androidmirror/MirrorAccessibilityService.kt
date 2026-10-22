package com.studiogh.androidmirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class MirrorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun performGlobalActionFor(key: String) {
        when (key) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    companion object {
        @Volatile private var instance: MirrorAccessibilityService? = null
        fun performTap(x: Float, y: Float) {
            val service = instance ?: return
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
                .build()
            service.dispatchGesture(gesture, null, null)
        }
        fun performGlobalActionFor(key: String) {
            instance?.performGlobalActionFor(key)
        }
    }
}
