package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String? = null,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val categoryName: String, // "WHATSAPP", "CALLS", "EMAIL", "MESSAGES", "SOCIAL", "SYSTEM"
    val profileName: String = "PERSONAL", // "PERSONAL", "WORK"
    val customToneUri: String? = null,
    val customToneName: String? = null,
    val isRead: Boolean = false,
    val isStarred: Boolean = false
)

@Entity(tableName = "notification_settings")
data class NotificationSettingEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isSilent: Boolean = false,
    val isVibrate: Boolean = true,
    val priority: Int = 1, // 0: Low, 1: Normal, 2: High
    val profileName: String = "PERSONAL",
    val customToneUri: String? = null,
    val customToneName: String = "System Default"
)

@Entity(tableName = "sender_tone_rules")
data class SenderToneRuleEntity(
    @PrimaryKey val ruleKey: String, // "$packageName|$senderTitle"
    val packageName: String,
    val senderTitle: String,
    val toneUri: String? = null,
    val toneName: String = "System Default"
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val contactName: String? = null,
    val callType: String, // "INCOMING", "OUTGOING", "MISSED", "REJECTED"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val isRead: Boolean = false
)

@Entity(tableName = "pinned_apps")
data class PinnedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isFavorite: Boolean = false,
    val categoryName: String = "TOOLS"
)
