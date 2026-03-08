package com.remotecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service that performs touch gestures and key presses
 * received from the remote viewer via WebRTC DataChannel / Socket.IO.
 */
class RemoteControlAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteControlAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for remote control
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ─── Touch Actions ────────────────────────────────────────────────────────

    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ─── System Actions ───────────────────────────────────────────────────────

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun pressNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // ─── Handle incoming control commands ────────────────────────────────────

    fun handleControlCommand(action: String, x: Float = 0f, y: Float = 0f,
                              x2: Float = 0f, y2: Float = 0f) {
        when (action) {
            "TAP"        -> performTap(x, y)
            "SWIPE"      -> performSwipe(x, y, x2, y2)
            "LONG_PRESS" -> performLongPress(x, y)
            "BACK"       -> pressBack()
            "HOME"       -> pressHome()
            "RECENTS"    -> pressRecents()
            "NOTIF"      -> pressNotifications()
        }
    }
}
