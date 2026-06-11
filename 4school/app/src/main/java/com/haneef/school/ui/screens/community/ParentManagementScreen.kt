package com.haneef.school.ui.screens.community

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicTrackNode
import com.haneef.school.data.models.ClassNode
import com.haneef.school.data.models.ParentLinkedStudent
import com.haneef.school.data.models.ParentListResponse
import com.haneef.school.viewmodel.ParentUiState
import com.haneef.school.viewmodel.ParentViewModel
import com.haneef.school.viewmodel.SchoolUiState
import com.haneef.school.viewmodel.SchoolViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


// ─── View State Enum ────────────────────────────────────────────────────────

enum class ParentView {
    LIST, ADD_FORM, EDIT_FORM, FULL_PROFILE, ASSIGN_STUDENTS
}

// ─── Screen Root ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentManagementScreen(modifier: Modifier = Modifier) {
    val parentViewModel: ParentViewModel = koinViewModel()
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()

    val parentUiState by parentViewModel.uiState.collectAsState()
    val schoolUiState by schoolViewModel.uiState.collectAsState()

    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""

    // ── Navigation State ──
    var currentView by remember { mutableStateOf(ParentView.LIST) }
    var selectedParent by remember { mutableStateOf<ParentListResponse?>(null) }

    // ── Delete Dialog ──
    var showDeleteDialog by remember { mutableStateOf(false) }
    var parentToDelete by remember { mutableStateOf<ParentListResponse?>(null) }

    // ── Student Unlink Dialog ──
    var showUnlinkDialog by remember { mutableStateOf(false) }
    var studentToUnlink by remember { mutableStateOf<ParentLinkedStudent?>(null) }

    // ── Form Fields ──
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var formValidationError by remember { mutableStateOf<String?>(null) }

    // ── Assign Students State ──
    var assignStudentIdInput by remember { mutableStateOf("") }
    val assignedStudentIds = remember { mutableStateListOf<String>() }

    // ── Filter State ──
    var localSearchQuery by remember { mutableStateOf(parentUiState.searchQuery ?: "") }
    var trackDropdownExpanded by remember { mutableStateOf(false) }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val selectedTrack = schoolUiState.academicStructure?.tracks?.find { it.id == parentUiState.selectedTrackId }
    val selectedClass = schoolUiState.academicStructure?.tracks
        ?.flatMap { it.departments }?.flatMap { it.classes }
        ?.find { it.id == parentUiState.selectedClassId }

    LaunchedEffect(schoolId) {
        if (schoolId.isNotEmpty() && accessToken.isNotEmpty()) {
            schoolViewModel.getAcademicStructure(schoolId, accessToken)
            parentViewModel.loadParents(schoolId, accessToken)
        }
    }

    // ── Helpers ──
    fun navigateToAddForm() {
        formValidationError = null
        firstName = ""; lastName = ""; email = ""; phone = ""; address = ""
        selectedParent = null
        currentView = ParentView.ADD_FORM
    }

    fun navigateToEditForm(parent: ParentListResponse) {
        formValidationError = null
        val nameParts = parent.fullName.trim().split(" ")
        firstName = nameParts.firstOrNull() ?: ""
        lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""
        email = parent.email
        phone = parent.phoneNumber
        address = parent.address ?: ""
        selectedParent = parent
        currentView = ParentView.EDIT_FORM
    }

    fun handleSave() {
        if (firstName.isBlank()) { formValidationError = "First name is required"; return }
        if (lastName.isBlank()) { formValidationError = "Last name is required"; return }
        if (email.isBlank()) { formValidationError = "Email is required"; return }
        formValidationError = null

        if (currentView == ParentView.ADD_FORM) {
            parentViewModel.createParent(
                schoolId = schoolId,
                accessToken = accessToken,
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone.takeIf { it.isNotBlank() },
                address = address.takeIf { it.isNotBlank() },
                onSuccess = { currentView = ParentView.LIST }
            )
        } else if (currentView == ParentView.EDIT_FORM && selectedParent != null) {
            parentViewModel.updateParent(
                schoolId = schoolId,
                accessToken = accessToken,
                parentId = selectedParent!!.id,
                fullName = "$firstName $lastName",
                email = email,
                phone = phone.takeIf { it.isNotBlank() },
                address = address.takeIf { it.isNotBlank() },
                onSuccess = { currentView = ParentView.LIST; selectedParent = null }
            )
        }
    }

    // ── Delete Dialog ──
    if (showDeleteDialog && parentToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; parentToDelete = null },
            title = {
                Text("Delete Parent", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
            },
            text = {
                Text(
                    "Are you sure you want to delete ${parentToDelete!!.fullName}? This will remove their account and all linked student relationships.",
                    color = Color(0xFF64748B), fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        parentViewModel.deleteParent(schoolId, accessToken, parentToDelete!!.id) {
                            showDeleteDialog = false; parentToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; parentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Student Unlink Dialog ──
    if (showUnlinkDialog && studentToUnlink != null && selectedParent != null) {
        val parent = selectedParent!!
        val student = studentToUnlink!!
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false; studentToUnlink = null },
            title = {
                Text("Unlink Student", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
            },
            text = {
                Text(
                    "Are you sure you want to remove the linkage for ${student.fullName}? This will unlink them from ${parent.fullName}.",
                    color = Color(0xFF64748B), fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        parentViewModel.deleteStudentAssignment(
                            schoolId = schoolId,
                            accessToken = accessToken,
                            assignmentId = student.id,
                            parentId = parent.id,
                            onSuccess = {
                                selectedParent = selectedParent?.copy(
                                    linkedStudents = selectedParent!!.linkedStudents.filter { it.id != student.id }
                                )
                                showUnlinkDialog = false
                                studentToUnlink = null
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Unlink", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnlinkDialog = false; studentToUnlink = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ─── View Router ──────────────────────────────────────────────────────
    when (currentView) {

        // ── LIST VIEW ────────────────────────────────────────────────────
        ParentView.LIST -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
            ) {
                // ── Header ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Breadcrumb
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Default.FamilyRestroom, null, Modifier.size(14.dp), tint = Color(0xFF034CD1))
                        Text(" Community", fontSize = 12.sp, color = Color(0xFF034CD1), fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(12.dp), tint = Color(0xFF94A3B8))
                        Text(" Parent Directory", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Parent Directory", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text(
                                "Manage parents, view linked children, and contact information.",
                                fontSize = 14.sp, color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { navigateToAddForm() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Add, "Add Parent", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Add Parent", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── Content List ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    // Filters Card
                    item {
                        ParentFiltersCard(
                            schoolUiState = schoolUiState,
                            parentUiState = parentUiState,
                            parentViewModel = parentViewModel,
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

                    // States
                    if (parentUiState.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF034CD1))
                            }
                        }
                    } else if (parentUiState.errorMessage != null) {
                        item {
                            ParentErrorCard(
                                message = parentUiState.errorMessage,
                                onRetry = { parentViewModel.loadParents(schoolId, accessToken) }
                            )
                        }
                    } else if (parentUiState.parentList.isEmpty()) {
                        item { ParentEmptyState() }
                    } else {
                        items(parentUiState.parentList) { parent ->
                            ParentCard(
                                parent = parent,
                                onEditClick = { navigateToEditForm(parent) },
                                onDeleteClick = {
                                    parentToDelete = parent
                                    showDeleteDialog = true
                                },
                                onViewProfileClick = {
                                    selectedParent = parent
                                    currentView = ParentView.FULL_PROFILE
                                },
                                onAssignStudentsClick = {
                                    selectedParent = parent
                                    assignedStudentIds.clear()
                                    assignStudentIdInput = ""
                                    currentView = ParentView.ASSIGN_STUDENTS
                                }
                            )
                        }

                        // Pagination
                        item {
                            ParentPaginationRow(
                                parentUiState = parentUiState,
                                parentViewModel = parentViewModel,
                                schoolId = schoolId,
                                accessToken = accessToken
                            )
                        }
                    }
                }
            }
        }

        // ── ADD / EDIT FORM VIEW ──────────────────────────────────────────
        ParentView.ADD_FORM, ParentView.EDIT_FORM -> {
            val titleText = if (currentView == ParentView.ADD_FORM) "Add New Parent" else "Edit Parent Profile"

            Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
                // Back Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentView = ParentView.LIST; selectedParent = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Validation Error Banner
                    if (formValidationError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Text(formValidationError!!, color = Color(0xFF991B1B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Save Error Banner
                    if (parentUiState.saveErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Text(parentUiState.saveErrorMessage!!, color = Color(0xFF991B1B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Personal Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

                            // Avatar Placeholder
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                                    .border(2.dp, Color(0xFFE2E8F0), CircleShape)
                                    .align(Alignment.CenterHorizontally),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, "Upload Photo", tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text("Upload Photo", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FormTextField(firstName, { firstName = it }, "First Name", "e.g. Chinelo", Modifier.weight(1f))
                                FormTextField(lastName, { lastName = it }, "Last Name", "e.g. Okoro", Modifier.weight(1f))
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FormTextField(email, { email = it }, "Email Address", "e.g. parent@example.com", Modifier.weight(1f))
                                FormTextField(phone, { phone = it }, "Phone Number", "e.g. +234 803 123 4567", Modifier.weight(1f))
                            }

                            FormTextField(address, { address = it }, "Home Address", "e.g. 12 Academic Close, Lagos", Modifier.fillMaxWidth())
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = { handleSave() },
                        enabled = !parentUiState.isSaving,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (parentUiState.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                if (currentView == ParentView.ADD_FORM) "Register Parent" else "Save Changes",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ── FULL PROFILE VIEW ─────────────────────────────────────────────
        ParentView.FULL_PROFILE -> {
            val parent = selectedParent
            Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .shadow(1.dp, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentView = ParentView.LIST; selectedParent = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Parent Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(Modifier.weight(1f))
                    // Small Avatar in top right
                    if (parent != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF034CD1)),
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = parent.fullName.replace("Mrs. ", "").replace("Mr. ", "")
                                .split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                            Text(initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (parent != null) {
                    val profileScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(profileScroll)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── Profile Summary Card ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                // ID + Verified badge row
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFEEF2FF))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(parent.parentId, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4338CA))
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Avatar
                                    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF034CD1).copy(alpha = 0.1f))
                                                .border(1.5.dp, Color(0xFF034CD1).copy(alpha = 0.3f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val initials = parent.fullName
                                                .replace("Mrs. ", "").replace("Mr. ", "").replace("Dr. ", "")
                                                .split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                                            Text(initials, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp).clip(CircleShape)
                                                .background(Color(0xFF034CD1))
                                                .border(1.5.dp, Color.White, CircleShape)
                                                .align(Alignment.BottomEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, "Verified", tint = Color.White, modifier = Modifier.size(11.dp))
                                        }
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(parent.fullName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.MailOutline, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                            Text(parent.email, fontSize = 13.sp, color = Color(0xFF64748B))
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.LocalPhone, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                            Text(parent.phoneNumber.ifEmpty { "Not provided" }, fontSize = 13.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }

                        // ── Linked Students Card ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(){
                                Text("LINKED STUDENTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 0.5.sp)
                                
                                // Assign Students
                                OutlinedButton(
                                    onClick = {
                                        selectedParent = parent
                                        assignedStudentIds.clear()
                                        assignStudentIdInput = ""
                                        currentView = ParentView.ASSIGN_STUDENTS
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFF034CD1)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF034CD1), modifier = Modifier.size(16.dp))
                                    }
                                }
                                }
                                if (parent.linkedStudents.isEmpty()) {
                                    Box(
                                        Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp)).padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No linked students yet.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        parent.linkedStudents.forEach { student ->
                                            LinkedStudentRow(student = student)
                                        }
                                    }
                                }
                            }
                        }

                        // ── Action Buttons Row ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Edit
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1F5F9))
                                        .clickable { navigateToEditForm(parent) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF475569), modifier = Modifier.size(18.dp)) }

                                // Delete
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFEF2F2))
                                        .clickable { parentToDelete = parent; showDeleteDialog = true },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) }

                                Spacer(Modifier.weight(1f))

                                
                            }
                        }

                        // ── Contact Information Card ──
                        if (!parent.address.isNullOrBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Contact Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                        Column {
                                            Text("Home Address", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                                            Text(parent.address, fontSize = 14.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── ASSIGN STUDENTS VIEW ──────────────────────────────────────────
        ParentView.ASSIGN_STUDENTS -> {
            val parent = selectedParent
            Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentView = ParentView.LIST; selectedParent = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Assign Students", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }

                if (parent != null) {
                    var studentSearchQuery by remember { mutableStateOf("") }

                    LaunchedEffect(studentSearchQuery) {
                        if (studentSearchQuery.trim().length >= 2) {
                            parentViewModel.searchStudents(
                                schoolId = schoolId,
                                accessToken = accessToken,
                                query = studentSearchQuery.trim()
                            )
                        } else {
                            parentViewModel.clearSearchedStudents()
                        }
                    }

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Parent summary
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF034CD1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val initials = parent.fullName
                                        .replace("Mrs. ", "").replace("Mr. ", "")
                                        .split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                                    Text(initials, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text(parent.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Text("Assigning students to this parent", fontSize = 12.sp, color = Color(0xFF64748B))
                                }
                            }
                        }

                        // Save error
                        if (parentUiState.saveErrorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    Text(parentUiState.saveErrorMessage!!, color = Color(0xFF991B1B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // Already Linked Students List Card
                        if (selectedParent?.linkedStudents?.isNotEmpty() == true) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Already Linked Students", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    
                                    selectedParent!!.linkedStudents.forEach { student ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFF8FAFC))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFE2E8F0)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val initials = student.fullName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                                                    Text(initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                                }
                                                Column {
                                                    Text(student.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                    val desc = buildString {
                                                        student.className?.let { append(it) }
                                                        append(" • ID: ")
                                                        append(student.studentId)
                                                    }
                                                    Text(desc, fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    studentToUnlink = student
                                                    showUnlinkDialog = true
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFFFEF2F2), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    "Unlink Student",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search & Add Student Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Link New Student", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text(
                                    "Search student by name to link to this parent. Selected students will be added to the pending assignments list.",
                                    fontSize = 13.sp, color = Color(0xFF64748B)
                                )

                                OutlinedTextField(
                                    value = studentSearchQuery,
                                    onValueChange = { studentSearchQuery = it },
                                    placeholder = { Text("Search by name...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF64748B)) },
                                    trailingIcon = {
                                        if (studentSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { studentSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, null, tint = Color(0xFF64748B))
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF034CD1),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                // Search Results List
                                if (parentUiState.isSearchingStudents) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF034CD1), modifier = Modifier.size(24.dp))
                                    }
                                } else if (studentSearchQuery.trim().length >= 2 && parentUiState.searchedStudents.isEmpty()) {
                                    Text("No students found matching \"$studentSearchQuery\"", fontSize = 13.sp, color = Color(0xFF64748B), modifier = Modifier.padding(vertical = 4.dp))
                                } else if (parentUiState.searchedStudents.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        parentUiState.searchedStudents.forEach { student ->
                                            val isAlreadyLinked = selectedParent?.linkedStudents?.any { it.studentId == student.studentId } == true
                                            val isAlreadyPending = assignedStudentIds.contains(student.studentId)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isAlreadyLinked) Color(0xFFF1F5F9) else Color.White)
                                                    .clickable(enabled = !isAlreadyLinked && !isAlreadyPending) {
                                                        assignedStudentIds.add(student.studentId)
                                                        studentSearchQuery = "" // clear search query
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(student.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isAlreadyLinked) Color(0xFF94A3B8) else Color(0xFF1E293B))
                                                    Text(
                                                        text = if (student.className.isNullOrBlank()) "ID: ${student.studentId}" else "${student.className} • ID: ${student.studentId}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                when {
                                                    isAlreadyLinked -> {
                                                        Text("Linked", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
                                                    }
                                                    isAlreadyPending -> {
                                                        Text("Pending", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF034CD1))
                                                    }
                                                    else -> {
                                                        Icon(Icons.Default.Add, null, tint = Color(0xFF034CD1), modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Student IDs List (Pending list)
                                if (assignedStudentIds.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Students to assign (${assignedStudentIds.size}):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                        assignedStudentIds.forEach { studentId ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFF0FDF4))
                                                    .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(Icons.Default.Person, null, tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                                                    Text(studentId, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF064E3B))
                                                }
                                                IconButton(
                                                    onClick = { assignedStudentIds.remove(studentId) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, "Remove", tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Confirm Button
                        Button(
                            onClick = {
                                if (assignedStudentIds.isNotEmpty()) {
                                    parentViewModel.assignStudents(
                                        schoolId = schoolId,
                                        accessToken = accessToken,
                                        parentId = parent.id,
                                        studentIds = assignedStudentIds.toList(),
                                        onSuccess = {
                                            currentView = ParentView.LIST
                                            selectedParent = null
                                        }
                                    )
                                }
                            },
                            enabled = assignedStudentIds.isNotEmpty() && !parentUiState.isSaving,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (parentUiState.isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Assign ${assignedStudentIds.size} Student(s)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ─── Parent Card ─────────────────────────────────────────────────────────────

@Composable
fun ParentCard(
    parent: ParentListResponse,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewProfileClick: () -> Unit,
    onAssignStudentsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // ── Header: Avatar + Info ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Circular Avatar with verified tick
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(64.dp).clip(CircleShape)
                            .background(Color(0xFF034CD1).copy(alpha = 0.1f))
                            .border(1.5.dp, Color(0xFF034CD1).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = parent.fullName
                            .replace("Mrs. ", "").replace("Mr. ", "").replace("Dr. ", "")
                            .split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                        Text(initials, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp).clip(CircleShape)
                            .background(Color(0xFF034CD1))
                            .border(1.5.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "Verified", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    // ID + Verified Badge
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFEEF2FF)).padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(parent.parentId, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4338CA))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(parent.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Email
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MailOutline, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                Text(parent.email, fontSize = 13.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            // Phone
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocalPhone, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                Text(parent.phoneNumber.ifEmpty { "No phone" }, fontSize = 13.sp, color = Color(0xFF64748B))
            }

            Spacer(Modifier.height(20.dp))

            // ── Linked Students Section ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LINKED STUDENTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 0.5.sp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEEF2FF))
                        .clickable(onClick = onAssignStudentsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, "Assign Students", tint = Color(0xFF034CD1), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            if (parent.linkedStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No students linked yet", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    parent.linkedStudents.forEach { student ->
                        LinkedStudentRow(student = student)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(Modifier.height(14.dp))

            // ── Action Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Edit
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1F5F9)).clickable(onClick = onEditClick),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF475569), modifier = Modifier.size(18.dp)) }

                    // Delete
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFEF2F2)).clickable(onClick = onDeleteClick),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) }
                }

                // Full Record Button
                Button(
                    onClick = onViewProfileClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Full Record", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ─── Linked Student Row ───────────────────────────────────────────────────────

@Composable
fun LinkedStudentRow(student: ParentLinkedStudent) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                val initials = student.fullName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                Text(initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(student.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF034CD1))
                Spacer(Modifier.height(2.dp))
                val desc = buildString {
                    student.className?.let { append(it) }
                    append(" • ID: ")
                    append(student.studentId)
                }
                Text(desc, fontSize = 12.sp, color = Color(0xFF64748B))
            }

            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Filters Card ─────────────────────────────────────────────────────────────

@Composable
fun ParentFiltersCard(
    schoolUiState: SchoolUiState,
    parentUiState: ParentUiState,
    parentViewModel: ParentViewModel,
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Search
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = {
                    onSearchQueryChange(it)
                    parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = it)
                },
                placeholder = { Text("Search by parent name, email or ID...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, search = null)
                        }) { Icon(Icons.Default.Clear, "Clear") }
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

            // Track & Class Dropdowns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    FilterDropdown(
                        label = selectedTrack?.name ?: "All Tracks",
                        icon = Icons.Default.Category,
                        onClick = { onTrackDropdownExpandedChange(true) }
                    )
                    DropdownMenu(
                        expanded = trackDropdownExpanded,
                        onDismissRequest = { onTrackDropdownExpandedChange(false) }
                    ) {
                        DropdownMenuItem(text = { Text("All Tracks") }, onClick = {
                            onTrackDropdownExpandedChange(false)
                            parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, trackId = null, classId = null)
                        })
                        schoolUiState.academicStructure?.tracks?.forEach { track ->
                            DropdownMenuItem(text = { Text(track.name) }, onClick = {
                                onTrackDropdownExpandedChange(false)
                                parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, trackId = track.id, classId = null)
                            })
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    FilterDropdown(
                        label = selectedClass?.className ?: "All Classes",
                        icon = Icons.Default.School,
                        onClick = { onClassDropdownExpandedChange(true) }
                    )
                    DropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { onClassDropdownExpandedChange(false) }
                    ) {
                        DropdownMenuItem(text = { Text("All Classes") }, onClick = {
                            onClassDropdownExpandedChange(false)
                            parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, classId = null)
                        })
                        val classesToShow = if (selectedTrack != null) {
                            selectedTrack.departments.flatMap { it.classes }
                        } else {
                            schoolUiState.academicStructure?.tracks?.flatMap { it.departments }?.flatMap { it.classes } ?: emptyList()
                        }
                        classesToShow.forEach { clazz ->
                            DropdownMenuItem(text = { Text(clazz.className) }, onClick = {
                                onClassDropdownExpandedChange(false)
                                parentViewModel.updateFilters(schoolId = schoolId, accessToken = accessToken, classId = clazz.id)
                            })
                        }
                    }
                }
            }

            // Clear Filters
            val hasFilters = parentUiState.searchQuery != null || parentUiState.selectedTrackId != null || parentUiState.selectedClassId != null
            if (hasFilters) {
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        parentViewModel.clearFilters(schoolId, accessToken)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.FilterListOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All Filters")
                }
            }
        }
    }
}

// ─── Pagination ───────────────────────────────────────────────────────────────

@Composable
fun ParentPaginationRow(
    parentUiState: ParentUiState,
    parentViewModel: ParentViewModel,
    schoolId: String,
    accessToken: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Page ${parentUiState.currentPage} of ${parentUiState.totalPages} (${parentUiState.totalItems} items)",
            fontSize = 13.sp, color = Color(0xFF64748B)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (parentUiState.hasPrevious)
                        parentViewModel.loadParents(schoolId, accessToken, page = parentUiState.currentPage - 1)
                },
                enabled = parentUiState.hasPrevious,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF034CD1))
            ) { Text("Previous") }

            Button(
                onClick = {
                    if (parentUiState.hasNext)
                        parentViewModel.loadParents(schoolId, accessToken, page = parentUiState.currentPage + 1)
                },
                enabled = parentUiState.hasNext,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
            ) { Text("Next") }
        }
    }
}

// ─── Error & Empty States ─────────────────────────────────────────────────────

@Composable
fun ParentErrorCard(message: String?, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFEE2E2))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Error, "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(message ?: "An error occurred.", color = Color(0xFF991B1B), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun ParentEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FamilyRestroom, null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
            Spacer(Modifier.height(16.dp))
            Text("No parents found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            Text("Try adjusting your filters or add a new parent.", fontSize = 14.sp, color = Color(0xFF94A3B8))
        }
    }
}
