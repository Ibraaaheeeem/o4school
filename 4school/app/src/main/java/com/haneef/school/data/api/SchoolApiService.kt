package com.haneef.school.data.api

import com.haneef.school.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface SchoolApiService {
    
    @PUT("auth/school/schools/{school_id}")
    suspend fun updateSchoolData(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String,
        @Body request: SchoolData
    ): Response<SchoolData>

    @GET("auth/school/{school_id}")
    suspend fun getCurrentSchoolData(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String
    ): Response<SchoolData>

    @GET("auth/school/{school_id}/academic-structure")
    suspend fun getAcademicStructure(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String
    ): Response<AcademicStructureResponse>

    @POST("auth/school/tracks")
    suspend fun createTrack(
        @Header("Authorization") authorization: String,
        @Body request: CreateEducationTrackRequest
    ): Response<AcademicTrackNode>

    @POST("auth/school/departments")
    suspend fun createDepartment(
        @Header("Authorization") authorization: String,
        @Body request: CreateDepartmentRequest
    ): Response<DepartmentNode>

    @POST("auth/school/classes")
    suspend fun createClass(
        @Header("Authorization") authorization: String,
        @Body request: CreateClassRequest
    ): Response<ClassNode>

    @POST("auth/school/default-structure/initialize")
    suspend fun initializeDefaultStructure(
        @Header("Authorization") authorization: String,
        @Body request: InitializeDefaultStructureRequest
    ): Response<InitializeDefaultStructureResponse>

    @GET("auth/school/subjects")
    suspend fun getAllSubjects(
        @Header("Authorization") authorization: String
    ): Response<List<SubjectNode>>

    @GET("auth/school/{school_id}/subjects")
    suspend fun getSchoolSubjects(
        @Path("school_id") schoolId: String,
        @Header("Authorization") authorization: String,
        @Query("class_name") className: String? = null,
        @Query("department_id") departmentId: String? = null,
        @Query("track_id") trackId: String? = null
    ): Response<List<SchoolSubjectResponse>>

    @POST("auth/school/school-subjects")
    suspend fun saveSchoolSubjects(
        @Header("Authorization") authorization: String,
        @Body request: SaveSchoolSubjectsRequest
    ): Response<List<SchoolSubjectResponse>>

    @POST("auth/school/school-subjects/{school_subject_id}/link-classes")
    suspend fun linkClassesToSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Header("Authorization") authorization: String,
        @Body request: LinkClassToSubjectRequest
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}/unlink-class/{class_id}")
    suspend fun unlinkClassFromSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Path("class_id") classId: String,
        @Header("Authorization") authorization: String
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}")
    suspend fun deactivateSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Header("Authorization") authorization: String
    ): Response<List<SchoolSubjectResponse>>

    // Students
    @GET("auth/students")
    suspend fun getStudents(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("track_id") trackId: String? = null,
        @Query("class_id") classId: String? = null
    ): Response<PaginatedResponse<Student>>

    @POST("auth/students/with-user")
    suspend fun createStudentWithUser(
        @Header("Authorization") authorization: String,
        @Body request: CreateStudentRequest
    ): Response<Student>

    @GET("auth/students/{id}")
    suspend fun getStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<StudentDetailDto>

    @PUT("auth/students/{id}")
    suspend fun updateStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: StudentDetailDto
    ): Response<StudentDetailDto>

    @DELETE("auth/students/{id}")
    suspend fun deleteStudent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/students/{id}/classes")
    suspend fun getStudentClassAssignments(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<List<StudentClassAssignmentResponse>>

    @POST("auth/students/{id}/classes/assign")
    suspend fun assignStudentClasses(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStudentClassesRequest
    ): Response<okhttp3.ResponseBody>

    @DELETE("auth/students/classes/assignments/{assignment_id}")
    suspend fun deleteStudentClassAssignment(
        @Path("assignment_id") assignmentId: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    // Staff
    @GET("auth/staff")
    suspend fun getStaff(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("track_id") trackId: String? = null,
        @Query("department_id") departmentId: String? = null,
        @Query("class_id") classId: String? = null,
        @Query("designation") designation: String? = null
    ): Response<PaginatedResponse<Staff>>

    @POST("auth/staff/with-user")
    suspend fun createStaffWithUser(
        @Header("Authorization") authorization: String,
        @Body request: CreateStaffWithUserRequest
    ): Response<Staff>

    @PUT("auth/staff/{id}")
    suspend fun updateStaff(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: Staff
    ): Response<Staff>

    @DELETE("auth/staff/{id}")
    suspend fun deleteStaff(
        @Path("id") id: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    @POST("auth/staff/{id}/classes/assign")
    suspend fun assignStaffClasses(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffClassesRequest
    ): Response<okhttp3.ResponseBody>

    @POST("auth/staff/{id}/subjects/assign")
    suspend fun assignStaffSubjects(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffSubjectsRequest
    ): Response<okhttp3.ResponseBody>

    @DELETE("auth/staff/classes/assignments/{assignment_id}")
    suspend fun deleteStaffClassAssignment(
        @Path("assignment_id") assignmentId: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    @DELETE("auth/staff/subjects/assignments/{assignment_id}")
    suspend fun deleteStaffSubjectAssignment(
        @Path("assignment_id") assignmentId: String,
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    // Parents
    @GET("auth/parents")
    suspend fun getParents(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("track_id") trackId: String? = null,
        @Query("class_id") classId: String? = null
    ): Response<PaginatedResponse<ParentListResponse>>

    @POST("auth/parents/with-user")
    suspend fun createParentWithUser(
        @Header("Authorization") authorization: String,
        @Body request: CreateParentWithUserRequest
    ): Response<ParentListResponse>

    @PUT("auth/parents/{id}")
    suspend fun updateParent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateParentRequest
    ): Response<ParentListResponse>

    @DELETE("auth/parents/{id}")
    suspend fun deleteParent(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @POST("auth/parents/{id}/students/assign")
    suspend fun assignParentStudents(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignParentStudentsRequest
    ): Response<okhttp3.ResponseBody>

    @DELETE("auth/parents/students/assignments/{assignment_id}")
    suspend fun deleteParentStudentAssignment(
        @Path("assignment_id") assignmentId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/sessions")
    suspend fun getAcademicSessions(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<List<AcademicSession>>

    @POST("auth/schedule/sessions")
    suspend fun createAcademicSession(
        @Header("Authorization") authorization: String,
        @Body request: CreateAcademicSessionRequest
    ): Response<AcademicSession>

    @PUT("auth/schedule/sessions/{id}")
    suspend fun updateAcademicSession(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body request: CreateAcademicSessionRequest
    ): Response<AcademicSession>

    @DELETE("auth/schedule/sessions/{id}")
    suspend fun deleteAcademicSession(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/sessions/{id}/terms")
    suspend fun getTermsInSession(
        @Path("id") sessionId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<List<Term>>

    @POST("auth/schedule/terms")
    suspend fun createTerm(
        @Header("Authorization") authorization: String,
        @Body request: CreateTermRequest
    ): Response<Term>

    @PUT("auth/schedule/terms/{id}")
    suspend fun updateTerm(
        @Path("id") termId: String,
        @Header("Authorization") authorization: String,
        @Body request: CreateTermRequest
    ): Response<Term>

    @DELETE("auth/schedule/terms/{id}")
    suspend fun deleteTerm(
        @Path("id") termId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/calendar-events")
    suspend fun getCalendarEvents(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<List<SchoolCalendar>>

    @POST("auth/schedule/calendar-events")
    suspend fun createCalendarEvent(
        @Header("Authorization") authorization: String,
        @Body request: CreateCalendarEventRequest
    ): Response<SchoolCalendar>

    @PUT("auth/schedule/calendar-events/{id}")
    suspend fun updateCalendarEvent(
        @Path("id") eventId: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateCalendarEventRequest
    ): Response<SchoolCalendar>

    @DELETE("auth/schedule/calendar-events/{id}")
    suspend fun deleteCalendarEvent(
        @Path("id") eventId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/school-timetable-items")
    suspend fun getSchoolTimetableItems(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<List<SchoolTimetable>>

    @POST("auth/schedule/school-timetable-items")
    suspend fun createSchoolTimetableItem(
        @Header("Authorization") authorization: String,
        @Body request: CreateSchoolTimetableRequest
    ): Response<List<SchoolTimetable>>

    @PUT("auth/schedule/school-timetable-items/{id}")
    suspend fun updateSchoolTimetableItem(
        @Path("id") itemId: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateSchoolTimetableRequest
    ): Response<SchoolTimetable>

    @DELETE("auth/schedule/school-timetable-items/{id}")
    suspend fun deleteSchoolTimetableItem(
        @Path("id") itemId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/current")
    suspend fun getCurrentSchedule(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<CurrentScheduleResponse>
}
