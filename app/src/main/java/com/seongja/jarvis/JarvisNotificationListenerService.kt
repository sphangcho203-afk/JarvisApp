package com.seongja.jarvis

import android.service.notification.NotificationListenerService

/**
 * Permission endpoint for future on-device notification context.
 *
 * This service intentionally does not persist notification text. It only
 * exposes whether Android has connected the listener so the permission center
 * can report a truthful status.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var connected: Boolean = false
            private set
    }
}
