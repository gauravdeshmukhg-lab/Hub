package com.example.data.repository

import com.example.data.local.NotificationDao
import com.example.data.local.NotificationEntity
import com.example.data.model.NotificationCategory
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val dao: NotificationDao,
    private val settingsDao: com.example.data.local.NotificationSettingDao
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

    suspend fun getSettingForApp(packageName: String) = settingsDao.getSettingForApp(packageName)

    suspend fun updateSetting(setting: com.example.data.local.NotificationSettingEntity) = settingsDao.updateSetting(setting)

    suspend fun deleteSetting(packageName: String) = settingsDao.deleteSetting(packageName)

    suspend fun insert(notification: NotificationEntity) = dao.insertNotification(notification)

    suspend fun insertAll(notifications: List<NotificationEntity>) = dao.insertAll(notifications)

    suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    suspend fun markAllAsRead() = dao.markAllAsRead()

    suspend fun toggleStarred(id: Long) = dao.toggleStarred(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
