package com.example.data.model

import androidx.compose.ui.graphics.vector.ImageVector

enum class NotificationCategory(val label: String) {
    ALL("All Feed"),
    WHATSAPP("WhatsApp"),
    CALLS("Calls"),
    EMAIL("Email"),
    MESSAGES("Messages"),
    SOCIAL("Social"),
    URGENT("Urgent"),
    PROMOTIONS("Promotions"),
    SYSTEM("System & Apps")
}

enum class ProfileType(val label: String) {
    ALL("All Profiles"),
    PERSONAL("Personal"),
    WORK("Work")
}

enum class FeedTypeFilter(val label: String) {
    CALLS("Calls"),
    MESSAGES("Messages"),
    EMAILS("Emails"),
    SYSTEM("System")
}

enum class CallType(val label: String) {
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MISSED("Missed"),
    REJECTED("Rejected")
}

enum class AppCategory(val label: String) {
    ALL("All Apps"),
    FAVORITES("Favorites"),
    SOCIAL("Social & Chat"),
    WORK("Email & Work"),
    TOOLS("Tools & Utilities"),
    MEDIA("Media & Games")
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isFavorite: Boolean = false,
    val category: AppCategory = AppCategory.TOOLS,
    val unreadCount: Int = 0,
    val lastNotificationTime: Long = 0L
)

sealed class UnifiedTimelineItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val title: String
    abstract val isRead: Boolean

    data class NotificationItem(
        override val id: String,
        val dbId: Long,
        val notificationKey: String? = null,
        val canReply: Boolean = false,
        val packageName: String,
        val appName: String,
        override val title: String,
        val text: String,
        val subText: String?,
        override val timestamp: Long,
        val category: NotificationCategory,
        val group: NotificationCategory,
        val profileType: ProfileType, // Added for Work/Personal separation
        val customToneUri: String? = null,
        val customToneName: String? = null,
        override val isRead: Boolean,
        val isStarred: Boolean,
        val isPinned: Boolean = false,
        val rawPostTime: Long
    ) : UnifiedTimelineItem()

    data class CallLogItem(
        override val id: String,
        val dbId: Long,
        val contactName: String?,
        val phoneNumber: String,
        val callType: CallType,
        override val timestamp: Long,
        val durationSeconds: Long,
        override val isRead: Boolean
    ) : UnifiedTimelineItem() {
        override val title: String
            get() = contactName ?: phoneNumber
    }
}

data class DailyDigestReport(
    val headline: String,
    val morningBriefing: String,
    val topNotifications: List<String>,
    val priorityEmails: List<String>,
    val actionItems: List<String>,
    val itemCountAnalyzed: Int,
    val generatedAt: Long = System.currentTimeMillis()
)

sealed interface DailyDigestState {
    data object Idle : DailyDigestState
    data object Loading : DailyDigestState
    data class Success(val report: DailyDigestReport) : DailyDigestState
    data class Error(val message: String) : DailyDigestState
}
