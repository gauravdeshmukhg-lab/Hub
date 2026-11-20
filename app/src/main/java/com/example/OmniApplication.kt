package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AppLauncherRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.NotificationRepository

class OmniApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val notificationRepository by lazy {
        NotificationRepository(
            database.notificationDao(),
            database.notificationSettingDao(),
            database.senderToneRuleDao()
        )
    }
    val callLogRepository by lazy { CallLogRepository(this, database.callLogDao()) }
    val appLauncherRepository by lazy { AppLauncherRepository(this, database.pinnedAppDao()) }
    val themePreferences by lazy { com.example.data.local.ThemePreferences(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
