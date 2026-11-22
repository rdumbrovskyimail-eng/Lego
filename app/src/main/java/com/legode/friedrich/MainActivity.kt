package com.legode.friedrich

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.core.Timex
import com.legode.friedrich.notify.Notifier
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.RootTab
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.chat.ChatScreen
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.SpeakingBar
import com.legode.friedrich.ui.decisions.DecisionsScreen
import com.legode.friedrich.ui.dossier.DossierScreen
import com.legode.friedrich.ui.onboarding.OnboardingScreen
import com.legode.friedrich.ui.reference.ReferenceScreen
import com.legode.friedrich.ui.sessions.SessionsScreen
import com.legode.friedrich.ui.settings.SettingsScreen
import com.legode.friedrich.ui.theme.AtlasAvatar
import com.legode.friedrich.ui.theme.AtlasTheme
import com.legode.friedrich.ui.theme.Fx
import com.legode.friedrich.ui.trainer.TrainerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val main: MainViewModel by viewModels()
    private var unlocked by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        highRefreshRate()

        if (savedInstanceState != null) {
            unlocked = savedInstanceState.getBoolean(KEY_UNLOCKED, false)
        } else {
            handleIntent(intent)
        }
        askNotificationsIfNeeded()

        setContent {
            val settings by main.settingsFlow.collectAsStateWithLifecycle()
            val speaking by main.speaking.collectAsStateWithLifecycle()

            LaunchedEffect(settings.screenshotBlock) {
                if (settings.screenshotBlock) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(settings.biometricLock, settings.onboarded) {
                if (settings.onboarded && settings.biometricLock && !unlocked) {
                    promptUnlock()
                }
            }

            AtlasTheme(fontScale = settings.fontScale) {
                Box(Modifier.fillMaxSize().background(Fx.Ink)) {
                    when {
                        !settings.onboarded -> OnboardingScreen(main)
                        settings.biometricLock && !unlocked -> LockOverlay(onUnlock = { promptUnlock() })
                        else -> {
                            BackHandler(enabled = main.stack.size > 1 || main.rootTab != RootTab.CHATS) {
                                main.back()
                            }
                            val screen = main.stack.lastOrNull() ?: Screen.Sessions
                            Column(Modifier.fillMaxSize()) {
                                if (screen !is Screen.Chat) {
                                    SpeakingBar(speaking = speaking, onStop = { main.stopSpeaking() })
                                }
                                Box(Modifier.weight(1f)) {
                                    Crossfade(
                                        targetState = screen,
                                        animationSpec = tween(150),
                                        label = "screen"
                                    ) { s ->
                                        when (s) {
                                            is Screen.Sessions -> RootHub(main)
                                            is Screen.Chat -> ChatScreen(s.sessionId, main, onBack = { main.back() })
                                            is Screen.Dossier -> DossierScreen(main, onBack = { main.back() })
                                            is Screen.Reference -> ReferenceScreen(main, s.topicId, onBack = { main.back() })
                                            is Screen.Settings -> SettingsScreen(main, onBack = { main.back() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        main.onForeground()
    }

    private fun promptUnlock() {
        val bm = BiometricManager.from(this)
        val canAuth = if (Build.VERSION.SDK_INT >= 30) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate()
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked = true
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход в Атлас")
            .setSubtitle("Личное досье и диалоги защищены")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        try {
            prompt.authenticate(builder.build())
        } catch (_: Throwable) {
            unlocked = true
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Notifier.ACTION_OPEN_CHAT -> main.openFromNotification(intent.getLongExtra(Notifier.EXTRA_SESSION, 0L))
            Intent.ACTION_VIEW -> if (main.settings.value.onboarded) main.openHomeChat()
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                main.acceptShare(text, listOfNotNull(uri))
            }
        }
    }

    private fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || !main.settings.value.onboarded) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            try {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
            }
        }
    }

    private fun highRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            val d = display ?: return
            val current = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val params = window.attributes
            params.preferredDisplayModeId = best.modeId
            window.attributes = params
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val KEY_UNLOCKED = "atlas_unlocked"
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Fx.Ink).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AtlasAvatar(76.dp)
        Spacer(Modifier.height(16.dp))
        Text("Атлас заблокирован", color = Fx.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Подтверди личность, чтобы открыть переписки и досье", color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Chip("Разблокировать 🔓", onClick = onUnlock, accent = true)
    }
}

@Composable
private fun RootHub(main: MainViewModel) {
    val sessions by main.sessions.collectAsStateWithLifecycle()
    val cards by main.knowledgeCards.collectAsStateWithLifecycle()
    val anchors by main.anchors.collectAsStateWithLifecycle()
    val predictions by main.predictions.collectAsStateWithLifecycle()

    val now = Timex.now()
    val unreadChats = remember(sessions) { sessions.sumOf { it.unread } }
    val dueTrainer = remember(cards, anchors, now) {
        cards.count { it.dueAt <= now } + anchors.count { it.dueAt <= now }
    }
    val dueDecisions = remember(predictions, now) {
        predictions.count { !it.resolved && it.checkAt <= now }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (main.rootTab) {
                RootTab.CHATS -> SessionsScreen(main)
                RootTab.TRAINER -> TrainerScreen(main)
                RootTab.DECISIONS -> DecisionsScreen(main)
                RootTab.LIBRARY -> ReferenceScreen(main, initialTopic = null, onBack = null)
                RootTab.DOSSIER -> DossierScreen(main, onBack = null)
            }
        }
        NavigationBar(
            containerColor = Fx.Panel,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
            for (tab in RootTab.entries) {
                val selected = main.rootTab == tab
                val icon = when (tab) {
                    RootTab.CHATS -> Icons.Filled.Forum
                    RootTab.TRAINER -> Icons.Filled.Psychology
                    RootTab.DECISIONS -> Icons.Filled.Explore
                    RootTab.LIBRARY -> Icons.Filled.MenuBook
                    RootTab.DOSSIER -> Icons.Filled.Insights
                }
                val badgeCount = when (tab) {
                    RootTab.CHATS -> unreadChats
                    RootTab.TRAINER -> dueTrainer
                    RootTab.DECISIONS -> dueDecisions
                    else -> 0
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { main.selectTab(tab) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (badgeCount > 0) {
                                    Badge(containerColor = Fx.Gold, contentColor = Fx.OnGold) {
                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(icon, contentDescription = tab.label)
                        }
                    },
                    label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Fx.Gold,
                        selectedTextColor = Fx.Gold,
                        indicatorColor = Fx.GoldDim,
                        unselectedIconColor = Fx.TextFaint,
                        unselectedTextColor = Fx.TextFaint
                    )
                )
            }
        }
    }
}