package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.*
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchoolCalendarUiState(
    val isLoading: Boolean = false,
    val events: List<SchoolCalendar> = emptyList(),
    val sessions: List<AcademicSession> = emptyList(),
    val termsMap: Map<String, List<Term>> = emptyMap(), // session_id -> list of terms
    val error: String? = null,
    val successMessage: String? = null
)

class SchoolCalendarViewModel(
    private val repository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchoolCalendarUiState())
    val uiState: StateFlow<SchoolCalendarUiState> = _uiState.asStateFlow()

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Fetches calendar events, academic sessions, and term details in parallel.
     */
    fun loadData(accessToken: String, schoolId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                coroutineScope {
                    val eventsDeferred = async { repository.getCalendarEvents(accessToken, schoolId) }
                    val sessionsDeferred = async { repository.getAcademicSessions(accessToken, schoolId) }
                    
                    val eventsResult = eventsDeferred.await()
                    val sessionsResult = sessionsDeferred.await()
                    
                    if (eventsResult.isFailure) {
                        _uiState.update { it.copy(isLoading = false, error = eventsResult.exceptionOrNull()?.message) }
                        return@coroutineScope
                    }
                    
                    if (sessionsResult.isFailure) {
                        _uiState.update { it.copy(isLoading = false, error = sessionsResult.exceptionOrNull()?.message) }
                        return@coroutineScope
                    }
                    
                    val eventList = eventsResult.getOrThrow()
                    val sessionList = sessionsResult.getOrThrow()
                    
                    val termsMap = mutableMapOf<String, List<Term>>()
                    // Fetch terms for each session concurrently
                    val termRequests = sessionList.map { session ->
                        async { session.id to repository.getTermsInSession(session.id, accessToken, schoolId) }
                    }
                    
                    termRequests.awaitAll().forEach { (sessionId, result) ->
                        result.onSuccess { terms ->
                            termsMap[sessionId] = terms
                        }
                    }
                    
                    _uiState.update { it.copy(
                        isLoading = false,
                        events = eventList,
                        sessions = sessionList,
                        termsMap = termsMap,
                        error = null
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "An unexpected error occurred") }
            }
        }
    }

    fun createCalendarEvent(
        accessToken: String,
        schoolId: String,
        sessionId: String,
        termId: String?,
        eventName: String,
        eventType: String,
        startDate: String,
        endDate: String?,
        color: String?,
        description: String?,
        isExamPeriod: Boolean,
        isHoliday: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val request = CreateCalendarEventRequest(
                schoolId = schoolId,
                sessionId = sessionId,
                termId = termId,
                eventName = eventName,
                eventType = eventType,
                startDate = startDate,
                endDate = endDate,
                color = color,
                description = description,
                isExamPeriod = isExamPeriod,
                isHoliday = isHoliday
            )
            repository.createCalendarEvent(accessToken, request)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Event created successfully") }
                    loadData(accessToken, schoolId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to create event") }
                }
        }
    }

    fun updateCalendarEvent(
        eventId: String,
        accessToken: String,
        schoolId: String,
        sessionId: String,
        termId: String?,
        eventName: String,
        eventType: String,
        startDate: String,
        endDate: String?,
        color: String?,
        description: String?,
        isExamPeriod: Boolean,
        isHoliday: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val request = UpdateCalendarEventRequest(
                eventName = eventName,
                eventType = eventType,
                startDate = startDate,
                endDate = endDate,
                color = color,
                description = description,
                isExamPeriod = isExamPeriod,
                isHoliday = isHoliday,
                sessionId = sessionId,
                termId = termId
            )
            repository.updateCalendarEvent(eventId, accessToken, request)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Event updated successfully") }
                    loadData(accessToken, schoolId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to update event") }
                }
        }
    }

    fun deleteCalendarEvent(eventId: String, accessToken: String, schoolId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.deleteCalendarEvent(eventId, accessToken, schoolId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Event deleted successfully") }
                    loadData(accessToken, schoolId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete event") }
                }
        }
    }
}
