package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.local.PinnedAppDao
import com.example.data.local.PinnedAppEntity
import com.example.data.model.AppCategory
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AppLauncherRepository(
    private val context: Context,
    private val dao: PinnedAppDao
) {

    val pinnedApps: Flow<List<PinnedAppEntity>> = dao.getAllPinnedApps()

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val favorites = try {
            dao.getAllPinnedApps().first().associate { it.packageName to it.isFavorite }
        } catch (e: Exception) {
            emptyMap()
        }

        val appList = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            val category = categorizeApp(packageName, resolveInfo.activityInfo.applicationInfo)
            val isFav = favorites[packageName] ?: (packageName == "com.whatsapp" || packageName.contains("gmail") || packageName.contains("dialer"))

            appList.add(
                InstalledAppInfo(
                    packageName = packageName,
                    appName = appName,
                    isFavorite = isFav,
                    category = category
                )
            )
        }

        appList.sortedWith(compareByDescending<InstalledAppInfo> { it.isFavorite }.thenBy { it.appName.lowercase() })
    }

    suspend fun toggleFavorite(packageName: String, isFav: Boolean, appName: String, category: AppCategory) {
        dao.insertPinnedApp(
            PinnedAppEntity(
                packageName = packageName,
                appName = appName,
                isFavorite = isFav,
                categoryName = category.name
            )
        )
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun categorizeApp(packageName: String, appInfo: ApplicationInfo): AppCategory {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("whatsapp") || pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter") || pkg.contains("telegram") || pkg.contains("snapchat") || pkg.contains("discord") -> AppCategory.SOCIAL
            pkg.contains("gmail") || pkg.contains("email") || pkg.contains("outlook") || pkg.contains("docs") || pkg.contains("sheets") || pkg.contains("slack") || pkg.contains("teams") -> AppCategory.WORK
            pkg.contains("youtube") || pkg.contains("spotify") || pkg.contains("netflix") || pkg.contains("music") || pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photos") -> AppCategory.MEDIA
            else -> AppCategory.TOOLS
        }
    }
}
