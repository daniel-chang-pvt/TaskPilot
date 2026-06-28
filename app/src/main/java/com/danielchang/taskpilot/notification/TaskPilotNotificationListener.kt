package com.danielchang.taskpilot.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.danielchang.taskpilot.engine.RuleEngine
import com.danielchang.taskpilot.model.TriggerType

class TaskPilotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val allText = "$packageName $title $text"
        RuleEngine.executeMatching(this, TriggerType.NOTIFICATION_RECEIVED, "notification") {
            it.trigger.text.isBlank() || packageName.contains(it.trigger.text, ignoreCase = true)
        }
        RuleEngine.executeMatching(this, TriggerType.NOTIFICATION_TEXT, "notification_text") {
            it.trigger.text.isNotBlank() && allText.contains(it.trigger.text, ignoreCase = true)
        }
    }
}
