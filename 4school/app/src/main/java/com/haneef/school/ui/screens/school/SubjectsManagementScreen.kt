package com.haneef.school.ui.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicTrackNode
import com.haneef.school.data.models.ClassNode
import com.haneef.school.data.models.SchoolSubjectResponse
import com.haneef.school.data.models.LinkedClassResponse
import com.haneef.school.data.models.SubjectNode
import com.haneef.school.data.models.SaveSchoolSubjectsRequest
import com.haneef.school.data.models.AcademicStructureResponse
import com.haneef.school.viewmodel.SchoolViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsManagementScreen(modifier: Modifier = Modifier) {
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val schoolUiState by schoolViewModel.uiState.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(schoolUiState.successMessage, schoolUiState.errorMessage) {
        schoolUiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            schoolViewModel.clearMessages()
        }
        schoolUiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            schoolViewModel.clearMessages()
        }
    }

    val accessToken = preferencesManager.getAccessToken()
    val schoolId = preferencesManager.getSchoolId()

    var showAddSubjectsDialog by remember { mutableStateOf(false) }
    var subjectForLinking by remember { mutableStateOf<SchoolSubjectResponse?>(null) }
    var unlinkClassInfo by remember { mutableStateOf<Pair<SchoolSubjectResponse, LinkedClassResponse>?>(null) }

    // Filters
    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var selectedClass by remember { mutableStateOf<ClassNode?>(null) }
    var subjectNameQuery by remember { mutableStateOf("") }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val tracks = schoolUiState.academicStructure?.tracks ?: emptyList()

    // Classes available in the selected track (all depts flattened)
    val classesInSelectedTrack: List<ClassNode> = remember(selectedTrackId, tracks) {
        tracks.find { it.id == selectedTrackId }
            ?.departments?.flatMap { it.classes }
            ?: emptyList()
    }

    fun applyFilters() {
        if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
            schoolViewModel.getSchoolSubjects(
                schoolId = schoolId!!,
                accessToken = accessToken!!,
                className = selectedClass?.className,
                departmentId = null,
                trackId = selectedTrackId
            )
        }
    }

    LaunchedEffect(schoolId) {
        if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
            schoolViewModel.getSchoolSubjects(schoolId!!, accessToken!!)
            schoolViewModel.getAcademicStructure(schoolId!!, accessToken!!)
            schoolViewModel.getAllSubjects(accessToken!!)
        }
    }

    // Client-side subject name filter on top of server results
    val displayedSubjects = remember(schoolUiState.schoolSubjects, subjectNameQuery) {
        if (subjectNameQuery.isBlank()) schoolUiState.schoolSubjects
        else schoolUiState.schoolSubjects.filter {
            it.name.contains(subjectNameQuery, ignoreCase = true)
        }
    }

    if (schoolUiState.isLoading && schoolUiState.schoolSubjects.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4A5FBF))
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF4A5FBF))
                        Text(" Organization", fontSize = 12.sp, color = Color(0xFF4A5FBF))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF94A3B8))
                        Text(" Subjects Management", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                    Text(
                        "Subjects Management",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Assign and manage academic subjects for your school and classes.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Browse button
            item {
                Button(
                    onClick = {
                        if (!accessToken.isNullOrEmpty()) {
                            schoolViewModel.getAllSubjects(accessToken!!)
                        }
                        showAddSubjectsDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Browse & Assign Subjects")
                }
            }

            // Filter card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Filter Subjects",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )

                        // 1. Track chips
                        if (tracks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedTrackId == null,
                                    onClick = {
                                        selectedTrackId = null
                                        selectedClass = null
                                        applyFilters()
                                    },
                                    label = { Text("All Tracks", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF034CD1),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedTrackId == null,
                                        selectedBorderColor = Color(0xFF034CD1),
                                        borderColor = Color(0xFFCBD5E1)
                                    )
                                )
                                tracks.forEach { track ->
                                    val isSelected = selectedTrackId == track.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTrackId = if (isSelected) null else track.id
                                            selectedClass = null
                                            applyFilters()
                                        },
                                        label = { Text(track.name, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF034CD1),
                                            selectedLabelColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            selectedBorderColor = Color(0xFF034CD1),
                                            borderColor = Color(0xFFCBD5E1)
                                        )
                                    )
                                }
                            }
                        }

                        // 2. Class dropdown
                        ExposedDropdownMenuBox(
                            expanded = classDropdownExpanded,
                            onExpandedChange = {
                                if (classesInSelectedTrack.isNotEmpty()) classDropdownExpanded = it
                            }
                        ) {
                            OutlinedTextField(
                                value = selectedClass?.className ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Class") },
                                placeholder = {
                                    Text(
                                        if (selectedTrackId == null) "Select a track first"
                                        else "All classes"
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                enabled = classesInSelectedTrack.isNotEmpty(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color(0xFF94A3B8),
                                    disabledBorderColor = Color(0xFFE2E8F0),
                                    disabledLabelColor = Color(0xFFCBD5E1)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = classDropdownExpanded,
                                onDismissRequest = { classDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All classes", color = Color(0xFF64748B)) },
                                    onClick = {
                                        selectedClass = null
                                        classDropdownExpanded = false
                                        applyFilters()
                                    }
                                )
                                classesInSelectedTrack.forEach { clazz ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(clazz.className, fontWeight = FontWeight.Medium)
                                                if (clazz.classCode != null) {
                                                    Text(
                                                        clazz.classCode,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedClass = clazz
                                            classDropdownExpanded = false
                                            applyFilters()
                                        }
                                    )
                                }
                            }
                        }

                        // 3. Subject name search
                        OutlinedTextField(
                            value = subjectNameQuery,
                            onValueChange = { subjectNameQuery = it },
                            label = { Text("Subject Name") },
                            placeholder = { Text("Subject name…") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (subjectNameQuery.isNotEmpty()) {
                                    IconButton(onClick = { subjectNameQuery = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // Subjects list or empty state
            if (displayedSubjects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF94A3B8)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("No subjects found", fontSize = 18.sp, color = Color(0xFF64748B))
                            Text(
                                "Try adjusting your filters or assign subjects first.",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            } else {
                items(displayedSubjects) { subject ->
                    SubjectCard(
                        subject = subject,
                        onAddClass = { subjectForLinking = subject },
                        onUnlinkClass = { classResponse ->
                            unlinkClassInfo = Pair(subject, classResponse)
                        }
                    )
                }
            }
        }
    }

    if (showAddSubjectsDialog) {
        BrowseSubjectsDialog(
            allSubjects = schoolUiState.allSubjects,
            currentSchoolSubjects = schoolUiState.schoolSubjects,
            onDismiss = { showAddSubjectsDialog = false },
            onAssignSubject = { subjectId ->
                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                    schoolViewModel.saveSchoolSubjects(schoolId!!, accessToken!!, listOf(subjectId))
                }
            },
            onDeactivateSubject = { schoolSubjectId ->
                if (!accessToken.isNullOrEmpty()) {
                    schoolViewModel.deactivateSchoolSubject(schoolSubjectId, accessToken!!)
                }
            }
        )
    }

    subjectForLinking?.let { subject ->
        val allClasses = remember(tracks) {
            tracks.flatMap { track -> track.departments.flatMap { it.classes } }
        }
        LinkClassDialog(
            subject = subject,
            availableClasses = allClasses,
            onDismiss = { subjectForLinking = null },
            onLink = { classNode ->
                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                    schoolViewModel.linkClassToSubject(
                        schoolSubjectId = subject.id,
                        schoolId = schoolId!!,
                        accessToken = accessToken!!,
                        classIds = listOf(classNode.id)
                    )
                }
                subjectForLinking = null
            }
        )
    }

    unlinkClassInfo?.let { (subject, clazz) ->
        AlertDialog(
            onDismissRequest = { unlinkClassInfo = null },
            title = { Text("Confirm Unlink", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove the class \"${clazz.name}\" from the subject \"${subject.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                            schoolViewModel.unlinkClassFromSubject(
                                schoolSubjectId = subject.id,
                                classId = clazz.id,
                                accessToken = accessToken!!
                            )
                        }
                        unlinkClassInfo = null
                    }
                ) {
                    Text("Unlink", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { unlinkClassInfo = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SubjectCard(
    subject: SchoolSubjectResponse,
    onAddClass: () -> Unit = {},
    onUnlinkClass: (LinkedClassResponse) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (!subject.code.isNullOrEmpty()) {
                        Text(
                            text = "Code: ${subject.code}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${subject.linkedClasses.orEmpty().size} Classes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }
                    // Add class button
                    SmallFloatingActionButton(
                        onClick = onAddClass,
                        containerColor = Color(0xFF034CD1),
                        contentColor = Color.White,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add class to subject",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (subject.linkedClasses.orEmpty().isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (clazz in subject.linkedClasses.orEmpty()) {
                        AssistChip(
                            onClick = { },
                            label = { Text(clazz.name, fontSize = 10.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unlink class",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onUnlinkClass(clazz) },
                                    tint = Color(0xFFDC2626)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFEFF6FF),
                                labelColor = Color(0xFF1E40AF)
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkClassDialog(
    subject: SchoolSubjectResponse,
    availableClasses: List<ClassNode>,
    onDismiss: () -> Unit,
    onLink: (ClassNode) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val linkedIds = remember(subject.linkedClasses) { subject.linkedClasses.orEmpty().map { it.id }.toSet() }
    val filteredClasses = remember(availableClasses, searchQuery, linkedIds) {
        availableClasses
            .filter { it.id !in linkedIds }
            .filter {
                searchQuery.isBlank() ||
                it.className.contains(searchQuery, ignoreCase = true) ||
                it.classCode?.contains(searchQuery, ignoreCase = true) == true
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add Class to Subject", fontWeight = FontWeight.Bold)
                Text(
                    subject.name,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search classes…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                if (filteredClasses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (availableClasses.isEmpty()) "No classes in the academic structure yet."
                            else "All classes are already linked.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredClasses) { clazz ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLink(clazz) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(clazz.className, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (!clazz.classCode.isNullOrEmpty()) {
                                        Text(clazz.classCode, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = "Link",
                                    tint = Color(0xFF034CD1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun EmptySubjectsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF94A3B8)
            )
            Spacer(Modifier.height(16.dp))
            Text("No subjects found", fontSize = 18.sp, color = Color(0xFF64748B))
            Text(
                "Try adjusting your filters or assign subjects first.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun BrowseSubjectsDialog(
    allSubjects: List<SubjectNode>,
    currentSchoolSubjects: List<SchoolSubjectResponse>,
    onDismiss: () -> Unit,
    onAssignSubject: (String) -> Unit,
    onDeactivateSubject: (String) -> Unit
) {
    val currentSubjectMap = remember(currentSchoolSubjects) {
        currentSchoolSubjects.associateBy { it.subjectId }
    }
    var filterText by remember { mutableStateOf("") }

    val filteredSubjects = allSubjects.filter {
        it.name.orEmpty().contains(filterText, ignoreCase = true) || it.code.orEmpty().contains(filterText, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Browse Academic Subjects") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Search subjects...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(8.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredSubjects) { subject ->
                        val matchingSchoolSubject = currentSubjectMap[subject.id]
                        val isSelected = matchingSchoolSubject != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        onDeactivateSubject(matchingSchoolSubject!!.id)
                                    } else {
                                        onAssignSubject(subject.id)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!isSelected) onAssignSubject(subject.id)
                                    } else {
                                        if (isSelected) onDeactivateSubject(matchingSchoolSubject!!.id)
                                    }
                                }
                            )
                            Column {
                                Text(subject.name.orEmpty(), fontWeight = FontWeight.Medium)
                                Text(subject.code.orEmpty(), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
            ) {
                Text("Close")
            }
        }
    )
}
