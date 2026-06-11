package com.haneef.school.ui.screens.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicTrackNode
import com.haneef.school.data.models.ClassNode
import com.haneef.school.data.models.CreateStudentClassInfo
import com.haneef.school.data.models.Student
import com.haneef.school.data.models.StudentClassAssignmentResponse
import com.haneef.school.data.models.StudentDetailDto
import com.haneef.school.viewmodel.SchoolUiState
import com.haneef.school.viewmodel.SchoolViewModel
import com.haneef.school.viewmodel.StudentUiState
import com.haneef.school.viewmodel.StudentViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


enum class StudentView {
    LIST,
    ADD_FORM,
    EDIT_FORM,
    FULL_PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(modifier: Modifier = Modifier) {
    val studentViewModel: StudentViewModel = koinViewModel()
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()

    val studentUiState by studentViewModel.uiState.collectAsState()
    val schoolUiState by schoolViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""
    // Find school slug dynamically from schoolViewModel or default to koin configuration
    val schoolSlug = "school"

    // Navigation and Action States
    var currentView by remember { mutableStateOf(StudentView.LIST) }
    var selectedStudentForAction by remember { mutableStateOf<Student?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var showUnlinkDialog by remember { mutableStateOf(false) }
    var classAssignmentToUnlink by remember { mutableStateOf<StudentClassAssignmentResponse?>(null) }

    // Form inputs state
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var studentIdInput by remember { mutableStateOf("") }
    var admissionNumber by remember { mutableStateOf("") }
    var admissionDate by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("MALE") }
    var currentGradeLevel by remember { mutableStateOf("") }
    var specialNeeds by remember { mutableStateOf("") }
    var transportation by remember { mutableStateOf("") }
    var academicStatus by remember { mutableStateOf("ENROLLED") }
    var hasSpecialNeeds by remember { mutableStateOf(false) }
    var passportPhotoUrl by remember { mutableStateOf("") }
    var isPassportUploading by remember { mutableStateOf(false) }
    var passportUploadError by remember { mutableStateOf<String?>(null) }

    val passportPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isPassportUploading = true
                passportUploadError = null
                val url = uploadToCloudinary(context, it)
                if (url != null) {
                    passportPhotoUrl = url
                } else {
                    passportPhotoUrl = it.toString()
                }
                isPassportUploading = false
            }
        }
    }

    // Selected Class IDs per Track ID
    val trackAssignments = remember { mutableStateMapOf<String, String>() } // TrackId -> ClassId
    var formValidationErrorMessage by remember { mutableStateOf<String?>(null) }

    // List search query local state
    var localSearchQuery by remember { mutableStateOf(studentUiState.searchQuery ?: "") }
    var trackDropdownExpanded by remember { mutableStateOf(false) }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    // Load academic hierarchy structure and initial student list
    LaunchedEffect(schoolId) {
        if (schoolId.isNotEmpty() && accessToken.isNotEmpty()) {
            schoolViewModel.getAcademicStructure(schoolId, accessToken)
            studentViewModel.loadStudents(schoolId, accessToken)
            studentViewModel.loadCurrentSchedule(schoolId, accessToken)
        }
    }

    // React to loading student details when entering EDIT_FORM
    LaunchedEffect(studentUiState.singleStudentDetail, studentUiState.singleStudentClassAssignments) {
        if (currentView == StudentView.EDIT_FORM && studentUiState.singleStudentDetail != null) {
            val detail = studentUiState.singleStudentDetail!!
            firstName = detail.firstName ?: ""
            lastName = detail.lastName ?: ""
            email = detail.email ?: ""
            phone = detail.phoneNumber ?: ""
            studentIdInput = detail.studentId
            admissionNumber = detail.admissionNumber ?: ""
            admissionDate = detail.admissionDate
            dateOfBirth = detail.dateOfBirth ?: ""
            gender = detail.gender ?: "MALE"
            currentGradeLevel = detail.currentGradeLevel ?: ""
            specialNeeds = detail.specialNeedsDescription ?: ""
            transportation = detail.transportationMethod ?: ""
            academicStatus = detail.academicStatus ?: "ENROLLED"
            hasSpecialNeeds = detail.hasSpecialNeeds
            passportPhotoUrl = detail.passportPhotoUrl ?: ""

            // Populate track class selections
            trackAssignments.clear()
            studentUiState.singleStudentClassAssignments.forEach { assignment ->
                // Look up track ID for this class
                schoolUiState.academicStructure?.tracks?.forEach { track ->
                    track.departments.forEach { dept ->
                        dept.classes.forEach { clazz ->
                            if (clazz.id == assignment.classId) {
                                trackAssignments[track.id] = clazz.id
                            }
                        }
                    }
                }
            }
        }
    }

    // Show success toast when an operation completes successfully
    LaunchedEffect(studentUiState.successMessage) {
        studentUiState.successMessage?.let { msg ->
            Toast.makeText(context, "✓ $msg", Toast.LENGTH_SHORT).show()
            studentViewModel.clearSuccessMessage()
        }
    }

    // Show error toast when a save operation fails
    LaunchedEffect(studentUiState.saveErrorMessage) {
        studentUiState.saveErrorMessage?.let { msg ->
            Toast.makeText(context, "✗ $msg", Toast.LENGTH_LONG).show()
            studentViewModel.clearSaveError()
        }
    }

    // Helper functions to navigate and initialize form states
    fun navigateToForm(student: Student?) {
        formValidationErrorMessage = null
        trackAssignments.clear()
        if (student == null) {
            // ADD FORM
            selectedStudentForAction = null
            firstName = ""
            lastName = ""
            email = ""
            phone = ""
            studentIdInput = ""
            admissionNumber = ""
            admissionDate = "2026-06-09"
            dateOfBirth = "2015-01-01"
            gender = "MALE"
            currentGradeLevel = ""
            specialNeeds = ""
            hasSpecialNeeds = false
            transportation = "School Bus"
            academicStatus = "ENROLLED"
            passportPhotoUrl = ""
            isPassportUploading = false
            passportUploadError = null
            currentView = StudentView.ADD_FORM
        } else {
            // EDIT FORM
            selectedStudentForAction = student
            studentViewModel.loadStudentDetailAndClasses(student.id, accessToken)
            // Show loading view before fields are populated in LaunchedEffect
            currentView = StudentView.EDIT_FORM
        }
    }

    fun handleSaveStudent() {
        if (firstName.isBlank()) {
            formValidationErrorMessage = "First Name is required"
            return
        }
        if (lastName.isBlank()) {
            formValidationErrorMessage = "Last Name is required"
            return
        }

        formValidationErrorMessage = null

        val selectedClassesPayload = trackAssignments.values.filter { it.isNotEmpty() }.map { classId ->
            CreateStudentClassInfo(
                classId = classId,
                sessionId = studentUiState.currentSessionId ?: "",
                termId = studentUiState.currentTermId ?: "",
                enrollmentDate = admissionDate.takeIf { it.isNotBlank() } ?: "2026-06-09"
            )
        }

        if (currentView == StudentView.ADD_FORM) {
            studentViewModel.createStudent(
                schoolId = schoolId,
                accessToken = accessToken,
                firstName = firstName,
                lastName = lastName,
                email = email.takeIf { it.isNotBlank() },
                phone = phone.takeIf { it.isNotBlank() },
                dateOfBirth = dateOfBirth,
                gender = gender,
                admissionNumber = admissionNumber,
                admissionDate = admissionDate,
                currentGradeLevel = currentGradeLevel,
                specialNeeds = if (hasSpecialNeeds) specialNeeds else "",
                transportation = transportation,
                schoolSlug = schoolSlug,
                initialClasses = selectedClassesPayload,
                academicStatus = academicStatus,
                passportPhotoUrl = passportPhotoUrl,
                hasSpecialNeeds = hasSpecialNeeds,
                onSuccess = {
                    currentView = StudentView.LIST
                }
            )
        } else if (currentView == StudentView.EDIT_FORM && selectedStudentForAction != null) {
            val original = selectedStudentForAction!!
            val detail = studentUiState.singleStudentDetail ?: return

            // Calculate additions and deletions
            val originalAssignments = studentUiState.singleStudentClassAssignments
            val assignmentsToDelete = mutableListOf<String>()
            val classesToAssign = mutableListOf<CreateStudentClassInfo>()

            // For each track, check if selection changed
            schoolUiState.academicStructure?.tracks?.forEach { track ->
                val originalAssignmentForTrack = originalAssignments.find { assignment ->
                    // Check if assignment class is in this track
                    track.departments.any { dept -> dept.classes.any { it.id == assignment.classId } }
                }
                val selectedClassId = trackAssignments[track.id]

                if (originalAssignmentForTrack == null) {
                    // New assignment
                    if (!selectedClassId.isNullOrEmpty()) {
                        classesToAssign.add(
                            CreateStudentClassInfo(
                                classId = selectedClassId,
                                sessionId = studentUiState.currentSessionId ?: "",
                                termId = studentUiState.currentTermId ?: "",
                                enrollmentDate = admissionDate.takeIf { it.isNotBlank() } ?: "2026-06-09"
                            )
                        )
                    }
                } else {
                    if (selectedClassId.isNullOrEmpty()) {
                        // Deleted assignment
                        assignmentsToDelete.add(originalAssignmentForTrack.id)
                    } else if (originalAssignmentForTrack.classId != selectedClassId) {
                        // Replaced assignment
                        assignmentsToDelete.add(originalAssignmentForTrack.id)
                        classesToAssign.add(
                            CreateStudentClassInfo(
                                classId = selectedClassId,
                                sessionId = studentUiState.currentSessionId ?: "",
                                termId = studentUiState.currentTermId ?: "",
                                enrollmentDate = admissionDate.takeIf { it.isNotBlank() } ?: "2026-06-09"
                            )
                        )
                    }
                }
            }

            val finalSpecialNeeds = if (hasSpecialNeeds) specialNeeds else ""
            val personalInfoChanged =
                firstName.trim() != (detail.firstName ?: "").trim() ||
                lastName.trim() != (detail.lastName ?: "").trim() ||
                email.trim() != (detail.email ?: "").trim() ||
                phone.trim() != (detail.phoneNumber ?: "").trim() ||
                gender != detail.gender ||
                dateOfBirth != detail.dateOfBirth ||
                admissionNumber != detail.admissionNumber ||
                admissionDate != detail.admissionDate ||
                currentGradeLevel != detail.currentGradeLevel ||
                finalSpecialNeeds != (detail.specialNeedsDescription ?: "") ||
                hasSpecialNeeds != detail.hasSpecialNeeds ||
                passportPhotoUrl != (detail.passportPhotoUrl ?: "") ||
                academicStatus != detail.academicStatus ||
                transportation != detail.transportationMethod

            val updatedDetail = detail.copy(
                firstName = firstName,
                lastName = lastName,
                email = email,
                phoneNumber = phone,
                gender = gender,
                dateOfBirth = dateOfBirth,
                admissionNumber = admissionNumber,
                admissionDate = admissionDate,
                currentGradeLevel = currentGradeLevel,
                specialNeedsDescription = finalSpecialNeeds,
                hasSpecialNeeds = hasSpecialNeeds,
                passportPhotoUrl = passportPhotoUrl,
                academicStatus = academicStatus,
                transportationMethod = transportation
            )

            if (personalInfoChanged) {
                studentViewModel.updateStudent(
                    schoolId = schoolId,
                    accessToken = accessToken,
                    studentId = original.id,
                    detailDto = updatedDetail,
                    classesToAssign = classesToAssign,
                    assignmentsToDelete = assignmentsToDelete,
                    onSuccess = {
                        currentView = StudentView.LIST
                        selectedStudentForAction = null
                    }
                )
            } else {
                studentViewModel.updateStudentClassesOnly(
                    schoolId = schoolId,
                    accessToken = accessToken,
                    studentId = original.id,
                    classesToAssign = classesToAssign,
                    assignmentsToDelete = assignmentsToDelete,
                    onSuccess = {
                        currentView = StudentView.LIST
                        selectedStudentForAction = null
                    }
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && studentToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                studentToDelete = null
            },
            title = {
                Text(
                    text = "Delete Student Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${studentToDelete!!.fullName}? This action is irreversible and will remove all class enrollments and academic records.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        studentViewModel.deleteStudent(schoolId, accessToken, studentToDelete!!.id) {
                            showDeleteConfirmDialog = false
                            studentToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        studentToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Class Assignment Unlink Dialog
    if (showUnlinkDialog && classAssignmentToUnlink != null && selectedStudentForAction != null) {
        AlertDialog(
            onDismissRequest = {
                showUnlinkDialog = false
                classAssignmentToUnlink = null
            },
            title = {
                Text(
                    text = "Unlink Class Assignment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${selectedStudentForAction!!.fullName} from the class ${classAssignmentToUnlink!!.className ?: "Class"}?",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        studentViewModel.deleteStudentClassAssignment(
                            schoolId = schoolId,
                            accessToken = accessToken,
                            studentId = selectedStudentForAction!!.id,
                            assignmentId = classAssignmentToUnlink!!.id
                        ) {
                            showUnlinkDialog = false
                            classAssignmentToUnlink = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Unlink", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUnlinkDialog = false
                        classAssignmentToUnlink = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val selectedTrack = schoolUiState.academicStructure?.tracks?.find { it.id == studentUiState.selectedTrackId }
    val selectedClass = schoolUiState.academicStructure?.tracks
        ?.flatMap { it.departments }?.flatMap { it.classes }
        ?.find { it.id == studentUiState.selectedClassId }

    when (currentView) {
        StudentView.LIST -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
            ) {
                // Header with Breadcrumbs and Add Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF034CD1)
                        )
                        Text(
                            text = " Community",
                            fontSize = 12.sp,
                            color = Color(0xFF034CD1),
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Text(
                            text = " Student Registry",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Student Registry",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Manage and filter student profiles and class placements.",
                                fontSize = 14.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Row {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { navigateToForm(null) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Student",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Add Student",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Main Scrollable Area
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    // Filter options
                    item {
                        StudentFiltersCard(
                            schoolUiState = schoolUiState,
                            studentUiState = studentUiState,
                            studentViewModel = studentViewModel,
                            schoolId = schoolId,
                            accessToken = accessToken,
                            localSearchQuery = localSearchQuery,
                            onSearchQueryChange = { localSearchQuery = it },
                            selectedTrack = selectedTrack,
                            selectedClass = selectedClass,
                            trackDropdownExpanded = trackDropdownExpanded,
                            classDropdownExpanded = classDropdownExpanded,
                            onTrackDropdownExpandedChange = { trackDropdownExpanded = it },
                            onClassDropdownExpandedChange = { classDropdownExpanded = it }
                        )
                    }

                    // Content States
                    if (studentUiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF034CD1))
                            }
                        }
                    } else if (studentUiState.errorMessage != null) {
                        item {
                            StudentErrorCard(
                                errorMessage = studentUiState.errorMessage,
                                onRetry = { studentViewModel.loadStudents(schoolId, accessToken) }
                            )
                        }
                    } else if (studentUiState.studentList.isEmpty()) {
                        item {
                            StudentEmptyStateView()
                        }
                    } else {
                        items(studentUiState.studentList) { student ->
                            StudentCard(
                                student = student,
                                academicStructure = schoolUiState.academicStructure,
                                onEditClick = { navigateToForm(student) },
                                onDeleteClick = {
                                    studentToDelete = student
                                    showDeleteConfirmDialog = true
                                },
                                onViewProfileClick = {
                                    selectedStudentForAction = student
                                    studentViewModel.loadStudentDetailAndClasses(student.id, accessToken)
                                    currentView = StudentView.FULL_PROFILE
                                }
                            )
                        }

                        // Pagination UI
                        item {
                            StudentPaginationRow(
                                studentUiState = studentUiState,
                                studentViewModel = studentViewModel,
                                schoolId = schoolId,
                                accessToken = accessToken
                            )
                        }
                    }
                }
            }
        }

        StudentView.ADD_FORM, StudentView.EDIT_FORM -> {
            val titleText = if (currentView == StudentView.ADD_FORM) "Add New Student" else "Edit Student Profile"

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
            ) {
                // Header custom navigation toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        currentView = StudentView.LIST
                        selectedStudentForAction = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = titleText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                // Check loading if detail is not populated yet in edit
                if (currentView == StudentView.EDIT_FORM && studentUiState.singleStudentDetail == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF034CD1))
                    }
                } else {
                    // Form Content
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (formValidationErrorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    Text(
                                        text = formValidationErrorMessage!!,
                                        color = Color(0xFF991B1B),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (studentUiState.saveErrorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    Text(
                                        text = studentUiState.saveErrorMessage!!,
                                        color = Color(0xFF991B1B),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Personal Information Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Personal Information",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Passport Photo", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                                .clickable { passportPickerLauncher.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (passportPhotoUrl.isNotBlank()) {
                                                Text(text = "✓", fontSize = 24.sp, color = Color(0xFF2E7D32))
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CloudUpload,
                                                    contentDescription = null,
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Button(
                                                onClick = { passportPickerLauncher.launch("image/*") },
                                                enabled = !isPassportUploading,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (isPassportUploading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = Color.White,
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Uploading...", color = Color.White, fontSize = 13.sp)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudUpload,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        if (passportPhotoUrl.isNotBlank()) "Replace Passport" else "Upload Passport",
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            if (passportUploadError != null) {
                                                Text(text = passportUploadError!!, fontSize = 11.sp, color = Color(0xFFD32F2F))
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StudentFormTextField(
                                        value = firstName,
                                        onValueChange = { firstName = it },
                                        label = "First Name",
                                        placeholder = "e.g. Chinwe",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudentFormTextField(
                                        value = lastName,
                                        onValueChange = { lastName = it },
                                        label = "Last Name",
                                        placeholder = "e.g. Eze",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "Date of Birth", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = dateOfBirth,
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("YYYY-MM-DD", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = "Select Date of Birth",
                                                    modifier = Modifier.clickable {
                                                        showDatePicker(context, dateOfBirth) { dateOfBirth = it }
                                                    }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                showDatePicker(context, dateOfBirth) { dateOfBirth = it }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF034CD1),
                                                unfocusedBorderColor = Color(0xFFE2E8F0)
                                            )
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        StudentFormDropdown(
                                            label = "Gender",
                                            selectedValue = gender,
                                            options = listOf("MALE", "FEMALE"),
                                            onValueChange = { gender = it }
                                        )
                                    }
                                }
                            }
                        }

                        // Academic Placement Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Academic & School Placement",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StudentFormTextField(
                                        value = admissionNumber,
                                        onValueChange = { admissionNumber = it },
                                        label = "Admission Number",
                                        placeholder = "e.g. ADM-2026-001",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "Admission Date", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = admissionDate,
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("YYYY-MM-DD", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = "Select Admission Date",
                                                    modifier = Modifier.clickable {
                                                        showDatePicker(context, admissionDate) { admissionDate = it }
                                                    }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                showDatePicker(context, admissionDate) { admissionDate = it }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF034CD1),
                                                unfocusedBorderColor = Color(0xFFE2E8F0)
                                            )
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        StudentFormDropdown(
                                            label = "Academic Status",
                                            selectedValue = academicStatus,
                                            options = listOf("ENROLLED", "GRADUATED", "TRANSFERRED", "EXPELLED", "SUSPENDED"),
                                            onValueChange = { academicStatus = it }
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        StudentFormDropdown(
                                            label = "Transportation Method",
                                            selectedValue = transportation,
                                            options = listOf("School Bus", "Car", "Walk", "Bicycle", "Public Transit", "Other"),
                                            onValueChange = { transportation = it }
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { hasSpecialNeeds = !hasSpecialNeeds }
                                ) {
                                    Checkbox(
                                        checked = hasSpecialNeeds,
                                        onCheckedChange = { hasSpecialNeeds = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF034CD1))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Needs Special Care",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E293B)
                                    )
                                }

                                if (hasSpecialNeeds) {
                                    StudentFormTextField(
                                        value = specialNeeds,
                                        onValueChange = { specialNeeds = it },
                                        label = "Special Needs Description",
                                        placeholder = "e.g. Allergies, Asthma...",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Class Assignments per Track Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Class Assignments (by Track)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Assign student to classes. You can assign at most one class per Track.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )

                                val tracks = schoolUiState.academicStructure?.tracks ?: emptyList()

                                if (tracks.isEmpty()) {
                                    Text(
                                        text = "No tracks or classes available in academic structure.",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    tracks.forEach { track ->
                                        val trackClasses = track.departments.flatMap { it.classes }
                                        val classNames = trackClasses.map { it.className }
                                        val classIds = trackClasses.map { it.id }

                                        // Find currently selected class name
                                        val selectedClassId = trackAssignments[track.id] ?: ""
                                        val selectedClassName = trackClasses.find { it.id == selectedClassId }?.className ?: "Not Placed"

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            var expanded by remember { mutableStateOf(false) }
                                            ExposedDropdownMenuBox(
                                                expanded = expanded,
                                                onExpandedChange = { expanded = !expanded }
                                            ) {
                                                OutlinedTextField(
                                                    value = selectedClassName,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Track: ${track.name}") },
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                        focusedBorderColor = Color(0xFF034CD1)
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .menuAnchor()
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Not Placed") },
                                                        onClick = {
                                                            trackAssignments.remove(track.id)
                                                            expanded = false
                                                        }
                                                    )
                                                    trackClasses.forEach { clazz ->
                                                        DropdownMenuItem(
                                                            text = { Text(clazz.className) },
                                                            onClick = {
                                                                trackAssignments[track.id] = clazz.id
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = { handleSaveStudent() },
                            enabled = !studentUiState.isSaving,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (studentUiState.isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = if (currentView == StudentView.ADD_FORM) "Register Student" else "Save Changes",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        StudentView.FULL_PROFILE -> {
            val student = selectedStudentForAction
            Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        currentView = StudentView.LIST
                        selectedStudentForAction = null
                        studentViewModel.clearSaveError()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Student Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                if (student != null) {
                    val profileScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(profileScroll)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Profile Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFEEF2FF))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = student.studentId,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (student.isActive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (student.isActive) "Active" else "Inactive",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (student.isActive) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF034CD1).copy(alpha = 0.1f))
                                            .border(1.5.dp, Color(0xFF034CD1).copy(alpha = 0.3f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val initials = student.fullName.split(" ")
                                            .mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                                        Text(initials, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = student.fullName,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        val displayClassName = remember(student.className, schoolUiState.academicStructure) {
                                            val rawClassName = student.className ?: ""
                                            val academicStructure = schoolUiState.academicStructure
                                            if (rawClassName.isEmpty()) {
                                                "No class assigned"
                                            } else if (academicStructure == null) {
                                                rawClassName
                                            } else {
                                                val allClassNodes = academicStructure.tracks.flatMap { track ->
                                                    track.departments.flatMap { dept ->
                                                        dept.classes.map { Pair(it.className, track.name) }
                                                    }
                                                }
                                                val sortedClasses = allClassNodes.distinctBy { it.first }.sortedByDescending { it.first.length }
                                                var res = rawClassName
                                                val replacedNames = mutableSetOf<String>()
                                                for ((className, trackName) in sortedClasses) {
                                                    if (className.isNotBlank() && res.contains(className) && !replacedNames.contains(className)) {
                                                        val replacement = "$className ($trackName)"
                                                        if (!res.contains(replacement)) {
                                                            res = res.replace(className, replacement)
                                                            replacedNames.add(className)
                                                        }
                                                    }
                                                }
                                                res
                                            }
                                        }
                                        Text(
                                            text = displayClassName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        // Details Card
                        val detail = studentUiState.singleStudentDetail
                        if (studentUiState.isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF034CD1))
                            }
                        } else if (detail != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Academic & Personal Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

                                    ProfileInfoRow(label = "Gender", value = detail.gender ?: "Not specified")
                                    ProfileInfoRow(label = "Date of Birth", value = detail.dateOfBirth ?: "Not specified")
                                    ProfileInfoRow(label = "Admission Number", value = detail.admissionNumber ?: "Not specified")
                                    ProfileInfoRow(label = "Admission Date", value = detail.admissionDate)
                                    ProfileInfoRow(label = "Current Grade", value = detail.currentGradeLevel ?: "Not specified")
                                    ProfileInfoRow(label = "Transportation", value = detail.transportationMethod ?: "Not specified")
                                    ProfileInfoRow(label = "Special Needs", value = detail.specialNeedsDescription ?: "None")
                                }
                            }
                        }

                        // Linked Class Placements Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("ACTIVE CLASS ENROLLMENTS FOR CURRENT TERM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))

                                val currentSessionAssignments = studentUiState.singleStudentClassAssignments.filter { assignment ->
                                    assignment.isActive
                                }

                                if (currentSessionAssignments.isEmpty()) {
                                    Text("No class assignments found for the current academic session and term.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                } else {
                                    currentSessionAssignments.forEach { assignment ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                val trackName = schoolUiState.academicStructure?.tracks?.find { track ->
                                                    track.departments.any { dept ->
                                                        dept.classes.any { it.id == assignment.classId }
                                                    }
                                                }?.name
                                                val classNameWithTrack = if (trackName != null && assignment.className != null) {
                                                    "${assignment.className} ($trackName)"
                                                } else {
                                                    assignment.className ?: "Class Placement"
                                                }
                                                Text(
                                                    text = classNameWithTrack,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = "Enrolled: ${assignment.enrollmentDate ?: "Unknown Date"}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                            IconButton(onClick = {
                                                classAssignmentToUnlink = assignment
                                                showUnlinkDialog = true
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.LinkOff,
                                                    contentDescription = "Unlink Class",
                                                    tint = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Parent / Guardian Card
                        if (detail != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Parent / Guardian Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    
                                    if (detail.guardianName != null) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Person, null, tint = Color(0xFF034CD1), modifier = Modifier.size(20.dp))
                                                Column {
                                                    val relationshipSuffix = if (!detail.guardianRelationship.isNullOrEmpty()) " (${detail.guardianRelationship})" else ""
                                                    Text(detail.guardianName + relationshipSuffix, fontSize = 14.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
                                                    Text("Primary Contact", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Phone, null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                                Text(detail.guardianPhone ?: "No phone number provided", fontSize = 13.sp, color = Color(0xFF334155))
                                            }

                                            if (!detail.guardianEmail.isNullOrEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Icon(Icons.Default.Email, null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                                    Text(detail.guardianEmail, fontSize = 13.sp, color = Color(0xFF334155))
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("No parent/guardian linked yet", color = Color(0xFF64748B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                Text("You can link parents to students from the Parent Management section.", color = Color(0xFF94A3B8), fontSize = 11.sp, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Action Buttons Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { navigateToForm(student) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit Student")
                                }

                                Button(
                                    onClick = {
                                        studentToDelete = student
                                        showDeleteConfirmDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = null
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-Components ─────────────────────────────────────────────────────────

@Composable
fun StudentFiltersCard(
    schoolUiState: SchoolUiState,
    studentUiState: StudentUiState,
    studentViewModel: StudentViewModel,
    schoolId: String,
    accessToken: String,
    localSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTrack: AcademicTrackNode?,
    selectedClass: ClassNode?,
    trackDropdownExpanded: Boolean,
    classDropdownExpanded: Boolean,
    onTrackDropdownExpandedChange: (Boolean) -> Unit,
    onClassDropdownExpandedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = {
                    onSearchQueryChange(it)
                    studentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = it)
                },
                placeholder = { Text("Search by student name, email or ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            studentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = null)
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF034CD1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )

            // Track & Class Dropdown selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StudentFilterDropdown(
                        label = selectedTrack?.name ?: "All Tracks",
                        icon = Icons.Default.Category,
                        onClick = { onTrackDropdownExpandedChange(true) }
                    )
                    DropdownMenu(
                        expanded = trackDropdownExpanded,
                        onDismissRequest = { onTrackDropdownExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Tracks") },
                            onClick = {
                                studentViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    trackId = null,
                                    classId = null
                                )
                                onTrackDropdownExpandedChange(false)
                            }
                        )
                        val tracks = schoolUiState.academicStructure?.tracks ?: emptyList()
                        tracks.forEach { track ->
                            DropdownMenuItem(
                                text = { Text(track.name) },
                                onClick = {
                                    studentViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        trackId = track.id,
                                        classId = null
                                    )
                                    onTrackDropdownExpandedChange(false)
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    StudentFilterDropdown(
                        label = selectedClass?.className ?: "All Classes",
                        icon = Icons.Default.Class,
                        onClick = { onClassDropdownExpandedChange(true) }
                    )
                    DropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { onClassDropdownExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Classes") },
                            onClick = {
                                studentViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    classId = null
                                )
                                onClassDropdownExpandedChange(false)
                            }
                        )

                        // If a track is selected, only show classes in that track, else show all classes
                        val classes = if (selectedTrack != null) {
                            selectedTrack.departments.flatMap { it.classes }
                        } else {
                            schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.flatMap { it.classes } ?: emptyList()
                        }

                        classes.forEach { clazz ->
                            DropdownMenuItem(
                                text = { Text(clazz.className) },
                                onClick = {
                                    studentViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        classId = clazz.id
                                    )
                                    onClassDropdownExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            }

            // Clear filter row
            if (studentUiState.selectedTrackId != null || studentUiState.selectedClassId != null || !studentUiState.searchQuery.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        studentViewModel.clearFilters(schoolId, accessToken)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.FilterListOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Filters", color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun StudentFilterDropdown(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF64748B))
    }
}

@Composable
fun StudentCard(
    student: Student,
    academicStructure: com.haneef.school.data.models.AcademicStructureResponse?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewProfileClick: () -> Unit
) {
    val context = LocalContext.current

    // Extract Academic Session from admission date (e.g. 2023-09-01 -> "2023/2024 Academic Session")
    val sessionText = remember(student.admissionDate) {
        try {
            val year = student.admissionDate.split("-").firstOrNull()?.toIntOrNull() ?: 2023
            "$year/${year + 1} Academic Session"
        } catch (e: Exception) {
            "2023/2024 Academic Session"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewProfileClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Avatar, ID pill, status, and Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Box with border and checkmark
                Box(
                    modifier = Modifier
                        .size(64.dp)
                ) {
                    // Outer ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color(0xFF034CD1).copy(alpha = 0.5f), CircleShape)
                            .padding(3.dp)
                    ) {
                        // Inner Avatar container
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFFEEF2F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = student.fullName.split(" ")
                                .mapNotNull { it.firstOrNull() }
                                .take(2)
                                .joinToString("")
                                .uppercase()
                            Text(
                                text = initials,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF034CD1)
                            )
                        }
                    }

                    // Verified Badge at bottom right
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF034CD1),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Full Name
                    Text(
                        text = student.fullName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // ID Pill
                    Box(
                        modifier = Modifier
                            .border(BorderStroke(1.dp, Color(0xFFC7D2FE)), RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEF2F6))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = student.studentId,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF312E81)
                        )
                    }

                    // Status
                    Text(
                        text = if (student.isActive) "ACTIVE" else "INACTIVE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (student.isActive) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )

                    
                }
            }

            // CURRENT PLACEMENT Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CURRENT PLACEMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Class/MeetingRoom icon
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = "Placement Icon",
                                tint = Color(0xFF034CD1),
                                modifier = Modifier.size(22.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val displayClassName = remember(student.className, academicStructure) {
                                    val rawClassName = student.className ?: ""
                                    if (rawClassName.isEmpty()) {
                                        "No class assigned"
                                    } else if (academicStructure == null) {
                                        rawClassName
                                    } else {
                                        val allClassNodes = academicStructure.tracks.flatMap { track ->
                                            track.departments.flatMap { dept ->
                                                dept.classes.map { Pair(it.className, track.name) }
                                            }
                                        }
                                        val sortedClasses = allClassNodes.distinctBy { it.first }.sortedByDescending { it.first.length }
                                        var res = rawClassName
                                        val replacedNames = mutableSetOf<String>()
                                        for ((className, trackName) in sortedClasses) {
                                            if (className.isNotBlank() && res.contains(className) && !replacedNames.contains(className)) {
                                                val replacement = "$className ($trackName)"
                                                if (!res.contains(replacement)) {
                                                    res = res.replace(className, replacement)
                                                    replacedNames.add(className)
                                                }
                                            }
                                        }
                                        res
                                    }
                                }
                                Text(
                                    text = displayClassName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3A8A)
                                )
                                Text(
                                    text = sessionText,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Assign Button
                        TextButton(
                            onClick = { onEditClick() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF034CD1)
                                )
                                Text(
                                    text = "Assign",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF034CD1)
                                )
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFF1F5F9))

            // Action Buttons Row (Edit, Delete, Link Parent, Full Record)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { onEditClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Student",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Student",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Full Record Button
                Button(
                    onClick = { onViewProfileClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Full Record",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudentPaginationRow(
    studentUiState: com.haneef.school.viewmodel.StudentUiState,
    studentViewModel: StudentViewModel,
    schoolId: String,
    accessToken: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Page ${studentUiState.currentPage} of ${studentUiState.totalPages} (${studentUiState.totalItems} items)",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    studentViewModel.loadStudents(
                        schoolId = schoolId,
                        accessToken = accessToken,
                        page = studentUiState.currentPage - 1
                    )
                },
                enabled = studentUiState.hasPrevious,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Previous", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    studentViewModel.loadStudents(
                        schoolId = schoolId,
                        accessToken = accessToken,
                        page = studentUiState.currentPage + 1
                    )
                },
                enabled = studentUiState.hasNext,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Next", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun StudentErrorCard(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                Text(
                    text = errorMessage ?: "Failed to load student registry data.",
                    color = Color(0xFF991B1B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Retry Loading", color = Color.White)
            }
        }
    }
}

@Composable
fun StudentEmptyStateView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(vertical = 48.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF94A3B8)
            )
            Text(
                text = "No Students Found",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "Try clearing filters, searching different keywords, or register a new student profile.",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StudentFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF034CD1),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF034CD1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
    }
}

private const val CLOUDINARY_CLOUD_NAME = "your_cloud_name"
private const val CLOUDINARY_UPLOAD_PRESET = "your_upload_preset"

private suspend fun uploadToCloudinary(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes()
            stream.close()
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("gif") -> "gif"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                .build()
            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload")
                .post(body)
                .build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: return@withContext null).getString("secure_url")
            } else null
        } catch (_: Exception) { null }
    }

fun showDatePicker(context: Context, initialDateStr: String, onDateSelected: (String) -> Unit) {
    val calendar = java.util.Calendar.getInstance()
    try {
        if (initialDateStr.isNotBlank()) {
            val parts = initialDateStr.split("-")
            if (parts.size == 3) {
                calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
            }
        }
    } catch (_: Exception) {}

    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH)
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    android.app.DatePickerDialog(
        context,
        { _, y, m, d ->
            val formattedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
            onDateSelected(formattedDate)
        },
        year,
        month,
        day
    ).show()
}
