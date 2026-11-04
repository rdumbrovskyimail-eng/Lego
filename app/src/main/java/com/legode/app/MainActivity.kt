package com.legode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.app.ui.AppTab
import com.legode.app.ui.MainViewModel
import com.legode.app.ui.cbt.CbtScreen
import com.legode.app.ui.components.CalendarStrip
import com.legode.app.ui.display.DisplayRateManager
import com.legode.app.ui.sport.SportScreen
import com.legode.app.ui.synergy.SynergyScreen
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

private val ObsidianPalette = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    background = Color(0xFF090D16),
    surface = Color(0xFF111827),
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        DisplayRateManager.setHighRefreshRate(window, true)

        setContent {
            MaterialTheme(colorScheme = ObsidianPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
                    val cbtEntries by viewModel.currentCbtEntries.collectAsStateWithLifecycle()
                    val workouts by viewModel.currentWorkouts.collectAsStateWithLifecycle()
                    val allCbt by viewModel.allCbtEntries.collectAsStateWithLifecycle()
                    val allWorkouts by viewModel.allWorkouts.collectAsStateWithLifecycle()
                    val cbtDates by viewModel.cbtActiveDates.collectAsStateWithLifecycle()
                    val sportDates by viewModel.sportActiveDates.collectAsStateWithLifecycle()

                    Scaffold(
                        topBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF090D16))
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "PsySport Pro",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    TextButton(onClick = { viewModel.setSelectedDate(LocalDate.now()) }) {
                                        Text("Сегодня", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                    }
                                }

                                CalendarStrip(
                                    selectedDateStr = selectedDate,
                                    cbtDates = cbtDates,
                                    sportDates = sportDates,
                                    onDateSelected = { date -> viewModel.setSelectedDate(date) }
                                )
                            }
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF0F172A),
                                modifier = Modifier.navigationBarsPadding()
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == AppTab.CBT,
                                    onClick = { viewModel.setTab(AppTab.CBT) },
                                    icon = { Icon(Icons.Default.Psychology, contentDescription = "КПТ") },
                                    label = { Text("КПТ-Разум", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFA855F7),
                                        selectedTextColor = Color(0xFFA855F7),
                                        indicatorColor = Color(0xFF581C87).copy(alpha = 0.3f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == AppTab.SPORT,
                                    onClick = { viewModel.setTab(AppTab.SPORT) },
                                    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Спорт") },
                                    label = { Text("Спорт-Тело", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF10B981),
                                        selectedTextColor = Color(0xFF10B981),
                                        indicatorColor = Color(0xFF065F46).copy(alpha = 0.3f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == AppTab.SYNERGY,
                                    onClick = { viewModel.setTab(AppTab.SYNERGY) },
                                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Аналитика") },
                                    label = { Text("Синергия", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF38BDF8),
                                        selectedTextColor = Color(0xFF38BDF8),
                                        indicatorColor = Color(0xFF0369A1).copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                AppTab.CBT -> CbtScreen(
                                    entries = cbtEntries,
                                    selectedDate = selectedDate,
                                    onAddEntry = { viewModel.addCbtEntry(it) },
                                    onDeleteEntry = { viewModel.deleteCbtEntry(it) }
                                )
                                AppTab.SPORT -> SportScreen(
                                    workouts = workouts,
                                    selectedDate = selectedDate,
                                    onAddWorkout = { viewModel.addWorkoutEntry(it) },
                                    onDeleteWorkout = { viewModel.deleteWorkoutEntry(it) }
                                )
                                AppTab.SYNERGY -> SynergyScreen(
                                    cbtEntries = allCbt,
                                    workouts = allWorkouts
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DisplayRateManager.setHighRefreshRate(window, true)
    }

    override fun onPause() {
        super.onPause()
        DisplayRateManager.setHighRefreshRate(window, false)
    }
}