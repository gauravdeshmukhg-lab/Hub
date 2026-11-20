package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE categoryName = :category ORDER BY timestamp DESC")
    fun getNotificationsByCategory(category: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE notificationKey = :key LIMIT 1")
    suspend fun getNotificationByKey(key: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Update
    suspend fun updateNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1, subText = :replySummary WHERE id = :id")
    suspend fun markAsReplied(id: Long, replySummary: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE notifications SET isStarred = NOT isStarred WHERE id = :id")
    suspend fun toggleStarred(id: Long)

    @Query("UPDATE notifications SET customToneUri = :toneUri, customToneName = :toneName WHERE id = :id")
    suspend fun updateNotificationTone(id: Long, toneUri: String?, toneName: String?)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

@Dao
interface NotificationSettingDao {
    @Query("SELECT * FROM notification_settings ORDER BY appName ASC")
    fun getAllSettings(): Flow<List<NotificationSettingEntity>>

    @Query("SELECT COUNT(*) FROM notification_settings")
    suspend fun getSettingCount(): Int

    @Query("SELECT * FROM notification_settings WHERE packageName = :packageName")
    suspend fun getSettingForApp(packageName: String): NotificationSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSetting(setting: NotificationSettingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllSettings(settings: List<NotificationSettingEntity>)

    @Query("DELETE FROM notification_settings WHERE packageName = :packageName")
    suspend fun deleteSetting(packageName: String)
}

@Dao
interface SenderToneRuleDao {
    @Query("SELECT * FROM sender_tone_rules WHERE ruleKey = :ruleKey LIMIT 1")
    suspend fun getRule(ruleKey: String): SenderToneRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: SenderToneRuleEntity)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = :type ORDER BY timestamp DESC")
    fun getCallLogsByType(type: String): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCallLogs(callLogs: List<CallLogEntity>)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}

@Dao
interface PinnedAppDao {
    @Query("SELECT * FROM pinned_apps")
    fun getAllPinnedApps(): Flow<List<PinnedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedApp(pinnedApp: PinnedAppEntity)

    @Query("UPDATE pinned_apps SET isFavorite = :isFav WHERE packageName = :packageName")
    suspend fun setFavorite(packageName: String, isFav: Boolean)
}
