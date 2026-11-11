package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AppLauncherScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.InboxInsightsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OmniViewModel
import com.example.ui.viewmodel.OmniViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.configureAppCheck(this, intent)
        enableEdgeToEdge()

        val app = application as OmniApplication

        setContent {
                    val viewModel: OmniViewModel = viewModel(
                        factory = OmniViewModelFactory(
                            app.notificationRepository,
                            app.callLogRepository,
                            app.appLauncherRepository,
                            app.themePreferences
                        )
                    )

                    val uiState by viewModel.uiState.collectAsState()
                    val darkTheme = when (uiState.themeMode) {
                        com.example.data.local.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                        com.example.data.local.ThemeMode.LIGHT -> false
                        com.example.data.local.ThemeMode.DARK -> true
                    }

                    MyApplicationTheme(darkTheme = darkTheme) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            OmniAppContent(viewModel = viewModel)
                        }
                    }
        }
    }
}

@Composable
fun OmniAppContent(viewModel: OmniViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val timelineItems by viewModel.timelineItems.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Permission launcher for Call log & Contacts
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.checkPermissions(context)
    }

    // Role launcher for Default Dialer
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.checkPermissions(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    // Automatically re-check permission status when user returns from settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle Back Button to return to Timeline (Tab 0) if on secondary screens
    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    val unreadCount = timelineItems.count { !it.isRead }
    val missedCallCount = timelineItems.count {
        it is com.example.data.model.UnifiedTimelineItem.CallLogItem &&
                it.callType == com.example.data.model.CallType.MISSED
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        if (unreadCount > 0) {
                            BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                Icon(Icons.Default.Dashboard, contentDescription = "Feed")
                            }
                        } else {
                            Icon(Icons.Default.Dashboard, contentDescription = "Feed")
                        }
                    },
                    label = { Text("Feed") },
                    modifier = Modifier.testTag("tab_feed")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        if (missedCallCount > 0) {
                            BadgedBox(badge = { Badge { Text("$missedCallCount") } }) {
                                Icon(Icons.Default.Call, contentDescription = "Calls")
                            }
                        } else {
                            Icon(Icons.Default.Call, contentDescription = "Calls")
                        }
                    },
                    label = { Text("Calls") },
                    modifier = Modifier.testTag("tab_calls")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                    label = { Text("Apps Hub") },
                    modifier = Modifier.testTag("tab_apps")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                    label = { Text("Insights") },
                    modifier = Modifier.testTag("tab_insights")
                )

                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TimelineScreen(
                    uiState = uiState,
                    timelineItems = timelineItems,
                    onCategorySelected = { viewModel.setSelectedCategory(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onMarkAllRead = { viewModel.markAllAsRead() },
                    onMarkRead = { viewModel.markAsRead(it) },
                    onToggleStar = { viewModel.toggleStarred(it) },
                    onDelete = { viewModel.deleteItem(it) },
                    onOpenApp = { pkg ->
                        val launched = viewModel.launchApp(pkg)
                        if (!launched) {
                            Toast.makeText(context, "Opening app: $pkg", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCallClick = { num -> viewModel.makePhoneCall(context, num) },
                    onWhatsAppClick = { num -> viewModel.launchWhatsAppChat(context, num) },
                    onSendQuickReply = { item, msg ->
                        Toast.makeText(context, "Reply sent to ${item.title}: \"$msg\"", Toast.LENGTH_SHORT).show()
                        viewModel.markAsRead(item)
                    },
                    onOpenPermissionSettings = { selectedTab = 4 },
                    onResetDemoData = { viewModel.resetDemoData() },
                    onQuickReplyClick = { item ->
                        viewModel.generateAiReplies(item)
                    },
                    onQuickReplyDismiss = {
                        viewModel.clearAiSuggestions()
                    },
                    onTruecallerClick = { num ->
                        viewModel.searchOnTruecaller(context, num)
                    },
                    onProfileSelected = { prof ->
                        viewModel.setSelectedProfile(prof)
                    }
                )

                1 -> CallsScreen(
                    timelineItems = timelineItems,
                    onCallClick = { num -> viewModel.makePhoneCall(context, num) },
                    onWhatsAppClick = { num -> viewModel.launchWhatsAppChat(context, num) },
                    onDelete = { viewModel.deleteItem(it) },
                    onMarkRead = { viewModel.markAsRead(it) },
                    onTruecallerClick = { num -> viewModel.searchOnTruecaller(context, num) }
                )

                2 -> AppLauncherScreen(
                    uiState = uiState,
                    onAppCategorySelected = { viewModel.setAppCategory(it) },
                    onAppSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                    onLaunchApp = { pkg ->
                        val launched = viewModel.launchApp(pkg)
                        if (!launched) {
                            Toast.makeText(context, "Launching $pkg", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleFavorite = { viewModel.toggleFavoriteApp(it) }
                )

                3 -> InboxInsightsScreen(
                    timelineItems = timelineItems
                )

                4 -> SettingsScreen(
                    uiState = uiState,
                    onRequestCallPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.CALL_PHONE
                            )
                        )
                    },
                    onResetDemoData = {
                        viewModel.resetDemoData()
                        Toast.makeText(context, "Sample feed reloaded!", Toast.LENGTH_SHORT).show()
                    },
                    onCheckPermissions = {
                        viewModel.checkPermissions(context)
                        Toast.makeText(context, "Permission status checked", Toast.LENGTH_SHORT).show()
                    },
                    onThemeModeSelected = {
                        viewModel.setThemeMode(it)
                    },
                    onRequestDefaultDialer = {
                        viewModel.requestDefaultDialerRole(context as android.app.Activity, roleLauncher)
                    },
                    onUpdateSetting = { setting ->
                        viewModel.updateNotificationSetting(setting)
                    }
                )
            }
        }
    }
}
