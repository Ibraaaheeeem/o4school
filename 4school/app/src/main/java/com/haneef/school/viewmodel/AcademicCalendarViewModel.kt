package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.*
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AcademicCalendarUiState(
    val isLoading: Boolean = false,
    val sessions: List<AcademicSession> = emptyList(),
    val termsMap: Map<String, List<Term>> = emptyMap(), // session_id -> list of terms
    val error: String? = null,
    val actionSuccessMessage: String? = null
)

class AcademicCalendarViewModel(
    private val repository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcademicCalendarUiState())
    val uiState: StateFlow<AcademicCalendarUiState> = _uiState.asStateFlow()

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(actionSuccessMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadData(accessToken: String, schoolId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val sessionsResult = repository.getAcademicSessions(accessToken, schoolId)
            
            sessionsResult.fold(
                onSuccess = { sessionList ->
                    val termsMap = mutableMapOf<String, List<Term>>()
                    var loadTermsError: String? = null

                    for (session in sessionList) {
                        val termsResult = repository.getTermsInSession(session.id, accessToken, schoolId)
                        termsResult.fold(
                            onSuccess = { termList ->
                                termsMap[session.id] = termList
                            },
                            onFailure = { error ->
                                loadTermsError = error.message
                            }
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessions = sessionList,
                        termsMap = termsMap,
                        error = loadTermsError
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load academic sessions"
                    )
                }
            )
        }
    }

    fun createAcademicSession(
        accessToken: String,
        schoolId: String,
        name: String,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = CreateAcademicSessionRequest(
                schoolId = schoolId,
                name = name,
                startDate = startDate,
                endDate = endDate,
                isCurrent = isCurrent
            )
            val result = repository.createAcademicSession(accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Academic session created successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create academic session"
                    )
                }
            )
        }
    }

    fun updateAcademicSession(
        sessionId: String,
        accessToken: String,
        schoolId: String,
        name: String,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = CreateAcademicSessionRequest(
                schoolId = schoolId,
                name = name,
                startDate = startDate,
                endDate = endDate,
                isCurrent = isCurrent
            )
            val result = repository.updateAcademicSession(sessionId, accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Academic session updated successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update academic session"
                    )
                }
            )
        }
    }

    fun deleteAcademicSession(
        sessionId: String,
        accessToken: String,
        schoolId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.deleteAcademicSession(sessionId, accessToken, schoolId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Academic session deleted successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to delete academic session"
                    )
                }
            )
        }
    }

    fun createTerm(
        accessToken: String,
        schoolId: String,
        sessionId: String,
        name: String,
        termNumber: Int,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = CreateTermRequest(
                schoolId = schoolId,
                sessionId = sessionId,
                name = name,
                termNumber = termNumber,
                startDate = startDate,
                endDate = endDate,
                isCurrent = isCurrent
            )
            val result = repository.createTerm(accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Term created successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create term"
                    )
                }
            )
        }
    }

    fun updateTerm(
        termId: String,
        accessToken: String,
        schoolId: String,
        sessionId: String,
        name: String,
        termNumber: Int,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val request = CreateTermRequest(
                schoolId = schoolId,
                sessionId = sessionId,
                name = name,
                termNumber = termNumber,
                startDate = startDate,
                endDate = endDate,
                isCurrent = isCurrent
            )
            val result = repository.updateTerm(termId, accessToken, request)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Term updated successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update term"
                    )
                }
            )
        }
    }

    fun deleteTerm(
        termId: String,
        accessToken: String,
        schoolId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.deleteTerm(termId, accessToken, schoolId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(actionSuccessMessage = "Term deleted successfully")
                    loadData(accessToken, schoolId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to delete term"
                    )
                }
            )
        }
    }
}
