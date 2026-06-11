package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.AssignStudentClassesRequest
import com.haneef.school.data.models.CreateStudentClassInfo
import com.haneef.school.data.models.CreateStudentDetails
import com.haneef.school.data.models.CreateStudentRequest
import com.haneef.school.data.models.CreateStudentUserInfo
import com.haneef.school.data.models.Student
import com.haneef.school.data.models.StudentClassAssignmentResponse
import com.haneef.school.data.models.StudentDetailDto
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentViewModel(
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    fun loadStudents(
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

            schoolRepository.getStudents(
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
                        studentList = response.data ?: emptyList(),
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
                        errorMessage = exception.message ?: "Failed to load students list"
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
        loadStudents(
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

    fun createStudent(
        schoolId: String,
        accessToken: String,
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        dateOfBirth: String?,
        gender: String?,
        admissionNumber: String?,
        admissionDate: String?,
        currentGradeLevel: String?,
        specialNeeds: String?,
        transportation: String?,
        schoolSlug: String,
        initialClasses: List<CreateStudentClassInfo>,
        academicStatus: String = "ENROLLED",
        passportPhotoUrl: String? = null,
        hasSpecialNeeds: Boolean = false,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            val request = CreateStudentRequest(
                user = CreateStudentUserInfo(
                    email = email?.takeIf { it.isNotBlank() },
                    phoneNumber = phone?.takeIf { it.isNotBlank() },
                    firstName = firstName,
                    middleName = null,
                    lastName = lastName,
                    gender = gender,
                    dateOfBirth = dateOfBirth?.takeIf { it.isNotBlank() },
                    schoolSlug = schoolSlug
                ),
                student = CreateStudentDetails(
                    studentId = null,
                    schoolId = schoolId,
                    admissionNumber = admissionNumber?.takeIf { it.isNotBlank() },
                    admissionDate = admissionDate?.takeIf { it.isNotBlank() },
                    graduationDate = null,
                    academicStatus = academicStatus,
                    currentGradeLevel = currentGradeLevel,
                    dateOfBirth = dateOfBirth?.takeIf { it.isNotBlank() },
                    gender = gender,
                    previousSchool = null,
                    specialNeedsDescription = specialNeeds?.takeIf { it.isNotBlank() },
                    transportationMethod = transportation?.takeIf { it.isNotBlank() },
                    passportPhotoUrl = passportPhotoUrl?.takeIf { it.isNotBlank() },
                    hasSpecialNeeds = hasSpecialNeeds
                ),
                studentClasses = initialClasses.takeIf { it.isNotEmpty() }
            )

            schoolRepository.createStudentWithUser(accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Student created successfully"
                    )
                    loadStudents(schoolId, accessToken, page = 1)
                    onSuccess()
                }
                .onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = ex.message ?: "Failed to create student"
                    )
                }
        }
    }

    fun loadStudentDetailAndClasses(
        studentId: String,
        accessToken: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            // 1. Get Student Detail
            schoolRepository.getStudent(studentId, accessToken)
                .onSuccess { detail ->
                    // 2. Get Student Class Assignments
                    schoolRepository.getStudentClassAssignments(studentId, accessToken)
                        .onSuccess { assignments ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                singleStudentDetail = detail,
                                singleStudentClassAssignments = assignments
                            )
                        }
                        .onFailure { ex ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                singleStudentDetail = detail,
                                singleStudentClassAssignments = emptyList(),
                                errorMessage = "Student details loaded, but class assignments failed: ${ex.message}"
                            )
                        }
                }
                .onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = ex.message ?: "Failed to load student details"
                    )
                }
        }
    }

    fun updateStudent(
        schoolId: String,
        accessToken: String,
        studentId: String,
        detailDto: StudentDetailDto,
        classesToAssign: List<CreateStudentClassInfo>,
        assignmentsToDelete: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            
            var success = true
            var errMsg: String? = null

            // 1. Process deletions
            for (assignmentId in assignmentsToDelete) {
                schoolRepository.deleteStudentClassAssignment(assignmentId, accessToken)
                    .onFailure { ex ->
                        success = false
                        errMsg = "Failed to remove previous class: ${ex.message}"
                    }
                if (!success) break
            }

            // 2. Update profile details
            if (success) {
                schoolRepository.updateStudent(studentId, accessToken, detailDto)
                    .onFailure { ex ->
                        success = false
                        errMsg = "Failed to update student details: ${ex.message}"
                    }
            }

            // 3. Add new assignments
            if (success && classesToAssign.isNotEmpty()) {
                val assignRequest = AssignStudentClassesRequest(
                    schoolId = schoolId,
                    classes = classesToAssign
                )
                schoolRepository.assignStudentClasses(studentId, accessToken, assignRequest)
                    .onFailure { ex ->
                        success = false
                        errMsg = "Failed to link classes: ${ex.message}"
                    }
            }

            if (success) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Student updated successfully"
                )
                loadStudents(schoolId, accessToken, page = _uiState.value.currentPage)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = errMsg ?: "Failed to update student"
                )
            }
        }
    }

    fun updateStudentClassesOnly(
        schoolId: String,
        accessToken: String,
        studentId: String,
        classesToAssign: List<CreateStudentClassInfo>,
        assignmentsToDelete: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            
            var success = true
            var errMsg: String? = null

            // 1. Delete assignments
            for (assignmentId in assignmentsToDelete) {
                schoolRepository.deleteStudentClassAssignment(assignmentId, accessToken)
                    .onFailure { ex ->
                        success = false
                        errMsg = "Failed to remove previous class: ${ex.message}"
                    }
                if (!success) break
            }

            // 2. Add new assignments
            if (success && classesToAssign.isNotEmpty()) {
                val assignRequest = AssignStudentClassesRequest(
                    schoolId = schoolId,
                    classes = classesToAssign
                )
                schoolRepository.assignStudentClasses(studentId, accessToken, assignRequest)
                    .onFailure { ex ->
                        success = false
                        errMsg = "Failed to link classes: ${ex.message}"
                    }
            }

            if (success) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Class assignments updated successfully"
                )
                loadStudents(schoolId, accessToken, page = _uiState.value.currentPage)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = errMsg ?: "Failed to update class assignments"
                )
            }
        }
    }

    fun assignStudentClasses(
        schoolId: String,
        accessToken: String,
        studentId: String,
        classes: List<CreateStudentClassInfo>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            val request = AssignStudentClassesRequest(schoolId, classes)
            schoolRepository.assignStudentClasses(studentId, accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Classes assigned successfully"
                    )
                    loadStudentDetailAndClasses(studentId, accessToken)
                    loadStudents(schoolId, accessToken, page = _uiState.value.currentPage)
                    onSuccess()
                }
                .onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = ex.message ?: "Failed to assign classes"
                    )
                }
        }
    }

    fun deleteStudentClassAssignment(
        schoolId: String,
        accessToken: String,
        studentId: String,
        assignmentId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.deleteStudentClassAssignment(assignmentId, accessToken)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Class assignment removed successfully"
                    )
                    loadStudentDetailAndClasses(studentId, accessToken)
                    loadStudents(schoolId, accessToken, page = _uiState.value.currentPage)
                    onSuccess()
                }
                .onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = ex.message ?: "Failed to delete class assignment"
                    )
                }
        }
    }

    fun deleteStudent(
        schoolId: String,
        accessToken: String,
        studentId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveErrorMessage = null)
            schoolRepository.deleteStudent(studentId, accessToken)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Student deleted successfully"
                    )
                    loadStudents(schoolId, accessToken, page = 1)
                    onSuccess()
                }
                .onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveErrorMessage = ex.message ?: "Failed to delete student"
                    )
                }
        }
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

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveErrorMessage = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}

data class StudentUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val studentList: List<Student> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val errorMessage: String? = null,
    val saveErrorMessage: String? = null,
    val successMessage: String? = null,
    val searchQuery: String? = null,
    val selectedTrackId: String? = null,
    val selectedClassId: String? = null,
    val singleStudentDetail: StudentDetailDto? = null,
    val singleStudentClassAssignments: List<StudentClassAssignmentResponse> = emptyList(),
    val currentSessionId: String? = null,
    val currentTermId: String? = null
)
