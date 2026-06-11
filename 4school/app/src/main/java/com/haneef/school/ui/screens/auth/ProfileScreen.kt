package com.haneef.school.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "4school.ng",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.Gray
                    )
                }
            },
            actions = {
                // Profile avatar in top bar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5FBF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Profile Avatar with edit badge
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5FBF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SO",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Edit badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5FBF))
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User Name
            Text(
                text = "Samuel Olumide",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // User Role
            Text(
                text = "School Administrator",
                fontSize = 16.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Staff Identity Card
            StaffIdentityCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Personal Information Section
            ProfileSection(
                title = "PERSONAL INFORMATION",
                items = listOf(
                    ProfileItem("Academic Background", Icons.Default.School),
                    ProfileItem("Emergency Contacts", Icons.Default.ContactPhone)
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Account Settings Section
            ProfileSection(
                title = "ACCOUNT SETTINGS",
                items = listOf(
                    ProfileItem("Notification Preferences", Icons.Default.Notifications),
                    ProfileItem("Security & MFA", Icons.Default.Security)
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Edit Profile Button
            Button(
                onClick = onEditProfileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Change Password Button
            OutlinedButton(
                onClick = onChangePasswordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF4A5FBF)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Color(0xFF4A5FBF))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Change Password",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StaffIdentityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STAFF IDENTITY CARD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            Color(0xFF4A5FBF),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                
                Text(
                    text = "4school.ng",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Staff ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "STAFF ID",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ADM/2024/082",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                
                // QR Code placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR Code",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Department
            Text(
                text = "DEPARTMENT",
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Administrative Affairs",
                fontSize = 14.sp,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Contact Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Email Address",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "s.olumide@4school.edu.ng",
                                fontSize = 12.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Phone Number",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "+234 802 345 6789",
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    items: List<ProfileItem>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    ProfileItemRow(
                        item = item,
                        showDivider = index < items.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItemRow(
    item: ProfileItem,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFF4A5FBF),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = item.title,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        if (showDivider) {
            Divider(
                color = Color(0xFFF0F0F0),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 52.dp)
            )
        }
    }
}

data class ProfileItem(
    val title: String,
    val icon: ImageVector
)