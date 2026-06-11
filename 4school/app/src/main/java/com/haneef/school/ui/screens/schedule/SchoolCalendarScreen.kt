package com.haneef.school.ui.screens.schedule

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.AcademicSession
import com.haneef.school.data.models.SchoolCalendar
import com.haneef.school.data.models.Term
import com.haneef.school.viewmodel.SchoolCalendarViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolCalendarScreen(
    onBackClick: () -> Unit = {},
    viewModel: SchoolCalendarViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()
    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""

    val uiState by viewModel.uiState.collectAsState()

    // Load calendar events, sessions, and terms
    LaunchedEffect(Unit) {
        if (accessToken.isNotEmpty() && schoolId.isNotEmpty()) {
            viewModel.loadData(accessToken, schoolId)
        }
    }

    // Dialogue trigger states
    var showEventDialog by remember { mutableStateOf(false) }
    var selectedEventForEdit by remember { mutableStateOf<SchoolCalendar?>(null) }
    var eventToDelete by remember { mutableStateOf<SchoolCalendar?>(null) }

    // Helpers to format database dates (YYYY-MM-DD) to friendly format
    fun formatFriendlyDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "Single day"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val date = inputFormat.parse(dateStr)
            date?.let { outputFormat.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    // Observer for alerts
    uiState.successMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    uiState.error?.let { err ->
        LaunchedEffect(err) {
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "School Calendar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1E293B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF4A5FBF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E293B)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    selectedEventForEdit = null
                    showEventDialog = true
                },
                icon = { Icon(Icons.Default.Add, "Add Event") },
                text = { Text("New Event") },
                containerColor = Color(0xFF4A5FBF),
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.events.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF4A5FBF)
                )
            } else if (uiState.events.isEmpty()) {
                // Empty state view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No calendar events found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create school events, public holidays, or examination schedules by tapping 'New Event'.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Central scheduling for public holidays, exams, term breaks, and campus-wide events.",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(uiState.events) { event ->
                        var showMenu by remember { mutableStateOf(false) }
                        
                        // Parse Hex code or fallback to standard color matching
                        val eventColor = try {
                            if (!event.color.isNullOrBlank()) Color(android.graphics.Color.parseColor(event.color))
                            else when (event.eventType.uppercase()) {
                                "HOLIDAY" -> Color(0xFFDC2626) // Red
                                "EXAM_PERIOD" -> Color(0xFFD97706) // Amber
                                else -> Color(0xFF2563EB) // Blue
                            }
                        } catch (e: Exception) {
                            Color(0xFF4A5FBF)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Event Type color bar indicator
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(eventColor)
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = event.eventName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        // Badge
                                        val badgeText = when {
                                            event.isHoliday == true -> "Holiday"
                                            event.isExamPeriod == true -> "Exams"
                                            else -> event.eventType.replace("_", " ").lowercase()
                                                .replaceFirstChar { it.uppercase() }
                                        }
                                        val badgeBg = if (event.isHoliday == true) Color(0xFFFEE2E2) else Color(0xFFEFF6FF)
                                        val badgeColor = if (event.isHoliday == true) Color(0xFFEF4444) else Color(0xFF3B82F6)
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                color = badgeColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Event Dates
                                    val dateDisplay = if (event.endDate.isNullOrBlank() || event.startDate == event.endDate) {
                                        formatFriendlyDate(event.startDate)
                                    } else {
                                        "${formatFriendlyDate(event.startDate)} — ${formatFriendlyDate(event.endDate)}"
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateDisplay,
                                            fontSize = 13.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    if (!event.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = event.description,
                                            fontSize = 13.sp,
                                            color = Color(0xFF475569),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Actions",
                                            tint = Color(0xFF64748B)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit Event") },
                                            onClick = {
                                                showMenu = false
                                                selectedEventForEdit = event
                                                showEventDialog = true
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Event", color = Color.Red) },
                                            onClick = {
                                                showMenu = false
                                                eventToDelete = event
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── DIALOGS ───

            // 1. Create/Edit Event Dialog
            if (showEventDialog) {
                var eventName by remember { mutableStateOf(selectedEventForEdit?.eventName ?: "") }
                var eventType by remember { mutableStateOf(selectedEventForEdit?.eventType ?: "GENERAL_EVENT") }
                var startDate by remember { mutableStateOf(selectedEventForEdit?.startDate ?: "") }
                var endDate by remember { mutableStateOf(selectedEventForEdit?.endDate ?: "") }
                var description by remember { mutableStateOf(selectedEventForEdit?.description ?: "") }
                var isHoliday by remember { mutableStateOf(selectedEventForEdit?.isHoliday ?: false) }
                var isExamPeriod by remember { mutableStateOf(selectedEventForEdit?.isExamPeriod ?: false) }
                
                var selectedSessionId by remember { 
                    mutableStateOf(selectedEventForEdit?.sessionId ?: uiState.sessions.find { it.isCurrent }?.id ?: "") 
                }
                var selectedTermId by remember { 
                    mutableStateOf<String?>(selectedEventForEdit?.termId) 
                }
                var selectedColorName by remember { 
                    mutableStateOf(
                        when(selectedEventForEdit?.color?.uppercase()) {
                            "#DC2626" -> "Red"
                            "#D97706" -> "Amber"
                            "#16A34A" -> "Green"
                            "#2563EB" -> "Blue"
                            "#9333EA" -> "Purple"
                            else -> "Blue"
                        }
                    ) 
                }

                val colorMap = mapOf(
                    "Blue" to "#2563EB",
                    "Red" to "#DC2626",
                    "Amber" to "#D97706",
                    "Green" to "#16A34A",
                    "Purple" to "#9333EA"
                )

                // Sync holiday/exam switches with event type selection
                LaunchedEffect(eventType) {
                    if (selectedEventForEdit == null) {
                        isHoliday = (eventType == "HOLIDAY")
                        isExamPeriod = (eventType == "EXAM_PERIOD")
                        selectedColorName = when (eventType) {
                            "HOLIDAY" -> "Red"
                            "EXAM_PERIOD" -> "Amber"
                            else -> "Blue"
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { showEventDialog = false },
                    title = { Text(if (selectedEventForEdit == null) "Create Calendar Event" else "Edit Calendar Event") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Event Name
                            OutlinedTextField(
                                value = eventName,
                                onValueChange = { eventName = it },
                                label = { Text("Event Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Event Type Dropdown
                            var typeDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = eventType.replace("_", " "),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Event Type") },
                                    trailingIcon = { IconButton(onClick = { typeDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }},
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(
                                    expanded = typeDropdownExpanded,
                                    onDismissRequest = { typeDropdownExpanded = false }
                                ) {
                                    listOf("GENERAL_EVENT", "HOLIDAY", "EXAM_PERIOD", "TERM_BREAK").forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.replace("_", " ")) },
                                            onClick = {
                                                eventType = type
                                                typeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Start Date
                            OutlinedTextField(
                                value = startDate,
                                onValueChange = { startDate = it },
                                label = { Text("Start Date (YYYY-MM-DD)") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showDatePicker(context, startDate) { startDate = it }
                                    }) {
                                        Icon(Icons.Default.CalendarToday, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // End Date (Optional for single day events)
                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { endDate = it },
                                label = { Text("End Date (YYYY-MM-DD) - Optional") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showDatePicker(context, endDate) { endDate = it }
                                    }) {
                                        Icon(Icons.Default.CalendarToday, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Description
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Color Selector Dropdown
                            var colorDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedColorName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Color Tag") },
                                    trailingIcon = { IconButton(onClick = { colorDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }},
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(
                                    expanded = colorDropdownExpanded,
                                    onDismissRequest = { colorDropdownExpanded = false }
                                ) {
                                    colorMap.keys.forEach { colName ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(android.graphics.Color.parseColor(colorMap[colName])))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(colName)
                                                }
                                            },
                                            onClick = {
                                                selectedColorName = colName
                                                colorDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Academic Session Selector
                            var sessionDropdownExpanded by remember { mutableStateOf(false) }
                            val currentSessionName = uiState.sessions.find { it.id == selectedSessionId }?.name ?: "Select Session"
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = currentSessionName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Academic Session") },
                                    trailingIcon = { IconButton(onClick = { sessionDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }},
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(
                                    expanded = sessionDropdownExpanded,
                                    onDismissRequest = { sessionDropdownExpanded = false }
                                ) {
                                    uiState.sessions.forEach { sess ->
                                        DropdownMenuItem(
                                            text = { Text(sess.name) },
                                            onClick = {
                                                selectedSessionId = sess.id
                                                selectedTermId = null // Reset selected term when session changes
                                                sessionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Term Selector (Optional)
                            var termDropdownExpanded by remember { mutableStateOf(false) }
                            val termsInSession = uiState.termsMap[selectedSessionId] ?: emptyList()
                            val currentTermName = termsInSession.find { it.id == selectedTermId }?.name ?: "No Term Selected"
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = currentTermName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Associated Term (Optional)") },
                                    trailingIcon = { IconButton(onClick = { termDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }},
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(
                                    expanded = termDropdownExpanded,
                                    onDismissRequest = { termDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None") },
                                        onClick = {
                                            selectedTermId = null
                                            termDropdownExpanded = false
                                        }
                                    )
                                    termsInSession.forEach { term ->
                                        DropdownMenuItem(
                                            text = { Text(term.name) },
                                            onClick = {
                                                selectedTermId = term.id
                                                termDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Holiday and Exam toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isHoliday, onCheckedChange = { isHoliday = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("This event is a public holiday")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isExamPeriod, onCheckedChange = { isExamPeriod = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("This event marks an exam period")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (eventName.isNotBlank() && startDate.isNotBlank() && selectedSessionId.isNotBlank()) {
                                    val finalEndDate = if (endDate.isBlank()) null else endDate
                                    val finalColor = colorMap[selectedColorName]
                                    val finalDescription = if (description.isBlank()) null else description
                                    
                                    if (selectedEventForEdit == null) {
                                        viewModel.createCalendarEvent(
                                            accessToken = accessToken,
                                            schoolId = schoolId,
                                            sessionId = selectedSessionId,
                                            termId = selectedTermId,
                                            eventName = eventName,
                                            eventType = eventType,
                                            startDate = startDate,
                                            endDate = finalEndDate,
                                            color = finalColor,
                                            description = finalDescription,
                                            isExamPeriod = isExamPeriod,
                                            isHoliday = isHoliday
                                        )
                                    } else {
                                        viewModel.updateCalendarEvent(
                                            eventId = selectedEventForEdit!!.id,
                                            accessToken = accessToken,
                                            schoolId = schoolId,
                                            sessionId = selectedSessionId,
                                            termId = selectedTermId,
                                            eventName = eventName,
                                            eventType = eventType,
                                            startDate = startDate,
                                            endDate = finalEndDate,
                                            color = finalColor,
                                            description = finalDescription,
                                            isExamPeriod = isExamPeriod,
                                            isHoliday = isHoliday
                                        )
                                    }
                                    showEventDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEventDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 2. Delete Confirmation Dialog
            eventToDelete?.let { event ->
                AlertDialog(
                    onDismissRequest = { eventToDelete = null },
                    title = { Text("Delete Event") },
                    text = { Text("Are you sure you want to delete the event '${event.eventName}'? This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteCalendarEvent(event.id, accessToken, schoolId)
                                eventToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { eventToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

// Helper to launch standard date picker dialog
private fun showDatePicker(
    context: android.content.Context,
    currentDateStr: String,
    onDateSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    if (currentDateStr.isNotBlank()) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(currentDateStr)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            // fallback to current time
        }
    }

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}
