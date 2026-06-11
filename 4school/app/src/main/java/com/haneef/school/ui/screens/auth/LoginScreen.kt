package com.haneef.school.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VerifiedUser
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
fun LoginScreen(
    onLoginClick: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onActivateNow: () -> Unit = {},
    onSignUp: () -> Unit = {}
) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF2F5BEA)
    val darkBlue = Color(0xFF1D2D8F)
    val background = Color(0xFFF4F6FB)
    val cardBackground = Color.White
    val borderColor = Color(0xFFE3E6EF)
    val textGray = Color(0xFF6B7280)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBackground
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Logo Box
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = darkBlue,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "4School",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Academic Precision & Control",
                        fontSize = 14.sp,
                        color = textGray
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = "Sign In",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Access your administrative dashboard",
                            fontSize = 14.sp,
                            color = textGray
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Email or Staff ID")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryBlue,
                                unfocusedBorderColor = borderColor
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Password")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null
                                )
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Forgot Password?",
                            color = primaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { onForgotPassword() }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Login Button
                        Button(
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue
                            )
                        ) {
                            Text(
                                text = "Sign In  →",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Divider(
                                modifier = Modifier.weight(1f),
                                color = borderColor
                            )

                            Text(
                                text = "  NEW HERE?  ",
                                color = textGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Divider(
                                modifier = Modifier.weight(1f),
                                color = borderColor
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Bottom Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            BottomActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Activate Now",
                                icon = Icons.Outlined.VerifiedUser,
                                onClick = onActivateNow
                            )

                            BottomActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Sign Up",
                                icon = Icons.Outlined.PersonAdd,
                                onClick = onSignUp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "© 2024 4school.ng. All rights reserved.",
                fontSize = 12.sp,
                color = textGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Privacy Policy   •   Terms of Service",
                fontSize = 12.sp,
                color = textGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BottomActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {

    Card(
        modifier = modifier
            .height(95.dp)
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = Color(0xFFE3E6EF),
                shape = RoundedCornerShape(14.dp)
            ),
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
                tint = Color(0xFF2F5BEA),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
@Preview(
    name = "Login Screen - Light",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun LoginScreenPreview() {
    MaterialTheme {
        Surface {
            LoginScreen()
        }
    }
}

@Preview(
    name = "Login Screen - Dark",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun LoginScreenDarkPreview() {
    MaterialTheme {
        Surface {
            LoginScreen()
        }
    }
}