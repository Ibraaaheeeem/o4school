package com.haneef.school.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.DashboardResponse
import com.haneef.school.data.repository.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "DashboardViewModel"
    }
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    fun loadDashboardData(accessToken: String) {
        Log.d(TAG, "=== LOADING DASHBOARD DATA ===")
        Log.d(TAG, "Access Token: ${if (accessToken.isNotEmpty()) "[TOKEN_PRESENT]" else "[NO_TOKEN]"}")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            Log.d(TAG, "UI State updated - Loading: true")
            
            dashboardRepository.getDashboardData(accessToken)
                .onSuccess { response ->
                    Log.d(TAG, "=== DASHBOARD DATA LOADED SUCCESSFULLY ===")
                    Log.d(TAG, "Dashboard Response Details:")
                    Log.d(TAG, "  - User ID: ${response.userId}")
                    Log.d(TAG, "  - School ID: ${response.schoolId}")
                    Log.d(TAG, "  - School Name: ${response.schoolName}")
                    
                    // Save school ID to preferences (suspend call within coroutine)
                    preferencesManager.saveSchoolId(response.schoolId)
                    preferencesManager.saveSchoolName(response.schoolName)
                    Log.d(TAG, "Persistent school data updated")

                    Log.d(TAG, "  - User Role: ${response.role}")
                    Log.d(TAG, "  - Admin Overview: ${response.adminOverview}")
                    Log.d(TAG, "  - Staff Overview: ${response.staffOverview}")
                    Log.d(TAG, "  - Parent Overview: ${response.parentOverview}")
                    Log.d(TAG, "  - Student Overview: ${response.studentOverview}")
                    Log.d(TAG, "  - Financial Health: ${response.financialHealth}")
                    Log.d(TAG, "  - Critical Alerts Count: ${response.criticalAlerts.size}")
                    Log.d(TAG, "  - Upcoming Events Count: ${response.upcomingEvents.size}")
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, 
                        dashboardData = response,
                        errorMessage = null
                    )
                    Log.d(TAG, "UI State updated - Loading: false, Data loaded successfully")
                }
                .onFailure { exception ->
                    Log.e(TAG, "=== DASHBOARD DATA LOADING FAILED ===")
                    Log.e(TAG, "Exception: ${exception.message}")
                    Log.e(TAG, "Exception Stack Trace:", exception)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, 
                        errorMessage = exception.message
                    )
                    Log.d(TAG, "UI State updated - Loading: false, Error: ${exception.message}")
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun refreshDashboard(accessToken: String) {
        Log.d(TAG, "=== REFRESHING DASHBOARD ===")
        loadDashboardData(accessToken)
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val dashboardData: DashboardResponse? = null
)

