package com.haneef.school.ui.screens.community

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.SchoolUser
import com.haneef.school.utils.Resource
import com.haneef.school.viewmodel.UserViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val userViewModel: UserViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val context = LocalContext.current

    val schoolId = preferencesManager.getSchoolId() ?: ""
    val usersListState by userViewModel.usersListState.collectAsState()
    val actionState by userViewModel.actionState.collectAsState()

    // Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<String?>(null) } // null means "All"
    var currentPage by remember { mutableStateOf(1) }

    // Dialog states
    var userToDeactivate by remember { mutableStateOf<SchoolUser?>(null) }
    var userToDeverify by remember { mutableStateOf<SchoolUser?>(null) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showDeverifyDialog by remember { mutableStateOf(false) }

    // Fetch initial list
    LaunchedEffect(schoolId, selectedRole, searchQuery, currentPage) {
        if (schoolId.isNotEmpty()) {
            userViewModel.getSchoolUsers(
                schoolId = schoolId,
                role = selectedRole,
                search = searchQuery.takeIf { it.isNotBlank() },
                page = currentPage,
                perPage = 10
            )
        }
    }

    // Handle action state feedback
    LaunchedEffect(actionState) {
        actionState?.let { resource ->
            when (resource) {
                is Resource.Success -> {
                    Toast.makeText(context, "✓ Action executed successfully!", Toast.LENGTH_SHORT).show()
                    userViewModel.clearActionState()
                    // Reload the current page to reflect changes
                    if (schoolId.isNotEmpty()) {
                        userViewModel.getSchoolUsers(
                            schoolId = schoolId,
                            role = selectedRole,
                            search = searchQuery.takeIf { it.isNotBlank() },
                            page = currentPage,
                            perPage = 10
                        )
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(context, "✗ Error: ${resource.message}", Toast.LENGTH_LONG).show()
                    userViewModel.clearActionState()
                }
                is Resource.Loading -> {
                    // Loading dialog can be shown or buttons disabled
                }
            }
        }
    }

    // Deactivation Dialog
    if (showDeactivateDialog && userToDeactivate != null) {
        AlertDialog(
            onDismissRequest = {
                showDeactivateDialog = false
                userToDeactivate = null
            },
            title = {
                Text(
                    text = "Deactivate User",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to deactivate ${userToDeactivate!!.fullName}? They will no longer be able to log in to the application.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userViewModel.deactivateUser(userToDeactivate!!.id)
                        showDeactivateDialog = false
                        userToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Deactivate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeactivateDialog = false
                        userToDeactivate = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Deverification Dialog
    if (showDeverifyDialog && userToDeverify != null) {
        AlertDialog(
            onDismissRequest = {
                showDeverifyDialog = false
                userToDeverify = null
            },
            title = {
                Text(
                    text = "Deverify User",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to de-verify ${userToDeverify!!.fullName}? They will need to re-verify their account before performing restricted actions.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userViewModel.deverifyUser(userToDeverify!!.id)
                        showDeverifyDialog = false
                        userToDeverify = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                ) {
                    Text("Deverify", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeverifyDialog = false
                        userToDeverify = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top breadcrumb and title banner
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
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Group,
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
                    text = " User Management",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "User Directory",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Administer user accounts, security roles, verification, and active state.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        // Filters card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentPage = 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by name or email...", color = Color(0xFF94A3B8)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                currentPage = 1
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF034CD1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // Role Chips Row
                Text(
                    text = "Filter by Role",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val roles = listOf(
                        RoleOption("All", null),
                        RoleOption("Admin", "ADMIN"),
                        RoleOption("Staff", "STAFF"),
                        RoleOption("Parent", "PARENT"),
                        RoleOption("Student", "STUDENT")
                    )

                    roles.forEach { roleOpt ->
                        val isSelected = selectedRole == roleOpt.value
                        val chipBg = if (isSelected) Color(0xFF034CD1) else Color(0xFFF1F5F9)
                        val chipText = if (isSelected) Color.White else Color(0xFF475569)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .clickable {
                                    selectedRole = roleOpt.value
                                    currentPage = 1
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = roleOpt.label,
                                color = chipText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // List Content or Loading/Error overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (val state = usersListState) {
                is Resource.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF034CD1))
                    }
                }
                is Resource.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message ?: "An unexpected error occurred",
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (schoolId.isNotEmpty()) {
                                    userViewModel.getSchoolUsers(
                                        schoolId = schoolId,
                                        role = selectedRole,
                                        search = searchQuery.takeIf { it.isNotBlank() },
                                        page = currentPage,
                                        perPage = 10
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
                is Resource.Success -> {
                    val usersList = state.data?.data ?: emptyList()
                    val pagination = state.data?.pagination

                    if (usersList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = "No results",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No users found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Try adjusting your search criteria or role filters.",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(usersList, key = { it.id }) { user ->
                                    UserCard(
                                        user = user,
                                        onActivateClick = { userViewModel.activateUser(user.id) },
                                        onDeactivateClick = {
                                            userToDeactivate = user
                                            showDeactivateDialog = true
                                        },
                                        onDeverifyClick = {
                                            userToDeverify = user
                                            showDeverifyDialog = true
                                        },
                                        onSendReminderClick = { userViewModel.sendActivationReminder(user.id) }
                                    )
                                }
                            }

                            // Pagination controls
                            pagination?.let { pag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Showing Page ${pag.currentPage} of ${pag.totalPages} (${pag.total} users)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { if (pag.hasPrevious) currentPage-- },
                                            enabled = pag.hasPrevious,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Text("Previous", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { if (pag.hasNext) currentPage++ },
                                            enabled = pag.hasNext,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Text("Next", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Initial or idle state
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: SchoolUser,
    onActivateClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    onDeverifyClick: () -> Unit,
    onSendReminderClick: () -> Unit
) {
    val roleColor = when (user.roleName.uppercase()) {
        "ADMIN", "SCHOOL_ADMIN" -> Color(0xFF6366F1) // Indigo
        "STAFF" -> Color(0xFF10B981) // Green
        "PARENT" -> Color(0xFF8B5CF6) // Purple
        "STUDENT" -> Color(0xFF06B6D4) // Cyan
        else -> Color(0xFF64748B) // Slate
    }

    val initials = if (user.fullName.isNotBlank()) {
        user.fullName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
    } else {
        user.email.take(2).uppercase()
    }

    var isActionsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upper row: Avatar + Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(roleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // User details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    user.phoneNumber?.let {
                        if (it.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                // Inline quick actions trigger
                Box {
                    IconButton(onClick = { isActionsExpanded = !isActionsExpanded }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = Color(0xFF64748B)
                        )
                    }
                    DropdownMenu(
                        expanded = isActionsExpanded,
                        onDismissRequest = { isActionsExpanded = false }
                    ) {
                        if (user.isActive) {
                            DropdownMenuItem(
                                text = { Text("Deactivate User", color = Color(0xFFEF4444)) },
                                onClick = {
                                    isActionsExpanded = false
                                    onDeactivateClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Activate User", color = Color(0xFF10B981)) },
                                onClick = {
                                    isActionsExpanded = false
                                    onActivateClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            )
                        }

                        if (user.isVerified) {
                            DropdownMenuItem(
                                text = { Text("De-verify Account", color = Color(0xFFF97316)) },
                                onClick = {
                                    isActionsExpanded = false
                                    onDeverifyClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = null,
                                        tint = Color(0xFFF97316)
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Send Activation Reminder", color = Color(0xFF034CD1)) },
                                onClick = {
                                    isActionsExpanded = false
                                    onSendReminderClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color(0xFF034CD1)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Middle row: Status Chips/Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role Badge
                StatusBadge(
                    label = user.roleName.replaceFirstChar { it.uppercase() },
                    contentColor = roleColor,
                    containerColor = roleColor.copy(alpha = 0.1f)
                )

                // Active status badge
                if (user.isActive) {
                    StatusBadge(
                        label = "Active",
                        contentColor = Color(0xFF10B981),
                        containerColor = Color(0xFFE6F4EA)
                    )
                } else {
                    StatusBadge(
                        label = "Inactive",
                        contentColor = Color(0xFFEF4444),
                        containerColor = Color(0xFFFCE8E6)
                    )
                }

                // Verification status badge
                if (user.isVerified) {
                    StatusBadge(
                        label = "Verified",
                        contentColor = Color(0xFF034CD1),
                        containerColor = Color(0xFFE8F0FE)
                    )
                } else {
                    StatusBadge(
                        label = "Unverified",
                        contentColor = Color(0xFFD97706),
                        containerColor = Color(0xFFFEF3C7)
                    )
                }

                // Approval Status Badge
                user.isApproved?.let { isApp ->
                    if (isApp) {
                        StatusBadge(
                            label = "Approved",
                            contentColor = Color(0xFF10B981),
                            containerColor = Color(0xFFE6F4EA)
                        )
                    } else {
                        StatusBadge(
                            label = "Pending",
                            contentColor = Color(0xFFD97706),
                            containerColor = Color(0xFFFEF3C7)
                        )
                    }
                }
            }

            // Lower row: Last Active Text & Fast Quick action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastSeen = user.lastLoginAt?.substringBefore("T") ?: "Never"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Last login: $lastSeen",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Quick toggles / quick button inline
                if (!user.isVerified) {
                    TextButton(
                        onClick = onSendReminderClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF034CD1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (user.isActive) {
                    TextButton(
                        onClick = onDeactivateClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deactivate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        onClick = onActivateClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF10B981))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    contentColor: Color,
    containerColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

data class RoleOption(
    val label: String,
    val value: String?
)
