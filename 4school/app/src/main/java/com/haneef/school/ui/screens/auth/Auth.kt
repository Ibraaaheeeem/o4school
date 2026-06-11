package com.haneef.school.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.ui.components.PhoneNumberInput
import com.haneef.school.ui.components.AddressInput
import com.haneef.school.viewmodel.AuthViewModel
import com.haneef.school.data.models.AuthNextRoute
import com.haneef.school.utils.ValidationUtils
import org.koin.androidx.compose.koinViewModel

enum class AuthState {
    LOGIN,
    SIGNUP,
    OTP,
    ACTIVATE_EMAIL,
    PROFILE,
    EDIT_PROFILE
}

data class AuthScreenState(
    val currentState: AuthState = AuthState.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+234",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "Nigeria",
    val otp: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val signupSuccessMessage: String? = null,  // shown in dialog before navigating to OTP
    val otpSuccessMessage: String? = null,  // shown after OTP verify before login
    val loginSuccessMessage: String? = null  // shown after login before navigating to dashboard
)

private const val TEST_FULL_NAME = "Test User"
private const val TEST_EMAIL = "test@school.ng"
private const val TEST_PHONE = "8012345678"
private const val TEST_COUNTRY_CODE = "+234"
private const val TEST_ADDRESS_1 = "12 Test Street"
private const val TEST_ADDRESS_2 = "Suite 4B"
private const val TEST_CITY = "Lagos"
private const val TEST_STATE = "Lagos"
private const val TEST_COUNTRY = "Nigeria"
private const val TEST_PASSWORD = "Password123!"
private const val TEST_CONFIRM_PASSWORD = "Password123!"

data class SignupFieldErrors(
    val fullName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    initialState: AuthState = AuthState.LOGIN,
    onLoginSuccess: () -> Unit = {},
    onSignupSuccess: () -> Unit = {},
    onProfileUpdate: () -> Unit = {},
    onBackToApp: () -> Unit = {},
    authViewModel: AuthViewModel = koinViewModel()
) {
    var state by remember { mutableStateOf(AuthScreenState(currentState = initialState)) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    // Observe ViewModel state
    val uiState by authViewModel.uiState.collectAsState()
    
    // Update loading state from ViewModel
    LaunchedEffect(uiState.isLoading) {
        state = state.copy(isLoading = uiState.isLoading)
    }
    
    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            state = state.copy(errorMessage = error)
        }
    }

    // Signup success dialog
    if (state.signupSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(signupSuccessMessage = null, currentState = AuthState.OTP)
            },
            icon = {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4A5FBF),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text("Account Created!", fontWeight = FontWeight.Bold) },
            text = { Text(state.signupSuccessMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        state = state.copy(signupSuccessMessage = null, currentState = AuthState.OTP)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                ) {
                    Text("Proceed to Verify")
                }
            }
        )
    }

    // OTP verification success dialog
    if (state.otpSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(otpSuccessMessage = null, currentState = AuthState.LOGIN, otp = "")
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4A5FBF),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text("Verification Successful", fontWeight = FontWeight.Bold) },
            text = { Text(state.otpSuccessMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        state = state.copy(otpSuccessMessage = null, currentState = AuthState.LOGIN, otp = "")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                ) {
                    Text("Continue to Sign In")
                }
            }
        )
    }

    // Login success dialog
    if (state.loginSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(loginSuccessMessage = null)
                onLoginSuccess()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4A5FBF),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text("Welcome Back!", fontWeight = FontWeight.Bold) },
            text = { Text(state.loginSuccessMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        state = state.copy(loginSuccessMessage = null)
                        onLoginSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
                ) {
                    Text("Go to Dashboard")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Logo and Branding (only show for auth flows, not profile)
        if (state.currentState in listOf(AuthState.LOGIN, AuthState.SIGNUP, AuthState.OTP, AuthState.ACTIVATE_EMAIL)) {
            AppBranding()
            Spacer(modifier = Modifier.height(48.dp))
        }
        
        when (state.currentState) {
            AuthState.LOGIN -> LoginContent(
                state = state,
                onStateChange = { state = it },
                onLoginClick = { 
                    // Handle login with ViewModel
                    authViewModel.login(
                        email = state.email,
                        password = state.password,
                        onSuccess = { nextRoute ->
                            val message = authViewModel.uiState.value.loginResponse?.message
                                ?: "You have successfully signed in."
                            when (nextRoute) {
                                AuthNextRoute.DASHBOARD -> {
                                    state = state.copy(loginSuccessMessage = message, errorMessage = null)
                                }
                                AuthNextRoute.VERIFY_EMAIL,
                                AuthNextRoute.VERIFY_OTP -> {
                                    state = state.copy(currentState = AuthState.OTP)
                                }
                                AuthNextRoute.PROFILE_COMPLETE -> {
                                    state = state.copy(loginSuccessMessage = message, errorMessage = null)
                                }
                                else -> {
                                    state = state.copy(loginSuccessMessage = message, errorMessage = null)
                                }
                            }
                        },
                        onError = { error ->
                            state = state.copy(errorMessage = error, isLoading = false)
                        }
                    )
                },
                onSignupClick = { 
                    state = state.copy(currentState = AuthState.SIGNUP) 
                },
                onForgotPasswordClick = { 
                    state = state.copy(currentState = AuthState.ACTIVATE_EMAIL) 
                },
                onActivateAccountClick = { 
                    state = state.copy(currentState = AuthState.ACTIVATE_EMAIL) 
                }
            )
            
            AuthState.SIGNUP -> SignupContent(
                state = state,
                onStateChange = { state = it },
                onSignupClick = { 
                    Log.d("AuthScreen", "=== SIGNUP BUTTON CLICKED ===")
                    Log.d("AuthScreen", "Current form state: Email=${state.email}, Name=${state.fullName}")
                    
                    // Handle signup with ViewModel
                    authViewModel.signUp(
                        state = state,
                        onSuccess = { nextRoute ->
                            Log.d("AuthScreen", "Signup success callback received with route: $nextRoute")
                            // Show response message in dialog; navigation happens on dialog dismiss
                            val message = authViewModel.uiState.value.signUpResponse?.message
                                ?: "Your account has been created successfully."
                            state = state.copy(signupSuccessMessage = message)
                        },
                        onError = { error ->
                            Log.e("AuthScreen", "Signup error callback received: $error")
                            state = state.copy(errorMessage = error, isLoading = false)
                        }
                    )
                },
                onBackToLoginClick = { 
                    state = state.copy(currentState = AuthState.LOGIN) 
                }
            )
            
            AuthState.OTP -> OTPContent(
                state = state,
                onStateChange = { state = it },
                onVerifyClick = { 
                    // Handle OTP verification with ViewModel
                    // next_route after signup OTP is always SignIn
                    authViewModel.verifyOtp(
                        email = state.email,
                        otp = state.otp,
                        nextRoute = AuthNextRoute.SIGN_IN,
                        onSuccess = { response ->
                            // show backend message first, then proceed to login after dialog confirm
                            state = state.copy(
                                otpSuccessMessage = response.message.ifBlank { "OTP verified successfully." },
                                errorMessage = null
                            )
                        },
                        onError = { error ->
                            state = state.copy(errorMessage = error, isLoading = false)
                        }
                    )
                },
                onResendClick = { 
                    // Handle resend OTP with ViewModel
                    authViewModel.resendOtp(
                        email = state.email,
                        onSuccess = {
                            // Show success message
                        },
                        onError = { error ->
                            state = state.copy(errorMessage = error)
                        }
                    )
                },
                onBackClick = { 
                    state = state.copy(currentState = AuthState.SIGNUP) 
                }
            )
            
            AuthState.ACTIVATE_EMAIL -> ActivateEmailContent(
                state = state,
                onStateChange = { state = it },
                onSendActivationClick = { email ->
                    // Handle forgot password with ViewModel
                    authViewModel.forgotPassword(
                        email = email,
                        onSuccess = {
                            state = state.copy(currentState = AuthState.LOGIN)
                        },
                        onError = { error ->
                            state = state.copy(errorMessage = error, isLoading = false)
                        }
                    )
                },
                onBackToSignInClick = { 
                    state = state.copy(currentState = AuthState.LOGIN) 
                }
            )
            
            AuthState.PROFILE -> ProfileContent(
                state = state,
                onStateChange = { state = it },
                onEditProfileClick = {
                    state = state.copy(currentState = AuthState.EDIT_PROFILE)
                },
                onBackClick = onBackToApp
            )
            
            AuthState.EDIT_PROFILE -> EditProfileContent(
                state = state,
                onStateChange = { state = it },
                onSaveClick = {
                    // Handle profile update
                    onProfileUpdate()
                    state = state.copy(currentState = AuthState.PROFILE)
                },
                onBackClick = {
                    state = state.copy(currentState = AuthState.PROFILE)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Support text (only show for auth flows, not profile)
        if (state.currentState in listOf(AuthState.LOGIN, AuthState.SIGNUP, AuthState.OTP, AuthState.ACTIVATE_EMAIL)) {
            Text(
                text = "Need help? Contact your administrator at\nsupport@school.ng",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AppBranding() {
    // Logo placeholder
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
    
    Text(
        text = "EduManage",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF4A5FBF)
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Reliable and organized school management\nsystems for modern education.",
        fontSize = 14.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onActivateAccountClick: () -> Unit
) {
    Text(
        text = "Welcome Back!",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Sign in to your account to continue",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Email Field
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EMAIL ADDRESS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = state.email,
            onValueChange = { onStateChange(state.copy(email = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your email", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Password Field
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PASSWORD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = state.password,
            onValueChange = { onStateChange(state.copy(password = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your password", color = Color.Gray) },
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { onStateChange(state.copy(isPasswordVisible = !state.isPasswordVisible)) }
                ) {
                    Icon(
                        imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password",
                        tint = Color(0xFF6B7280)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Forgot Password
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onForgotPasswordClick) {
            Text(
                text = "Forgot Password?",
                color = Color(0xFF4A5FBF),
                fontSize = 14.sp
            )
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Login Button
    Button(
        onClick = onLoginClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
        shape = RoundedCornerShape(12.dp),
        enabled = !state.isLoading
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = "Sign In",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Sign Up Link
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Don't have an account? ",
            color = Color.Gray,
            fontSize = 14.sp
        )
        TextButton(onClick = onSignupClick) {
            Text(
                text = "Sign Up",
                color = Color(0xFF4A5FBF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Activate Account Link
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onActivateAccountClick) {
            Text(
                text = "Activate Account",
                color = Color(0xFF4A5FBF),
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignupContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onSignupClick: () -> Unit,
    onBackToLoginClick: () -> Unit
) {
    val fullNameValue = state.fullName.ifEmpty { TEST_FULL_NAME }
    val emailValue = state.email.ifEmpty { TEST_EMAIL }
    val phoneValue = state.phoneNumber.ifEmpty { TEST_PHONE }
    val countryCodeValue = state.countryCode.ifEmpty { TEST_COUNTRY_CODE }
    val addressLine1Value = state.addressLine1.ifEmpty { TEST_ADDRESS_1 }
    val addressLine2Value = state.addressLine2.ifEmpty { TEST_ADDRESS_2 }
    val cityValue = state.city.ifEmpty { TEST_CITY }
    val stateValue = state.state.ifEmpty { TEST_STATE }
    val countryValue = state.country.ifEmpty { TEST_COUNTRY }
    val passwordValue = state.password.ifEmpty { TEST_PASSWORD }
    val confirmPasswordValue = state.confirmPassword.ifEmpty { TEST_CONFIRM_PASSWORD }

    // Track which fields have been touched to avoid showing errors before user interacts
    var touchedFullName by remember { mutableStateOf(false) }
    var touchedEmail by remember { mutableStateOf(false) }
    var touchedPhone by remember { mutableStateOf(false) }
    var touchedPassword by remember { mutableStateOf(false) }
    var touchedConfirmPassword by remember { mutableStateOf(false) }

    // Live per-field errors (only shown after field is touched)
    val fullNameError = if (touchedFullName) ValidationUtils.validateFullName(fullNameValue).errorMessage else null
    val emailError = if (touchedEmail) ValidationUtils.validateEmail(emailValue).errorMessage else null
    val phoneError = if (touchedPhone && phoneValue.isNotBlank()) ValidationUtils.validatePhoneNumber(phoneValue).errorMessage else null
    val passwordError = if (touchedPassword) ValidationUtils.validatePassword(passwordValue).errorMessage else null
    val confirmPasswordError = if (touchedConfirmPassword) ValidationUtils.validatePasswordMatch(passwordValue, confirmPasswordValue).errorMessage else null

    // Helper composable for field label + field + error text
    @Composable
    fun FieldError(error: String?) {
        if (error != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    Text(
        text = "Create Account",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Join EduManage to get started",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Full Name
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "FULL NAME",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = fullNameValue,
            onValueChange = {
                touchedFullName = true
                onStateChange(state.copy(fullName = it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your full name", color = Color.Gray) },
            isError = fullNameError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
        FieldError(fullNameError)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Email
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EMAIL ADDRESS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = emailValue,
            onValueChange = {
                touchedEmail = true
                onStateChange(state.copy(email = it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your email", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = emailError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
        FieldError(emailError)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Phone
    Column(modifier = Modifier.fillMaxWidth()) {
        PhoneNumberInput(
            label = "PHONE NUMBER",
            phoneNumber = phoneValue,
            countryCode = countryCodeValue,
            onPhoneNumberChange = {
                touchedPhone = true
                onStateChange(state.copy(phoneNumber = it))
            },
            onCountryCodeChange = { onStateChange(state.copy(countryCode = it)) },
            placeholder = "Enter your phone number"
        )
        FieldError(phoneError)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Address
    AddressInput(
        addressLine1 = addressLine1Value,
        addressLine2 = addressLine2Value,
        city = cityValue,
        state = stateValue,
        country = countryValue,
        onAddressLine1Change = { onStateChange(state.copy(addressLine1 = it)) },
        onAddressLine2Change = { onStateChange(state.copy(addressLine2 = it)) },
        onCityChange = { onStateChange(state.copy(city = it)) },
        onStateChange = { onStateChange(state.copy(state = it)) },
        onCountryChange = { onStateChange(state.copy(country = it)) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Password
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PASSWORD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = passwordValue,
            onValueChange = {
                touchedPassword = true
                onStateChange(state.copy(password = it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Create a password", color = Color.Gray) },
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError != null,
            trailingIcon = {
                IconButton(onClick = { onStateChange(state.copy(isPasswordVisible = !state.isPasswordVisible)) }) {
                    Icon(
                        imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF6B7280)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
        // Password strength hints when actively typing
        if (touchedPassword && passwordValue.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                PasswordHint("At least 8 characters", passwordValue.length >= 8)
                PasswordHint("One uppercase letter", passwordValue.any { it.isUpperCase() })
                PasswordHint("One lowercase letter", passwordValue.any { it.isLowerCase() })
                PasswordHint("One number", passwordValue.any { it.isDigit() })
            }
        } else {
            FieldError(passwordError)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Confirm Password
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CONFIRM PASSWORD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = confirmPasswordValue,
            onValueChange = {
                touchedConfirmPassword = true
                onStateChange(state.copy(confirmPassword = it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Confirm your password", color = Color.Gray) },
            visualTransformation = if (state.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = confirmPasswordError != null,
            trailingIcon = {
                IconButton(onClick = { onStateChange(state.copy(isConfirmPasswordVisible = !state.isConfirmPasswordVisible)) }) {
                    Icon(
                        imageVector = if (state.isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF6B7280)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
        FieldError(confirmPasswordError)
    }

    Spacer(modifier = Modifier.height(24.dp))

    // API-level error / info banner
    if (state.errorMessage != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Sign Up Button
    Button(
        onClick = {
            // Touch all fields to reveal any remaining errors before submitting
            touchedFullName = true
            touchedEmail = true
            touchedPhone = true
            touchedPassword = true
            touchedConfirmPassword = true
            onSignupClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
        shape = RoundedCornerShape(12.dp),
        enabled = !state.isLoading
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = "Already have an account? ", color = Color.Gray, fontSize = 14.sp)
        TextButton(onClick = onBackToLoginClick) {
            Text("Sign In", color = Color(0xFF4A5FBF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PasswordHint(text: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (met) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (met) Color(0xFF2E7D32) else Color(0xFF9E9E9E)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OTPContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Text(
        text = "Verify Your Email",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "We've sent a verification code to\n${state.email}",
        fontSize = 14.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // OTP Input
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "VERIFICATION CODE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = state.otp,
            onValueChange = { if (it.length <= 6) onStateChange(state.copy(otp = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter 6-digit code", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4A5FBF),
                unfocusedBorderColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Resend Code
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Didn't receive the code? ",
            color = Color.Gray,
            fontSize = 14.sp
        )
        TextButton(onClick = onResendClick) {
            Text(
                text = "Resend",
                color = Color(0xFF4A5FBF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Verify Button
    Button(
        onClick = onVerifyClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
        shape = RoundedCornerShape(12.dp),
        enabled = !state.isLoading && state.otp.length == 6
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = "Verify Email",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Back Button
    TextButton(
        onClick = onBackClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "← Back to Sign Up",
            fontSize = 16.sp,
            color = Color(0xFF4A5FBF)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivateEmailContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onSendActivationClick: (String) -> Unit,
    onBackToSignInClick: () -> Unit
) {
    Text(
        text = "Activate Email?",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "We need to verify your email address. Enter your email and we'll send you an activation link.",
        fontSize = 14.sp,
        color = Color.Gray,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Email Input
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EMAIL ADDRESS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = state.email,
            onValueChange = { onStateChange(state.copy(email = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your email address", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
        onClick = { onSendActivationClick(state.email) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
        shape = RoundedCornerShape(12.dp),
        enabled = !state.isLoading && state.email.isNotEmpty()
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
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
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Back to Sign In
    TextButton(
        onClick = onBackToSignInClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "← Back to Sign In",
            fontSize = 16.sp,
            color = Color(0xFF4A5FBF)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onEditProfileClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "← Back",
                    fontSize = 16.sp,
                    color = Color(0xFF4A5FBF)
                )
            }
            
            Text(
                text = "Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Profile Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF4A5FBF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AU",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // User Info
        Text(
            text = "Admin User",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Principal Office",
            fontSize = 16.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "admin@edumanage.school",
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Profile Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                ProfileDetailRow("Full Name", "Admin User")
                ProfileDetailRow("Email", "admin@edumanage.school")
                ProfileDetailRow("Phone", "+234 801 234 5678")
                ProfileDetailRow("Role", "Principal")
                ProfileDetailRow("Department", "Administration")
                ProfileDetailRow("Joined", "January 2024")
            }
        }
        
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
            Text(
                text = "Edit Profile",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(2f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileContent(
    state: AuthScreenState,
    onStateChange: (AuthScreenState) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "← Cancel",
                    fontSize = 16.sp,
                    color = Color(0xFF4A5FBF)
                )
            }
            
            Text(
                text = "Edit Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            TextButton(onClick = onSaveClick) {
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Profile Avatar with edit option
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
                    text = "AU",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Edit icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📷",
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Edit Form Fields
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Full Name Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "FULL NAME",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.fullName.ifEmpty { "Admin User" },
                    onValueChange = { onStateChange(state.copy(fullName = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Email Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "EMAIL ADDRESS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.email.ifEmpty { "admin@edumanage.school" },
                    onValueChange = { onStateChange(state.copy(email = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Phone Number Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "PHONE NUMBER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.phoneNumber.ifEmpty { "+234 801 234 5678" },
                    onValueChange = { onStateChange(state.copy(phoneNumber = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Save Changes Button
        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Save Changes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}
