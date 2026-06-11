package com.haneef.school.ui.screens.school

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicStructureResponse
import com.haneef.school.data.models.AcademicTrackNode
import com.haneef.school.data.models.DepartmentNode
import com.haneef.school.data.models.ClassNode
import com.haneef.school.viewmodel.SchoolViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicHierarchyScreen(modifier: Modifier = Modifier) {
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val schoolUiState by schoolViewModel.uiState.collectAsState()
    
    val accessToken = preferencesManager.getAccessToken()
    val schoolId = preferencesManager.getSchoolId()
    
    val academicStructure = schoolUiState.academicStructure
    
    var showCreateTrackDialog by remember { mutableStateOf(false) }
    var showCreateDeptDialog by remember { mutableStateOf(false) }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    
    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var selectedDeptId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(schoolId) {
        if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
            schoolViewModel.getAcademicStructure(schoolId!!, accessToken!!)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF4A5FBF))
                    Text(" Organization", fontSize = 12.sp, color = Color(0xFF4A5FBF))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF94A3B8))
                    Text(" Academic Structure", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
                Text(
                    "Academic Hierarchy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    "Configure and manage educational tracks, departments, and specific classes.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        item {
            // New Track Button
            Button(
                onClick = { showCreateTrackDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Track")
            }
        }

        item {
            // Summary Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val totalTracks = academicStructure?.tracks?.size ?: 0
                val totalDepts = academicStructure?.tracks?.sumOf { it.departments.size } ?: 0
                val totalClasses = academicStructure?.tracks?.sumOf { t -> t.departments.sumOf { d -> d.classes.size } } ?: 0
                
                SummaryCard(Modifier.weight(1f), "Tracks", totalTracks.toString().padStart(2, '0'), Icons.Default.AccountTree, Color(0xFF4A5FBF))
                SummaryCard(Modifier.weight(1f), "Dep'ts", totalDepts.toString().padStart(2, '0'), Icons.Default.BusinessCenter, Color(0xFF4A5FBF))
                SummaryCard(Modifier.weight(1f), "Classes", totalClasses.toString().padStart(2, '0'), Icons.Default.School, Color(0xFF4A5FBF))
            }
        }

        // Hierarchy List or Empty State
        if (academicStructure?.tracks?.isEmpty() == true) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No academic structure found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                                    schoolViewModel.initializeDefaultStructure(schoolId!!, accessToken!!)
                                }
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Default Academic Structure")
                        }
                    }
                }
            }
        } else {
            items(academicStructure?.tracks ?: emptyList()) { track ->
                TrackItem(
                    track = track,
                    onAddDept = {
                        selectedTrackId = track.id
                        showCreateDeptDialog = true
                    },
                    onAddClass = { deptId ->
                        selectedDeptId = deptId
                        showCreateClassDialog = true
                    }
                )
            }
        }
    }

    // Dialogs
    if (showCreateTrackDialog) {
        CreateTrackDialog(
            onDismiss = { showCreateTrackDialog = false },
            onConfirm = { name, desc ->
                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                    schoolViewModel.createTrack(schoolId!!, accessToken!!, name, desc)
                }
            }
        )
    }

    if (showCreateDeptDialog && selectedTrackId != null) {
        CreateDepartmentDialog(
            onDismiss = { showCreateDeptDialog = false },
            onConfirm = { name, desc ->
                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                    schoolViewModel.createDepartment(schoolId!!, accessToken!!, selectedTrackId!!, name, desc)
                }
            }
        )
    }

    if (showCreateClassDialog && selectedDeptId != null) {
        CreateClassDialog(
            onDismiss = { showCreateClassDialog = false },
            onConfirm = { name, code, level ->
                if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty()) {
                    schoolViewModel.createClass(schoolId!!, accessToken!!, selectedDeptId!!, name, code, level)
                }
            }
        )
    }
}

@Composable
fun SummaryCard(modifier: Modifier, label: String, count: String, icon: ImageVector, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ){
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(count, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
            Column {
                
                Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
fun TrackItem(track: AcademicTrackNode, onAddDept: () -> Unit, onAddClass: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    Text("Lead: N/A • ${track.departments.size} Departments", fontSize = 12.sp, color = Color(0xFF64748B))
                }
                IconButton(onClick = onAddDept) {
                    Icon(Icons.Default.AddBox, contentDescription = "Add Department", tint = Color(0xFF4A5FBF))
                }
                IconButton(onClick = { /* More options */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF64748B))
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 16.dp)) {
                    track.departments.forEach { dept ->
                        DepartmentItem(dept, onAddClass = { onAddClass(dept.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun DepartmentItem(dept: DepartmentNode, onAddClass: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dept.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${dept.classes.size} Classes", fontSize = 11.sp, color = Color(0xFF64748B))
            }
            IconButton(onClick = onAddClass, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Class", tint = Color(0xFF4A5FBF), modifier = Modifier.size(18.dp))
            }
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                dept.classes.forEach { clazz ->
                    ClassItem(clazz)
                }
            }
        }
    }
}

@Composable
fun ClassItem(clazz: ClassNode) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF034CD1)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(clazz.className, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (clazz.gradeLevel != null || clazz.classCode != null) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE0F2FE)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = clazz.classCode ?: "G${clazz.gradeLevel}",
                        fontSize = 10.sp,
                        color = Color(0xFF0369A1),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CreateTrackDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name, desc.ifBlank { null }); onDismiss() }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))) {
                Text("Save Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Create New Track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("TRACK NAME") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
fun CreateDepartmentDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name, desc.ifBlank { null }); onDismiss() }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))) {
                Text("Save Department")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Create New Department") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("DEPARTMENT NAME") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
fun CreateClassDialog(onDismiss: () -> Unit, onConfirm: (String, String?, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, code.ifBlank { null }, level.toIntOrNull())
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
            ) {
                Text("Save Class")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Create New Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("CLASS NAME") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Class Code (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Grade Level (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}
