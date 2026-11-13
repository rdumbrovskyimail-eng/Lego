package com.legode.friedrich

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.notify.Notifier
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.chat.ChatScreen
import com.legode.friedrich.ui.dossier.DossierScreen
import com.legode.friedrich.ui.onboarding.OnboardingScreen
import com.legode.friedrich.ui.reference.ReferenceScreen
import com.legode.friedrich.ui.sessions.SessionsScreen
import com.legode.friedrich.ui.settings.SettingsScreen
import com.legode.friedrich.ui.theme.FriedrichTheme
import com.legode.friedrich.ui.theme.Fx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val main: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        highRefreshRate()

        if (savedInstanceState == null) handleIntent(intent)
        askNotificationsIfNeeded()

        setContent {
            val settings by main.settingsFlow.collectAsStateWithLifecycle()
            FriedrichTheme(fontScale = settings.fontScale) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Fx.Ink)
                ) {
                    if (!settings.onboarded) {
                        OnboardingScreen(main)
                    } else {
                        BackHandler(enabled = main.stack.size > 1) { main.back() }
                        val screen = main.stack.lastOrNull() ?: Screen.Sessions
                        Crossfade(
                            targetState = screen,
                            animationSpec = tween(160),
                            label = "screen"
                        ) { s ->
                            when (s) {
                                is Screen.Sessions -> SessionsScreen(main)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        main.onForeground()
    }

    /** Уведомление → нужный чат; «Поделиться» → текст и файлы в чат с Фридрихом. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Notifier.ACTION_OPEN_CHAT -> {
                main.openFromNotification(intent.getLongExtra(Notifier.EXTRA_SESSION, 0L))
            }

            Intent.ACTION_VIEW -> if (main.settings.value.onboarded) main.openHomeChat()

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                main.acceptShare(text, listOfNotNull(uri))
            }
        }
    }

    private fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (!main.settings.value.onboarded) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            try {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
            }
        }
    }

    /** Максимальная частота обновления экрана — плавная прокрутка чата. */
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
}
