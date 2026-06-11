package com.haneef.school.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Palette based on screenshot
val BrandBlue = Color(0xFF0052D4)
val LightBlueBg = Color(0xFFF0F4FA)
val InfoBoxBg = Color(0xFFE3EDFA)
val TextDark = Color(0xFF1E293B)
val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivateAccountScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EduManage",
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { /* Handle search */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBlueBg)
            )
        },
        containerColor = LightBlueBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // App Shield Logo Placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Using standard icon placeholder for the shield logo
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Logo",
                    tint = BrandBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Main White Card Content
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter OTP",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "We've sent a 6-digit activation code to admin@4school.ng. Please enter it below to proceed.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 6-Digit Code Inputs
                    OtpInputField()

                    Spacer(modifier = Modifier.height(24.dp))

                    // Verify Button
                    Button(
                        onClick = { /* Handle Verification */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Verify OTP",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Resend Code Link
                    Text(
                        text = "Didn't receive the code?",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Resend Code",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                        modifier = Modifier.clickable { /* Handle resend */ }
                    )
                }
            }

            // Info / Spam Folder Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InfoBoxBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = BrandBlue,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "If you don't see the email in your inbox, please check your Spam or Promotions folder. The code is valid for 10 minutes.",
                    fontSize = 13.sp,
                    color = BrandBlue,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Back to Login Footer Button
            Row(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clickable { /* Handle Navigation Back */ },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Back to Login",
                    fontSize = 14.sp,
                    color = TextDark,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun OtpInputField() {
    // Basic structural layout for the 6 boxes
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(6) { index ->
            var textState by remember { mutableStateOf("") }

            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 48.dp)
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Invisible/minimalist TextField wrapper inside the square block
                // For a robust system, you'd link focus-requests between these blocks
                BasicTextField(
                    value = textState,
                    onValueChange = { if (it.length <= 1) textState = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewActivateAccountScreen() {
    ActivateAccountScreen()
}