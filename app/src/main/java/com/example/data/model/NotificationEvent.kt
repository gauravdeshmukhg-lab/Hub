package com.example.data.model

/**
 * Data class representing an incoming notification event captured by [NotificationHubService].
 */
data class NotificationEvent(
    val id: String = "",
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val category: NotificationCategory = NotificationCategory.SYSTEM,
    val notificationKey: String = ""
)
