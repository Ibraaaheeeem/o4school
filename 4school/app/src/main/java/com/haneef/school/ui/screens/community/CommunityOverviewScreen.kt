package com.haneef.school.ui.screens.community

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityOverviewScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardViewModel: DashboardViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()

    val accessToken = preferencesManager.getAccessToken() ?: ""

    LaunchedEffect(Unit) {
        if (accessToken.isNotEmpty() && dashboardUiState.dashboardData == null) {
            dashboardViewModel.loadDashboardData(accessToken)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // --- Breadcrumbs & Header ---
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
                    text = " Overview",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Text(
                text = "Community Hub",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "View consolidated directory metrics and manage community registries.",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }

        // --- Main Content Area ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                dashboardUiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF034CD1))
                    }
                }

                dashboardUiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Failed to load community statistics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = dashboardUiState.errorMessage.orEmpty(),
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (accessToken.isNotEmpty()) {
                                        dashboardViewModel.loadDashboardData(accessToken)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034CD1))
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }

                else -> {
                    val adminOverview = dashboardUiState.dashboardData?.adminOverview
                    val totalStudents = adminOverview?.totalStudents ?: 0
                    val totalStaff = adminOverview?.totalStaff ?: adminOverview?.activeStaff ?: 0
                    val totalParents = adminOverview?.totalParents ?: 0
                    val activeSessions = adminOverview?.activeSessions ?: 0
                    val pendingActivations = adminOverview?.pendingActivations ?: 0
                    val totalUsers = activeSessions + pendingActivations

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Welcome Gradient Card
                        WelcomeBanner(schoolName = dashboardUiState.dashboardData?.schoolName ?: "Our School")

                        // Quick Statistics Header
                        Text(
                            text = "Community Breakdown",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        // Quick Statistics Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatWidget(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.School,
                                iconColor = Color(0xFF034CD1),
                                title = "Students",
                                value = totalStudents.toString(),
                                subtext = "Registered learners"
                            )
                            StatWidget(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Badge,
                                iconColor = Color(0xFF10B981),
                                title = "Staff",
                                value = totalStaff.toString(),
                                subtext = "Teachers & Admins"
                            )
                        }

                        // Quick Statistics Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatWidget(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.FamilyRestroom,
                                iconColor = Color(0xFF8B5CF6),
                                title = "Parents",
                                value = totalParents.toString(),
                                subtext = "Family guardians"
                            )
                            StatWidget(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.People,
                                iconColor = Color(0xFF64748B),
                                title = "Users",
                                value = if (totalUsers > 0) totalUsers.toString() else "Active",
                                subtext = if (pendingActivations > 0) "$pendingActivations pending activation" else "All accounts verified"
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Management Modules Header
                        Text(
                            text = "Management Portals",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        // Registry Management Modules
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PortalCard(
                                title = "Student Registry",
                                description = "Manage student profiles, academic status, class enrollments, and personal info. Enroll new students or review existing learners.",
                                icon = Icons.Default.School,
                                iconColor = Color(0xFF034CD1),
                                buttonText = "Manage Students",
                                onClick = { onNavigate("community/students") }
                            )

                            PortalCard(
                                title = "Staff Directory",
                                description = "Oversee teachers, administrative staff, class mentors, and specialized personnel. Assign classes and configure subjects.",
                                icon = Icons.Default.Badge,
                                iconColor = Color(0xFF10B981),
                                buttonText = "Manage Staff",
                                onClick = { onNavigate("community/staff") }
                            )

                            PortalCard(
                                title = "Parent Registry",
                                description = "Maintain parent and guardian contact records. Establish student linkage relationships and view children metrics.",
                                icon = Icons.Default.FamilyRestroom,
                                iconColor = Color(0xFF8B5CF6),
                                buttonText = "Manage Parents",
                                onClick = { onNavigate("community/parents") }
                            )

                            PortalCard(
                                title = "User Accounts",
                                description = "View authenticated platform accounts, monitor active sessions, manage roles, and review pending activation requests.",
                                icon = Icons.Default.People,
                                iconColor = Color(0xFF64748B),
                                buttonText = "Manage Users",
                                onClick = { onNavigate("community/users") }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeBanner(schoolName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4A5FBF), Color(0xFF034CD1))
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Welcome to Community Hub",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = schoolName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Monitor and manage students, parents, staff members, and active system users in one consolidated dashboard.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun StatWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    value: String,
    subtext: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PortalCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF1E293B)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = buttonText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
