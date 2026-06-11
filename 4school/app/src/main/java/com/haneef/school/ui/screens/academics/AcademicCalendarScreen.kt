package com.haneef.school.ui.screens.academics

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.haneef.school.data.models.Term
import com.haneef.school.viewmodel.AcademicCalendarViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicCalendarScreen(
    onBackClick: () -> Unit = {},
    viewModel: AcademicCalendarViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()
    val accessToken = preferencesManager.getAccessToken() ?: ""
    val schoolId = preferencesManager.getSchoolId() ?: ""

    val uiState by viewModel.uiState.collectAsState()

    // Trigger initial load
    LaunchedEffect(Unit) {
        if (accessToken.isNotEmpty() && schoolId.isNotEmpty()) {
            viewModel.loadData(accessToken, schoolId)
        }
    }

    // Dialog state management
    var showSessionDialog by remember { mutableStateOf(false) }
    var selectedSessionForEdit by remember { mutableStateOf<AcademicSession?>(null) }
    var showTermDialog by remember { mutableStateOf(false) }
    var selectedTermForEdit by remember { mutableStateOf<Term?>(null) }
    var selectedSessionForNewTerm by remember { mutableStateOf<AcademicSession?>(null) }

    // Delete confirmation dialogs
    var sessionToDelete by remember { mutableStateOf<AcademicSession?>(null) }
    var termToDelete by remember { mutableStateOf<Term?>(null) }

    // Warning closure dialog states
    var showSessionClosureWarning by remember { mutableStateOf(false) }
    var pendingSessionSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sessionClosureWarningMessage by remember { mutableStateOf("") }

    var showTermClosureWarning by remember { mutableStateOf(false) }
    var pendingTermSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var termClosureWarningMessage by remember { mutableStateOf("") }

    // Helper to format API dates (YYYY-MM-DD) to friendly format (e.g. Sept 4, 2023)
    fun formatFriendlyDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "Open ended"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val date = inputFormat.parse(dateStr)
            date?.let { outputFormat.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Academic Calendar",
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
                    selectedSessionForEdit = null
                    showSessionDialog = true
                },
                icon = { Icon(Icons.Default.Add, "Add Session") },
                text = { Text("New Session") },
                containerColor = Color(0xFF4A5FBF),
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            if (uiState.isLoading && uiState.sessions.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF4A5FBF)
                )
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Description text
                    Text(
                        text = "Manage the school's session timelines, terms, and holiday structures from a central location.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val currentSession = uiState.sessions.find { it.isCurrent }
                    val otherSessions = uiState.sessions.filter { !it.isCurrent }

                    // 1. Current Session Card
                    if (currentSession != null) {
                        val currentSessionTerms = uiState.termsMap[currentSession.id] ?: emptyList()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEFF6FF))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "CURRENT SESSION",
                                            color = Color(0xFF3B82F6),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            selectedSessionForEdit = currentSession
                                            showSessionDialog = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit session",
                                                tint = Color(0xFF64748B)
                                            )
                                        }
                                        IconButton(onClick = {
                                            sessionToDelete = currentSession
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete session",
                                                tint = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${currentSession.name} Academic Session",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3A8A),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Calendar",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${formatFriendlyDate(currentSession.startDate)} — ${formatFriendlyDate(currentSession.endDate)}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SESSION TERMS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            selectedSessionForNewTerm = currentSession
                                            selectedTermForEdit = null
                                            showTermDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "Add term",
                                            tint = Color(0xFF4A5FBF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add Term",
                                            color = Color(0xFF4A5FBF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (currentSessionTerms.isEmpty()) {
                                        Text(
                                            text = "No terms added to this session yet.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        )
                                    } else {
                                        currentSessionTerms.forEachIndexed { index, term ->
                                            val numberSuffix = when (term.termNumber) {
                                                1 -> "1st"
                                                2 -> "2nd"
                                                3 -> "3rd"
                                                else -> "${term.termNumber}th"
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        1.dp,
                                                        Color(0xFFE2E8F0),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .background(Color(0xFFF8FAFC))
                                                    .clickable {
                                                        selectedSessionForNewTerm = currentSession
                                                        selectedTermForEdit = term
                                                        showTermDialog = true
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEFF6FF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = numberSuffix,
                                                        color = Color(0xFF3B82F6),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = term.name,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = Color(0xFF334155)
                                                        )
                                                        if (term.isCurrent) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF10B981))
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "LIVE",
                                                                color = Color(0xFF10B981),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 9.sp
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${formatFriendlyDate(term.startDate)} — ${formatFriendlyDate(term.endDate)}",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF64748B),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        termToDelete = term
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete term",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Prompt to create first session
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventNote,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No current active session",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Create an academic session to manage terms and dates.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        selectedSessionForEdit = null
                                        showSessionDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                                ) {
                                    Text("Create Session")
                                }
                            }
                        }
                    }

                    // 2. Other Sessions Section
                    Text(
                        text = "Other Sessions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (otherSessions.isEmpty()) {
                        Text(
                            text = "No other academic sessions records.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        otherSessions.forEach { session ->
                            val statusLabel = if (session.startDate > SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) "PLANNED" else "ARCHIVED"
                            val statusColor = if (statusLabel == "PLANNED") Color(0xFF64748B) else Color(0xFF94A3B8)
                            val statusBg = if (statusLabel == "PLANNED") Color(0xFFF1F5F9) else Color(0xFFF8FAFC)

                            var showDropdownMenu by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = borderStrokeForSession(session.isCurrent),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${session.name} Session",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(statusBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Session duration",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (statusLabel == "ARCHIVED") "Completed ${formatFriendlyDate(session.endDate)}" else "${formatFriendlyDate(session.startDate)} — ${formatFriendlyDate(session.endDate)}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                selectedSessionForNewTerm = session
                                                // trigger term viewing or expansion
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (statusLabel == "PLANNED") Color(0xFFE0E7FF) else Color(0xFFF8FAFC),
                                                contentColor = if (statusLabel == "PLANNED") Color(0xFF4F46E5) else Color(0xFF475569)
                                            ),
                                            border = if (statusLabel == "ARCHIVED") BorderStroke(1.dp, Color(0xFFE2E8F0)) else null
                                        ) {
                                            Text(
                                                text = if (statusLabel == "PLANNED") "Manage Terms" else "View Records",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Box {
                                            IconButton(
                                                onClick = { showDropdownMenu = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Actions",
                                                    tint = Color(0xFF64748B)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showDropdownMenu,
                                                onDismissRequest = { showDropdownMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit Session") },
                                                    onClick = {
                                                        showDropdownMenu = false
                                                        selectedSessionForEdit = session
                                                        showSessionDialog = true
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete Session", color = Color.Red) },
                                                    onClick = {
                                                        showDropdownMenu = false
                                                        sessionToDelete = session
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

                    // 3. Bottom Cards grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Blue Card: Days Elapsed
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Days Elapsed (Current Term)",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "72 / 102",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                    LinearProgressIndicator(
                                        progress = { 72f / 102f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }

                        // Green Card: Public Holidays
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Public Holidays",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "12 Days",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                        Text(
                                            text = "Next: National Day (Oct 1)",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Custom FAB overlay inside Card
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clickable { /* Add Holiday Action */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add holiday",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Snackbar notification observer
            uiState.actionSuccessMessage?.let { msg ->
                LaunchedEffect(msg) {
                    val toast = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG)
                    toast.show()
                    viewModel.clearSuccessMessage()
                }
            }

            uiState.error?.let { err ->
                LaunchedEffect(err) {
                    val toast = android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG)
                    toast.show()
                    viewModel.clearError()
                }
            }

            // ─── DIALOGS ───

            // 1. Session Create/Edit Dialog
            if (showSessionDialog) {
                var name by remember { mutableStateOf(selectedSessionForEdit?.name ?: "") }
                var startDate by remember { mutableStateOf(selectedSessionForEdit?.startDate ?: "") }
                var endDate by remember { mutableStateOf(selectedSessionForEdit?.endDate ?: "") }
                var isCurrent by remember { mutableStateOf(selectedSessionForEdit?.isCurrent ?: false) }

                AlertDialog(
                    onDismissRequest = { showSessionDialog = false },
                    title = { Text(if (selectedSessionForEdit == null) "Create Academic Session" else "Edit Academic Session") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Session Name (e.g. 2023/2024)") },
                                placeholder = { Text("YYYY/YYYY") },
                                modifier = Modifier.fillMaxWidth()
                            )

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

                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { endDate = it },
                                label = { Text("End Date (YYYY-MM-DD)") },
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(checked = isCurrent, onCheckedChange = { isCurrent = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set as current active session")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (name.isNotBlank() && startDate.isNotBlank() && (isCurrent || endDate.isNotBlank())) {
                                    val finalEndDate = if (endDate.isBlank()) null else endDate
                                    val saveAction = {
                                        if (selectedSessionForEdit == null) {
                                            viewModel.createAcademicSession(
                                                accessToken, schoolId, name, startDate, finalEndDate, isCurrent
                                            )
                                        } else {
                                            viewModel.updateAcademicSession(
                                                selectedSessionForEdit!!.id, accessToken, schoolId, name, startDate, finalEndDate, isCurrent
                                            )
                                        }
                                        showSessionDialog = false
                                    }

                                    if (isCurrent) {
                                        val existingCurrentWithNoEndDate = uiState.sessions.find {
                                            it.isCurrent && it.id != selectedSessionForEdit?.id && it.endDate.isNullOrBlank()
                                        }
                                        if (existingCurrentWithNoEndDate != null) {
                                            sessionClosureWarningMessage = "The existing current session '${existingCurrentWithNoEndDate.name}' does not have an end date. If you proceed, today's date will be entered as its end date. Do you want to proceed?"
                                            pendingSessionSaveAction = saveAction
                                            showSessionClosureWarning = true
                                        } else {
                                            saveAction()
                                        }
                                    } else {
                                        saveAction()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSessionDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 2. Term Create/Edit Dialog
            if (showTermDialog) {
                var name by remember { mutableStateOf(selectedTermForEdit?.name ?: "") }
                var termNumber by remember { mutableStateOf(selectedTermForEdit?.termNumber?.toString() ?: "") }
                var startDate by remember { mutableStateOf(selectedTermForEdit?.startDate ?: "") }
                var endDate by remember { mutableStateOf(selectedTermForEdit?.endDate ?: "") }
                var isCurrent by remember { mutableStateOf(selectedTermForEdit?.isCurrent ?: false) }

                AlertDialog(
                    onDismissRequest = { showTermDialog = false },
                    title = { Text(if (selectedTermForEdit == null) "Add Term" else "Edit Term") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Term Name (e.g. First Term)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = termNumber,
                                onValueChange = { termNumber = it },
                                label = { Text("Term Number (e.g. 1, 2, 3)") },
                                modifier = Modifier.fillMaxWidth()
                            )

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

                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { endDate = it },
                                label = { Text("End Date (YYYY-MM-DD)") },
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(checked = isCurrent, onCheckedChange = { isCurrent = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set as current active term")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val session = selectedSessionForNewTerm ?: return@Button
                                val num = termNumber.toIntOrNull() ?: 1
                                if (name.isNotBlank() && startDate.isNotBlank() && (isCurrent || endDate.isNotBlank())) {
                                    val finalEndDate = if (endDate.isBlank()) null else endDate
                                    val saveAction = {
                                        if (selectedTermForEdit == null) {
                                            viewModel.createTerm(
                                                accessToken, schoolId, session.id, name, num, startDate, finalEndDate, isCurrent
                                            )
                                        } else {
                                            viewModel.updateTerm(
                                                selectedTermForEdit!!.id, accessToken, schoolId, session.id, name, num, startDate, finalEndDate, isCurrent
                                            )
                                        }
                                        showTermDialog = false
                                    }

                                    if (isCurrent) {
                                        val existingTerms = uiState.termsMap[session.id]
                                        val existingCurrentWithNoEndDate = existingTerms?.find {
                                            it.isCurrent && it.id != selectedTermForEdit?.id && it.endDate.isNullOrBlank()
                                        }
                                        if (existingCurrentWithNoEndDate != null) {
                                            termClosureWarningMessage = "The existing current term '${existingCurrentWithNoEndDate.name}' does not have an end date. If you proceed, today's date will be entered as its end date. Do you want to proceed?"
                                            pendingTermSaveAction = saveAction
                                            showTermClosureWarning = true
                                        } else {
                                            saveAction()
                                        }
                                    } else {
                                        saveAction()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTermDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 3. Delete Session Confirmation Dialog
            sessionToDelete?.let { session ->
                AlertDialog(
                    onDismissRequest = { sessionToDelete = null },
                    title = { Text("Delete Academic Session") },
                    text = { Text("Are you sure you want to delete ${session.name} Session? This will deactivate the session and all terms under it.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteAcademicSession(session.id, accessToken, schoolId)
                                sessionToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { sessionToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 4. Delete Term Confirmation Dialog
            termToDelete?.let { term ->
                AlertDialog(
                    onDismissRequest = { termToDelete = null },
                    title = { Text("Delete Term") },
                    text = { Text("Are you sure you want to delete ${term.name}? This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteTerm(term.id, accessToken, schoolId)
                                termToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { termToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 5. Session Closure Warning Dialog
            if (showSessionClosureWarning) {
                AlertDialog(
                    onDismissRequest = {
                        showSessionClosureWarning = false
                        pendingSessionSaveAction = null
                    },
                    title = { Text("Active Session Closure Warning") },
                    text = { Text(sessionClosureWarningMessage) },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingSessionSaveAction?.invoke()
                                showSessionClosureWarning = false
                                pendingSessionSaveAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Proceed")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSessionClosureWarning = false
                                pendingSessionSaveAction = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 6. Term Closure Warning Dialog
            if (showTermClosureWarning) {
                AlertDialog(
                    onDismissRequest = {
                        showTermClosureWarning = false
                        pendingTermSaveAction = null
                    },
                    title = { Text("Active Term Closure Warning") },
                    text = { Text(termClosureWarningMessage) },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingTermSaveAction?.invoke()
                                showTermClosureWarning = false
                                pendingTermSaveAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Proceed")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showTermClosureWarning = false
                                pendingTermSaveAction = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

private fun borderStrokeForSession(isCurrent: Boolean): BorderStroke? {
    return if (isCurrent) BorderStroke(1.5.dp, Color(0xFF3B82F6)) else null
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
