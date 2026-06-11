package com.haneef.school.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val badge: String? = null,
    val subItems: List<MenuItem> = emptyList()
)

@Composable
fun SidebarMenu(
    modifier: Modifier = Modifier,
    onMenuItemClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var expandedSections by remember { mutableStateOf(setOf<String>()) }
    
    val menuItems = listOf(
        MenuItem(
            title = "Dashboard",
            icon = Icons.Default.Dashboard,
            route = "dashboard"
        ),
        MenuItem(
            title = "School Setup",
            icon = Icons.Default.School,
            route = "school_setup",
            subItems = listOf(
                MenuItem("Overview", Icons.Default.Visibility, "school_setup/overview"),
                MenuItem("School Details", Icons.Default.Info, "school_setup/details"),
                MenuItem("Academic Structure", Icons.Default.AccountTree, "school_setup/academic"),
                MenuItem("Subjects", Icons.AutoMirrored.Filled.MenuBook, "school_setup/subjects"),
                MenuItem("Subscriptions", Icons.Default.Subscriptions, "school_setup/subscriptions")
            )
        ),
        MenuItem(
            title = "Community",
            icon = Icons.Default.Group,
            route = "community",
            subItems = listOf(
                MenuItem("Overview", Icons.Default.Visibility, "community/overview"),
                MenuItem("Students", Icons.Default.School, "community/students"),
                MenuItem("Staff", Icons.Default.Badge, "community/staff"),
                MenuItem("Parents", Icons.Default.FamilyRestroom, "community/parents"),
                MenuItem("Users", Icons.Default.People, "community/users")
            )
        ),
        MenuItem(
            title = "Schedule",
            icon = Icons.Default.Schedule,
            route = "schedule",
            subItems = listOf(
                MenuItem("Overview", Icons.Default.Visibility, "schedule/overview"),
                MenuItem("Sessions & Terms", Icons.Default.DateRange, "schedule/sessions"),
                MenuItem("Calendar", Icons.Default.CalendarMonth, "schedule/calendar"),
                MenuItem("Timetable", Icons.Default.TableChart, "schedule/timetable")
            )
        ),
        MenuItem(
            title = "Finance",
            icon = Icons.Default.AccountBalance,
            route = "finance",
            subItems = listOf(
                MenuItem("📊 Overview", Icons.Default.Analytics, "finance/overview"),
                MenuItem("💳 Fee Items", Icons.Default.Receipt, "finance/fee_items"),
                MenuItem("🔒 Optional Fees", Icons.Default.Lock, "finance/optional_fees"),
                MenuItem("💰 Payments", Icons.Default.Payment, "finance/payments", badge = "12 NEW"),
                MenuItem("📈 Analytics", Icons.AutoMirrored.Filled.TrendingUp, "finance/analytics")
            )
        ),
        MenuItem(
            title = "Assessments",
            icon = Icons.AutoMirrored.Filled.Assignment,
            route = "assessments",
            subItems = listOf(
                MenuItem("Overview", Icons.Default.Visibility, "assessments/overview"),
                MenuItem("Examinations", Icons.Default.Quiz, "assessments/examinations"),
                MenuItem("Scoring", Icons.Default.Grade, "assessments/scoring"),
                MenuItem("Reports", Icons.Default.Assessment, "assessments/reports")
            )
        ),
        MenuItem(
            title = "Messaging",
            icon = Icons.AutoMirrored.Filled.Message,
            route = "messaging",
            subItems = listOf(
                MenuItem("Internal", Icons.Default.Forum, "messaging/internal"),
                MenuItem("WhatsApp", Icons.AutoMirrored.Filled.Chat, "messaging/whatsapp"),
                MenuItem("SMS", Icons.Default.Sms, "messaging/sms")
            )
        )
    )
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
    ) {
        // Header with school info
        SchoolHeader()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // User info
        UserInfo(onProfileClick = onProfileClick)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Menu items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(menuItems) { item ->
                MenuItemComponent(
                    item = item,
                    isExpanded = expandedSections.contains(item.route),
                    onToggleExpanded = { route ->
                        expandedSections = if (expandedSections.contains(route)) {
                            expandedSections - route
                        } else {
                            expandedSections + route
                        }
                    },
                    onItemClick = onMenuItemClick
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign out button
        Button(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFFE53E3E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sign Out",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer
        Text(
            text = "4School",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("HELP", fontSize = 10.sp, color = Color.Gray)
            Text("PRIVACY", fontSize = 10.sp, color = Color.Gray)
            Text("SUPPORT", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun SchoolHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // School logo
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A5FBF))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛡️",
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "EduManage Academy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "EDUADMIN PRO",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        IconButton(
            onClick = { /* Handle close */ }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close menu",
                tint = Color.Gray
            )
        }
    }
}@Composable
private fun UserInfo(onProfileClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFFE8F4FD),
                RoundedCornerShape(12.dp)
            )
            .clickable { onProfileClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF4A5FBF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AU",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Admin User",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = "Principal Office",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        IconButton(
            onClick = { /* Handle settings */ }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MenuItemComponent(
    item: MenuItem,
    isExpanded: Boolean,
    onToggleExpanded: (String) -> Unit,
    onItemClick: (String) -> Unit
) {
    Column {
        // Main menu item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.subItems.isNotEmpty()) {
                        onToggleExpanded(item.route)
                    } else {
                        onItemClick(item.route)
                    }
                }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = item.title,
                fontSize = 14.sp,
                color = Color(0xFF374151),
                modifier = Modifier.weight(1f)
            )
            
            // Badge if present
            item.badge?.let { badge ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A5FBF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Expand/collapse icon for items with subitems
            if (item.subItems.isNotEmpty()) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Sub-items
        if (isExpanded && item.subItems.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 32.dp)
            ) {
                item.subItems.forEach { subItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(subItem.route) }
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = subItem.icon,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = subItem.title,
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Badge for sub-items
                        subItem.badge?.let { badge ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A5FBF)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Sidebar menu",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun SideMenu() {
    MaterialTheme {
        Surface {
            SidebarMenu(
                modifier = Modifier,
                onMenuItemClick = {},
                onSignOut = {}
            )
        }
    }
}