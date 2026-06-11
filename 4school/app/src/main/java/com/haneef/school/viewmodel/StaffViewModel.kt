package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.*
import com.haneef.school.data.repository.SchoolRepository
import com.haneef.school.ui.screens.community.AssignmentFormEntry
import com.haneef.school.ui.screens.community.AssignmentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StaffViewModel(
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffUiState())
    val uiState: StateFlow<StaffUiState> = _uiState.asStateFlow()

    fun loadStaff(
        schoolId: String,
        accessToken: String,
        page: Int = _uiState.value.currentPage,
        search: String? = _uiState.value.searchQuery,
        trackId: String? = _uiState.value.selectedTrackId,
        departmentId: String? = _uiState.value.selectedDeptId,
        classId: String? = _uiState.value.selectedClassId,
        designation: String? = _uiState.value.selectedDesignation
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                searchQuery = search,
                selectedTrackId = trackId,
                selectedDeptId = departmentId,
                selectedClassId = classId,
                selectedDesignation = designation,
                currentPage = page
            )

            schoolRepository.getStaff(
                accessToken = accessToken,
                schoolId = schoolId,
                page = page,
                perPage = 10,
                search = search,
                trackId = trackId,
                departmentId = departmentId,
                classId = classId,
                designation = designation
            )
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        staffList = response.data ?: emptyList(),
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
                        errorMessage = exception.message ?: "Failed to load staff list"
                    )
                }
        }
    }

    fun updateFilters(
        schoolId: String,
        accessToken: String,
        search: String? = _uiState.value.searchQuery,
        trackId: String? = _uiState.value.selectedTrackId,
        departmentId: String? = _uiState.value.selectedDeptId,
        classId: String? = _uiState.value.selectedClassId,
        designation: String? = _uiState.value.selectedDesignation
    ) {
        loadStaff(
            schoolId = schoolId,
            accessToken = accessToken,
            page = 1,
            search = search,
            trackId = trackId,
            departmentId = departmentId,
            classId = classId,
            designation = designation
        )
    }

    fun clearFilters(schoolId: String, accessToken: String) {
        updateFilters(
            schoolId = schoolId,
            accessToken = accessToken,
            search = null,
            trackId = null,
            departmentId = null,
            classId = null,
            designation = null
        )
    }

    fun loadCurrentSchedule(schoolId: String, accessToken: String) {
        viewModelScope.launch {
            schoolRepository.getCurrentSchedule(schoolId, accessToken)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        currentSessionId = response.sessionId,
                        currentTermId = response.termId
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = exception.message ?: "Failed to get current schedule"
                    )
                }
        }
    }

    fun createStaff(
        schoolId: String,
        accessToken: String,
        email: String,
        firstName: String,
        lastName: String,
        phone: String?,
        designation: String,
        hireDate: String,
        department: String?,
        employeeNumber: String?,
        classes: List<CreateClassTeacherInfo>,
        subjects: List<CreateSubjectTeacherInfo>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null, isSaveSuccessful = false)
            val request = CreateStaffWithUserRequest(
                schoolId = schoolId,
                email = email,
                phoneNumber = phone,
                firstName = firstName,
                middleName = null,
                lastName = lastName,
                gender = null,
                dateOfBirth = null,
                staff = CreateStaffInfo(
                    staffId = null,
                    employeeNumber = employeeNumber,
                    designation = designation,
                    hireDate = hireDate,
                    employmentStatus = "ACTIVE",
                    employmentType = "FULL_TIME",
                    highestDegree = null,
                    department = department,
                    isClassTeacher = classes.isNotEmpty(),
                    isSubjectTeacher = subjects.isNotEmpty()
                ),
                staffClassAssignments = classes.takeIf { it.isNotEmpty() },
                staffSubjectAssignments = subjects.takeIf { it.isNotEmpty() }
            )
            schoolRepository.createStaffWithUser(accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaveSuccessful = true)
                    loadStaff(schoolId, accessToken)
                    onSuccess()
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = exception.message ?: "Failed to create staff member"
                    )
                }
        }
    }

    private suspend fun processAssignmentDeletions(
        accessToken: String,
        deletedAssignments: List<AssignmentFormEntry>
    ): Result<Unit> {
        for (entry in deletedAssignments) {
            val result = if (entry.type == AssignmentType.CLASS_TEACHER) {
                schoolRepository.deleteStaffClassAssignment(entry.id, accessToken)
            } else {
                schoolRepository.deleteStaffSubjectAssignment(entry.id, accessToken)
            }
            if (result.isFailure) {
                return result
            }
        }
        return Result.success(Unit)
    }

    fun updateStaff(
        schoolId: String,
        accessToken: String,
        staffId: String,
        staff: Staff,
        classes: List<CreateClassTeacherInfo>,
        subjects: List<CreateSubjectTeacherInfo>,
        deletedAssignments: List<AssignmentFormEntry>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null, isSaveSuccessful = false)
            
            processAssignmentDeletions(accessToken, deletedAssignments)
                .onSuccess {
                    schoolRepository.updateStaff(staffId, accessToken, staff)
                        .onSuccess {
                            schoolRepository.assignStaffClasses(
                                staffId,
                                accessToken,
                                AssignStaffClassesRequest(schoolId, classes)
                            ).onSuccess {
                                schoolRepository.assignStaffSubjects(
                                    staffId,
                                    accessToken,
                                    AssignStaffSubjectsRequest(schoolId, subjects)
                                ).onSuccess {
                                    _uiState.value = _uiState.value.copy(isSaving = false, isSaveSuccessful = true)
                                    loadStaff(schoolId, accessToken)
                                    onSuccess()
                                }.onFailure { exception ->
                                    _uiState.value = _uiState.value.copy(
                                        isSaving = false,
                                        saveErrorMessage = exception.message ?: "Failed to update subject assignments"
                                    )
                                }
                            }.onFailure { exception ->
                                _uiState.value = _uiState.value.copy(
                                    isSaving = false,
                                    saveErrorMessage = exception.message ?: "Failed to update class assignments"
                                )
                            }
                        }
                        .onFailure { exception ->
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveErrorMessage = exception.message ?: "Failed to update staff details"
                            )
                        }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = exception.message ?: "Failed to delete assignments"
                    )
                }
        }
    }

    fun updateAssignmentsOnly(
        schoolId: String,
        accessToken: String,
        staffId: String,
        classes: List<CreateClassTeacherInfo>,
        subjects: List<CreateSubjectTeacherInfo>,
        deletedAssignments: List<AssignmentFormEntry>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null, isSaveSuccessful = false)

            processAssignmentDeletions(accessToken, deletedAssignments)
                .onSuccess {
                    schoolRepository.assignStaffClasses(
                        staffId,
                        accessToken,
                        AssignStaffClassesRequest(schoolId, classes)
                    ).onSuccess {
                        schoolRepository.assignStaffSubjects(
                            staffId,
                            accessToken,
                            AssignStaffSubjectsRequest(schoolId, subjects)
                        ).onSuccess {
                            _uiState.value = _uiState.value.copy(isSaving = false, isSaveSuccessful = true)
                            loadStaff(schoolId, accessToken)
                            onSuccess()
                        }.onFailure { exception ->
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveErrorMessage = exception.message ?: "Failed to update subject assignments"
                            )
                        }
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveErrorMessage = exception.message ?: "Failed to update class assignments"
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = exception.message ?: "Failed to delete assignments"
                    )
                }
        }
    }

    fun deleteStaff(
        schoolId: String,
        accessToken: String,
        staffId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            schoolRepository.deleteStaff(staffId, accessToken)
                .onSuccess {
                    loadStaff(schoolId, accessToken)
                    onSuccess()
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to delete staff member"
                    )
                }
        }
    }

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveErrorMessage = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(isSaveSuccessful = false)
    }
}

data class StaffUiState(
    val isLoading: Boolean = false,
    val staffList: List<Staff> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String? = null,
    val selectedTrackId: String? = null,
    val selectedDeptId: String? = null,
    val selectedClassId: String? = null,
    val selectedDesignation: String? = null,
    val currentSessionId: String? = null,
    val currentTermId: String? = null,
    val isSaving: Boolean = false,
    val isSaveSuccessful: Boolean = false,
    val saveErrorMessage: String? = null
)
