- [ ] Create data models for school timetable in `SchoolTimetableModels.kt`
- [ ] Remove old unused `SchoolTimetable.kt`
- [ ] Add Retrofit definitions in `SchoolApiService.kt`
- [ ] Add repository methods in `SchoolRepository.kt`
- [ ] Create and register `TimetableViewModel` in Koin
- [ ] Implement Compose `TimetableScreen.kt` mimicking mockup
- [ ] Map route `"schedule/timetable"` in `SchoolApp.kt` navigation
- [ ] Verify execution and build the app

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
  - Delete verification alerts.
- **Route registration**: Connected the route `"schedule/sessions"` in [SchoolApp.kt](file:///home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/SchoolApp.kt#L309).

## Verification Results

### Backend Compile Check
Successfully checked the Rust backend using cargo:
```bash
cargo check
```
- Compilation completed successfully with 0 errors.

### Android Compile Check
Successfully compiled the Android app:
```bash
./gradlew compileDebugKotlin
```
- Compilation completed successfully with 0 errors (`BUILD SUCCESSFUL`).

## Troubleshooting & Restart Fix

### The 404 Root Cause
The Android app received a `404 Not Found` because the active Rust server process was running an old pre-compiled binary that lacked our new route.

### Database Connection Panic
Upon trying to compile and restart the Rust backend normally, the server attempted to connect to the remote Render database configured in the environment shell, which panicked due to EOF / socket timeout.

### Resolution & Verification
1. **Terminated old server process**: Killed the outdated Actix-web server instance.
2. **Started updated server on local DB**: Restarted the Rust backend by explicitly pointing to the running local database:
   ```bash
   DATABASE_URL=postgres://postgres:password@localhost:5432/myschool cargo run
   ```
3. **Verified Health Check**:
   - `GET /api/health` returns: `{"status":"ok","database":"connected","timestamp":"..."}`
4. **Verified Sessions Routing**:
   - `GET /api/auth/schedule/sessions` correctly returns `400 Bad Request (missing school_id)` rather than 404, showing routing is success.
   - `GET /api/auth/schedule/sessions?school_id=...` correctly returns `401 Unauthorized (Authentication required)`.


// MISSING LINE 73
// MISSING LINE 74
// MISSING LINE 75
// MISSING LINE 76
// MISSING LINE 77
// MISSING LINE 78
// MISSING LINE 79
// MISSING LINE 80
// MISSING LINE 81
// MISSING LINE 82
// MISSING LINE 83
// MISSING LINE 84
// MISSING LINE 85
// MISSING LINE 86
// MISSING LINE 87
// MISSING LINE 88
// MISSING LINE 89
// MISSING LINE 90
// MISSING LINE 91
// MISSING LINE 92
// MISSING LINE 93
// MISSING LINE 94
// MISSING LINE 95
// MISSING LINE 96
// MISSING LINE 97
// MISSING LINE 98
// MISSING LINE 99
// MISSING LINE 100
// MISSING LINE 101
// MISSING LINE 102
// MISSING LINE 103
// MISSING LINE 104
// MISSING LINE 105
// MISSING LINE 106
// MISSING LINE 107
// MISSING LINE 108
// MISSING LINE 109
// MISSING LINE 110
// MISSING LINE 111
// MISSING LINE 112
// MISSING LINE 113
// MISSING LINE 114
// MISSING LINE 115
// MISSING LINE 116
// MISSING LINE 117
// MISSING LINE 118
// MISSING LINE 119
// MISSING LINE 120
// MISSING LINE 121
// MISSING LINE 122
// MISSING LINE 123
// MISSING LINE 124
// MISSING LINE 125
// MISSING LINE 126
// MISSING LINE 127
// MISSING LINE 128
// MISSING LINE 129
// MISSING LINE 130
// MISSING LINE 131
// MISSING LINE 132
// MISSING LINE 133
// MISSING LINE 134
// MISSING LINE 135
// MISSING LINE 136
// MISSING LINE 137
// MISSING LINE 138
// MISSING LINE 139
// MISSING LINE 140
// MISSING LINE 141
// MISSING LINE 142
// MISSING LINE 143
// MISSING LINE 144
// MISSING LINE 145
// MISSING LINE 146
// MISSING LINE 147
// MISSING LINE 148
// MISSING LINE 149
// MISSING LINE 150
// MISSING LINE 151
// MISSING LINE 152
// MISSING LINE 153
// MISSING LINE 154
// MISSING LINE 155
// MISSING LINE 156
// MISSING LINE 157
// MISSING LINE 158
// MISSING LINE 159
// MISSING LINE 160
// MISSING LINE 161
// MISSING LINE 162
// MISSING LINE 163
// MISSING LINE 164
// MISSING LINE 165
// MISSING LINE 166
// MISSING LINE 167
// MISSING LINE 168
// MISSING LINE 169
// MISSING LINE 170
// MISSING LINE 171
// MISSING LINE 172
// MISSING LINE 173
// MISSING LINE 174
// MISSING LINE 175
// MISSING LINE 176
// MISSING LINE 177
// MISSING LINE 178
// MISSING LINE 179
// MISSING LINE 180
// MISSING LINE 181
// MISSING LINE 182
// MISSING LINE 183
// MISSING LINE 184
// MISSING LINE 185
// MISSING LINE 186
// MISSING LINE 187
// MISSING LINE 188
// MISSING LINE 189
// MISSING LINE 190
// MISSING LINE 191
// MISSING LINE 192
// MISSING LINE 193
// MISSING LINE 194
// MISSING LINE 195
// MISSING LINE 196
// MISSING LINE 197
// MISSING LINE 198
// MISSING LINE 199
// MISSING LINE 200
// MISSING LINE 201
// MISSING LINE 202
// MISSING LINE 203
// MISSING LINE 204
// MISSING LINE 205
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
}
