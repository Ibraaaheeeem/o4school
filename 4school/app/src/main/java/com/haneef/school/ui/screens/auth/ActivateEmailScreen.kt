package com.haneef.school.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivateEmailScreen(
    onSendActivationClick: (String) -> Unit = {},
    onBackToSignInClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        
        // Logo placeholder (you'll need to add the actual logo resource)
        Card(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A5FBF))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 32.sp,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // App Title
        Text(
            text = "EduManage",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A5FBF)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = "Reliable and organized school management\nsystems for modern education.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Main Title
        Text(
            text = "Activate Email?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Description
        Text(
            text = "We need to verify your email address. Enter your email and we'll send you an activation link.",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Email Input
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "EMAIL ADDRESS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A5FBF),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        text = "Enter your email address",
                        color = Color.Gray
                    ) 
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Info message
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ℹ️",
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Please use your registered school email.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Send Activation Link Button
        Button(
            onClick = { onSendActivationClick(email) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A5FBF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Send Activation Link",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "→",
                fontSize = 16.sp,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Back to Sign In
        TextButton(
            onClick = onBackToSignInClick
        ) {
            Text(
                text = "←",
                fontSize = 16.sp,
                color = Color(0xFF4A5FBF),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Back to Sign In",
                fontSize = 16.sp,
                color = Color(0xFF4A5FBF)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Support text
        Text(
            text = "Need help? Contact your administrator at\nsupport@school.ng",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
@Preview(
    name = "Login Screen - Light",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun ActivateEmailScreenLight() {
    MaterialTheme {
        Surface {
            ActivateEmailScreen()
        }
    }
}

@Preview(
    name = "Login Screen - Dark",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun ActivateEmailScreenDark() {
    MaterialTheme {
        Surface {
            ActivateEmailScreen()
        }
    }
}