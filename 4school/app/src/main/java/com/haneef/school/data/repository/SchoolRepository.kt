package com.haneef.school.data.repository

import com.google.gson.Gson
import com.haneef.school.data.api.SchoolApiService
import com.haneef.school.data.models.*
import retrofit2.HttpException
import java.io.IOException

class SchoolRepository(
    private val apiService: SchoolApiService,
    private val gson: Gson
) {
    
    private suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = errorBody?.let {
                    try {
                        gson.fromJson(it, ApiError::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                val errorMessage = apiError?.message ?: "API Error: ${response.message()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: HttpException) {
            Result.failure(Exception("Network error: ${e.message()}"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: Please check your internet connection"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    suspend fun getAcademicStructure(schoolId: String, accessToken: String) = 
        safeApiCall { apiService.getAcademicStructure(schoolId, "Bearer $accessToken") }

    suspend fun createTrack(accessToken: String, request: CreateEducationTrackRequest) =
        safeApiCall { apiService.createTrack("Bearer $accessToken", request) }

    suspend fun createDepartment(accessToken: String, request: CreateDepartmentRequest) =
        safeApiCall { apiService.createDepartment("Bearer $accessToken", request) }

    suspend fun createClass(accessToken: String, request: CreateClassRequest) =
        safeApiCall { apiService.createClass("Bearer $accessToken", request) }

    suspend fun initializeDefaultStructure(accessToken: String, request: InitializeDefaultStructureRequest) =
        safeApiCall { apiService.initializeDefaultStructure("Bearer $accessToken", request) }

    suspend fun getAllSubjects(accessToken: String) =
        safeApiCall { apiService.getAllSubjects("Bearer $accessToken") }

    suspend fun getSchoolSubjects(schoolId: String, accessToken: String, className: String? = null, departmentId: String? = null, trackId: String? = null) =
        safeApiCall { apiService.getSchoolSubjects(schoolId, "Bearer $accessToken", className, departmentId, trackId) }

    suspend fun saveSchoolSubjects(accessToken: String, request: SaveSchoolSubjectsRequest) =
        safeApiCall { apiService.saveSchoolSubjects("Bearer $accessToken", request) }

    suspend fun linkClassesToSchoolSubject(schoolSubjectId: String, accessToken: String, request: LinkClassToSubjectRequest) =
        safeApiCall { apiService.linkClassesToSchoolSubject(schoolSubjectId, "Bearer $accessToken", request) }

    suspend fun unlinkClassFromSchoolSubject(schoolSubjectId: String, classId: String, accessToken: String) =
        safeApiCall { apiService.unlinkClassFromSchoolSubject(schoolSubjectId, classId, "Bearer $accessToken") }

    suspend fun deactivateSchoolSubject(schoolSubjectId: String, accessToken: String) =
        safeApiCall { apiService.deactivateSchoolSubject(schoolSubjectId, "Bearer $accessToken") }
    
    suspend fun updateSchoolData(schoolId: String, accessToken: String, request: SchoolData) =
        safeApiCall { apiService.updateSchoolData(schoolId, "Bearer $accessToken", request) }

    suspend fun getCurrentSchoolData(schoolId: String, accessToken: String) =
        safeApiCall { apiService.getCurrentSchoolData(schoolId, "Bearer $accessToken") }

    // Students
    suspend fun getStudents(accessToken: String, schoolId: String, page: Int = 1, perPage: Int = 20, search: String? = null, trackId: String? = null, classId: String? = null) =
        safeApiCall { apiService.getStudents("Bearer $accessToken", schoolId, page, perPage, search, trackId, classId) }

    suspend fun createStudentWithUser(accessToken: String, request: CreateStudentRequest) =
        safeApiCall { apiService.createStudentWithUser("Bearer $accessToken", request) }

    suspend fun getStudent(id: String, accessToken: String) =
        safeApiCall { apiService.getStudent(id, "Bearer $accessToken") }

    suspend fun updateStudent(id: String, accessToken: String, request: StudentDetailDto) =
        safeApiCall { apiService.updateStudent(id, "Bearer $accessToken", request) }

    suspend fun deleteStudent(id: String, accessToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteStudent(id, "Bearer $accessToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete student"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getStudentClassAssignments(id: String, accessToken: String) =
        safeApiCall { apiService.getStudentClassAssignments(id, "Bearer $accessToken") }

    suspend fun assignStudentClasses(id: String, accessToken: String, request: AssignStudentClassesRequest): Result<Unit> {
        return try {
            val response = apiService.assignStudentClasses(id, "Bearer $accessToken", request)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to assign classes"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteStudentClassAssignment(assignmentId: String, accessToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteStudentClassAssignment(assignmentId, "Bearer $accessToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete class assignment"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Staff
    suspend fun getStaff(accessToken: String, schoolId: String, page: Int = 1, perPage: Int = 20, search: String? = null, trackId: String? = null, departmentId: String? = null, classId: String? = null, designation: String? = null) =
        safeApiCall { apiService.getStaff("Bearer $accessToken", schoolId, page, perPage, search, trackId, departmentId, classId, designation) }

    suspend fun createStaffWithUser(accessToken: String, request: CreateStaffWithUserRequest) =
        safeApiCall { apiService.createStaffWithUser("Bearer $accessToken", request) }

    suspend fun updateStaff(id: String, accessToken: String, request: Staff) =
        safeApiCall { apiService.updateStaff(id, "Bearer $accessToken", request) }

    suspend fun deleteStaff(id: String, accessToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteStaff(id, "Bearer $accessToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete staff member"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun assignStaffClasses(id: String, accessToken: String, request: AssignStaffClassesRequest): Result<Unit> {
        return try {
            val response = apiService.assignStaffClasses(id, "Bearer $accessToken", request)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to assign classes"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun assignStaffSubjects(id: String, accessToken: String, request: AssignStaffSubjectsRequest): Result<Unit> {
        return try {
            val response = apiService.assignStaffSubjects(id, "Bearer $accessToken", request)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to assign subjects"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteStaffClassAssignment(assignmentId: String, accessToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteStaffClassAssignment(assignmentId, "Bearer $accessToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete class assignment"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteStaffSubjectAssignment(assignmentId: String, accessToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteStaffSubjectAssignment(assignmentId, "Bearer $accessToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete subject assignment"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Parents
    suspend fun getParents(accessToken: String, schoolId: String, page: Int = 1, perPage: Int = 20, search: String? = null, trackId: String? = null, classId: String? = null) =
        safeApiCall { apiService.getParents("Bearer $accessToken", schoolId, page, perPage, search, trackId, classId) }

    suspend fun createParentWithUser(accessToken: String, request: CreateParentWithUserRequest) =
        safeApiCall { apiService.createParentWithUser("Bearer $accessToken", request) }

    suspend fun updateParent(id: String, accessToken: String, request: UpdateParentRequest) =
        safeApiCall { apiService.updateParent(id, "Bearer $accessToken", request) }

    suspend fun deleteParent(id: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteParent(id, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete parent"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun assignParentStudents(id: String, accessToken: String, request: AssignParentStudentsRequest): Result<Unit> {
        return try {
            val response = apiService.assignParentStudents(id, "Bearer $accessToken", request)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to assign students"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteParentStudentAssignment(assignmentId: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteParentStudentAssignment(assignmentId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete student linkage"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Schedule & Sessions
    suspend fun getAcademicSessions(accessToken: String, schoolId: String) =
        safeApiCall { apiService.getAcademicSessions("Bearer $accessToken", schoolId) }

    suspend fun createAcademicSession(accessToken: String, request: CreateAcademicSessionRequest) =
        safeApiCall { apiService.createAcademicSession("Bearer $accessToken", request) }

    suspend fun updateAcademicSession(sessionId: String, accessToken: String, request: CreateAcademicSessionRequest) =
        safeApiCall { apiService.updateAcademicSession(sessionId, "Bearer $accessToken", request) }

    suspend fun deleteAcademicSession(sessionId: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteAcademicSession(sessionId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete session"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getTermsInSession(sessionId: String, accessToken: String, schoolId: String) =
        safeApiCall { apiService.getTermsInSession(sessionId, "Bearer $accessToken", schoolId) }

    suspend fun createTerm(accessToken: String, request: CreateTermRequest) =
        safeApiCall { apiService.createTerm("Bearer $accessToken", request) }

    suspend fun updateTerm(termId: String, accessToken: String, request: CreateTermRequest) =
        safeApiCall { apiService.updateTerm(termId, "Bearer $accessToken", request) }

    suspend fun deleteTerm(termId: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteTerm(termId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete term"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Calendar Events
    suspend fun getCalendarEvents(accessToken: String, schoolId: String) =
        safeApiCall { apiService.getCalendarEvents("Bearer $accessToken", schoolId) }

    suspend fun createCalendarEvent(accessToken: String, request: CreateCalendarEventRequest) =
        safeApiCall { apiService.createCalendarEvent("Bearer $accessToken", request) }

    suspend fun updateCalendarEvent(eventId: String, accessToken: String, request: UpdateCalendarEventRequest) =
        safeApiCall { apiService.updateCalendarEvent(eventId, "Bearer $accessToken", request) }

    suspend fun deleteCalendarEvent(eventId: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteCalendarEvent(eventId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete event"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Timetable
    suspend fun getSchoolTimetableItems(accessToken: String, schoolId: String) =
        safeApiCall { apiService.getSchoolTimetableItems("Bearer $accessToken", schoolId) }

    suspend fun createSchoolTimetableItem(accessToken: String, request: CreateSchoolTimetableRequest) =
        safeApiCall { apiService.createSchoolTimetableItem("Bearer $accessToken", request) }

    suspend fun updateSchoolTimetableItem(itemId: String, accessToken: String, request: UpdateSchoolTimetableRequest) =
        safeApiCall { apiService.updateSchoolTimetableItem(itemId, "Bearer $accessToken", request) }

    suspend fun deleteSchoolTimetableItem(itemId: String, accessToken: String, schoolId: String): Result<Unit> {
        return try {
            val response = apiService.deleteSchoolTimetableItem(itemId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete timetable item"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCurrentSchedule(schoolId: String, accessToken: String) =
        safeApiCall { apiService.getCurrentSchedule("Bearer $accessToken", schoolId) }
}
