package com.haneef.school.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.ui.components.PhoneNumberInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onSaveClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf("Dr. Samuel Adebayo") }
    var email by remember { mutableStateOf("s.adebayo@centralhigh.edu.ng") }
    var phoneNumber by remember { mutableStateOf("803 123 4567") }
    var countryCode by remember { mutableStateOf("+234") }
    var department by remember { mutableStateOf("Administration") }
    var isLoading by remember { mutableStateOf(false) }
    
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
                    text = "Edit Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Gray
                    )
                }
            },
            actions = {
                Text(
                    text = "4school.ng",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 16.dp)
                )
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
            
            // Profile Photo Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Profile image placeholder
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A5FBF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SA",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    // Camera icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A5FBF))
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Update Photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { /* Handle photo update */ }
                ) {
                    Text(
                        text = "Update Photo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4A5FBF)
                    )
                }
                
                Text(
                    text = "JPG or PNG, max 2MB",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Form Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Full Name Field
                ProfileInputField(
                    label = "Full Name",
                    value = fullName,
                    onValueChange = { fullName = it },
                    leadingIcon = Icons.Default.Person
                )
                
                // Email Field
                ProfileInputField(
                    label = "Email Address",
                    value = email,
                    onValueChange = { email = it },
                    leadingIcon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email
                )
                
                // Phone Number Field with Country Code
                PhoneNumberInput(
                    label = "Phone Number",
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                    onPhoneNumberChange = { phoneNumber = it },
                    onCountryCodeChange = { countryCode = it },
                    placeholder = "Enter phone number"
                )
                
                // Department Field
                DepartmentDropdownField(
                    label = "Department",
                    value = department,
                    onValueChange = { department = it }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // System Role Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF4A5FBF),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "System Role: Super Admin",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4A5FBF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Profile changes are logged and visible to school auditors to maintain administrative precision.",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Save Changes Button
            Button(
                onClick = {
                    isLoading = true
                    // Simulate save operation
                    onSaveClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Changes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)),
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color(0xFFE5E7EB),
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartmentDropdownField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val departments = listOf(
        "Administration",
        "Academic Affairs",
        "Student Affairs",
        "Finance",
        "Human Resources",
        "IT Department"
    )
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9FAFB))
                    .menuAnchor(),
                readOnly = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color(0xFFE5E7EB)
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                departments.forEach { department ->
                    DropdownMenuItem(
                        text = { Text(department) },
                        onClick = {
                            onValueChange(department)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}