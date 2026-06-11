package com.haneef.school.ui.screens.auth

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignUpScreen(
    onSignUpClick: () -> Unit = {},
    onSignInClick: () -> Unit = {}
) {

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var schoolCode by remember { mutableStateOf("") }

    var selectedRole by remember { mutableStateOf("Staff") }

    val primaryBlue = Color(0xFF1456DB)
    val darkBlue = Color(0xFF081C86)
    val background = Color(0xFFF5F7FB)
    val borderColor = Color(0xFFDADFEA)
    val textGray = Color(0xFF667085)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(14.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Logo
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                Color.White,
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = darkBlue,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "Create Account",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Empowering the future of education at\n4school.ng",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = textGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        )
                    ) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {

                            Text(
                                text = "Register as",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = darkBlue
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                RoleCard(
                                    modifier = Modifier.weight(1f),
                                    title = "School\nOwner",
                                    icon = Icons.Outlined.School,
                                    selected = selectedRole == "School Owner",
                                    onClick = {
                                        selectedRole = "School Owner"
                                    }
                                )

                                RoleCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Staff",
                                    icon = Icons.Outlined.Badge,
                                    selected = selectedRole == "Staff",
                                    onClick = {
                                        selectedRole = "Staff"
                                    }
                                )

                                RoleCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Parent",
                                    icon = Icons.Outlined.Groups,
                                    selected = selectedRole == "Parent",
                                    onClick = {
                                        selectedRole = "Parent"
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // First and Last Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text("First Name")
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryBlue,
                                        unfocusedBorderColor = borderColor
                                    )
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text("Last Name")
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryBlue,
                                        unfocusedBorderColor = borderColor
                                    )
                                )
                            }

                            if (selectedRole == "Staff" || selectedRole == "Parent") {
                                Spacer(modifier = Modifier.height(18.dp))

                                // School Code
                                OutlinedTextField(
                                    value = schoolCode,
                                    onValueChange = { schoolCode = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("School Code")
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryBlue,
                                        unfocusedBorderColor = borderColor
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Email
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text("Email Address")
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryBlue,
                                    unfocusedBorderColor = borderColor
                                )
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Password
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text("Password")
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = textGray
                                    )
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryBlue,
                                    unfocusedBorderColor = borderColor
                                )
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Button(
                                onClick = onSignUpClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryBlue
                                )
                            ) {

                                Text(
                                    text = "Sign Up  →",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(34.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Already have an account?",
                            color = textGray,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "Sign In",
                            color = primaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable {
                                onSignInClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoleCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {

    val primaryBlue = Color(0xFF1456DB)
    val borderColor = if (selected) primaryBlue else Color(0xFFDADFEA)

    Card(
        modifier = modifier
            .height(82.dp)
            .border(
                width = 1.4.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview(
    name = "Signup Screen",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun SignUpScreenPreview() {

    MaterialTheme {
        Surface {
            SignUpScreen()
        }
    }
}

@Preview(
    name = "Signup Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    showSystemUi = true
)
@Composable
fun SignUpScreenDarkPreview() {

    MaterialTheme {
        Surface {
            SignUpScreen()
        }
    }
}
