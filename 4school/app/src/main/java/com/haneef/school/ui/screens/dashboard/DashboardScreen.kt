package com.haneef.school.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.models.CriticalAlert
import com.haneef.school.data.models.DashboardResponse
import com.haneef.school.viewmodel.DashboardUiState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll

@Composable
fun DashboardScreen(
    dashboardUiState: DashboardUiState,
    onDashboardOpened: () -> Unit
) {
    LaunchedEffect(Unit) {
        onDashboardOpened()
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            dashboardUiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF4A5FBF))
                }
            }

            dashboardUiState.errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to load dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dashboardUiState.errorMessage.orEmpty(),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDashboardOpened,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }

            dashboardUiState.dashboardData != null -> {
                DashboardContentWithData(
                    dashboardData = dashboardUiState.dashboardData,
                    scrollState = scrollState
                )
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading dashboard...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContentWithData(
    dashboardData: DashboardResponse,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (dashboardData.role.uppercase()) {
            "SCHOOL_ADMIN", "ADMIN" -> {
                dashboardData.adminOverview?.let { adminOverview ->
                    DashboardSectionHeader("Academic Overview")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.People,
                            iconTint = Color(0xFF4A5FBF),
                            label = "Total Students",
                            value = adminOverview.totalStudents?.toString() ?: "N/A",
                            trend = "Active enrollment",
                            trendPositive = true
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Badge,
                            iconTint = Color(0xFF2E7D32),
                            label = "Total Staff",
                            value = adminOverview.totalStaff?.toString() ?: adminOverview.activeStaff?.toString() ?: "N/A",
                            trend = "Full strength",
                            trendPositive = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.FamilyRestroom,
                            iconTint = Color(0xFF9C27B0),
                            label = "Total Parents",
                            value = adminOverview.totalParents?.toString() ?: "N/A",
                            trend = "Registered",
                            trendPositive = true
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Payment,
                            iconTint = Color(0xFF4CAF50),
                            label = "Total Settlements",
                            value = adminOverview.totalSettlements?.let { "₦${String.format("%.0f", it)}" } ?: "N/A",
                            trend = "Collected",
                            trendPositive = true
                        )
                    }

                    if (adminOverview.activeSessions != null || adminOverview.pendingActivations != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            adminOverview.activeSessions?.let { sessions ->
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Computer,
                                    iconTint = Color(0xFF0288D1),
                                    label = "Active Sessions",
                                    value = sessions.toString(),
                                    trend = "Online now",
                                    trendPositive = sessions > 0
                                )
                            } ?: Box(modifier = Modifier.weight(1f))

                            adminOverview.pendingActivations?.let { pending ->
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PendingActions,
                                    iconTint = if (pending > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
                                    label = "Pending Activations",
                                    value = pending.toString(),
                                    trend = if (pending == 0) "All activated" else "Need attention",
                                    trendPositive = pending == 0
                                )
                            } ?: Box(modifier = Modifier.weight(1f))
                        }
                    }

                    adminOverview.attendancePercent?.let { attendance ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CheckCircle,
                                iconTint = Color(0xFF0288D1),
                                label = "Attendance Rate",
                                value = "${String.format("%.1f", attendance)}%",
                                trend = if (attendance >= 90) "Excellent" else "Needs attention",
                                trendPositive = attendance >= 90
                            )
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            "STAFF" -> {
                dashboardData.staffOverview?.let { staffOverview ->
                    DashboardSectionHeader("Staff Overview")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Class,
                            iconTint = Color(0xFF4A5FBF),
                            label = "My Classes",
                            value = staffOverview.classesCount?.toString() ?: "0",
                            trend = "Active classes",
                            trendPositive = true
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            iconTint = Color(0xFFFF9800),
                            label = "Pending Tasks",
                            value = staffOverview.pendingTasks?.toString() ?: "0",
                            trend = if ((staffOverview.pendingTasks ?: 0) == 0) "All caught up!" else "Action needed",
                            trendPositive = (staffOverview.pendingTasks ?: 0) == 0
                        )
                    }
                }
            }
            "PARENT" -> {
                dashboardData.parentOverview?.let { parentOverview ->
                    DashboardSectionHeader("Parent Overview")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.FamilyRestroom,
                            iconTint = Color(0xFF4A5FBF),
                            label = "My Children",
                            value = parentOverview.childrenCount?.toString() ?: "0",
                            trend = "Enrolled",
                            trendPositive = true
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Payment,
                            iconTint = Color(0xFFC62828),
                            label = "Due Payments",
                            value = parentOverview.duePayments?.let { "₦${String.format("%.2f", it)}" } ?: "₦0.00",
                            trend = if ((parentOverview.duePayments ?: 0.0) == 0.0) "All paid up!" else "Payment due",
                            trendPositive = (parentOverview.duePayments ?: 0.0) == 0.0
                        )
                    }
                }
            }
            "STUDENT" -> {
                dashboardData.studentOverview?.let { studentOverview ->
                    DashboardSectionHeader("Student Overview")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Grade,
                            iconTint = Color(0xFF4A5FBF),
                            label = "Current GPA",
                            value = studentOverview.currentGpa?.let { String.format("%.2f", it) } ?: "N/A",
                            trend = "This semester",
                            trendPositive = (studentOverview.currentGpa ?: 0.0) >= 3.0
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CheckCircle,
                            iconTint = Color(0xFF2E7D32),
                            label = "My Attendance",
                            value = studentOverview.attendancePercent?.let { "${String.format("%.1f", it)}%" } ?: "N/A",
                            trend = if ((studentOverview.attendancePercent ?: 0.0) >= 90) "Excellent" else "Improve",
                            trendPositive = (studentOverview.attendancePercent ?: 0.0) >= 90
                        )
                    }
                }
            }
        }

        if (dashboardData.role.uppercase() in listOf("SCHOOL_ADMIN", "ADMIN", "STAFF")) {
            DashboardSectionHeader("Financial Health")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Financial Overview", fontSize = 13.sp, color = Color(0xFF555555))
                        Text("View Details", fontSize = 12.sp, color = Color(0xFF4A5FBF), fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        dashboardData.financialHealth.collectionRatePercent?.let { rate ->
                            FinancialMetricRow("Collection Rate", "${String.format("%.1f", rate)}%")
                        }
                        dashboardData.financialHealth.totalOutstandingFees?.let { fees ->
                            FinancialMetricRow("Outstanding Fees", "₦${String.format("%.2f", fees)}")
                        }
                        dashboardData.financialHealth.monthlyRevenue?.let { revenue ->
                            FinancialMetricRow("Monthly Revenue", "₦${String.format("%.2f", revenue)}")
                        }
                        dashboardData.financialHealth.netCashFlow?.let { cashFlow ->
                            FinancialMetricRow(
                                "Net Cash Flow",
                                "₦${String.format("%.2f", cashFlow)}",
                                isPositive = cashFlow >= 0
                            )
                        }
                    }
                }
            }
        }

        val validAlerts = dashboardData.criticalAlerts.filter { alert ->
            alert.title.isNotBlank() && alert.alertType.isNotBlank()
        }

        if (validAlerts.isNotEmpty()) {
            DashboardSectionHeader("Critical Alerts")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    validAlerts.forEach { alert ->
                        AlertRowFromApi(alert)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {},
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Clear All Notifications", color = Color(0xFF4A5FBF), fontSize = 13.sp)
                    }
                }
            }
        }

        DashboardSectionHeader("Quick Actions")
        when (dashboardData.role.uppercase()) {
            "SCHOOL_ADMIN", "ADMIN" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(Modifier.weight(1f), Icons.Default.PersonAdd, "Add Student")
                    QuickActionCard(Modifier.weight(1f), Icons.Default.Payment, "Log Payment")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(Modifier.weight(1f), Icons.Default.Campaign, "Post Announcement")
                    QuickActionCard(Modifier.weight(1f), Icons.Default.TableChart, "View Timetable")
                }
            }
            "STAFF" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(Modifier.weight(1f), Icons.AutoMirrored.Filled.Assignment, "Grade Assignments")
                    QuickActionCard(Modifier.weight(1f), Icons.Default.CheckCircle, "Take Attendance")
                }
            }
            "PARENT" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(Modifier.weight(1f), Icons.Default.Payment, "Make Payment")
                    QuickActionCard(Modifier.weight(1f), Icons.AutoMirrored.Filled.Message, "Contact Teacher")
                }
            }
            "STUDENT" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(Modifier.weight(1f), Icons.AutoMirrored.Filled.Assignment, "View Assignments")
                    QuickActionCard(Modifier.weight(1f), Icons.Default.Grade, "Check Grades")
                }
            }
        }

        if (dashboardData.upcomingEvents.isNotEmpty()) {
            DashboardSectionHeader("Upcoming Events")
            dashboardData.upcomingEvents.forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A5FBF)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Upcoming Event", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        val eventDetails = buildString {
                            event.startAt?.let { append(it) }
                            event.location?.let {
                                if (isNotEmpty()) append(" • ")
                                append(it)
                            }
                            event.description?.let {
                                if (isNotEmpty()) append(" • ")
                                append(it)
                            }
                        }
                        if (eventDetails.isNotEmpty()) {
                            Text(eventDetails, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "View Event",
                                color = Color(0xFF4A5FBF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun getAlertColor(type: String): Color {
    return when (type.uppercase()) {
        "ERROR" -> Color(0xFFEF5350)
        "WARNING" -> Color(0xFFFF9800)
        "INFO" -> Color(0xFF4A5FBF)
        "SUCCESS" -> Color(0xFF2E7D32)
        else -> Color(0xFF4A5FBF)
    }
}

private fun getAlertBackgroundColor(type: String): Color {
    return when (type.uppercase()) {
        "ERROR" -> Color(0xFFFFEBEE)
        "WARNING" -> Color(0xFFFFF3E0)
        "INFO" -> Color(0xFFE8EAF6)
        "SUCCESS" -> Color(0xFFE8F5E9)
        else -> Color(0xFFE8EAF6)
    }
}

private fun getAlertIcon(type: String): ImageVector {
    return when (type.uppercase()) {
        "ERROR" -> Icons.Default.Error
        "WARNING" -> Icons.Default.Warning
        "INFO" -> Icons.Default.Info
        "SUCCESS" -> Icons.AutoMirrored.Filled.Assignment
        else -> Icons.Default.Info
    }
}

@Composable
private fun AlertRowFromApi(alert: CriticalAlert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(getAlertBackgroundColor(alert.alertType))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            getAlertIcon(alert.alertType),
            contentDescription = null,
            tint = getAlertColor(alert.alertType),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(alert.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
            if (alert.description.isNotBlank()) {
                Text(alert.description, fontSize = 11.sp, color = Color.Gray)
            }
            alert.affectedCount?.let { count ->
                if (count > 0) {
                    Text("Affects $count items", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun FinancialMetricRow(
    label: String,
    value: String,
    isPositive: Boolean? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF555555))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = when (isPositive) {
                true -> Color(0xFF2E7D32)
                false -> Color(0xFFC62828)
                null -> Color(0xFF1E293B)
            }
        )
    }
}

@Composable
private fun DashboardSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E293B)
    )
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    trend: String,
    trendPositive: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Text(
                    label,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 13.sp,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(Modifier.height(4.dp))
            Text(
                trend,
                fontSize = 10.sp,
                color = if (trendPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A5FBF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color(0xFF4A5FBF), modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
        }
    }
}
