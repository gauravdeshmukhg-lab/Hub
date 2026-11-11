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
import com.example.data.model.AppCategory
import com.example.data.model.CallType
import com.example.data.model.InstalledAppInfo
import com.example.data.model.NotificationCategory
import com.example.data.model.ProfileType
import com.example.data.model.UnifiedTimelineItem
import com.example.data.repository.AppLauncherRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val aiError: String? = null
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
        notificationRepo.allSettings
    ) { state, theme, settings ->
        state.copy(themeMode = theme, notificationSettings = settings)
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

    private val _installedAppsState = MutableStateFlow<List<InstalledAppInfo>>(emptyList())

    val timelineItems: StateFlow<List<UnifiedTimelineItem>> = combine(
        notificationRepo.allNotifications,
        callLogRepo.allCallLogs,
        uiState
    ) { notifications, callLogs, state ->
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

            UnifiedTimelineItem.NotificationItem(
                id = "NOTIF_${entity.id}",
                dbId = entity.id,
                packageName = entity.packageName,
                appName = entity.appName,
                title = entity.title,
                text = entity.text,
                subText = entity.subText,
                timestamp = entity.timestamp,
                category = cat,
                group = group,
                profileType = profile,
                isRead = entity.isRead,
                isStarred = entity.isStarred,
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

        val combined = (notifItems + callItems).sortedByDescending { it.timestamp }

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

        // Filter by category
        val categoryFiltered = when (state.selectedCategory) {
            NotificationCategory.ALL -> profileFiltered
            NotificationCategory.WHATSAPP -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.WHATSAPP
            }
            NotificationCategory.CALLS -> profileFiltered.filter {
                it is UnifiedTimelineItem.CallLogItem || (it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.CALLS)
            }
            NotificationCategory.EMAIL -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.EMAIL
            }
            NotificationCategory.MESSAGES -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.category == NotificationCategory.MESSAGES
            }
            NotificationCategory.SOCIAL -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && (it.category == NotificationCategory.SOCIAL || it.group == NotificationCategory.SOCIAL)
            }
            NotificationCategory.URGENT -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.group == NotificationCategory.URGENT
            }
            NotificationCategory.PROMOTIONS -> profileFiltered.filter {
                it is UnifiedTimelineItem.NotificationItem && it.group == NotificationCategory.PROMOTIONS
            }
            NotificationCategory.SYSTEM -> profileFiltered.filter {
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

    init {
        loadInstalledApps()
        seedDemoDataIfEmpty()
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

    fun checkPermissions(context: Context) {
        val listenerGranted = isNotificationListenerPermissionGranted(context)
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

        if (callLogGranted) {
            viewModelScope.launch {
                callLogRepo.syncSystemCallLogs()
            }
        }
    }

    private fun isNotificationListenerPermissionGranted(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    fun seedDemoDataIfEmpty() {
        viewModelScope.launch {
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
