package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.NotificationEntity
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Service extending [NotificationListenerService] to intercept incoming
 * notifications from WhatsApp, Gmail, Messages, and other installed apps.
 */
class NotificationHubService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)
        val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        try {
            val packageName = sbn.packageName ?: return
            // Ignore self notifications to prevent loops
            if (packageName == applicationContext.packageName) return

            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            if (title.isBlank() && text.isBlank()) return

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            val category = determineCategory(packageName, sbn.notification)

            // Persist to local Room database
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val settingsDao = db.notificationSettingDao()
                val notificationDao = db.notificationDao()

                // Check or create setting for this app
                var setting = settingsDao.getSettingForApp(packageName)
                if (setting == null) {
                    setting = com.example.data.local.NotificationSettingEntity(
                        packageName = packageName,
                        appName = appName,
                        profileName = "PERSONAL"
                    )
                    settingsDao.updateSetting(setting)
                }

                val entity = NotificationEntity(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    subText = subText,
                    timestamp = sbn.postTime,
                    categoryName = category.name,
                    profileName = setting.profileName,
                    isRead = false,
                    isStarred = false
                )
                notificationDao.insertNotification(entity)

                // Emit to live stream after DB insertion to ensure consistency
                val event = NotificationEvent(
                    id = sbn.key ?: "${packageName}_${sbn.postTime}",
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    subText = subText,
                    timestamp = sbn.postTime,
                    category = category,
                    notificationKey = sbn.key ?: ""
                )
                _notificationEvents.tryEmit(event)
            }
        } catch (e: Exception) {
            Log.e("NotificationHubService", "Error intercepting notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        Log.d("NotificationHubService", "Notification removed: ${sbn.packageName}")
    }

    private fun determineCategory(packageName: String, notification: Notification): NotificationCategory {
        val lowerPkg = packageName.lowercase()
        return when {
            lowerPkg.contains("whatsapp") -> NotificationCategory.WHATSAPP
            lowerPkg.contains("dialer") || lowerPkg.contains("telecom") || lowerPkg.contains("phone") || notification.category == Notification.CATEGORY_CALL -> NotificationCategory.CALLS
            lowerPkg.contains("gmail") || lowerPkg.contains("email") || lowerPkg.contains("outlook") || notification.category == Notification.CATEGORY_EMAIL -> NotificationCategory.EMAIL
            lowerPkg.contains("messaging") || lowerPkg.contains("sms") || notification.category == Notification.CATEGORY_MESSAGE -> NotificationCategory.MESSAGES
            lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("twitter") || lowerPkg.contains("telegram") || lowerPkg.contains("snapchat") || lowerPkg.contains("linkedin") -> NotificationCategory.SOCIAL
            else -> NotificationCategory.SYSTEM
        }
    }
}
