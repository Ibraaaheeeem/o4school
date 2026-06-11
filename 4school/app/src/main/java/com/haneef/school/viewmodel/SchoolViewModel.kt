package com.haneef.school.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneef.school.data.models.*
import com.haneef.school.data.repository.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SchoolViewModel(
    private val schoolRepository: SchoolRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SchoolUiState())
    val uiState: StateFlow<SchoolUiState> = _uiState.asStateFlow()
    
    fun updateSchoolData(
        schoolId: String,
        accessToken: String,
        request: SchoolData
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                isUpdateSuccessful = false
            )
            
            schoolRepository.updateSchoolData(schoolId, accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isUpdateSuccessful = true,
                        successMessage = "School updated successfully"
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to update school data"
                    )
                }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            isUpdateSuccessful = false
        )
    }

    fun getCurrentSchoolData(schoolId: String, accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                isUpdateSuccessful = false
            )

            schoolRepository.getCurrentSchoolData(schoolId, accessToken)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSchoolData = response,
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to get current school data"
                    )
                }
        }
    }

    fun getAcademicStructure(schoolId: String, accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            schoolRepository.getAcademicStructure(schoolId, accessToken)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        academicStructure = response
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to get academic structure"
                    )
                }
        }
    }

    fun createTrack(schoolId: String, accessToken: String, name: String, description: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = CreateEducationTrackRequest(schoolId, name, description)
            schoolRepository.createTrack(accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Track created successfully"
                    )
                    getAcademicStructure(schoolId, accessToken)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to create track"
                    )
                }
        }
    }

    fun createDepartment(schoolId: String, accessToken: String, trackId: String, name: String, description: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = CreateDepartmentRequest(schoolId, trackId, name, description)
            schoolRepository.createDepartment(accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Department created successfully"
                    )
                    getAcademicStructure(schoolId, accessToken)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to create department"
                    )
                }
        }
    }

    fun createClass(schoolId: String, accessToken: String, departmentId: String, name: String, code: String?, gradeLevel: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = CreateClassRequest(
                schoolId = schoolId,
                departmentId = departmentId,
                className = name,
                classCode = code,
                gradeLevel = gradeLevel
            )
            schoolRepository.createClass(accessToken, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Class created successfully"
                    )
                    getAcademicStructure(schoolId, accessToken)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to create class"
                    )
                }
        }
    }

    fun initializeDefaultStructure(schoolId: String, accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = InitializeDefaultStructureRequest(schoolId)
            schoolRepository.initializeDefaultStructure(accessToken, request)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = response.message
                    )
                    getAcademicStructure(schoolId, accessToken)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to initialize default structure"
                    )
                }
        }
    }

    fun getAllSubjects(accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            schoolRepository.getAllSubjects(accessToken)
                .onSuccess { subjects ->
                    _uiState.value = _uiState.value.copy(isLoading = false, allSubjects = subjects)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                }
        }
    }

    fun getSchoolSubjects(
        schoolId: String,
        accessToken: String,
        className: String? = null,
        departmentId: String? = null,
        trackId: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            schoolRepository.getSchoolSubjects(schoolId, accessToken, className, departmentId, trackId)
                .onSuccess { subjects ->
                    _uiState.value = _uiState.value.copy(isLoading = false, schoolSubjects = subjects)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                }
        }
    }

    fun saveSchoolSubjects(schoolId: String, accessToken: String, subjectIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val request = SaveSchoolSubjectsRequest(schoolId, subjectIds)
            schoolRepository.saveSchoolSubjects(accessToken, request)
                .onSuccess { subjects ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Subjects saved successfully",
                        schoolSubjects = subjects
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message)
                }
        }
    }

    fun linkClassToSubject(
        schoolSubjectId: String,
        schoolId: String,
        accessToken: String,
        classIds: List<String>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = LinkClassToSubjectRequest(schoolId = schoolId, classIds = classIds)
            schoolRepository.linkClassesToSchoolSubject(schoolSubjectId, accessToken, request)
                .onSuccess { updatedSubjects ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Class linked to subject successfully",
                        schoolSubjects = updatedSubjects
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to link class"
                    )
                }
        }
    }

    fun unlinkClassFromSubject(
        schoolSubjectId: String,
        classId: String,
        accessToken: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            schoolRepository.unlinkClassFromSchoolSubject(schoolSubjectId, classId, accessToken)
                .onSuccess { updatedSubjects ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Class unlinked from subject successfully",
                        schoolSubjects = updatedSubjects
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to unlink class"
                    )
                }
        }
    }

    fun deactivateSchoolSubject(
        schoolSubjectId: String,
        accessToken: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            schoolRepository.deactivateSchoolSubject(schoolSubjectId, accessToken)
                .onSuccess { updatedSubjects ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Subject removed successfully",
                        schoolSubjects = updatedSubjects
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to remove subject"
                    )
                }
        }
    }
}

data class SchoolUiState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val isUpdateSuccessful: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentSchoolData: SchoolData? = null,
    val academicStructure: AcademicStructureResponse? = null,
    val allSubjects: List<SubjectNode> = emptyList(),
    val schoolSubjects: List<SchoolSubjectResponse> = emptyList()
)
