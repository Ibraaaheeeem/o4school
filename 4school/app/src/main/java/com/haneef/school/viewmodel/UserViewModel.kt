package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.api.DashboardResponse
import com.haneef.school.data.repository.UserRepository
import com.haneef.school.data.models.*
import com.haneef.school.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for user profile operations
 */
class UserViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    
    // User profile state
    private val _userProfileState = MutableStateFlow<Resource<User>?>(null)
    val userProfileState: StateFlow<Resource<User>?> = _userProfileState.asStateFlow()
    
    // Update profile state
    private val _updateProfileState = MutableStateFlow<Resource<User>?>(null)
    val updateProfileState: StateFlow<Resource<User>?> = _updateProfileState.asStateFlow()
    
    // Change password state
    private val _changePasswordState = MutableStateFlow<Resource<String>?>(null)
    val changePasswordState: StateFlow<Resource<String>?> = _changePasswordState.asStateFlow()
    
    // Upload avatar state
    private val _uploadAvatarState = MutableStateFlow<Resource<String>?>(null)
    val uploadAvatarState: StateFlow<Resource<String>?> = _uploadAvatarState.asStateFlow()
    
    // Dashboard state
    private val _dashboardState = MutableStateFlow<Resource<DashboardResponse>?>(null)
    val dashboardState: StateFlow<Resource<DashboardResponse>?> = _dashboardState.asStateFlow()

    // Users list state
    private val _usersListState = MutableStateFlow<Resource<PaginatedResponse<SchoolUser>>?>(null)
    val usersListState: StateFlow<Resource<PaginatedResponse<SchoolUser>>?> = _usersListState.asStateFlow()

    // Action state (activation, deactivation, deverification, reminder)
    private val _actionState = MutableStateFlow<Resource<Unit>?>(null)
    val actionState: StateFlow<Resource<Unit>?> = _actionState.asStateFlow()

    // Current user data
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    init {
        // Load cached user data
        _currentUser.value = userRepository.getCachedUser()
    }
    
    fun getUserProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { resource ->
                _userProfileState.value = resource
                if (resource is Resource.Success) {
                    _currentUser.value = resource.data
                }
            }
        }
    }
    
    fun updateProfile(
        fullName: String,
        email: String,
        phoneNumber: String?,
        department: String?
    ) {
        viewModelScope.launch {
            userRepository.updateProfile(
                fullName = fullName,
                email = email,
                phoneNumber = phoneNumber,
                department = department
            ).collect { resource ->
                _updateProfileState.value = resource
                if (resource is Resource.Success) {
                    _currentUser.value = resource.data
                }
            }
        }
    }
    
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        newPasswordConfirmation: String
    ) {
        viewModelScope.launch {
            userRepository.changePassword(
                currentPassword = currentPassword,
                newPassword = newPassword,
                newPasswordConfirmation = newPasswordConfirmation
            ).collect { resource ->
                _changePasswordState.value = resource
            }
        }
    }
    
    fun uploadAvatar(imageFile: File) {
        viewModelScope.launch {
            userRepository.uploadAvatar(imageFile).collect { resource ->
                _uploadAvatarState.value = resource
                if (resource is Resource.Success) {
                    // Refresh user profile to get updated avatar URL
                    getUserProfile()
                }
            }
        }
    }
    
    fun fetchDashboard() {
        viewModelScope.launch {
            userRepository.getDashboard().collect { resource ->
                _dashboardState.value = resource
            }
        }
    }

    fun getSchoolUsers(
        schoolId: String,
        role: String? = null,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 20
    ) {
        viewModelScope.launch {
            userRepository.getSchoolUsers(
                schoolId = schoolId,
                role = role,
                search = search,
                page = page,
                perPage = perPage
            ).collect { resource ->
                _usersListState.value = resource
            }
        }
    }

    fun deactivateUser(userId: String) {
        viewModelScope.launch {
            userRepository.deactivateUser(userId).collect { resource ->
                _actionState.value = resource
            }
        }
    }

    fun activateUser(userId: String) {
        viewModelScope.launch {
            userRepository.activateUser(userId).collect { resource ->
                _actionState.value = resource
            }
        }
    }

    fun deverifyUser(userId: String) {
        viewModelScope.launch {
            userRepository.deverifyUser(userId).collect { resource ->
                _actionState.value = resource
            }
        }
    }

    fun sendActivationReminder(userId: String) {
        viewModelScope.launch {
            userRepository.sendActivationReminder(userId).collect { resource ->
                _actionState.value = resource
            }
        }
    }

    fun refreshUserData() {
        getUserProfile()
    }
    
    fun clearUserProfileState() {
        _userProfileState.value = null
    }
    
    fun clearUpdateProfileState() {
        _updateProfileState.value = null
    }
    
    fun clearChangePasswordState() {
        _changePasswordState.value = null
    }
    
    fun clearUploadAvatarState() {
        _uploadAvatarState.value = null
    }

    fun clearActionState() {
        _actionState.value = null
    }

    fun clearUsersListState() {
        _usersListState.value = null
    }
    
    fun getCachedUser(): User? {
        return _currentUser.value
    }
}