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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicStructureResponse
import com.haneef.school.data.models.SchoolSubjectResponse
import com.haneef.school.data.models.CreateClassTeacherInfo
import com.haneef.school.data.models.CreateSubjectTeacherInfo
import com.haneef.school.data.models.Staff
import com.haneef.school.viewmodel.SchoolViewModel
import com.haneef.school.viewmodel.StaffViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

enum class StaffView {
    LIST,
    ADD_FORM,
    EDIT_FORM,
    FULL_PROFILE
}

enum class AssignmentType {
    CLASS_TEACHER,
    SUBJECT_TEACHER
}

data class AssignmentFormEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AssignmentType = AssignmentType.CLASS_TEACHER,
    val selectedTrackId: String? = null,
    val selectedDeptId: String? = null,
    val selectedClassId: String? = null,
    val selectedSubjectId: String? = null,
    val isNew: Boolean = true
)

fun populateAssignmentsFromStaff(
    staff: Staff,
    academicStructure: AcademicStructureResponse?,
    schoolSubjects: List<SchoolSubjectResponse>
): List<AssignmentFormEntry> {
    val list = mutableListOf<AssignmentFormEntry>()

    staff.classAssignments.forEach { ct ->
        var matchedTrackId: String? = null
        var matchedDeptId: String? = null
        academicStructure?.tracks?.forEach { track ->
            track.departments.forEach { dept ->
                dept.classes.forEach { clazz ->
                    if (clazz.id == ct.classId) {
                        matchedTrackId = track.id
                        matchedDeptId = dept.id
                    }
                }
            }
        }
        list.add(
            AssignmentFormEntry(
                id = ct.id,
                type = AssignmentType.CLASS_TEACHER,
                selectedTrackId = matchedTrackId,
                selectedDeptId = matchedDeptId,
                selectedClassId = ct.classId,
                isNew = false
            )
        )
    }

    staff.subjectAssignments.forEach { st ->
        var matchedTrackId: String? = null
        var matchedDeptId: String? = null
        academicStructure?.tracks?.forEach { track ->
            track.departments.forEach { dept ->
                dept.classes.forEach { clazz ->
                    if (clazz.id == st.classId) {
                        matchedTrackId = track.id
                        matchedDeptId = dept.id
                    }
                }
            }
        }
        list.add(
            AssignmentFormEntry(
                id = st.id,
                type = AssignmentType.SUBJECT_TEACHER,
                selectedTrackId = matchedTrackId,
                selectedDeptId = matchedDeptId,
                selectedClassId = st.classId,
                selectedSubjectId = st.subjectId,
                isNew = false
            )
        )
    }

    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(modifier: Modifier = Modifier) {
    val staffViewModel: StaffViewModel = koinViewModel()
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()

    val staffUiState by staffViewModel.uiState.collectAsState()
    val schoolUiState by schoolViewModel.uiState.collectAsState()

    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""

    // Navigation and Action States
    var currentView by remember { mutableStateOf(StaffView.LIST) }
    var selectedStaffForAction by remember { mutableStateOf<Staff?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }

    // Form inputs state
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var employeeNumber by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("Teacher") }
    var department by remember { mutableStateOf("") }
    var hireDate by remember { mutableStateOf("2026-06-08") }
    val assignments = remember { mutableStateListOf<AssignmentFormEntry>() }
    val deletedAssignments = remember { mutableStateListOf<AssignmentFormEntry>() }
    var formValidationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Popular designations for the dropdown list
    val designationOptions = listOf("Teacher", "Class Teacher", "Subject Teacher", "Principal", "Vice Principal", "Registrar", "Accountant", "Administrator")

    // List search query local state
    var localSearchQuery by remember { mutableStateOf(staffUiState.searchQuery ?: "") }

    // Load academic hierarchy structure, subjects, schedule, and initial staff list
    LaunchedEffect(schoolId) {
        if (schoolId.isNotEmpty() && accessToken.isNotEmpty()) {
            schoolViewModel.getAcademicStructure(schoolId, accessToken)
            schoolViewModel.getSchoolSubjects(schoolId, accessToken)
            staffViewModel.loadStaff(schoolId, accessToken)
            staffViewModel.loadCurrentSchedule(schoolId, accessToken)
        }
    }

    // Helper functions to navigate and initialize form states
    fun navigateToForm(staff: Staff?) {
        formValidationErrorMessage = null
        deletedAssignments.clear()
        if (staff == null) {
            // ADD FORM
            selectedStaffForAction = null
            firstName = ""
            lastName = ""
            email = ""
            phone = ""
            employeeNumber = ""
            designation = "Teacher"
            department = ""
            hireDate = "2026-06-08"
            assignments.clear()
            currentView = StaffView.ADD_FORM
        } else {
            // EDIT FORM
            selectedStaffForAction = staff
            val nameParts = staff.fullName.trim().split(" ")
            firstName = nameParts.firstOrNull() ?: ""
            lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""
            email = staff.email
            phone = staff.phoneNumber
            employeeNumber = staff.staffId
            designation = staff.position
            department = staff.department
            hireDate = staff.hireDate
            
            assignments.clear()
            assignments.addAll(
                populateAssignmentsFromStaff(
                    staff,
                    schoolUiState.academicStructure,
                    schoolUiState.schoolSubjects
                )
            )
            currentView = StaffView.EDIT_FORM
        }
    }

    fun handleSaveStaff() {
        if (firstName.isBlank()) {
            formValidationErrorMessage = "First Name is required"
            return
        }
        if (lastName.isBlank()) {
            formValidationErrorMessage = "Last Name is required"
            return
        }
        if (email.isBlank()) {
            formValidationErrorMessage = "Email is required"
            return
        }
        
        // Validate assignments
        for (i in assignments.indices) {
            val entry = assignments[i]
            if (entry.selectedClassId.isNullOrEmpty()) {
                formValidationErrorMessage = "Assignment #${i + 1} has no Class selected"
                return
            }
            if (entry.type == AssignmentType.SUBJECT_TEACHER && entry.selectedSubjectId.isNullOrEmpty()) {
                formValidationErrorMessage = "Assignment #${i + 1} is a Subject Teacher but has no Subject selected"
                return
            }
        }

        formValidationErrorMessage = null

        val classesPayload = assignments.filter { it.type == AssignmentType.CLASS_TEACHER }.map {
            CreateClassTeacherInfo(
                classId = it.selectedClassId!!,
                sessionId = staffUiState.currentSessionId ?: "",
                termId = staffUiState.currentTermId ?: ""
            )
        }

        val subjectsPayload = assignments.filter { it.type == AssignmentType.SUBJECT_TEACHER }.map {
            CreateSubjectTeacherInfo(
                subjectId = it.selectedSubjectId!!,
                classId = it.selectedClassId!!,
                sessionId = staffUiState.currentSessionId ?: "",
                termId = staffUiState.currentTermId ?: ""
            )
        }

        if (currentView == StaffView.ADD_FORM) {
            staffViewModel.createStaff(
                schoolId = schoolId,
                accessToken = accessToken,
                email = email,
                firstName = firstName,
                lastName = lastName,
                phone = phone.takeIf { it.isNotBlank() },
                designation = designation,
                hireDate = hireDate,
                department = department.takeIf { it.isNotBlank() },
                employeeNumber = employeeNumber.takeIf { it.isNotBlank() },
                classes = classesPayload,
                subjects = subjectsPayload,
                onSuccess = {
                    currentView = StaffView.LIST
                }
            )
        } else if (currentView == StaffView.EDIT_FORM && selectedStaffForAction != null) {
            val original = selectedStaffForAction!!
            val originalNameParts = original.fullName.trim().split(" ")
            val originalFirstName = originalNameParts.firstOrNull() ?: ""
            val originalLastName = if (originalNameParts.size > 1) originalNameParts.drop(1).joinToString(" ") else ""

            val personalInfoChanged =
                firstName.trim() != originalFirstName.trim() ||
                lastName.trim() != originalLastName.trim() ||
                email.trim() != original.email.trim() ||
                phone.trim() != original.phoneNumber.trim() ||
                designation.trim() != original.position.trim() ||
                department.trim() != original.department.trim() ||
                hireDate.trim() != original.hireDate.trim()

            if (personalInfoChanged) {
                // Full update: PUT staff details + assignment endpoints
                val updatedStaff = original.copy(
                    fullName = "$firstName $lastName",
                    email = email,
                    phoneNumber = phone,
                    position = designation,
                    department = department,
                    hireDate = hireDate
                )
                staffViewModel.updateStaff(
                    schoolId = schoolId,
                    accessToken = accessToken,
                    staffId = original.id,
                    staff = updatedStaff,
                    classes = classesPayload,
                    subjects = subjectsPayload,
                    deletedAssignments = deletedAssignments,
                    onSuccess = {
                        currentView = StaffView.LIST
                        selectedStaffForAction = null
                    }
                )
            } else {
                // Assignments-only update: skip PUT, use dedicated endpoints only
                staffViewModel.updateAssignmentsOnly(
                    schoolId = schoolId,
                    accessToken = accessToken,
                    staffId = original.id,
                    classes = classesPayload,
                    subjects = subjectsPayload,
                    deletedAssignments = deletedAssignments,
                    onSuccess = {
                        currentView = StaffView.LIST
                        selectedStaffForAction = null
                    }
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && staffToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                staffToDelete = null
            },
            title = {
                Text(
                    text = "Delete Staff Member",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${staffToDelete!!.fullName}? This action cannot be undone and will remove all their active class and subject assignments.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        staffViewModel.deleteStaff(schoolId, accessToken, staffToDelete!!.id) {
                            showDeleteConfirmDialog = false
                            staffToDelete = null
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
                        staffToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    when (currentView) {
        StaffView.LIST -> {
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
                            text = " Staff Directory",
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
                                text = "Staff Directory",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Manage and filter school staff, track assignments, and view profiles.",
                                fontSize = 14.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Row{
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
                                    contentDescription = "Add Staff",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Add Staff",
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
                        FiltersCard(
                            schoolUiState = schoolUiState,
                            staffUiState = staffUiState,
                            staffViewModel = staffViewModel,
                            schoolId = schoolId,
                            accessToken = accessToken,
                            localSearchQuery = localSearchQuery,
                            onSearchQueryChange = { localSearchQuery = it },
                            designationOptions = designationOptions
                        )
                    }

                    // Content States
                    if (staffUiState.isLoading) {
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
                    } else if (staffUiState.errorMessage != null) {
                        item {
                            ErrorStateCard(
                                errorMessage = staffUiState.errorMessage,
                                onRetry = { staffViewModel.loadStaff(schoolId, accessToken) }
                            )
                        }
                    } else if (staffUiState.staffList.isEmpty()) {
                        item {
                            EmptyStateView()
                        }
                    } else {
                        items(staffUiState.staffList) { staff ->
                            StaffCard(
                                staff = staff,
                                onEditClick = { navigateToForm(staff) },
                                onDeleteClick = {
                                    staffToDelete = staff
                                    showDeleteConfirmDialog = true
                                },
                                onViewProfileClick = {
                                    selectedStaffForAction = staff
                                    currentView = StaffView.FULL_PROFILE
                                }
                            )
                        }

                        // Pagination UI
                        item {
                            PaginationRow(
                                staffUiState = staffUiState,
                                staffViewModel = staffViewModel,
                                schoolId = schoolId,
                                accessToken = accessToken
                            )
                        }
                    }
                }
            }
        }

        StaffView.ADD_FORM, StaffView.EDIT_FORM -> {
            val titleText = if (currentView == StaffView.ADD_FORM) "Add New Staff" else "Edit Staff Profile"
            
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
                        currentView = StaffView.LIST
                        selectedStaffForAction = null
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

                    if (staffUiState.saveErrorMessage != null) {
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
                                    text = staffUiState.saveErrorMessage!!,
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

                            // Uploader avatar placeholder
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                                    .border(2.dp, Color(0xFFE2E8F0), CircleShape)
                                    .clickable { /* Photo Picker placeholder */ }
                                    .align(Alignment.CenterHorizontally),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("Upload Photo", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FormTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = "First Name",
                                    placeholder = "e.g. John",
                                    modifier = Modifier.weight(1f)
                                )
                                FormTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = "Last Name",
                                    placeholder = "e.g. Doe",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FormTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = "Email Address",
                                    placeholder = "e.g. john.doe@school.com",
                                    modifier = Modifier.weight(1f)
                                )
                                FormTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = "Phone Number",
                                    placeholder = "e.g. +123456789",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FormTextField(
                                    value = employeeNumber,
                                    onValueChange = { employeeNumber = it },
                                    label = "Staff ID",
                                    placeholder = "e.g. STF001",
                                    modifier = Modifier.weight(1f)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    FormDropdown(
                                        label = "Designation",
                                        selectedValue = designation,
                                        options = designationOptions,
                                        onValueChange = { designation = it }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Load departments from structure
                                val structuralDepts = schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.map { it.name }?.distinct() ?: emptyList()
                                val deptOptions = structuralDepts.ifEmpty { listOf("Academic", "Science", "Arts", "Commercial", "Administration") }
                                Box(modifier = Modifier.weight(1f)) {
                                    FormDropdown(
                                        label = "Department",
                                        selectedValue = department,
                                        options = deptOptions,
                                        onValueChange = { department = it }
                                    )
                                }
                                FormTextField(
                                    value = hireDate,
                                    onValueChange = { hireDate = it },
                                    label = "Hire Date",
                                    placeholder = "YYYY-MM-DD",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Class & Subject Assignments Card
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Class & Subject Assignments",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Link assignments to active academic schedule.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            if (assignments.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No active class/subject assignments selected.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    assignments.forEachIndexed { index, entry ->
                                        AssignmentRow(
                                            index = index,
                                            entry = entry,
                                            schoolUiState = schoolUiState,
                                            onRemove = {
                                                val removed = assignments.removeAt(index)
                                                if (!removed.isNew) {
                                                    deletedAssignments.add(removed)
                                                }
                                            },
                                            onUpdate = { updatedEntry ->
                                                assignments[index] = updatedEntry
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    assignments.add(AssignmentFormEntry(type = AssignmentType.CLASS_TEACHER))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF034CD1))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Add New Assignment Row", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Save / Submit Button
                    Button(
                        onClick = { handleSaveStaff() },
                        enabled = !staffUiState.isSaving,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (staffUiState.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (currentView == StaffView.ADD_FORM) "Create Staff Member" else "Save Changes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        StaffView.FULL_PROFILE -> {
            val staff = selectedStaffForAction
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
            ) {
                if (staff != null) {
                    // Header back toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .shadow(1.dp, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            currentView = StaffView.LIST; selectedStaffForAction = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Staff Profile Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    val profileScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(profileScrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Profile summary box (matches mockup card look)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF034CD1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val initials = staff.fullName.split(" ")
                                            .mapNotNull { it.firstOrNull() }
                                            .take(2)
                                            .joinToString("")
                                            .uppercase()
                                        Text(
                                            text = initials,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF034CD1))
                                            .border(2.dp, Color.White, CircleShape)
                                            .align(Alignment.BottomEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Verified",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = staff.fullName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = staff.position,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF034CD1),
                                    textAlign = TextAlign.Center
                                )

                                if (staff.department.isNotEmpty()) {
                                    Text(
                                        text = staff.department,
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEDE9FE))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AssignmentInd,
                                            contentDescription = null,
                                            tint = Color(0xFF4338CA),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "ID: ${staff.staffId}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }
                                }
                            }
                        }

                        // Detailed Personal Information Card
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
                                    text = "Detailed Profile",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                ProfileFieldRow(
                                    icon = Icons.Default.MailOutline,
                                    label = "Email Address",
                                    value = staff.email
                                )

                                ProfileFieldRow(
                                    icon = Icons.Default.Phone,
                                    label = "Phone Number",
                                    value = staff.phoneNumber.ifEmpty { "Not Provided" }
                                )

                                ProfileFieldRow(
                                    icon = Icons.Default.Business,
                                    label = "Department",
                                    value = staff.department.ifEmpty { "Not Assigned" }
                                )

                                ProfileFieldRow(
                                    icon = Icons.Default.CalendarToday,
                                    label = "Hire Date",
                                    value = staff.hireDate
                                )

                                ProfileFieldRow(
                                    icon = Icons.Default.Info,
                                    label = "Employment Status",
                                    value = if (staff.isActive) "Active" else "Inactive"
                                )
                            }
                        }

                        // Class & Subject Assignments Detail Card
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
                                    text = "Current Class & Subject Assignments",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                // Class teacher assignments
                                if (!staff.classTeacherClassName.isNullOrEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Class Teacher Assignments",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFEFF6FF))
                                                .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "Class Teacher of ${staff.classTeacherClassName}",
                                                    color = Color(0xFF1D4ED8),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                // Subject teacher assignments
                                if (!staff.subjectTeacherSubjects.isNullOrEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Subject Teacher Assignments",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        val subjectsList = staff.subjectTeacherSubjects.split(",").map { it.trim() }
                                        subjectsList.forEach { sub ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFECFDF5))
                                                    .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = sub,
                                                        color = Color(0xFF047857),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (staff.classTeacherClassName.isNullOrEmpty() && staff.subjectTeacherSubjects.isNullOrEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No active classes or subjects assigned to this staff.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileFieldRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun FiltersCard(
    schoolUiState: com.haneef.school.viewmodel.SchoolUiState,
    staffUiState: com.haneef.school.viewmodel.StaffUiState,
    staffViewModel: StaffViewModel,
    schoolId: String,
    accessToken: String,
    localSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    designationOptions: List<String>
) {
    var trackDropdownExpanded by remember { mutableStateOf(false) }
    var deptDropdownExpanded by remember { mutableStateOf(false) }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var designationDropdownExpanded by remember { mutableStateOf(false) }

    val selectedTrack = schoolUiState.academicStructure?.tracks?.find { it.id == staffUiState.selectedTrackId }
    val selectedDept = selectedTrack?.departments?.find { it.id == staffUiState.selectedDeptId }
        ?: schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.find { it.id == staffUiState.selectedDeptId }
    val selectedClass = selectedDept?.classes?.find { it.id == staffUiState.selectedClassId }
        ?: schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.flatMap { it.classes }?.find { it.id == staffUiState.selectedClassId }

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
                    staffViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = it)
                },
                placeholder = { Text("Search by name, email or staff ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            staffViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = null)
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

            // Filters Row 1: Track & Department
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Track Filter
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdown(
                        label = selectedTrack?.name ?: "All Tracks",
                        icon = Icons.Default.Category,
                        onClick = { trackDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = trackDropdownExpanded,
                        onDismissRequest = { trackDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Tracks") },
                            onClick = {
                                trackDropdownExpanded = false
                                staffViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    trackId = null,
                                    departmentId = null,
                                    classId = null
                                )
                            }
                        )
                        schoolUiState.academicStructure?.tracks?.forEach { track ->
                            DropdownMenuItem(
                                text = { Text(track.name) },
                                onClick = {
                                    trackDropdownExpanded = false
                                    staffViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        trackId = track.id,
                                        departmentId = null,
                                        classId = null
                                    )
                                }
                            )
                        }
                    }
                }

                // Department Filter
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdown(
                        label = selectedDept?.name ?: "All Departments",
                        icon = Icons.Default.Business,
                        onClick = { deptDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = deptDropdownExpanded,
                        onDismissRequest = { deptDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Departments") },
                            onClick = {
                                deptDropdownExpanded = false
                                staffViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    departmentId = null,
                                    classId = null
                                )
                            }
                        )
                        val deptsToShow = if (selectedTrack != null) {
                            selectedTrack.departments
                        } else {
                            schoolUiState.academicStructure?.tracks?.flatMap { it.departments } ?: emptyList()
                        }
                        deptsToShow.forEach { dept ->
                            DropdownMenuItem(
                                text = { Text(dept.name) },
                                onClick = {
                                    deptDropdownExpanded = false
                                    staffViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        departmentId = dept.id,
                                        classId = null
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Filters Row 2: Class & Designation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Class Filter
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdown(
                        label = selectedClass?.className ?: "All Classes",
                        icon = Icons.Default.School,
                        onClick = { classDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { classDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Classes") },
                            onClick = {
                                classDropdownExpanded = false
                                staffViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    classId = null
                                )
                            }
                        )
                        val classesToShow = if (selectedDept != null) {
                            selectedDept.classes
                        } else if (selectedTrack != null) {
                            selectedTrack.departments.flatMap { it.classes }
                        } else {
                            schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.flatMap { it.classes } ?: emptyList()
                        }
                        classesToShow.forEach { clazz ->
                            DropdownMenuItem(
                                text = { Text(clazz.className) },
                                onClick = {
                                    classDropdownExpanded = false
                                    staffViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        classId = clazz.id
                                    )
                                }
                            )
                        }
                    }
                }

                // Designation Filter
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdown(
                        label = staffUiState.selectedDesignation ?: "All Designations",
                        icon = Icons.Default.WorkOutline,
                        onClick = { designationDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = designationDropdownExpanded,
                        onDismissRequest = { designationDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Designations") },
                            onClick = {
                                designationDropdownExpanded = false
                                staffViewModel.updateFilters(
                                    schoolId = schoolId,
                                    accessToken = accessToken,
                                    designation = null
                                )
                            }
                        )
                        designationOptions.forEach { desig ->
                            DropdownMenuItem(
                                text = { Text(desig) },
                                onClick = {
                                    designationDropdownExpanded = false
                                    staffViewModel.updateFilters(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        designation = desig
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Reset Options chip
            val hasFilters = staffUiState.searchQuery != null ||
                    staffUiState.selectedTrackId != null ||
                    staffUiState.selectedDeptId != null ||
                    staffUiState.selectedClassId != null ||
                    staffUiState.selectedDesignation != null

            if (hasFilters) {
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        staffViewModel.clearFilters(schoolId, accessToken)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.FilterListOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All Filters")
                }
            }
        }
    }
}

@Composable
fun ErrorStateCard(errorMessage: String?, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFEE2E2))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Error, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(errorMessage ?: "Unknown error occurred.", color = Color(0xFF991B1B), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
            Spacer(Modifier.height(16.dp))
            Text("No staff members found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            Text("Try adjusting your search query or filters.", fontSize = 14.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun PaginationRow(
    staffUiState: com.haneef.school.viewmodel.StaffUiState,
    staffViewModel: StaffViewModel,
    schoolId: String,
    accessToken: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Page ${staffUiState.currentPage} of ${staffUiState.totalPages} (${staffUiState.totalItems} items)",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (staffUiState.hasPrevious) {
                        staffViewModel.loadStaff(schoolId, accessToken, page = staffUiState.currentPage - 1)
                    }
                },
                enabled = staffUiState.hasPrevious,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF034CD1))
            ) {
                Text("Previous")
            }

            Button(
                onClick = {
                    if (staffUiState.hasNext) {
                        staffViewModel.loadStaff(schoolId, accessToken, page = staffUiState.currentPage + 1)
                    }
                },
                enabled = staffUiState.hasNext,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun AssignmentRow(
    index: Int,
    entry: AssignmentFormEntry,
    schoolUiState: com.haneef.school.viewmodel.SchoolUiState,
    onRemove: () -> Unit,
    onUpdate: (AssignmentFormEntry) -> Unit
) {
    val tracks = schoolUiState.academicStructure?.tracks ?: emptyList()
    val schoolSubjects = schoolUiState.schoolSubjects
    val isEditable = entry.isNew

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assignment Row #${index + 1}${if (!isEditable) " (Existing)" else ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Row",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Row 1: Assignment Type dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                FormDropdown(
                    label = "Assignment Role Type",
                    selectedValue = if (entry.type == AssignmentType.CLASS_TEACHER) "Class Teacher" else "Subject Teacher",
                    options = listOf("Class Teacher", "Subject Teacher"),
                    enabled = isEditable,
                    onValueChange = { role ->
                        val nextType = if (role == "Class Teacher") AssignmentType.CLASS_TEACHER else AssignmentType.SUBJECT_TEACHER
                        onUpdate(entry.copy(type = nextType, selectedSubjectId = null))
                    }
                )
            }

            // Row 2: Track & Department Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val trackName = tracks.find { it.id == entry.selectedTrackId }?.name ?: ""
                    FormDropdown(
                        label = "Track",
                        selectedValue = trackName,
                        options = tracks.map { it.name },
                        enabled = isEditable,
                        onValueChange = { name ->
                            val track = tracks.find { it.name == name }
                            onUpdate(
                                entry.copy(
                                    selectedTrackId = track?.id,
                                    selectedDeptId = null,
                                    selectedClassId = null
                                )
                            )
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    val activeTrack = tracks.find { it.id == entry.selectedTrackId }
                    val depts = activeTrack?.departments ?: emptyList()
                    val deptName = depts.find { it.id == entry.selectedDeptId }?.name ?: ""
                    FormDropdown(
                        label = "Department",
                        selectedValue = deptName,
                        options = depts.map { it.name },
                        enabled = isEditable,
                        onValueChange = { name ->
                            val dept = depts.find { it.name == name }
                            onUpdate(
                                entry.copy(
                                    selectedDeptId = dept?.id,
                                    selectedClassId = null
                                )
                            )
                        }
                    )
                }
            }

            // Row 3: Class & Subject Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val activeTrack = tracks.find { it.id == entry.selectedTrackId }
                    val depts = activeTrack?.departments ?: emptyList()
                    val activeDept = depts.find { it.id == entry.selectedDeptId }
                    val classes = activeDept?.classes ?: emptyList()
                    val className = classes.find { it.id == entry.selectedClassId }?.className ?: ""
                    FormDropdown(
                        label = "Assigned Class",
                        selectedValue = className,
                        options = classes.map { it.className },
                        enabled = isEditable,
                        onValueChange = { name ->
                            val clazz = classes.find { it.className == name }
                            onUpdate(entry.copy(selectedClassId = clazz?.id))
                        }
                    )
                }

                if (entry.type == AssignmentType.SUBJECT_TEACHER) {
                    Box(modifier = Modifier.weight(1f)) {
                        val subName = schoolSubjects.find { it.subjectId == entry.selectedSubjectId }?.name ?: ""
                        FormDropdown(
                            label = "Assigned Subject",
                            selectedValue = subName,
                            options = schoolSubjects.map { it.name },
                            enabled = isEditable,
                            onValueChange = { name ->
                                val sub = schoolSubjects.find { it.name == name }
                                onUpdate(entry.copy(selectedSubjectId = sub?.subjectId))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaffCard(
    staff: Staff,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper Row: Avatar Centered-Left + ID Badge Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with Verification Tick overlay
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF034CD1)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = staff.fullName.split(" ")
                            .mapNotNull { it.firstOrNull() }
                            .take(2)
                            .joinToString("")
                            .uppercase()
                        Text(
                            text = initials,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF034CD1))
                            .border(2.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Purple/Lavender ID Badge Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEDE9FE))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentInd,
                            contentDescription = null,
                            tint = Color(0xFF4338CA),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = staff.staffId,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4338CA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full Name Title (Centered)
            Text(
                text = staff.fullName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Email Address (Centered Row)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = staff.email,
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Department / Designation Label
            if (staff.department.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${staff.position} • ${staff.department}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Class Teacher Assignment Tag
            if (!staff.classTeacherClassName.isNullOrEmpty()) {
                val classes = staff.classTeacherClassName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (classes.isNotEmpty()) {
                    val combinedClasses = classes.joinToString(", ")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Class Teacher: $combinedClasses",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1D4ED8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Subject Teacher Assignment Tag
            if (!staff.subjectTeacherSubjects.isNullOrEmpty()) {
                val subjects = staff.subjectTeacherSubjects.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (subjects.isNotEmpty()) {
                    val combinedSubjects = subjects.joinToString(", ")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFECFDF5))
                            .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subject Teacher: $combinedSubjects",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF047857),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Lower Action Row: Edit, Delete, Full Profile buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Edit Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable(onClick = onEditClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF2F2))
                            .clickable(onClick = onDeleteClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Staff",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Full Profile Button
                Button(
                    onClick = onViewProfileClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Full Profile",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDropdown(label: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
        }
    }
}

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    readOnly: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                readOnly = readOnly,
                textStyle = TextStyle(
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FormDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) Color(0xFFF1F5F9) else Color(0xFFF8FAFC))
                .border(
                    width = 1.dp,
                    color = if (enabled) Color.Transparent else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(10.dp)
                )
                .then(
                    if (enabled) Modifier.clickable { expanded = true } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedValue.ifEmpty { "Select option" },
                    color = if (selectedValue.isEmpty()) Color(0xFF94A3B8) else if (enabled) Color(0xFF1E293B) else Color(0xFF64748B),
                    fontSize = 14.sp
                )
                if (enabled) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }
            }
            if (enabled) {
                DropdownMenu(
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
}
