package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.NotificationEntity
import com.example.data.local.NotificationSettingEntity
import com.example.data.local.ThemePreferences
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationEvent
import com.example.util.NotificationTonePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Service extending [NotificationListenerService] to intercept incoming
 * notifications from WhatsApp, Gmail, Messages, and other installed apps,
 * persist them to Room for the OmniHub feed, and support inline RemoteInput replies.
 */
class NotificationHubService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class ReplyCapability(
        val actionIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private const val TAG = "NotificationHubService"

        private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)
        val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val replyActions = ConcurrentHashMap<String, ReplyCapability>()

        private fun fallbackKey(packageName: String, title: String): String = "${packageName}|${title.trim()}"

        fun canReply(notificationKey: String?, packageName: String? = null, title: String? = null): Boolean {
            if (!notificationKey.isNullOrBlank() && replyActions.containsKey(notificationKey)) {
                return true
            }
            if (!packageName.isNullOrBlank() && !title.isNullOrBlank()) {
                return replyActions.containsKey(fallbackKey(packageName, title))
            }
            return false
        }

        fun sendInlineReply(
            context: Context,
            notificationKey: String?,
            replyText: String,
            packageName: String? = null,
            title: String? = null
        ): Boolean {
            if (replyText.isBlank()) return false
            val capability = (!notificationKey.isNullOrBlank()).let { hasKey ->
                if (hasKey) replyActions[notificationKey] else null
            } ?: if (!packageName.isNullOrBlank() && !title.isNullOrBlank()) {
                replyActions[fallbackKey(packageName, title)]
            } else {
                null
            } ?: return false

            return try {
                val fillInIntent = Intent()
                val bundle = Bundle()
                for (remoteInput in capability.remoteInputs) {
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(capability.remoteInputs, fillInIntent, bundle)
                capability.actionIntent.send(context, 0, fillInIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send inline reply for key=$notificationKey", e)
                false
            }
        }

        fun isPermissionGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, NotificationHubService::class.java)
            return flat.contains(componentName.flattenToString()) || flat.contains(context.packageName)
        }

        fun requestRebindIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPermissionGranted(context) && !_isConnected.value) {
                try {
                    requestRebind(ComponentName(context, NotificationHubService::class.java))
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to request NotificationListenerService rebind", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        Log.d(TAG, "NotificationListenerService connected")

        // Sync existing active notifications immediately so the OmniHub feed isn't empty on first grant
        try {
            val active = activeNotifications ?: return
            active.forEach { sbn ->
                processStatusBarNotification(sbn, playSound = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing active notifications on connect", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
        Log.d(TAG, "NotificationListenerService disconnected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, NotificationHubService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request rebind on disconnect", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        processStatusBarNotification(sbn, playSound = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        val key = sbn.key
        if (!key.isNullOrBlank()) {
            replyActions.remove(key)
        }
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification, playSound: Boolean) {
        try {
            val packageName = sbn.packageName ?: return
            // Ignore self notifications to prevent feedback loops
            if (packageName == applicationContext.packageName) return

            val notification = sbn.notification ?: return
            val flags = notification.flags

            // Ignore group summary shells and non-communication ongoing background services
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            val notifCategory = notification.category
            if (isOngoing && notifCategory != Notification.CATEGORY_CALL) return
            if (notifCategory == Notification.CATEGORY_PROGRESS ||
                notifCategory == Notification.CATEGORY_TRANSPORT ||
                notifCategory == Notification.CATEGORY_SERVICE
            ) {
                return
            }

            val extras = notification.extras ?: Bundle.EMPTY
            val title = extractTitle(extras)
            val text = extractRichText(extras)
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            if (title.isBlank() && text.isBlank()) return

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            val category = determineCategory(packageName, notification)
            val notifKey = sbn.key ?: "${packageName}_${sbn.id}_${sbn.postTime}"

            // Cache inline RemoteInput reply capability if exposed by the source app
            val fbKey = if (title.isNotBlank()) fallbackKey(packageName, title) else null
            extractReplyCapability(notification)?.let { capability ->
                replyActions[notifKey] = capability
                if (fbKey != null) replyActions[fbKey] = capability
            } ?: run {
                replyActions.remove(notifKey)
                if (fbKey != null) replyActions.remove(fbKey)
            }

            val isWorkUser = sbn.user != Process.myUserHandle()

            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val settingsDao = db.notificationSettingDao()
                val notificationDao = db.notificationDao()
                val senderToneRuleDao = db.senderToneRuleDao()
                val pinnedThreadDao = db.pinnedThreadDao()

                // Check or create per-app setting
                var setting = settingsDao.getSettingForApp(packageName)
                if (setting == null) {
                    setting = NotificationSettingEntity(
                        packageName = packageName,
                        appName = appName,
                        profileName = if (isWorkUser) "WORK" else "PERSONAL"
                    )
                    settingsDao.updateSetting(setting)
                }

                val resolvedTitle = title.ifBlank { appName }
                val senderRule = senderToneRuleDao.getRule("${packageName}|${resolvedTitle.trim()}")
                val resolvedToneUri = senderRule?.toneUri ?: setting.customToneUri
                val resolvedToneName = senderRule?.toneName ?: setting.customToneName
                val isThreadPinned = pinnedThreadDao.isThreadPinned("${packageName}|${resolvedTitle.trim().lowercase()}")

                val resolvedProfile = if (isWorkUser) "WORK" else setting.profileName

                // Deduplicate / update existing notification with the same system key
                val existing = notificationDao.getNotificationByKey(notifKey)
                val isNewOrUpdatedTimestamp = existing == null || existing.timestamp != sbn.postTime
                if (existing != null) {
                    if (existing.title == resolvedTitle && existing.text == text && existing.timestamp == sbn.postTime) {
                        return@launch
                    }
                    val updated = existing.copy(
                        appName = appName,
                        title = resolvedTitle,
                        text = text,
                        subText = subText,
                        timestamp = sbn.postTime,
                        categoryName = category.name,
                        profileName = resolvedProfile,
                        customToneUri = resolvedToneUri,
                        customToneName = resolvedToneName,
                        isRead = setting.isSilent,
                        isPinned = existing.isPinned || isThreadPinned
                    )
                    notificationDao.updateNotification(updated)
                } else {
                    val entity = NotificationEntity(
                        notificationKey = notifKey,
                        packageName = packageName,
                        appName = appName,
                        title = resolvedTitle,
                        text = text,
                        subText = subText,
                        timestamp = sbn.postTime,
                        categoryName = category.name,
                        profileName = resolvedProfile,
                        customToneUri = resolvedToneUri,
                        customToneName = resolvedToneName,
                        isRead = setting.isSilent,
                        isStarred = false,
                        isPinned = isThreadPinned
                    )
                    notificationDao.insertNotification(entity)
                }

                // Play configured custom tone for new/updated notifications if app isn't muted
                val focusModeActive = ThemePreferences(applicationContext).isFocusModeEnabled.first()
                val urgentKeywords = listOf(
                    "otp", "verification", "code", "urgent", "critical",
                    "immediate", "alert", "security", "warning", "emergency"
                )
                val isUrgent = urgentKeywords.any {
                    "$resolvedTitle $text $appName".lowercase().contains(it)
                }

                if (playSound &&
                    isNewOrUpdatedTimestamp &&
                    !setting.isSilent &&
                    (!focusModeActive || isUrgent) &&
                    !resolvedToneUri.isNullOrBlank() &&
                    resolvedToneUri != NotificationTonePlayer.URI_SILENT
                ) {
                    NotificationTonePlayer.playTone(applicationContext, resolvedToneUri)
                }

                val event = NotificationEvent(
                    id = notifKey,
                    packageName = packageName,
                    appName = appName,
                    title = title.ifBlank { appName },
                    text = text,
                    subText = subText,
                    timestamp = sbn.postTime,
                    category = category,
                    notificationKey = notifKey
                )
                _notificationEvents.tryEmit(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error intercepting notification", e)
        }
    }

    private fun extractTitle(extras: Bundle): String {
        return extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
    }

    private fun extractRichText(extras: Bundle): String {
        // 1. MessagingStyle (WhatsApp, Messages, Telegram)
        val rawMessages: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        if (!rawMessages.isNullOrEmpty()) {
            val formatted = rawMessages.takeLast(3).mapNotNull { parcelable ->
                val msgBundle = parcelable as? Bundle ?: return@mapNotNull null
                val sender = msgBundle.getCharSequence("sender")?.toString()
                val body = msgBundle.getCharSequence("text")?.toString()
                when {
                    !sender.isNullOrBlank() && !body.isNullOrBlank() -> "$sender: $body"
                    !body.isNullOrBlank() -> body
                    else -> null
                }
            }
            if (formatted.isNotEmpty()) {
                return formatted.joinToString("\n")
            }
        }

        // 2. BigTextStyle
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText

        // 3. Standard text
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) return text

        // 4. InboxStyle (Gmail multi-message digest)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) {
            return lines.take(4).joinToString("\n") { it.toString() }
        }

        return ""
    }

    private fun extractReplyCapability(notification: Notification): ReplyCapability? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            val pendingIntent = action.actionIntent
            if (!remoteInputs.isNullOrEmpty() && pendingIntent != null) {
                return ReplyCapability(pendingIntent, remoteInputs)
            }
        }
        return null
    }

    private fun determineCategory(packageName: String, notification: Notification): NotificationCategory {
        val lowerPkg = packageName.lowercase()
        return when {
            lowerPkg.contains("whatsapp") -> NotificationCategory.WHATSAPP
            lowerPkg.contains("dialer") || lowerPkg.contains("telecom") || lowerPkg.contains("phone") || notification.category == Notification.CATEGORY_CALL -> NotificationCategory.CALLS
            lowerPkg.contains("gmail") || lowerPkg.contains("email") || lowerPkg.contains("outlook") || lowerPkg.contains("yahoo.mobile.client.android.mail") || notification.category == Notification.CATEGORY_EMAIL -> NotificationCategory.EMAIL
            lowerPkg.contains("messaging") || lowerPkg.contains("sms") || lowerPkg.contains("mms") || notification.category == Notification.CATEGORY_MESSAGE -> NotificationCategory.MESSAGES
            lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("twitter") || lowerPkg.contains("telegram") || lowerPkg.contains("snapchat") || lowerPkg.contains("linkedin") || lowerPkg.contains("discord") || notification.category == Notification.CATEGORY_SOCIAL -> NotificationCategory.SOCIAL
            else -> NotificationCategory.SYSTEM
        }
    }
}
