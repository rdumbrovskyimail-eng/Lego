package com.legode.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legode.app.data.model.CbtEntry
import com.legode.app.data.model.WorkoutEntry
import com.legode.app.data.repository.PsySportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class AppTab { CBT, SPORT, SYNERGY }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PsySportRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormatter))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.CBT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    val currentCbtEntries: StateFlow<List<CbtEntry>> = _selectedDate.flatMapLatest { date ->
        repository.getCbtByDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWorkouts: StateFlow<List<WorkoutEntry>> = _selectedDate.flatMapLatest { date ->
        repository.getWorkoutsByDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCbtEntries: StateFlow<List<CbtEntry>> = repository.getAllCbt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkouts: StateFlow<List<WorkoutEntry>> = repository.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cbtActiveDates: StateFlow<List<String>> = repository.getCbtActiveDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportActiveDates: StateFlow<List<String>> = repository.getSportActiveDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date.format(dateFormatter)
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun addCbtEntry(entry: CbtEntry) {
        viewModelScope.launch {
            repository.saveCbt(entry)
        }
    }

    fun deleteCbtEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteCbt(id)
        }
    }

    fun addWorkoutEntry(entry: WorkoutEntry) {
        viewModelScope.launch {
            repository.saveWorkout(entry)
        }
    }

    fun deleteWorkoutEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkout(id)
        }
    }
}