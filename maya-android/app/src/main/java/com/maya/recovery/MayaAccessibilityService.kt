package com.maya.recovery

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MayaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally does not read or transmit screen contents in this recovery build.
    }

    override fun onInterrupt() = Unit
}
