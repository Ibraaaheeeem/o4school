package com.haneef.school.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.*
import com.haneef.school.data.repository.AuthRepository
import com.haneef.school.ui.screens.auth.AuthScreenState
import com.haneef.school.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "AuthViewModel"
    }
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    fun signUp(state: AuthScreenState, onSuccess: (AuthNextRoute) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "=== SIGNUP VALIDATION START ===")
        Log.d(TAG, "Form State:")
        Log.d(TAG, "  - Email: ${state.email}")
        Log.d(TAG, "  - Full Name: ${state.fullName}")
        Log.d(TAG, "  - Phone: ${state.phoneNumber} (${state.countryCode})")
        Log.d(TAG, "  - Address Line 1: ${state.addressLine1}")
        Log.d(TAG, "  - Address Line 2: ${state.addressLine2}")
        Log.d(TAG, "  - City: ${state.city}")
        Log.d(TAG, "  - State: ${state.state}")
        Log.d(TAG, "  - Country: ${state.country}")
        
        val validation = ValidationUtils.validateSignupForm(state)
        if (!validation.isValid) {
            Log.e(TAG, "Form validation failed: ${validation.errorMessage}")
            onError(validation.errorMessage ?: "Validation failed")
            return
        }
        
        Log.d(TAG, "Form validation passed")
        
        val (firstName, lastName) = ValidationUtils.parseFullName(state.fullName)
        Log.d(TAG, "Parsed name - First: '$firstName', Last: '$lastName'")
        
        val request = SignUpRequest(
            email = state.email.trim(),
            password = state.password,
            firstName = firstName,
            lastName = lastName,
            phoneNumber = if (state.phoneNumber.isNotBlank()) state.phoneNumber.trim() else null,
            phoneCountryCode = if (state.phoneNumber.isNotBlank()) state.countryCode else null,
            addressLine1 = if (state.addressLine1.isNotBlank()) state.addressLine1.trim() else null,
            addressLine2 = if (state.addressLine2.isNotBlank()) state.addressLine2.trim() else null,
            city = if (state.city.isNotBlank()) state.city.trim() else null,
            state = if (state.state.isNotBlank()) state.state.trim() else null,
            country = if (state.country.isNotBlank()) state.country.trim() else null,
            role = UserRole.SCHOOL_ADMIN.value
        )
        
        Log.d(TAG, "=== CALLING SIGNUP API ===")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            Log.d(TAG, "UI State updated - Loading: true")
            
            authRepository.signUp(request)
                .onSuccess { response ->
                    Log.d(TAG, "=== SIGNUP API SUCCESS ===")
                    Log.d(TAG, "Next Route: ${response.nextRoute}")
                    Log.d(TAG, "Message: ${response.message}")
                    
                    _uiState.value = _uiState.value.copy(isLoading = false, signUpResponse = response)
                    Log.d(TAG, "UI State updated - Loading: false, Response saved")
                    
                    onSuccess(response.nextRoute)
                    Log.d(TAG, "Success callback executed with next route: ${response.nextRoute}")
                }
                .onFailure { exception ->
                    Log.e(TAG, "=== SIGNUP API FAILURE ===")
                    Log.e(TAG, "Exception: ${exception.message}")
                    
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                    Log.d(TAG, "UI State updated - Loading: false, Error: ${exception.message}")
                    
                    onError(exception.message ?: "Signup failed")
                    Log.d(TAG, "Error callback executed")
                }
        }
    }
    
    fun login(email: String, password: String, onSuccess: (AuthNextRoute) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "=== LOGIN VALIDATION START ===")
        Log.d(TAG, "Login attempt for email: $email")
        
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) {
            Log.e(TAG, "Email validation failed: ${emailValidation.errorMessage}")
            onError(emailValidation.errorMessage ?: "Invalid email")
            return
        }
        
        if (password.isBlank()) {
            Log.e(TAG, "Password validation failed: Password is empty")
            onError("Password is required")
            return
        }
        
        Log.d(TAG, "Login validation passed")
        
        val request = LoginRequest(email = email.trim(), password = password)
        
        Log.d(TAG, "=== CALLING LOGIN API ===")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            Log.d(TAG, "UI State updated - Loading: true")
            
            authRepository.login(request)
                .onSuccess { response ->
                    Log.d(TAG, "=== LOGIN API SUCCESS ===")
                    Log.d(TAG, "Next Route: ${response.nextRoute}")
                    Log.d(TAG, "Message: ${response.message}")

                    // Save authentication data
                    preferencesManager.saveAccessToken(response.accessToken)
                    response.refreshToken?.let { preferencesManager.saveRefreshToken(it) }
                    preferencesManager.setLoggedIn(true)
                    preferencesManager.saveLoginTimestamp(System.currentTimeMillis())
                    
                    // Save school ID from the first school in the list
                    response.schools.firstOrNull()?.let { school ->
                        preferencesManager.saveSchoolId(school.schoolId)
                        preferencesManager.saveSchoolName(school.schoolName)
                        Log.d(TAG, "School ID saved: ${school.schoolId}")
                    }
                    
                    // Create User object for backward compatibility
                    val user = User(
                        id = response.userId,
                        email = response.email,
                        firstName = response.firstName ?: "",
                        lastName = response.lastName ?: "",
                        role = response.schools.firstOrNull()?.roles?.firstOrNull()?.roleName ?: "USER"
                    )
                    preferencesManager.saveUserData(user)

                    _uiState.value = _uiState.value.copy(isLoading = false, loginResponse = response)
                    Log.d(TAG, "UI State updated - Loading: false, Response saved, Auth data stored")
                    
                    onSuccess(response.nextRoute)
                    Log.d(TAG, "Success callback executed with next route: ${response.nextRoute}")
                }
                .onFailure { exception ->
                    Log.e(TAG, "=== LOGIN API FAILURE ===")
                    Log.e(TAG, "Exception: ${exception.message}")
                    
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                    Log.d(TAG, "UI State updated - Loading: false, Error: ${exception.message}")
                    
                    onError(exception.message ?: "Login failed")
                    Log.d(TAG, "Error callback executed")
                }
        }
    }

    fun verifyOtp(email: String, otp: String, nextRoute: AuthNextRoute, onSuccess: (SignUpResponse) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.verifyOtp(email, otp, nextRoute)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isLoading = false, otpResponse = response)
                    onSuccess(response)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                    onError(exception.message ?: "OTP verification failed")
                }
        }
    }
    
    fun resendOtp(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.resendOtp(email)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isLoading = false, resendOtpResponse = response)
                    onSuccess()
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                    onError(exception.message ?: "Failed to resend OTP")
                }
        }
    }
    
    fun forgotPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.forgotPassword(email)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isLoading = false, forgotPasswordResponse = response)
                    onSuccess()
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                    onError(exception.message ?: "Failed to send reset link")
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val signUpResponse: SignUpResponse? = null,
    val loginResponse: LoginResponse? = null,
    val otpResponse: SignUpResponse? = null,
    val resendOtpResponse: SignUpResponse? = null,
    val forgotPasswordResponse: SignUpResponse? = null
)