- [x] Rust Backend changes
    - [x] Add `get_current_schedule` handler in `schedule/mod.rs`
    - [x] Register `/api/auth/schedule/current` route in `main.rs`
- [x] Android models & repository & service modifications
    - [x] Define request/response DTOs in `School.kt`
    - [x] Declare API endpoints in `SchoolApiService.kt`
    - [x] Implement mapping methods in `SchoolRepository.kt`
- [x] Android ViewModel modifications
    - [x] Add Koin ViewModel support methods for staff operations in `StaffViewModel.kt`
- [x] Android UI modifications
    - [x] Add "Add Staff" button to header
    - [x] Implement Add/Edit Form matching the mockup design in `StaffManagementScreen.kt`
    - [x] Implement Delete Confirm Dialog and Edit button triggers in card
    - [x] Implement Full Profile dialog view
- [x] Verification
    - [x] Verify backend compilation with `cargo check`
    - [x] Verify Android compilation with `./gradlew compileDebugKotlin`

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
        @Body requ
// MISSING LINE 44
// MISSING LINE 45
// MISSING LINE 46
// MISSING LINE 47
// MISSING LINE 48
// MISSING LINE 49
// MISSING LINE 50
// MISSING LINE 51
// MISSING LINE 52
// MISSING LINE 53
// MISSING LINE 54
// MISSING LINE 55
// MISSING LINE 56
// MISSING LINE 57
// MISSING LINE 58
// MISSING LINE 59
// MISSING LINE 60
// MISSING LINE 61
// MISSING LINE 62
// MISSING LINE 63
// MISSING LINE 64
// MISSING LINE 65
// MISSING LINE 66
// MISSING LINE 67
// MISSING LINE 68
// MISSING LINE 69
    ): Response<List<SchoolSubjectResponse>>

    @POST("auth/school/school-subjects/{school_subject_id}/classes/link")
    suspend fun linkClassesToSchoolSubject(
        @Path("school_subject_id") schoolSubjectId: String,
        @Header("Authorization") authorization: String,
        @Body request: LinkClassToSubjectRequest
    ): Response<List<SchoolSubjectResponse>>

    @DELETE("auth/school/school-subjects/{school_subject_id}/classes/{class_id}/unlink")
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
    ): Response<Unit>

    @POST("auth/staff/{id}/classes/assign")
    suspend fun assignStaffClasses(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffClassesRequest
    ): Response<Unit>

    @POST("auth/staff/{id}/subjects/assign")
    suspend fun assignStaffSubjects(
        @Path("id") id: String,
        @Header("Authorization") authorization: String,
        @Body request: AssignStaffSubjectsRequest
    ): Response<Unit>

    @GET("auth/schedule/current")
    suspend fun getCurrentSchedule(
        @Header("Authorization") authorization: String,
