package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CallLogEntity
import com.example.data.local.NotificationEntity
import android.text.format.DateUtils
import com.example.data.model.AppCategory
import com.example.data.model.CallType
import com.example.data.model.DailyDigestReport
import com.example.data.model.DailyDigestState
import com.example.data.model.FeedTypeFilter
import com.example.data.model.InstalledAppInfo
import com.example.data.model.NotificationCategory
import com.example.data.model.ProfileType
import com.example.data.model.UnifiedTimelineItem
import com.example.data.repository.AppLauncherRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.NotificationRepository
import com.example.service.NotificationHubService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import android.util.Log

data class OmniUiState(
    val selectedCategory: NotificationCategory = NotificationCategory.ALL,
    val visibleFeedTypes: Set<FeedTypeFilter> = FeedTypeFilter.entries.toSet(),
    val isFocusModeEnabled: Boolean = false,
    val selectedProfile: com.example.data.model.ProfileType = com.example.data.model.ProfileType.ALL,
    val searchQuery: String = "",
    val callLogFilter: CallType? = null,
    val selectedAppCategory: AppCategory = AppCategory.ALL,
    val appSearchQuery: String = "",
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val notificationSettings: List<com.example.data.local.NotificationSettingEntity> = emptyList(),
    val isNotificationListenerGranted: Boolean = false,
    val isCallLogGranted: Boolean = false,
    val isContactsGranted: Boolean = false,
    val isSampleDataActive: Boolean = true,
    val isRefreshing: Boolean = false,
    val themeMode: com.example.data.local.ThemeMode = com.example.data.local.ThemeMode.SYSTEM,
    val aiSuggestions: List<String> = emptyList(),
    val isAiLoading: Boolean = false,
    val aiError: String? = null,
    val dailyDigestState: DailyDigestState = DailyDigestState.Idle
)

class OmniViewModel(
    private val notificationRepo: NotificationRepository,
    private val callLogRepo: CallLogRepository,
    private val appLauncherRepo: AppLauncherRepository,
    private val themePrefs: com.example.data.local.ThemePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OmniUiState())
    val uiState: StateFlow<OmniUiState> = combine(
        _uiState,
        themePrefs.themeMode,
        themePrefs.visibleFeedTypes,
        themePrefs.isFocusModeEnabled,
        notificationRepo.allSettings
    ) { state, theme, visibleTypes, focusMode, settings ->
        state.copy(
            themeMode = theme,
            visibleFeedTypes = visibleTypes,
            isFocusModeEnabled = focusMode,
            notificationSettings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _uiState.value
    )

    fun setThemeMode(mode: com.example.data.local.ThemeMode) {
        viewModelScope.launch {
            themePrefs.setThemeMode(mode)
        }
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePrefs.setFocusModeEnabled(enabled)
        }
    }

    private val replySchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.string(description = "A short, context-aware reply suggestion"),
                description = "List of 3-5 quick reply suggestions"
            )
        )
    )

    private val aiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = replySchema
            }
        )
    }

    fun generateAiReplies(item: UnifiedTimelineItem.NotificationItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, aiSuggestions = emptyList(), aiError = null) }
            try {
                val prompt = """
                    Based on this notification from ${item.appName}, suggest 3-5 short quick reply options.
                    Keep them conversational and relevant to the content.
                    
                    Sender: ${item.title}
                    Message: ${item.text}
                    ${if (item.subText != null) "Subtext: ${item.subText}" else ""}
                """.trimIndent()

                val response = withTimeout(30_000L) {
                    aiModel.generateContent(prompt)
                }

                val jsonStr = response.text ?: throw IllegalStateException("Empty AI response")
                val json = org.json.JSONObject(jsonStr)
                val suggestionsArray = json.getJSONArray("suggestions")
                val suggestions = (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }

                _uiState.update { it.copy(aiSuggestions = suggestions, isAiLoading = false) }
            } catch (e: TimeoutCancellationException) {
                _uiState.update { it.copy(isAiLoading = false, aiError = "AI Request timed out") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OmniViewModel", "AI Generation failed", e)
                _uiState.update { it.copy(isAiLoading = false, aiError = "Failed to get suggestions") }
            }
        }
    }

    fun clearAiSuggestions() {
        _uiState.update { it.copy(aiSuggestions = emptyList(), aiError = null) }
    }

    private val digestSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "A concise 1-line morning headline summarizing the past 24 hours"),
            "morningBriefing" to Schema.string(description = "A clear 2-3 sentence morning briefing summarizing the main themes and activity"),
            "topNotifications" to Schema.array(
                items = Schema.string(description = "A concise bullet summarizing an important notification, message, or missed call"),
                description = "Up to 5 top notifications or calls from the past 24 hours"
            ),
            "priorityEmails" to Schema.array(
                items = Schema.string(description = "A concise summary of a priority email with sender and key point"),
                description = "Up to 4 priority emails from the past 24 hours"
            ),
            "actionItems" to Schema.array(
                items = Schema.string(description = "A short suggested follow-up action for the user"),
                description = "Up to 3 suggested next steps based on unread or urgent updates"
            )
        )
    )

    private val digestModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = digestSchema
            }
        )
    }

    fun generateDailyDigest() {
        viewModelScope.launch {
            _uiState.update { it.copy(dailyDigestState = DailyDigestState.Loading) }
            try {
                val now = System.currentTimeMillis()
                val cutoff = now - (24L * 60L * 60L * 1000L)

                val recentNotifications = notificationRepo.allNotifications.first()
                    .filter { it.timestamp >= cutoff }
                    .sortedByDescending { it.timestamp }
                    .take(40)

                val recentCalls = callLogRepo.allCallLogs.first()
                    .filter { it.timestamp >= cutoff }
                    .sortedByDescending { it.timestamp }
                    .take(15)

                val totalCount = recentNotifications.size + recentCalls.size
                if (totalCount == 0) {
                    _uiState.update {
                        it.copy(
                            dailyDigestState = DailyDigestState.Error(
                                "No notifications, emails, or calls found in the past 24 hours."
                            )
                        )
                    }
                    return@launch
                }

                val emailLines = recentNotifications
                    .filter { it.categoryName == NotificationCategory.EMAIL.name }
                    .joinToString("\n") { entity ->
                        val age = DateUtils.getRelativeTimeSpanString(entity.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
                        val groupTag = classifyGroup(entity.title, entity.text, entity.appName).name
                        val status = buildList {
                            if (!entity.isRead) add("UNREAD")
                            if (entity.isStarred) add("STARRED")
                            add(entity.profileName)
                            add(groupTag)
                        }.joinToString(", ")
                        "- [$status | $age] ${entity.appName} | From: ${entity.title} | ${entity.text}"
                    }
                    .ifBlank { "None" }

                val otherNotifLines = recentNotifications
                    .filter { it.categoryName != NotificationCategory.EMAIL.name }
                    .joinToString("\n") { entity ->
                        val age = DateUtils.getRelativeTimeSpanString(entity.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
                        val groupTag = classifyGroup(entity.title, entity.text, entity.appName).name
                        val status = buildList {
                            if (!entity.isRead) add("UNREAD")
                            if (entity.isStarred) add("STARRED")
                            add(entity.profileName)
                            add(groupTag)
                        }.joinToString(", ")
                        "- [$status | $age] ${entity.appName} | ${entity.title}: ${entity.text}"
                    }
                    .ifBlank { "None" }

                val callLines = recentCalls
                    .joinToString("\n") { call ->
                        val age = DateUtils.getRelativeTimeSpanString(call.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
                        val caller = call.contactName ?: call.phoneNumber
                        "- [${call.callType} | $age] $caller (${call.phoneNumber})"
                    }
                    .ifBlank { "None" }

                val prompt = """
                    You are creating a concise Daily Digest morning briefing for OmniHub covering the past 24 hours.
                    Analyze the real user activity below and highlight the most important notifications, urgent alerts, missed calls, and priority emails.
                    Do not invent any notifications or emails not listed below.

                    PRIORITY EMAILS (past 24h):
                    $emailLines

                    NOTIFICATIONS & MESSAGES (past 24h):
                    $otherNotifLines

                    RECENT CALLS (past 24h):
                    $callLines
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    digestModel.generateContent(prompt)
                }

                val jsonStr = response.text ?: throw IllegalStateException("Empty AI response")
                val json = org.json.JSONObject(jsonStr)

                fun extractStringList(key: String): List<String> {
                    val arr = json.optJSONArray(key) ?: return emptyList()
                    return (0 until arr.length())
                        .mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
                }

                val report = DailyDigestReport(
                    headline = json.optString("headline").ifBlank { "Your 24-Hour Morning Briefing" },
                    morningBriefing = json.optString("morningBriefing").ifBlank {
                        "Analyzed $totalCount recent updates across your notifications, emails, and calls."
                    },
                    topNotifications = extractStringList("topNotifications"),
                    priorityEmails = extractStringList("priorityEmails"),
                    actionItems = extractStringList("actionItems"),
                    itemCountAnalyzed = totalCount,
                    generatedAt = System.currentTimeMillis()
                )

                _uiState.update { it.copy(dailyDigestState = DailyDigestState.Success(report)) }
            } catch (e: TimeoutCancellationException) {
                Log.e("OmniViewModel", "Daily Digest generation timed out", e)
                _uiState.update {
                    it.copy(dailyDigestState = DailyDigestState.Error("Daily Digest request timed out. Please try again."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OmniViewModel", "Daily Digest generation failed", e)
                _uiState.update {
                    it.copy(
                        dailyDigestState = DailyDigestState.Error(
                            e.localizedMessage ?: "Failed to generate Daily Digest."
                        )
                    )
                }
            }
        }
    }

    private val _installedAppsState = MutableStateFlow<List<InstalledAppInfo>>(emptyList())

    val timelineItems: StateFlow<List<UnifiedTimelineItem>> = combine(
        notificationRepo.allNotifications,
        callLogRepo.allCallLogs,
        notificationRepo.allPinnedThreads,
        uiState
    ) { notifications, callLogs, pinnedThreads, state ->
        val pinnedKeys = pinnedThreads.map { it.threadKey }.toSet()

        val notifItems = notifications.map { entity ->
            val cat = try {
                NotificationCategory.valueOf(entity.categoryName)
            } catch (e: Exception) {
                NotificationCategory.SYSTEM
            }

            val group = classifyGroup(entity.title, entity.text, entity.appName)
            val profile = try {
                ProfileType.valueOf(entity.profileName)
            } catch (e: Exception) {
                ProfileType.PERSONAL
            }

            val appSetting = state.notificationSettings.firstOrNull { it.packageName == entity.packageName }
            val resolvedToneUri = entity.customToneUri ?: appSetting?.customToneUri
            val resolvedToneName = entity.customToneName ?: appSetting?.customToneName
            val threadKey = "${entity.packageName}|${entity.title.trim().lowercase()}"
            val pinned = entity.isPinned || pinnedKeys.contains(threadKey)

            UnifiedTimelineItem.NotificationItem(
                id = "NOTIF_${entity.id}",
                dbId = entity.id,
                notificationKey = entity.notificationKey,
                canReply = NotificationHubService.canReply(entity.notificationKey, entity.packageName, entity.title),
                packageName = entity.packageName,
                appName = entity.appName,
                title = entity.title,
                text = entity.text,
                subText = entity.subText,
                timestamp = entity.timestamp,
                category = cat,
                group = group,
                profileType = profile,
                customToneUri = resolvedToneUri,
                customToneName = resolvedToneName,
                isRead = entity.isRead,
                isStarred = entity.isStarred,
                isPinned = pinned,
                rawPostTime = entity.timestamp
            )
        }

        val callItems = callLogs.map { entity ->
            val type = try {
                CallType.valueOf(entity.callType)
            } catch (e: Exception) {
                CallType.INCOMING
            }

            UnifiedTimelineItem.CallLogItem(
                id = "CALL_${entity.id}",
                dbId = entity.id,
                contactName = entity.contactName,
                phoneNumber = entity.phoneNumber,
                callType = type,
                timestamp = entity.timestamp,
                durationSeconds = entity.durationSeconds,
                isRead = entity.isRead
            )
        }

        val combined = (notifItems + callItems).sortedWith(
            compareByDescending<UnifiedTimelineItem> {
                (it as? UnifiedTimelineItem.NotificationItem)?.isPinned == true
            }.thenByDescending { it.timestamp }
        )

        // Filter by Profile (Personal vs Work)
        val profileFiltered = when (state.selectedProfile) {
            ProfileType.ALL -> combined
            ProfileType.PERSONAL -> combined.filter {
                it is UnifiedTimelineItem.CallLogItem || (it is UnifiedTimelineItem.NotificationItem && it.profileType == ProfileType.PERSONAL)
            }
            ProfileType.WORK -> combined.filter {
                it is UnifiedTimelineItem.NotificationItem && it.profileType == ProfileType.WORK
            }
        }

        // Filter by Feed Type visibility toggles (Calls, Messages, Emails, System)
        val visibilityFiltered = profileFiltered.filter { item ->
            val feedType = when (item) {
                is UnifiedTimelineItem.CallLogItem -> FeedTypeFilter.CALLS
                is UnifiedTimelineItem.NotificationItem -> when (item.category) {
                    NotificationCategory.CALLS -> FeedTypeFilter.CALLS
                    NotificationCategory.WHATSAPP,
                    NotificationCategory.MESSAGES,
                    NotificationCategory.SOCIAL -> FeedTypeFilter.MESSAGES
                    NotificationCategory.EMAIL -> FeedTypeFilter.EMAILS
                    NotificationCategory.SYSTEM,
                    NotificationCategory.URGENT,
                    NotificationCategory.PROMOTIONS,
                    NotificationCategory.ALL -> FeedTypeFilter.SYSTEM
                }
            }
            feedType in state.visibleFeedTypes
        }

        // Focus Mode: suppress all non-urgent notifications based on keyword categorization
        val focusFiltered = if (state.isFocusModeEnabled) {
            visibilityFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.group == NotificationCategory.URGENT
            }
        } else {
            visibilityFiltered
        }

        // Filter by category
        val categoryFiltered = when (state.selectedCategory) {
            NotificationCategory.ALL -> focusFiltered
            NotificationCategory.WHATSAPP -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.WHATSAPP
            }
            NotificationCategory.CALLS -> focusFiltered.filter {
                it is UnifiedTimelineItem.CallLogItem || (it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.CALLS)
            }
            NotificationCategory.EMAIL -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.EMAIL
            }
            NotificationCategory.MESSAGES -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && (
                    it.category == NotificationCategory.MESSAGES ||
                        it.category == NotificationCategory.WHATSAPP ||
                        it.category == NotificationCategory.SOCIAL
                    )
            }
            NotificationCategory.SOCIAL -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && (it.category == NotificationCategory.SOCIAL || it.group == NotificationCategory.SOCIAL)
            }
            NotificationCategory.URGENT -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.group == NotificationCategory.URGENT
            }
            NotificationCategory.PROMOTIONS -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.group == NotificationCategory.PROMOTIONS
            }
            NotificationCategory.SYSTEM -> focusFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.SYSTEM
            }
        }

        // Filter by search query
        if (state.searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val q = state.searchQuery.lowercase()
            categoryFiltered.filter { item ->
                when (item) {
                    is UnifiedTimelineItem.NotificationItem ->
                        item.title.lowercase().contains(q) || item.text.lowercase().contains(q) || item.appName.lowercase().contains(q)
                    is UnifiedTimelineItem.CallLogItem ->
                        (item.contactName?.lowercase()?.contains(q) == true) || item.phoneNumber.contains(q)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun classifyGroup(title: String, text: String, appName: String): NotificationCategory {
        val content = (title + " " + text + " " + appName).lowercase()

        val urgentKeywords = listOf("otp", "verification", "code", "urgent", "critical", "immediate", "alert", "security", "warning", "emergency")
        val promotionKeywords = listOf("offer", "discount", "sale", "save", "deal", "coupon", "cashback", "buy", "off", "promo", "free", "marketing")
        val socialKeywords = listOf("friend", "follow", "comment", "like", "message", "chat", "whatsapp", "instagram", "facebook", "telegram", "snapchat")

        return when {
            urgentKeywords.any { content.contains(it) } -> NotificationCategory.URGENT
            promotionKeywords.any { content.contains(it) } -> NotificationCategory.PROMOTIONS
            socialKeywords.any { content.contains(it) } -> NotificationCategory.SOCIAL
            else -> NotificationCategory.SYSTEM
        }
    }

    fun setSelectedProfile(profile: com.example.data.model.ProfileType) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    fun updateNotificationSetting(setting: com.example.data.local.NotificationSettingEntity) {
        viewModelScope.launch {
            notificationRepo.updateSetting(setting)
        }
    }

    fun setToneForNotification(
        item: UnifiedTimelineItem.NotificationItem,
        toneUri: String?,
        toneName: String
    ) {
        viewModelScope.launch {
            notificationRepo.updateNotificationTone(item.dbId, toneUri, toneName)
            notificationRepo.upsertSenderToneRule(item.packageName, item.title, toneUri, toneName)
        }
    }

    fun setToneForApp(
        setting: com.example.data.local.NotificationSettingEntity,
        toneUri: String?,
        toneName: String
    ) {
        viewModelScope.launch {
            val isSilentTone = toneUri == com.example.util.NotificationTonePlayer.URI_SILENT
            notificationRepo.updateSetting(
                setting.copy(
                    customToneUri = toneUri,
                    customToneName = toneName,
                    isSilent = isSilentTone
                )
            )
        }
    }

    init {
        loadInstalledApps()
        seedDemoDataIfEmpty()
        seedDefaultAppSettingsIfEmpty()
    }

    private fun seedDefaultAppSettingsIfEmpty() {
        viewModelScope.launch {
            if (notificationRepo.getSettingCount() > 0) return@launch
            val defaults = listOf(
                com.example.data.local.NotificationSettingEntity(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    profileName = "PERSONAL",
                    customToneUri = com.example.util.NotificationTonePlayer.URI_CHIME,
                    customToneName = "Crystal Chime"
                ),
                com.example.data.local.NotificationSettingEntity(
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    profileName = "WORK",
                    customToneUri = com.example.util.NotificationTonePlayer.URI_WORK,
                    customToneName = "Work Chime"
                ),
                com.example.data.local.NotificationSettingEntity(
                    packageName = "com.google.android.apps.messaging",
                    appName = "Messages",
                    profileName = "PERSONAL",
                    customToneUri = com.example.util.NotificationTonePlayer.URI_PING,
                    customToneName = "Quick Ping"
                ),
                com.example.data.local.NotificationSettingEntity(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    profileName = "PERSONAL",
                    customToneUri = null,
                    customToneName = "System Default"
                ),
                com.example.data.local.NotificationSettingEntity(
                    packageName = "com.microsoft.teams",
                    appName = "Teams",
                    profileName = "WORK",
                    customToneUri = com.example.util.NotificationTonePlayer.URI_BELL,
                    customToneName = "Classic Bell"
                )
            )
            notificationRepo.insertAllSettings(defaults)
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = appLauncherRepo.getInstalledApps()
            _installedAppsState.value = apps
            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    fun setSelectedCategory(category: NotificationCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleFeedType(type: FeedTypeFilter) {
        viewModelScope.launch {
            val current = uiState.value.visibleFeedTypes
            val updated = if (type in current) current - type else current + type
            themePrefs.setVisibleFeedTypes(updated)
        }
    }

    fun resetFeedTypeFilters() {
        viewModelScope.launch {
            themePrefs.setVisibleFeedTypes(FeedTypeFilter.entries.toSet())
            _uiState.update { it.copy(selectedCategory = NotificationCategory.ALL) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setAppCategory(category: AppCategory) {
        _uiState.update { it.copy(selectedAppCategory = category) }
    }

    fun setAppSearchQuery(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    fun setCallFilter(type: CallType?) {
        _uiState.update { it.copy(callLogFilter = type) }
    }

    fun markAsRead(item: UnifiedTimelineItem) {
        viewModelScope.launch {
            when (item) {
                is UnifiedTimelineItem.NotificationItem -> notificationRepo.markAsRead(item.dbId)
                is UnifiedTimelineItem.CallLogItem -> { /* Call logs marked */ }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepo.markAllAsRead()
        }
    }

    fun toggleStarred(item: UnifiedTimelineItem.NotificationItem) {
        viewModelScope.launch {
            notificationRepo.toggleStarred(item.dbId)
        }
    }

    fun togglePinned(item: UnifiedTimelineItem.NotificationItem) {
        viewModelScope.launch {
            notificationRepo.togglePinThread(item.packageName, item.title, item.isPinned)
        }
    }

    fun deleteItem(item: UnifiedTimelineItem) {
        viewModelScope.launch {
            when (item) {
                is UnifiedTimelineItem.NotificationItem -> notificationRepo.deleteById(item.dbId)
                is UnifiedTimelineItem.CallLogItem -> callLogRepo.deleteById(item.dbId)
            }
        }
    }

    fun toggleFavoriteApp(app: InstalledAppInfo) {
        viewModelScope.launch {
            val newFav = !app.isFavorite
            appLauncherRepo.toggleFavorite(app.packageName, newFav, app.appName, app.category)
            loadInstalledApps()
        }
    }

    fun launchApp(packageName: String): Boolean {
        return appLauncherRepo.launchApp(packageName)
    }

    fun launchWhatsAppChat(context: Context, phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.replace("[^0-9]".toRegex(), "")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun makePhoneCall(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun searchOnTruecaller(context: Context, phoneNumber: String) {
        try {
            // Standard Truecaller search deep link
            val uri = Uri.parse("truecaller://search?q=$phoneNumber")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Open Truecaller web search
            val webUri = Uri.parse("https://www.truecaller.com/search/in/$phoneNumber")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun requestDefaultDialerRole(activity: android.app.Activity, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                launcher.launch(intent)
            } else {
                Toast.makeText(activity, "OmniHub is already your default dialer", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            launcher.launch(intent)
        }
    }

    fun sendQuickReply(
        context: Context,
        item: UnifiedTimelineItem.NotificationItem,
        message: String
    ): Boolean {
        val sentLive = NotificationHubService.sendInlineReply(
            context = context,
            notificationKey = item.notificationKey,
            replyText = message,
            packageName = item.packageName,
            title = item.title
        )
        viewModelScope.launch {
            notificationRepo.markAsReplied(item.dbId, "You replied: $message")
        }
        return sentLive
    }

    fun checkPermissions(context: Context) {
        val listenerGranted = NotificationHubService.isPermissionGranted(context)
        val callLogGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val contactsGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        _uiState.update {
            it.copy(
                isNotificationListenerGranted = listenerGranted,
                isCallLogGranted = callLogGranted,
                isContactsGranted = contactsGranted
            )
        }

        if (listenerGranted) {
            NotificationHubService.requestRebindIfNeeded(context)
        }

        if (callLogGranted) {
            viewModelScope.launch {
                callLogRepo.syncSystemCallLogs()
            }
        }
    }

    fun seedDemoDataIfEmpty() {
        viewModelScope.launch {
            if (notificationRepo.getNotificationCount() > 0) return@launch
            val now = System.currentTimeMillis()
            val sampleNotifs = listOf(
                NotificationEntity(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Priya Sharma",
                    text = "Hey! Did you check the new project timeline?",
                    subText = "2 new messages",
                    timestamp = now - (3 * 60 * 1000), // 3 mins ago
                    categoryName = NotificationCategory.WHATSAPP.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Tech India Group",
                    text = "Rahul: Shared document link for app architecture overview.",
                    subText = "Group Chat",
                    timestamp = now - (18 * 60 * 1000), // 18 mins ago
                    categoryName = NotificationCategory.WHATSAPP.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "Google Cloud Platform",
                    text = "Monthly statement & project resource usage overview for November.",
                    timestamp = now - (45 * 60 * 1000), // 45 mins ago
                    categoryName = NotificationCategory.EMAIL.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.google.android.apps.messaging",
                    appName = "Messages",
                    title = "ICICI Bank OTP",
                    text = "Your OTP for login verification is 849201. Valid for 10 minutes.",
                    timestamp = now - (2 * 60 * 60 * 1000), // 2 hrs ago
                    categoryName = NotificationCategory.MESSAGES.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    title = "Ananya V.",
                    text = "Liked your recent story and commented: 'Awesome UI design!'",
                    timestamp = now - (3 * 60 * 60 * 1000), // 3 hrs ago
                    categoryName = NotificationCategory.SOCIAL.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Amit Patel",
                    text = "Can we schedule a quick call tomorrow at 11 AM?",
                    timestamp = now - (5 * 60 * 60 * 1000),
                    categoryName = NotificationCategory.WHATSAPP.name,
                    profileName = "PERSONAL"
                ),
                NotificationEntity(
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "HR Department",
                    text = "Upcoming holiday schedule and quarterly review details.",
                    timestamp = now - (30 * 60 * 1000), // 30 mins ago
                    categoryName = NotificationCategory.EMAIL.name,
                    profileName = "WORK"
                ),
                NotificationEntity(
                    packageName = "com.microsoft.teams",
                    appName = "Teams",
                    title = "Suresh (Manager)",
                    text = "Please join the standup meeting in Room 4B.",
                    timestamp = now - (10 * 60 * 1000), // 10 mins ago
                    categoryName = NotificationCategory.SYSTEM.name,
                    profileName = "WORK"
                )
            )

            val sampleCalls = listOf(
                CallLogEntity(
                    phoneNumber = "+91 98765 43210",
                    contactName = "Aman Gupta",
                    callType = CallType.MISSED.name,
                    timestamp = now - (12 * 60 * 1000), // 12 mins ago
                    durationSeconds = 0,
                    isRead = false
                ),
                CallLogEntity(
                    phoneNumber = "+91 98123 45678",
                    contactName = "Priya Sharma",
                    callType = CallType.INCOMING.name,
                    timestamp = now - (1 * 60 * 60 * 1000), // 1 hr ago
                    durationSeconds = 245,
                    isRead = true
                ),
                CallLogEntity(
                    phoneNumber = "+91 99000 11223",
                    contactName = "Mom",
                    callType = CallType.OUTGOING.name,
                    timestamp = now - (4 * 60 * 60 * 1000), // 4 hrs ago
                    durationSeconds = 512,
                    isRead = true
                ),
                CallLogEntity(
                    phoneNumber = "+91 98888 77766",
                    contactName = "Rohan Tech lead",
                    callType = CallType.MISSED.name,
                    timestamp = now - (6 * 60 * 60 * 1000),
                    durationSeconds = 0,
                    isRead = false
                )
            )

            notificationRepo.insertAll(sampleNotifs)
            callLogRepo.insertAll(sampleCalls)
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            notificationRepo.deleteAll()
            callLogRepo.deleteAll()
            seedDemoDataIfEmpty()
        }
    }
}

class OmniViewModelFactory(
    private val notificationRepo: NotificationRepository,
    private val callLogRepo: CallLogRepository,
    private val appLauncherRepo: AppLauncherRepository,
    private val themePrefs: com.example.data.local.ThemePreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OmniViewModel(notificationRepo, callLogRepo, appLauncherRepo, themePrefs) as T
    }
}
