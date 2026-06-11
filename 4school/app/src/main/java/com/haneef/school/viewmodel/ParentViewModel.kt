package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.AssignParentStudentsRequest
import com.haneef.school.data.models.CreateParentInfo
import com.haneef.school.data.models.CreateParentWithUserRequest
import com.haneef.school.data.models.ParentListResponse
import com.haneef.school.data.models.Student
import com.haneef.school.data.models.UpdateParentRequest
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ParentViewModel(
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    fun loadParents(
        schoolId: String,
        accessToken: String,
        page: Int = _uiState.value.currentPage,
        search: String? = _uiState.value.searchQuery,
        trackId: String? = _uiState.value.selectedTrackId,
        classId: String? = _uiState.value.selectedClassId
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                searchQuery = search,
                selectedTrackId = trackId,
                selectedClassId = classId,
                currentPage = page
            )

            schoolRepository.getParents(
                accessToken = accessToken,
                schoolId = schoolId,
                page = page,
                perPage = 10,
                search = search,
                trackId = trackId,
                classId = classId
            )
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        parentList = response.data ?: emptyList(),
                        currentPage = response.pagination?.currentPage ?: page,
                        totalPages = response.pagination?.totalPages ?: 1,
                        totalItems = response.pagination?.total ?: 0,
                        hasNext = response.pagination?.hasNext ?: false,
                        hasPrevious = response.pagination?.hasPrevious ?: false
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to load parent list"
                    )
                }
        }
    }

    fun updateFilters(
        schoolId: String,
        accessToken: String,
        search: String? = _uiState.value.searchQuery,
        trackId: String? = _uiState.value.selectedTrackId,
        classId: String? = _uiState.value.selectedClassId
    ) {
        loadParents(
            schoolId = schoolId,
            accessToken = accessToken,
            page = 1,
            search = search,
            trackId = trackId,
            classId = classId
        )
    }

    fun clearFilters(schoolId: String, accessToken: String) {
        updateFilters(
            schoolId = schoolId,
            accessToken = accessToken,
            search = null,
            trackId = null,
            classId = null
        )
    }

    fun createParent(
        schoolId: String,
        accessToken: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.createParentWithUser(
                accessToken = accessToken,
                request = CreateParentWithUserRequest(
                    schoolId = schoolId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phone?.takeIf { it.isNotBlank() },
                    parent = CreateParentInfo(
                        businessAddress = address?.takeIf { it.isNotBlank() }
                    )
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadParents(schoolId, accessToken, page = 1)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = ex.message ?: "Failed to create parent"
                )
            }
        }
    }

    fun updateParent(
        schoolId: String,
        accessToken: String,
        parentId: String,
        fullName: String,
        email: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.updateParent(
                id = parentId,
                accessToken = accessToken,
                request = UpdateParentRequest(
                    fullName = fullName,
                    email = email,
                    phoneNumber = phone?.takeIf { it.isNotBlank() },
                    address = address?.takeIf { it.isNotBlank() }
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadParents(schoolId, accessToken, page = _uiState.value.currentPage)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = ex.message ?: "Failed to update parent"
                )
            }
        }
    }

    fun deleteParent(
        schoolId: String,
        accessToken: String,
        parentId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.deleteParent(
                id = parentId,
                accessToken = accessToken,
                schoolId = schoolId
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadParents(schoolId, accessToken, page = 1)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = ex.message ?: "Failed to delete parent"
                )
            }
        }
    }

    fun assignStudents(
        schoolId: String,
        accessToken: String,
        parentId: String,
        studentIds: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.assignParentStudents(
                id = parentId,
                accessToken = accessToken,
                request = AssignParentStudentsRequest(
                    schoolId = schoolId,
                    studentIds = studentIds
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadParents(schoolId, accessToken, page = _uiState.value.currentPage)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = ex.message ?: "Failed to assign students"
                )
            }
        }
    }

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveErrorMessage = null)
    }

    fun searchStudents(
        schoolId: String,
        accessToken: String,
        query: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingStudents = true)
            schoolRepository.getStudents(
                accessToken = accessToken,
                schoolId = schoolId,
                page = 1,
                perPage = 50,
                search = query
            ).onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isSearchingStudents = false,
                    searchedStudents = response.data ?: emptyList()
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSearchingStudents = false,
                    searchedStudents = emptyList()
                )
            }
        }
    }

    fun clearSearchedStudents() {
        _uiState.value = _uiState.value.copy(searchedStudents = emptyList())
    }

    fun deleteStudentAssignment(
        schoolId: String,
        accessToken: String,
        assignmentId: String,
        parentId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.deleteParentStudentAssignment(
                assignmentId = assignmentId,
                accessToken = accessToken,
                schoolId = schoolId
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadParents(schoolId, accessToken, page = _uiState.value.currentPage)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = ex.message ?: "Failed to delete student linkage"
                )
            }
        }
    }
}

data class ParentUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val parentList: List<ParentListResponse> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val errorMessage: String? = null,
    val saveErrorMessage: String? = null,
    val searchQuery: String? = null,
    val selectedTrackId: String? = null,
    val selectedClassId: String? = null,
    val searchedStudents: List<Student> = emptyList(),
    val isSearchingStudents: Boolean = false
)
