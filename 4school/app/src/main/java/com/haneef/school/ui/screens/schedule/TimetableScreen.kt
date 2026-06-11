package com.haneef.school.ui.screens.schedule

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.SchoolTimetable
import com.haneef.school.viewmodel.TimetableViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun TimetableScreen(
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: TimetableViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val preferencesManager: PreferencesManager = koinInject()
    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""
    val context = LocalContext.current

    // Dialog trigger states
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItemForEdit by remember { mutableStateOf<SchoolTimetable?>(null) }
    var itemToDelete by remember { mutableStateOf<SchoolTimetable?>(null) }

    // Retrieve Sunday of current week dynamically to calculate Sunday to Saturday (Sunday to Monday)
    val today = remember { LocalDate.now() }
    val sunday = remember { today.minusDays(today.dayOfWeek.value.toLong() % 7) }
    val weekDays = remember { (0..6).map { sunday.plusDays(it.toLong()) } }

    // Date formatter for header date display
    val headerDateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US) }

    // Filtered items based on selected day of the week
    val filteredItems = remember(uiState.timetableItems, uiState.selectedDay) {
        uiState.timetableItems
            .filter { it.dayOfWeek.trim().uppercase() == uiState.selectedDay.trim().uppercase() }
            .sortedBy { it.startTime }
    }

    LaunchedEffect(Unit) {
        if (accessToken.isNotEmpty() && schoolId.isNotEmpty()) {
            viewModel.loadData(accessToken, schoolId)
        }
    }

    // Snackbar alerts
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF1E3A8A),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add Activity")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAFAFA))
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ─── HEADER SECTION ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color(0xFF1E3A8A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "General Timetable",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Retrieve date corresponding to currently selected DayOfWeek
                    val selectedDate = remember(uiState.selectedDay) {
                        val selectedDayOfWeek = try {
                            DayOfWeek.valueOf(uiState.selectedDay)
                        } catch (e: Exception) {
                            DayOfWeek.MONDAY
                        }
                        today.with(selectedDayOfWeek)
                    }
                    Text(
                        text = selectedDate.format(headerDateFormatter),
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── HORIZONTAL WEEKDAY SELECTOR ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { date ->
                    val dayName = date.dayOfWeek.name
                    val dayNum = date.dayOfMonth.toString()
                    val isSelected = uiState.selectedDay == dayName

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF1E3A8A) else Color.Transparent)
                            .clickable { viewModel.setSelectedDay(dayName) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dayName.take(1),
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dayNum,
                                fontSize = 16.sp,
                                color = if (isSelected) Color.White else Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── TIMELINE EVENTS LIST ───
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1E3A8A))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (filteredItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventNote,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No activities scheduled",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(filteredItems) { index, item ->
                            TimelineItemRow(
                                item = item,
                                isFirst = index == 0,
                                isLast = index == filteredItems.lastIndex,
                                onEditClick = { selectedItemForEdit = item },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }

                    // Campus image card placed at the very bottom of timeline scroll
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    // ─── DIALOGS ───

    // 1. Add/Edit Dialog
    val showDialog = showAddDialog || selectedItemForEdit != null
    if (showDialog) {
        val isEdit = selectedItemForEdit != null
        var title by remember { mutableStateOf(selectedItemForEdit?.title ?: "") }
        var description by remember { mutableStateOf(selectedItemForEdit?.description ?: "") }
        var activityType by remember { mutableStateOf(selectedItemForEdit?.activityType ?: "CLASS") }
        var startTime by remember { mutableStateOf(selectedItemForEdit?.startTime ?: "08:00") }
        var endTime by remember { mutableStateOf(selectedItemForEdit?.endTime ?: "09:00") }
        
        // Multi-select days state (contains set of uppercase day strings)
        var selectedDays by remember {
            mutableStateOf(
                if (isEdit) {
                    setOf(selectedItemForEdit?.dayOfWeek ?: uiState.selectedDay)
                } else {
                    setOf(uiState.selectedDay)
                }
            )
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                selectedItemForEdit = null
            },
            title = { Text(if (isEdit) "Edit Activity" else "Add Activity") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g. Math, Recess)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Details / Location (e.g. Great Hall)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Activity Type Selector
                    Column {
                        Text("Activity Type", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        val types = listOf("CLASS", "BREAK", "LUNCH", "EVENT", "DISMISSAL")
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .clickable { expanded = true }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(activityType)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t) },
                                        onClick = {
                                            activityType = t
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Day Selector Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isEdit) "Day of Week" else "Select Day(s)", fontSize = 12.sp, color = Color.Gray)
                        
                        if (!isEdit) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedDays = setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Weekdays", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        selectedDays = setOf("SATURDAY", "SUNDAY")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Weekends", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        selectedDays = setOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("All Days", fontSize = 11.sp)
                                }
                            }
                        }

                        val daysList = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
                        val daysAbbr = mapOf(
                            "SUNDAY" to "Su",
                            "MONDAY" to "Mo",
                            "TUESDAY" to "Tu",
                            "WEDNESDAY" to "We",
                            "THURSDAY" to "Th",
                            "FRIDAY" to "Fr",
                            "SATURDAY" to "Sa"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            daysList.forEach { d ->
                                val isDaySelected = selectedDays.contains(d)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDaySelected) Color(0xFF1E3A8A) else Color(0xFFF1F5F9))
                                        .clickable {
                                            if (isEdit) {
                                                selectedDays = setOf(d)
                                            } else {
                                                selectedDays = if (isDaySelected) {
                                                    if (selectedDays.size > 1) selectedDays - d else selectedDays
                                                } else {
                                                    selectedDays + d
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = daysAbbr[d] ?: d.take(2),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDaySelected) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }

                    // Start & End Time Pickers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { },
                            label = { Text("Start Time") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    showTimePicker(context, startTime) { startTime = it }
                                }) {
                                    Icon(Icons.Default.Schedule, null)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { },
                            label = { Text("End Time") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    showTimePicker(context, endTime) { endTime = it }
                                }) {
                                    Icon(Icons.Default.Schedule, null)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && selectedDays.isNotEmpty()) {
                            if (isEdit) {
                                viewModel.updateTimetableItem(
                                    itemId = selectedItemForEdit!!.id,
                                    accessToken = accessToken,
                                    schoolId = schoolId,
                                    classId = null,
                                    dayOfWeek = selectedDays.first(),
                                    activityType = activityType,
                                    startTime = startTime,
                                    endTime = endTime,
                                    title = title,
                                    description = if (description.isBlank()) null else description
                                )
                            } else {
                                viewModel.createTimetableItem(
                                    accessToken = accessToken,
                                    schoolId = schoolId,
                                    classId = null,
                                    dayOfWeek = null,
                                    daysOfWeek = selectedDays.toList(),
                                    activityType = activityType,
                                    startTime = startTime,
                                    endTime = endTime,
                                    title = title,
                                    description = if (description.isBlank()) null else description
                                )
                            }
                            showAddDialog = false
                            selectedItemForEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    selectedItemForEdit = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Activity") },
            text = { Text("Are you sure you want to delete '${item.title}' from the timetable?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTimetableItem(item.id, accessToken, schoolId)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimelineItemRow(
    item: SchoolTimetable,
    isFirst: Boolean,
    isLast: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Resolve colors and icons matching the mockup
    val (iconBgColor, iconTintColor, leftBorderColor, timeBgColor, timeTextColor, icon) = remember(item.activityType, item.title) {
        val type = item.activityType.trim().uppercase()
        val titleLower = item.title.lowercase()
        when {
            titleLower.contains("assembly") || type == "EVENT" && titleLower.contains("assembly") -> {
                TimetableItemStyle(
                    iconBgColor = Color(0xFFEFF6FF), // icon bg
                    iconTintColor = Color(0xFF1E40AF), // icon tint
                    leftBorderColor = Color(0xFF1E3A8A), // left border
                    timeBgColor = Color(0xFFEFF6FF), // time bg
                    timeTextColor = Color(0xFF1E40AF), // time text
                    icon = Icons.Default.NotificationsActive
                )
            }
            type == "CLASS" -> {
                TimetableItemStyle(
                    iconBgColor = Color(0xFFF1F5F9),
                    iconTintColor = Color(0xFF64748B),
                    leftBorderColor = Color(0xFF94A3B8),
                    timeBgColor = Color(0xFFF1F5F9),
                    timeTextColor = Color(0xFF475569),
                    icon = Icons.Default.Schedule
                )
            }
            titleLower.contains("break") || titleLower.contains("recess") || type == "BREAK" -> {
                TimetableItemStyle(
                    iconBgColor = Color(0xFFECFDF5),
                    iconTintColor = Color(0xFF10B981),
                    leftBorderColor = Color(0xFF10B981),
                    timeBgColor = Color(0xFFECFDF5),
                    timeTextColor = Color(0xFF047857),
                    icon = Icons.Default.LocalCafe
                )
            }
            type == "LUNCH" || titleLower.contains("lunch") -> {
                TimetableItemStyle(
                    iconBgColor = Color(0xFFEEF2FF),
                    iconTintColor = Color(0xFF4F46E5),
                    leftBorderColor = Color(0xFF4F46E5),
                    timeBgColor = Color(0xFFEEF2FF),
                    timeTextColor = Color(0xFF4338CA),
                    icon = Icons.Default.Restaurant
                )
            }
            type == "DISMISSAL" || titleLower.contains("dismissal") -> {
                TimetableItemStyle(
                    iconBgColor = Color(0xFFFEF2F2),
                    iconTintColor = Color(0xFFEF4444),
                    leftBorderColor = Color(0xFFEF4444),
                    timeBgColor = Color(0xFFFEF2F2),
                    timeTextColor = Color(0xFFB91C1C),
                    icon = Icons.AutoMirrored.Filled.Logout
                )
            }
            else -> { // Default Event or Extracurricular
                TimetableItemStyle(
                    iconBgColor = Color(0xFFF8FAFC),
                    iconTintColor = Color(0xFF475569),
                    leftBorderColor = Color(0xFF64748B),
                    timeBgColor = Color(0xFFF1F5F9),
                    timeTextColor = Color(0xFF334155),
                    icon = Icons.Default.Groups
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 24.dp.toPx()
                val startY = if (isFirst) 26.dp.toPx() else 0f
                val endY = if (isLast) 26.dp.toPx() else size.height
                if (!isFirst || !isLast) {
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = androidx.compose.ui.geometry.Offset(x, startY),
                        end = androidx.compose.ui.geometry.Offset(x, endY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            },
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column (left line + circle node)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            // Circular icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content card representing the activity
        var showMenu by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Left indicator vertical stripe
                            drawRect(
                                color = leftBorderColor,
                                size = androidx.compose.ui.geometry.Size(width = 6.dp.toPx(), height = size.height)
                            )
                        }
                        .clickable { showMenu = true }
                        .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val desc = item.description
                        if (!desc.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.activityType == "CLASS") {
                                    Text(
                                        text = desc,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Time range pill/capsule
                    val timeString = if (item.startTime == item.endTime) {
                        item.startTime
                    } else {
                        "${item.startTime} - ${item.endTime}"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(timeBgColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeString,
                            color = timeTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Activity") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Activity", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}

// Timepicker dialog trigger helper
private fun showTimePicker(
    context: android.content.Context,
    currentTimeStr: String,
    onTimeSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    if (currentTimeStr.isNotBlank()) {
        try {
            val parts = currentTimeStr.split(":")
            if (parts.size >= 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val formattedTime = String.format("%02d:%02d", hourOfDay, minute)
            onTimeSelected(formattedTime)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true // 24hr format
    ).show()
}

private data class TimetableItemStyle(
    val iconBgColor: Color,
    val iconTintColor: Color,
    val leftBorderColor: Color,
    val timeBgColor: Color,
    val timeTextColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
