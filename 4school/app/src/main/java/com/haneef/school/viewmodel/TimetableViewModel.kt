package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.CreateSchoolTimetableRequest
import com.haneef.school.data.models.SchoolTimetable
import com.haneef.school.data.models.UpdateSchoolTimetableRequest
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimetableUiState(
    val isLoading: Boolean = false,
    val timetableItems: List<SchoolTimetable> = emptyList(),
    val selectedDay: String = java.time.LocalDate.now().dayOfWeek.name, // Defaults dynamically to today (MONDAY..SUNDAY)
    val error: String? = null,
    val successMessage: String? = null
)

class TimetableViewModel(
    private val repository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setSelectedDay(day: String) {
        _uiState.value = _uiState.value.copy(selectedDay = day.trim().uppercase())
    }

    fun loadData(accessToken: String, schoolId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getSchoolTimetableItems(accessToken, schoolId)
            result.fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        timetableItems = items
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        timetableItems = emptyList(),
                        error = error.message ?: "Failed to load timetable items"
                    )
                }
            )
        }
    }

    fun createTimetableItem(
        accessToken: String,
        schoolId: String,
        classId: String?,
        dayOfWeek: String?,
        daysOfWeek: List<String>?,
        activityType: String,
        startTime: String,
        endTime: String,
        title: String,
        description: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = CreateSchoolTimetableRequest(
                schoolId = schoolId,
                classId = classId,
                dayOfWeek = dayOfWeek,
                daysOfWeek = daysOfWeek,
                activityType = activityType,
                startTime = startTime,
                endTime = endTime,
                title = title,
                description = description
            )
            val result = repository.createSchoolTimetableItem(accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Timetable activity created successfully"
                    )
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create activity"
                    )
                }
            )
        }
    }

    fun updateTimetableItem(
        itemId: String,
        accessToken: String,
        schoolId: String,
        classId: String?,
        dayOfWeek: String,
        activityType: String,
        startTime: String,
        endTime: String,
        title: String,
        description: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = UpdateSchoolTimetableRequest(
                schoolId = schoolId,
                classId = classId,
                dayOfWeek = dayOfWeek,
                activityType = activityType,
                startTime = startTime,
                endTime = endTime,
                title = title,
                description = description
            )
            val result = repository.updateSchoolTimetableItem(itemId, accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Timetable activity updated successfully"
                    )
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update activity"
                    )
                }
            )
        }
    }

    fun deleteTimetableItem(
        itemId: String,
        accessToken: String,
        schoolId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.deleteSchoolTimetableItem(itemId, accessToken, schoolId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Timetable activity deleted successfully"
                    )
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to delete activity"
                    )
                }
            )
        }
    }


}
