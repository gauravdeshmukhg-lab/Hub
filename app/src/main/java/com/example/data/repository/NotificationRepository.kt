package com.example.data.repository

import com.example.data.local.NotificationDao
import com.example.data.local.NotificationEntity
import com.example.data.model.NotificationCategory
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val dao: NotificationDao,
    private val settingsDao: com.example.data.local.NotificationSettingDao,
    private val senderToneRuleDao: com.example.data.local.SenderToneRuleDao
) {

    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()
    val allSettings: Flow<List<com.example.data.local.NotificationSettingEntity>> = settingsDao.getAllSettings()

    fun getNotificationsByCategory(category: NotificationCategory): Flow<List<NotificationEntity>> {
        return if (category == NotificationCategory.ALL) {
            dao.getAllNotifications()
        } else {
            dao.getNotificationsByCategory(category.name)
        }
    }

    suspend fun getSettingCount(): Int = settingsDao.getSettingCount()

    suspend fun getSettingForApp(packageName: String) = settingsDao.getSettingForApp(packageName)

    suspend fun updateSetting(setting: com.example.data.local.NotificationSettingEntity) = settingsDao.updateSetting(setting)

    suspend fun insertAllSettings(settings: List<com.example.data.local.NotificationSettingEntity>) = settingsDao.insertAllSettings(settings)

    suspend fun deleteSetting(packageName: String) = settingsDao.deleteSetting(packageName)

    suspend fun updateNotificationTone(id: Long, toneUri: String?, toneName: String?) =
        dao.updateNotificationTone(id, toneUri, toneName)

    suspend fun upsertSenderToneRule(packageName: String, senderTitle: String, toneUri: String?, toneName: String) {
        val key = "${packageName}|${senderTitle.trim()}"
        senderToneRuleDao.upsertRule(
            com.example.data.local.SenderToneRuleEntity(
                ruleKey = key,
                packageName = packageName,
                senderTitle = senderTitle.trim(),
                toneUri = toneUri,
                toneName = toneName
            )
        )
    }

    suspend fun getNotificationCount(): Int = dao.getNotificationCount()

    suspend fun insert(notification: NotificationEntity) = dao.insertNotification(notification)

    suspend fun insertAll(notifications: List<NotificationEntity>) = dao.insertAll(notifications)

    suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    suspend fun markAsReplied(id: Long, replySummary: String) = dao.markAsReplied(id, replySummary)

    suspend fun markAllAsRead() = dao.markAllAsRead()

    suspend fun toggleStarred(id: Long) = dao.toggleStarred(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
